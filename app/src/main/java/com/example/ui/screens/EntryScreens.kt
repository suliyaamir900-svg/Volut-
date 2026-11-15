package com.example.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.ui.components.GlassBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientPrimaryButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LocalGlassTokens
import com.example.ui.theme.RoseAlert

@Composable
fun SplashScreen() {
    GlassBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF131C31),
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(118.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_app_logo),
                        contentDescription = "YaadRakshak Emblem",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "YaadRakshak",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your Entire Life, Searchable in One Place.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

private data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onContinueGuest: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                title = "Your Entire Life, Offline-First",
                subtitle = "Store documents, receipts, warranties, voice notes, and contacts in an encrypted local Room vault that works anywhere—even without internet.",
                icon = Icons.Default.OfflineBolt,
                badge = "100% LOCAL VAULT"
            ),
            OnboardingPage(
                title = "Source-Grounded AI Memory Assistant",
                subtitle = "Ask natural-language questions and get precise answers backed by your saved documents, powered by Groq through your authenticated backend.",
                icon = Icons.Default.AutoAwesome,
                badge = "GROQ AI"
            ),
            OnboardingPage(
                title = "Keystore Encryption & Local Backups",
                subtitle = "Lock sensitive files in Private Vault with biometric & PIN protection, export AES-256 .yrbak backups locally, and verify identity with Google Sign-In.",
                icon = Icons.Default.Shield,
                badge = "ZERO-TRUST PRIVACY"
            )
        )
    }
    var pageIndex by remember { mutableIntStateOf(0) }
    val tokens = LocalGlassTokens.current

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(label = "YAADRAKSHAK", color = MaterialTheme.colorScheme.primary)
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text("Skip")
                }
            }

            AnimatedContent(targetState = pageIndex, label = "onboarding_slide") { idx ->
                val page = pages[idx]
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Image(
                            painter = painterResource(R.drawable.img_hero_saffron_1794757220661),
                            contentDescription = page.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (tokens.isDark) {
                                        Brush.verticalGradient(
                                            listOf(Color(0x33141210), Color(0xEB141210))
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(Color(0x22FFFFFF), Color(0xE6FFF7ED))
                                        )
                                    }
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(page.icon, contentDescription = null, tint = Color.White)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = page.badge,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = page.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    pages.indices.forEach { idx ->
                        val active = idx == pageIndex
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(6.dp)
                                .width(if (active) 28.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                GradientPrimaryButton(
                    text = if (pageIndex < pages.lastIndex) "Next" else "Continue to Sign In",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = {
                        if (pageIndex < pages.lastIndex) pageIndex++ else onFinish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "onboarding_next_button"
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onContinueGuest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("onboarding_guest_button")
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use Local-Only Guest Vault")
                }
            }
        }
    }
}

@Composable
fun AuthChoiceScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onGoogleSignIn: () -> Unit,
    onContinueGuest: () -> Unit
) {
    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFE25503),
                shadowElevation = 8.dp,
                modifier = Modifier.size(84.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.yaadrakshak_saffron_icon_1794757219427),
                    contentDescription = "YaadRakshak Logo",
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Welcome to YaadRakshak",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Both Users and Admin sign in here with Google. Firebase handles Auth & billing only—all personal memories, documents, receipts, and contacts stay 100% local on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                if (errorMessage != null) {
                    StatusPill(label = errorMessage, color = RoseAlert)
                    Spacer(Modifier.height(12.dp))
                }
                GradientPrimaryButton(
                    text = if (isLoading) "Signing in..." else "Sign in with Google",
                    enabled = !isLoading,
                    onClick = onGoogleSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "google_sign_in_button"
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onContinueGuest,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("guest_mode_button")
                ) {
                    Icon(Icons.Default.OfflineBolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Continue in Guest / Local-Only Mode")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Signing in with the authorized Admin Google account automatically unlocks the Admin Control Center. Vault records are always stored strictly on your device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AppLockGateScreen(
    biometricEnabled: Boolean,
    onVerifyPin: (String) -> Boolean,
    onBiometricSuccess: () -> Unit,
    title: String = "Unlock YaadRakshak"
) {
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    fun launchBiometricPrompt() {
        val activity = context as? FragmentActivity ?: return
        val manager = BiometricManager.from(activity)
        val canAuth = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) return

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onBiometricSuccess()
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock YaadRakshak Vault")
            .setSubtitle("Verify your identity to access your saved memories")
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) launchBiometricPrompt()
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .widthIn(max = 400.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (pinError) "Incorrect PIN. Try again." else "Enter your 4-digit Vault PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = if (pinError) RoseAlert else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { idx ->
                    val filled = idx < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                            )
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            val digits = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )
            digits.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    row.forEach { d ->
                        PinKeyButton(label = d) {
                            if (enteredPin.length < 4) {
                                pinError = false
                                val next = enteredPin + d
                                enteredPin = next
                                if (next.length == 4) {
                                    if (!onVerifyPin(next)) {
                                        pinError = true
                                        enteredPin = ""
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (biometricEnabled) {
                    IconButton(
                        onClick = { launchBiometricPrompt() },
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("biometric_unlock_button")
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = "Unlock with Biometrics",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.size(64.dp))
                }
                PinKeyButton(label = "0") {
                    if (enteredPin.length < 4) {
                        pinError = false
                        val next = enteredPin + "0"
                        enteredPin = next
                        if (next.length == 4 && !onVerifyPin(next)) {
                            pinError = true
                            enteredPin = ""
                        }
                    }
                }
                IconButton(
                    onClick = { if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1) },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.Backspace, contentDescription = "Backspace")
                }
            }
        }
    }
}

@Composable
private fun PinKeyButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .size(64.dp)
            .testTag("pin_key_$label")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
