package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.crypto.VaultCryptoManager
import com.example.core.datastore.AppPreferences
import com.example.core.datastore.ThemeMode
import com.example.core.datastore.UserPreferencesStore
import com.example.data.remote.CloudSyncRepository
import com.example.data.repository.VaultRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface AppEntryState {
    data object Splash : AppEntryState
    data object Onboarding : AppEntryState
    data object AuthChoice : AppEntryState
    data class LockGate(val biometricEnabled: Boolean) : AppEntryState
    data class AuthenticatedReady(
        val userId: String,
        val displayName: String,
        val email: String,
        val photoUrl: String,
        val isGuest: Boolean,
        val isAdmin: Boolean
    ) : AppEntryState
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val auth: FirebaseAuth,
    private val preferencesStore: UserPreferencesStore,
    private val vaultRepository: VaultRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val cryptoManager: VaultCryptoManager
) : ViewModel() {

    private val _splashDone = MutableStateFlow(false)
    private val _isUnlocked = MutableStateFlow(false)
    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)

    val preferences: StateFlow<AppPreferences> = preferencesStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppPreferences())

    private val firebaseUserFlow = callbackFlow<FirebaseUser?> {
        val listener = FirebaseAuth.AuthStateListener { fa -> trySend(fa.currentUser) }
        faAdd(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private fun faAdd(listener: FirebaseAuth.AuthStateListener) {
        auth.addAuthStateListener(listener)
    }

    val entryState: StateFlow<AppEntryState> = combine(
        _splashDone,
        firebaseUserFlow,
        preferences,
        _isUnlocked,
        _isAdmin
    ) { splashDone, fbUser, prefs, unlocked, isAdmin ->
        if (!splashDone) return@combine AppEntryState.Splash

        val hasSession = fbUser != null || prefs.isGuestMode
        if (!prefs.hasCompletedOnboarding && !hasSession) {
            return@combine AppEntryState.Onboarding
        }
        if (!hasSession) {
            return@combine AppEntryState.AuthChoice
        }
        if (prefs.isAppLockEnabled && !unlocked) {
            return@combine AppEntryState.LockGate(prefs.isBiometricEnabled)
        }
        if (fbUser != null) {
            AppEntryState.AuthenticatedReady(
                userId = fbUser.uid,
                displayName = fbUser.displayName?.takeIf { it.isNotBlank() } ?: "YaadRakshak User",
                email = fbUser.email.orEmpty(),
                photoUrl = fbUser.photoUrl?.toString().orEmpty(),
                isGuest = false,
                isAdmin = isAdmin
            )
        } else {
            AppEntryState.AuthenticatedReady(
                userId = VaultRepository.GUEST_UID,
                displayName = "Guest Vault",
                email = "Local-only offline mode",
                photoUrl = "",
                isGuest = true,
                isAdmin = false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppEntryState.Splash)

    init {
        viewModelScope.launch {
            attemptSilentAutoSignIn()
            delay(850L)
            _splashDone.value = true
        }
        viewModelScope.launch {
            firebaseUserFlow.collect { user ->
                if (user != null) {
                    val adminActive = cloudSyncRepository.checkIsAdmin()
                    _isAdmin.value = adminActive
                    vaultRepository.ensureLocalUser(
                        uid = user.uid,
                        email = user.email.orEmpty(),
                        displayName = user.displayName ?: "YaadRakshak User",
                        photoUrl = user.photoUrl?.toString().orEmpty(),
                        isGuest = false,
                        role = if (adminActive) "admin" else "user"
                    )
                    cloudSyncRepository.ensureUserProfile(
                        displayName = user.displayName ?: "YaadRakshak User",
                        email = user.email,
                        photoUrl = user.photoUrl?.toString()
                    )
                } else {
                    _isAdmin.value = false
                }
            }
        }
    }

    private suspend fun attemptSilentAutoSignIn() {
        if (auth.currentUser != null) return
        val clientId = runCatching { appContext.getString(R.string.default_web_client_id) }.getOrNull()
            ?: return
        val credentialManager = CredentialManager.create(appContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
        runCatching {
            val result = credentialManager.getCredential(appContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                auth.signInWithCredential(authCredential).await()
                preferencesStore.setGuestMode(false)
                preferencesStore.setOnboardingCompleted(true)
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        val clientId = runCatching { activityContext.getString(R.string.default_web_client_id) }.getOrNull()
        if (clientId.isNullOrBlank()) {
            _authError.value = "Google Sign-In configuration missing (default_web_client_id)."
            return
        }
        val activity = activityContext as? Activity
        if (activity == null) {
            _authError.value = "Activity context required for Google Sign-In."
            return
        }

        _isSigningIn.value = true
        _authError.value = null
        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth.signInWithCredential(authCredential).await()
                    preferencesStore.setGuestMode(false)
                    preferencesStore.setOnboardingCompleted(true)
                    _isUnlocked.value = true
                } else {
                    _authError.value = "Unexpected credential type received."
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                _authError.value = "Sign-in was cancelled. Tap Sign in with Google to try again."
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                _authError.value = e.localizedMessage ?: "Google Sign-In failed."
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            vaultRepository.ensureLocalUser(
                uid = VaultRepository.GUEST_UID,
                email = "",
                displayName = "Guest Vault",
                photoUrl = "",
                isGuest = true
            )
            preferencesStore.setGuestMode(true)
            preferencesStore.setOnboardingCompleted(true)
            _isUnlocked.value = true
            _authError.value = null
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesStore.setOnboardingCompleted(true)
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun unlockWithBiometric() {
        _isUnlocked.value = true
    }

    fun verifyAppPin(pin: String): Boolean {
        val hash = cryptoManager.hashPin(pin)
        val ok = hash == preferences.value.pinHash
        if (ok) _isUnlocked.value = true
        return ok
    }

    fun configureAppLock(pin: String, enable: Boolean, biometric: Boolean) {
        viewModelScope.launch {
            val hash = if (pin.isNotBlank()) cryptoManager.hashPin(pin) else preferences.value.pinHash
            preferencesStore.setAppPin(hash, enable && hash.isNotBlank())
            preferencesStore.setBiometricEnabled(biometric)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesStore.setThemeMode(mode) }
    }

    fun setBackendBaseUrl(url: String) {
        viewModelScope.launch { preferencesStore.setBackendBaseUrl(url) }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setCloudSyncEnabled(enabled) }
    }

    fun signOut(context: Context) {
        val credentialManager = CredentialManager.create(context)
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                }.onFailure {
                    Log.w("Auth", "Failed to clear credential state: ${it.message}")
                }
                auth.signOut()
                preferencesStore.setGuestMode(false)
                _isUnlocked.value = false
            }
        }
    }
}
