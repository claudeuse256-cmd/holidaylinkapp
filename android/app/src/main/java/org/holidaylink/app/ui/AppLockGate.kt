package org.holidaylink.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.holidaylink.app.data.AppSettings

/**
 * App lock.
 *
 * Nothing sensitive is rendered until the user proves who they are. Fingerprint or face is the
 * only thing on screen; the PIN stays hidden behind "Use PIN instead" so it is the last resort,
 * but it is still there — a device with a dirty sensor must never lock the owner out of their
 * own account. The gate re-arms whenever the app leaves the foreground.
 */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // androidx.biometric needs a FragmentActivity host, which is also a LifecycleOwner, so both
    // the prompt and the re-arm observer resolve to the same object.
    val activity = context.findActivity() as? FragmentActivity
    val settings = AppSettings.state

    var unlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var prompted by remember { mutableStateOf(false) }
    // The PIN stays hidden until biometrics is unavailable or the user asks for it.
    var showPin by remember { mutableStateOf(false) }

    val hasPin = settings.appLockPin != null

    val biometricReady = remember(settings.biometricLockEnabled) {
        val host = activity ?: return@remember false
        if (!settings.biometricLockEnabled) return@remember false
        BiometricManager.from(host).canAuthenticate(BIOMETRIC_WEAK) == BIOMETRIC_SUCCESS
    }

    // With no PIN there is no in-app way to unlock, so fall back to the device screen lock.
    val deviceCredentialReady = remember(settings.biometricLockEnabled) {
        val host = activity ?: return@remember false
        if (!settings.biometricLockEnabled) return@remember false
        BiometricManager.from(host)
            .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BIOMETRIC_SUCCESS
    }

    val biometricUsable = biometricReady || (!hasPin && deviceCredentialReady)

    // A lock that cannot be opened must never trap the user: require a PIN or a working sensor.
    val lockRequired = settings.appLockEnabled && (hasPin || deviceCredentialReady)

    DisposableEffect(activity, lockRequired) {
        val lifecycle = activity?.lifecycle ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && lockRequired) {
                unlocked = false
                prompted = false
                pin = ""
                showPin = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lockRequired, biometricUsable, hasPin, unlocked) {
        val host = activity ?: return@LaunchedEffect
        if (lockRequired && !unlocked && biometricUsable && !prompted) {
            prompted = true
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock HolidayLink")
                .setSubtitle("Confirm it's you to open your chats")
            if (hasPin && biometricReady) {
                // A PIN exists, so biometrics is enough here and "Use PIN" stays legal.
                builder.setAllowedAuthenticators(BIOMETRIC_WEAK)
                    .setNegativeButtonText("Use PIN")
            } else {
                // No PIN to fall back on: the device screen lock has to be part of the prompt,
                // and androidx rejects a negative button in that combination.
                builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            }
            val info = builder.build()
            BiometricPrompt(
                host,
                ContextCompat.getMainExecutor(host),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        unlocked = true
                        error = ""
                    }

                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        if (code != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            code != BiometricPrompt.ERROR_USER_CANCELED
                        ) {
                            error = "Couldn't verify it was you. Try again."
                        }
                    }
                }
            ).authenticate(info)
        }
    }

    if (!lockRequired || unlocked) {
        content()
        return
    }

    // Biometrics prompt first (see LaunchedEffect above); the PIN form below is
    // the fallback. Themed surface so light/dark both render correctly.
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("HolidayLink is locked", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            if (biometricUsable) "Use your fingerprint or face to open the app."
            else "This phone has no biometric unlock set up, so use your PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        if (biometricUsable) {
            Button(
                onClick = { prompted = false; error = "" },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Fingerprint, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Unlock with fingerprint or face")
            }
            if (hasPin) {
                Spacer(Modifier.height(6.dp))
                // The PIN is deliberately the last option: biometrics first, always.
                TextButton(onClick = { showPin = !showPin }) {
                    Text(if (showPin) "Hide the PIN" else "Use PIN instead")
                }
            }
        }

        if (hasPin && (showPin || !biometricUsable)) {
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (pin == settings.appLockPin) {
                        unlocked = true
                        pin = ""
                        error = ""
                    } else {
                        error = "That PIN isn't right."
                        pin = ""
                    }
                },
                enabled = pin.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Unlock") }
        } else if (!biometricUsable && !hasPin) {
            TextButton(onClick = { prompted = false }) { Text("Try again") }
        }

        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    }
}