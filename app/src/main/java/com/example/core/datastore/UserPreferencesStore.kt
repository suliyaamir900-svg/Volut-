package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "yaadrakshak_prefs")

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val hasCompletedOnboarding: Boolean = false,
    val isGuestMode: Boolean = false,
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val pinHash: String = "",
    val backendBaseUrl: String = BuildConfig.BACKEND_BASE_URL,
    val lastCloudSyncMillis: Long = 0L,
    val cloudSyncEnabled: Boolean = true
)

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val GUEST_MODE = booleanPreferencesKey("guest_mode")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        val LAST_CLOUD_SYNC = longPreferencesKey("last_cloud_sync")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.LIGHT.name
        val theme = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.LIGHT)
        AppPreferences(
            themeMode = theme,
            hasCompletedOnboarding = prefs[Keys.ONBOARDING_DONE] ?: false,
            isGuestMode = prefs[Keys.GUEST_MODE] ?: false,
            isAppLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            isBiometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            backendBaseUrl = prefs[Keys.BACKEND_BASE_URL]?.ifBlank { BuildConfig.BACKEND_BASE_URL }
                ?: BuildConfig.BACKEND_BASE_URL,
            lastCloudSyncMillis = prefs[Keys.LAST_CLOUD_SYNC] ?: 0L,
            cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = completed }
    }

    suspend fun setGuestMode(guest: Boolean) {
        context.dataStore.edit { it[Keys.GUEST_MODE] = guest }
    }

    suspend fun setAppPin(pinHash: String, enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.PIN_HASH] = pinHash
            it[Keys.APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setBackendBaseUrl(url: String) {
        val sanitized = url.trim().let { if (it.endsWith("/")) it else "$it/" }
        context.dataStore.edit { it[Keys.BACKEND_BASE_URL] = sanitized }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLOUD_SYNC_ENABLED] = enabled }
    }

    suspend fun recordCloudSync(timestampMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_CLOUD_SYNC] = timestampMillis }
    }

    suspend fun clearAllPreferences() {
        context.dataStore.edit { it.clear() }
    }
}
