package com.otpless.headlessdemo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
// import androidx.compose.material3.OutlinedButton // only used by the OAuth section, commented out below
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otpless.headlessdemo.AuthUiState
import com.otpless.headlessdemo.LogEntry
import com.otpless.headlessdemo.R
import com.otpless.v2.android.sdk.dto.OtplessChannelType

private val NumberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

// Material3's default filled-Button shape is a fully rounded pill/stadium —
// pass this explicitly to every Button so it's a small, consistent corner
// radius instead, regardless of how the theme's shape scale maps internally.
private val ButtonShape = RoundedCornerShape(10.dp)

// Only referenced by the OAuth section, which is commented out below for now.
// private val OAuthChannels = listOf(
//     OtplessChannelType.GOOGLE_SDK,
//     OtplessChannelType.FACEBOOK_SDK,
//     OtplessChannelType.MICROSOFT,
//     OtplessChannelType.GITHUB,
// )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(
    state: AuthUiState,
    log: List<LogEntry>,
    onStartAuto: (phone: String, countryCode: String) -> Unit,
    onSendSmsOtp: (phone: String, countryCode: String) -> Unit,
    onSendWhatsAppOtp: (phone: String, countryCode: String) -> Unit,
    onVerifyOtp: (otp: String) -> Unit,
    onStartEmail: (email: String) -> Unit,
    onOAuth: (OtplessChannelType) -> Unit,
    onShareLogs: () -> Unit,
    onCancel: () -> Unit,
) {
    var countryCode by remember { mutableStateOf("91") }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    // var email by remember { mutableStateOf("") } // only used by the email-auth section, commented out below

    // Mirrors an OTP_AUTO_READ-detected code into the field so it's visible, even
    // though it's also verified automatically in the background. Keyed on the seq
    // number (not the OTP string) so it re-syncs even if the same digits repeat.
    LaunchedEffect(state.autoDetectedOtpSeq) {
        state.autoDetectedOtp?.let { otp = it }
    }

    var showSuccessDialog by remember { mutableStateOf(false) }
    // Fires only on the false->true transition, so dismissing the dialog doesn't
    // bring it back on unrelated recompositions while isAuthenticated stays true.
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) showSuccessDialog = true
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) { Text("OK") }
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF2E7D32), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            },
            title = { Text("Authenticated") },
            text = {
                Text(
                    "OTPless verified the OTP successfully." +
                        (state.userId?.let { "\nuserId: $it" } ?: "")
                )
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.otpless_logo),
                            contentDescription = "OTPless logo",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("OTPless Headless Demo")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(state, onCancel) }

            item {
                SectionCard(title = "Phone authentication") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = countryCode,
                            onValueChange = { countryCode = it.filter(Char::isDigit) },
                            label = { Text("Country code") },
                            keyboardOptions = NumberKeyboard,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it.filter(Char::isDigit) },
                            label = { Text("Phone number") },
                            keyboardOptions = NumberKeyboard,
                            modifier = Modifier.weight(2f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onStartAuto(phone, countryCode) },
                            modifier = Modifier.weight(1f),
                            enabled = state.sdkReady && !state.isBusy,
                            shape = ButtonShape,
                        ) { Text("SNA → OTP") }
                        Button(
                            onClick = { onSendSmsOtp(phone, countryCode) },
                            modifier = Modifier.weight(1f),
                            enabled = state.sdkReady && !state.isBusy,
                            shape = ButtonShape,
                        ) { Text("SMS OTP") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onSendWhatsAppOtp(phone, countryCode) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.sdkReady && !state.isBusy,
                        shape = ButtonShape,
                    ) { Text("WhatsApp OTP") }
                }
            }

            // Only relevant once an OTP has actually been sent (authType == "OTP",
            // set from INITIATE for SMS/WhatsApp/email, or from FALLBACK_TRIGGERED
            // when SNA drops down to OTP). Pure SNA success never needs this at all.
            if (state.authType == "OTP") {
                item {
                    SectionCard(title = "Verify OTP") {
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { otp = it.filter(Char::isDigit) },
                            label = { Text("OTP") },
                            keyboardOptions = NumberKeyboard,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onVerifyOtp(otp); otp = "" },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.sdkReady,
                            shape = ButtonShape,
                        ) { Text("Verify") }
                    }
                }
            }

            // Email auth and OAuth/social login are not needed right now — commented
            // out rather than removed so they're easy to bring back later.
            /*
            item {
                SectionCard(title = "Email authentication") {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email address") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onStartEmail(email) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.sdkReady && !state.isBusy,
                    ) { Text("Send email OTP") }
                }
            }

            item {
                SectionCard(title = "OAuth / social login") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OAuthChannels.take(2).forEach { channel ->
                            OutlinedButton(
                                onClick = { onOAuth(channel) },
                                modifier = Modifier.weight(1f),
                                enabled = state.sdkReady && !state.isBusy,
                            ) { Text(channel.name.removeSuffix("_SDK")) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OAuthChannels.drop(2).forEach { channel ->
                            OutlinedButton(
                                onClick = { onOAuth(channel) },
                                modifier = Modifier.weight(1f),
                                enabled = state.sdkReady && !state.isBusy,
                            ) { Text(channel.name) }
                        }
                    }
                }
            }
            */

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Event log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onShareLogs, enabled = log.isNotEmpty()) {
                        Text("Share logs")
                    }
                }
            }

            items(log, key = { it.timestamp.toString() + it.type }) { entry ->
                LogRow(entry)
            }
        }
    }
}

@Composable
private fun StatusCard(state: AuthUiState, onCancel: () -> Unit) {
    SectionCard(title = "Status") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(
                color = when {
                    state.isBusy -> MaterialTheme.colorScheme.tertiary
                    state.sdkReady -> Color(0xFF2E7D32)
                    else -> MaterialTheme.colorScheme.outline
                }
            )
            Spacer(Modifier.width(8.dp))
            Text(if (state.sdkReady) "SDK ready" else "SDK not ready", fontWeight = FontWeight.Bold)
            if (state.isBusy) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Working…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.isBusy) {
            Spacer(Modifier.height(4.dp))
            // The other request buttons are disabled while busy (to avoid firing
            // overlapping requests), so this is the only way out if a channel
            // never responds — no need to wait out the 45s watchdog.
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        Spacer(Modifier.height(4.dp))
        Text(state.statusMessage, style = MaterialTheme.typography.bodyMedium)
        state.authType?.let { Text("authType: $it", style = MaterialTheme.typography.bodySmall) }
        state.deliveryChannel?.let { Text("deliveryChannel: $it", style = MaterialTheme.typography.bodySmall) }
        if (state.isAuthenticated) {
            Text("Authenticated" + (state.userId?.let { " — userId: $it" } ?: ""), fontWeight = FontWeight.Bold)
        }
        state.lastError?.let {
            Text("Error: $it", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, shape = CircleShape)
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "${entry.timeLabel}  ${entry.type}" + (entry.statusCode?.let { "  [$it]" } ?: ""),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            entry.detail,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}
