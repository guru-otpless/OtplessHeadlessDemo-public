package com.otpless.headlessdemo

import com.otpless.v2.android.sdk.dto.OtplessResponse
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One row in the on-screen debug/event log. */
data class LogEntry(
    val timestamp: Long,
    val type: String,
    val statusCode: Int?,
    val detail: String,
) {
    val timeLabel: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

/**
 * Keys inside [OtplessResponse.getResponse] that must never be shown, logged, or
 * persisted verbatim. Matched case-insensitively so we don't rely on guessing the
 * SDK's exact casing for every field.
 */
private val SENSITIVE_KEY_FRAGMENTS = listOf(
    "otp", "token", "secret", "password", "code", "pin",
)

/**
 * Builds a redacted, human-readable summary of an [OtplessResponse] for the debug
 * log. [OtplessResponse.getResponse] is a raw JSONObject whose exact key set isn't
 * pinned down in the public docs, so rather than guessing field names we walk the
 * JSON generically and mask anything that looks sensitive by key name.
 */
fun OtplessResponse.toLogDetail(): String {
    val payload = response ?: return "(no payload)"
    val redacted = JSONObject()
    val keys = payload.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val isSensitive = SENSITIVE_KEY_FRAGMENTS.any { key.contains(it, ignoreCase = true) }
        redacted.put(key, if (isSensitive) "***redacted***" else payload.opt(key))
    }
    return redacted.toString()
}

/** Best-effort convenience accessor; returns null if the key isn't present. */
fun OtplessResponse.optField(key: String): String? {
    val value = response?.opt(key) ?: return null
    return value.toString()
}

/**
 * Maps documented OTPless error codes to a human-readable explanation.
 * Reference: https://otpless.com/docs/frontend-sdks/app-sdks/android/new/references/error-codes
 */
fun describeErrorCode(code: Int?): String {
    if (code == null) return "Unknown error"
    return when (code) {
        7101 -> "Invalid parameter values, or a required parameter is missing"
        7102 -> "Invalid phone number"
        7103 -> "Invalid phone number delivery channel"
        7104 -> "Invalid email address"
        7105 -> "Invalid email delivery channel"
        7106 -> "Invalid phone number or email"
        7113 -> "Invalid OTP expiry configuration"
        7116 -> "OTP length is invalid — only 4 or 6 are allowed"
        7121 -> "Invalid app hash"
        4000 -> "Request values are incorrect"
        4001 -> "SDK does not support two-factor auth yet"
        4003 -> "This channel is not enabled for this app in the OTPless dashboard"
        401 -> "Unauthorized — invalid APP_ID"
        7025 -> "SMS delivery is unavailable for the requested country"
        7020 -> "Too many authentication attempts — rate limited"
        7022 -> "This identity exceeded its authentication request limit"
        7023 -> "This IP address exceeded its authentication request limit"
        7024 -> "This application exceeded its authentication request limit"
        9100 -> "Network error: socket timeout"
        9103 -> "Network error: unknown host"
        9104 -> "Network error: I/O exception"
        7112 -> "Empty OTP submitted"
        7115 -> "OTP was already verified"
        7118 -> "Incorrect OTP"
        7303 -> "OTP has expired"
        5003 -> "SDK initialization failed"
        500 -> "Internal server error"
        else -> "Unrecognized error code ($code) — see OTPless error-codes reference"
    }
}
