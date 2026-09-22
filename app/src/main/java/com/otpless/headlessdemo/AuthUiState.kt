package com.otpless.headlessdemo

/** Which kind of request is awaiting an OTP-verify step; local to this app. */
enum class PendingMedium { PHONE, EMAIL, OAUTH }

/** Everything the single test screen needs to render. */
data class AuthUiState(
    val sdkReady: Boolean = false,
    val isBusy: Boolean = false,
    val statusMessage: String = "Initializing OTPless SDK...",
    val authType: String? = null,
    val deliveryChannel: String? = null,
    val pendingMedium: PendingMedium? = null,
    val isAuthenticated: Boolean = false,
    val userId: String? = null,
    val lastError: String? = null,
    // Populated from OTP_AUTO_READ purely so the UI can show the detected code in
    // the OTP field. autoDetectedOtpSeq increments on every auto-read event so the
    // screen re-syncs the field even if the same digits are detected twice (e.g.
    // sandbox/test numbers that always return the same OTP).
    val autoDetectedOtp: String? = null,
    val autoDetectedOtpSeq: Int = 0,
)
