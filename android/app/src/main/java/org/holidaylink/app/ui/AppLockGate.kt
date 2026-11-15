package org.holidaylink.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.holidaylink.app.data.AppSettings

/** Biometric-only gate. No PIN or device-credential fallback is offered. */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val activity = LocalContext.current.findActivity() as? FragmentActivity
    val settings = AppSettings.state
    var unlocked by remember { mutableStateOf(false) }
    var prompted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val enabled = settings.appLockEnabled && settings.biometricLockEnabled
    val ready = remember(activity, enabled) {
        activity?.let { BiometricManager.from(it).canAuthenticate(BIOMETRIC_WEAK) == BIOMETRIC_SUCCESS } == true
    }
    DisposableEffect(activity, enabled) {
        val lifecycle = activity?.lifecycle ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && enabled) { unlocked = false; prompted = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(enabled, ready, unlocked, prompted) {
        val host = activity ?: return@LaunchedEffect
        if (enabled && ready && !unlocked && !prompted) {
            prompted = true
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock HolidayLink")
                .setSubtitle("Use your fingerprint or face")
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .setNegativeButtonText("Cancel")
                .build()
            BiometricPrompt(host, ContextCompat.getMainExecutor(host), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true; error = "" }
                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON) error = "Biometric verification failed. Try again."
                }
            }).authenticate(info)
        }
    }
    if (!enabled || unlocked) { content(); return }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(14.dp))
            Text("HolidayLink is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(if (ready) "Verify with fingerprint or face to continue." else "Biometric authentication is unavailable. Disable app lock from device settings or restore biometric access.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Button(onClick = { prompted = false; error = "" }, enabled = ready) {
                Icon(Icons.Default.Fingerprint, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Try biometric unlock")
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
