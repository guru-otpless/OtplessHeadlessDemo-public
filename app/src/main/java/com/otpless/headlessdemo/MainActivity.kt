package com.otpless.headlessdemo

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import com.otpless.headlessdemo.ui.DemoScreen
import com.otpless.headlessdemo.ui.theme.OtplessTheme
import com.otpless.v2.android.sdk.dto.OtplessChannelType
import com.otpless.v2.android.sdk.dto.OtplessRequest
import com.otpless.v2.android.sdk.dto.OtplessResponse
import com.otpless.v2.android.sdk.dto.ResponseTypes
import com.otpless.v2.android.sdk.main.OtplessSDK


class MainActivity : ComponentActivity() {

    private var uiState by mutableStateOf(AuthUiState())

    private val log = mutableStateListOf<LogEntry>()

    private var lastPhoneNumber: String = ""
    private var lastCountryCode: String = ""
    private var lastEmail: String = ""

    // Tracks whether a verify call is currently outstanding and, if so, which OTP
    // it was submitted with — used only to dedupe OTP_AUTO_READ's automatic verify
    // against a manual paste-and-verify of the same code (see verifyOtp()).
    private var otpVerifyInFlight: String? = null

    // Some delivery channels (notably WhatsApp's "Zero-Tap" auto-detect, which
    // needs the app's package name + signing cert pre-registered with the
    // WhatsApp Business Account on Meta's side) may never send OTPLESS a follow-up
    // event at all if that registration isn't in place — the SDK just waits
    // forever with nothing to report. This watchdog stops the UI from silently
    // spinning in that case; manual OTP entry still works regardless of whether
    // it fires.
    private var responseTimeoutJob: Job? = null
    private val RESPONSE_TIMEOUT_MS = 45_000L

    private fun armResponseTimeout() {
        responseTimeoutJob?.cancel()
        responseTimeoutJob = lifecycleScope.launch {
            delay(RESPONSE_TIMEOUT_MS)
            appendLog(
                "REQUEST_TIMEOUT",
                null,
                "No further event from OTPless for ${RESPONSE_TIMEOUT_MS / 1000}s"
            )
            uiState = uiState.copy(
                isBusy = false,
                statusMessage = "No response received",
                lastError = "Timed out waiting for OTPless. Check the actual " +
                    "SMS/WhatsApp app for a delivered code and enter it manually " +
                    "— auto-detect (e.g. WhatsApp Zero-Tap) may not be configured " +
                    "for this app yet. Also check the dashboard's channel config."
            )
        }
    }

    // Lets the user bail out of a stuck request immediately instead of waiting on
    // the 45s watchdog — every other action button is disabled while isBusy is
    // true, so without this there was no way to recover except waiting it out.
    private fun cancelPendingRequest() {
        responseTimeoutJob?.cancel()
        otpVerifyInFlight = null
        appendLog("REQUEST_CANCELLED", null, "Cancelled by user")
        uiState = uiState.copy(
            isBusy = false,
            statusMessage = "Cancelled",
        )
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appendLog(
            "SDK_INIT_START",
            null,
            "Starting OTPLESS initialization"
        )

        lifecycleScope.launch {
            try {
                // otpless-headless-sdk 0.9.0's initialize() is a suspend function
                // that takes the response callback directly. Passing it here (rather
                // than a separate setResponseCallback() call afterward) matters:
                // setResponseCallback() only forwards to the SDK's internal service
                // object, and if that object doesn't exist yet it silently no-ops
                // (confirmed by disassembling the SDK) — leaving the app stuck with
                // no SDK_READY *and* no FAILED event ever arriving. Passing the
                // callback into initialize() itself avoids that ordering hazard.
                OtplessSDK.initialize(
                    appId = BuildConfig.OTPLESS_APP_ID,
                    activity = this@MainActivity,
                    callback = ::onOtplessResponse
                )

                appendLog(
                    "SDK_INIT_RETURNED",
                    null,
                    "OtplessSDK.initialize() returned; response callback registered"
                )

            } catch (e: Exception) {
                appendLog(
                    "SDK_INIT_EXCEPTION",
                    null,
                    "${e::class.simpleName}: ${e.message}"
                )

                uiState = uiState.copy(
                    sdkReady = false,
                    isBusy = false,
                    statusMessage = "SDK initialization failed",
                    lastError = e.message ?: e::class.simpleName
                )
            }
        }

        /*
         * Jetpack Compose UI
         */
        setContent {
            OtplessTheme {
                DemoScreen(
                    state = uiState,
                    log = log,

                    onStartAuto = ::startAutoAuth,

                    onSendSmsOtp = ::startSmsOtp,

                    onSendWhatsAppOtp = ::startWhatsAppOtp,

                    onVerifyOtp = ::verifyOtp,

                    onStartEmail = ::startEmailAuth,

                    onOAuth = ::startOAuth,

                    onShareLogs = ::shareLogs,

                    onCancel = ::cancelPendingRequest
                )
            }
        }
    }


    // -------------------------------------------------------------------------
    // Start authentication
    // -------------------------------------------------------------------------

    private fun startAutoAuth(
        phone: String,
        countryCode: String
    ) {
        if (!validatePhone(phone, countryCode)) return

        lastPhoneNumber = phone
        lastCountryCode = countryCode

        // Clear any authType/deliveryChannel left over from a previous attempt so
        // the Verify OTP section (gated on authType == "OTP") doesn't show stale
        // state before this request's own INITIATE response comes back — SNA may
        // resolve without ever needing an OTP at all.
        uiState = uiState.copy(authType = null, deliveryChannel = null)

        val request = OtplessRequest().apply {
            setPhoneNumber(
                number = phone,
                countryCode = countryCode
            )
        }

        runRequest(
            request = request,
            medium = PendingMedium.PHONE,
            statusMessage = "Starting authentication"
        )
    }


    private fun startSmsOtp(
        phone: String,
        countryCode: String
    ) {
        if (!validatePhone(phone, countryCode)) return

        lastPhoneNumber = phone
        lastCountryCode = countryCode
        uiState = uiState.copy(authType = null, deliveryChannel = null)

        val request = OtplessRequest().apply {
            setPhoneNumber(
                number = phone,
                countryCode = countryCode
            )

            setDeliveryChannel("SMS")
        }

        runRequest(
            request = request,
            medium = PendingMedium.PHONE,
            statusMessage = "Requesting SMS OTP"
        )
    }


    private fun startWhatsAppOtp(
        phone: String,
        countryCode: String
    ) {
        if (!validatePhone(phone, countryCode)) return

        lastPhoneNumber = phone
        lastCountryCode = countryCode
        uiState = uiState.copy(authType = null, deliveryChannel = null)

        val request = OtplessRequest().apply {
            setPhoneNumber(
                number = phone,
                countryCode = countryCode
            )

            setDeliveryChannel("WHATSAPP")
        }

        runRequest(
            request = request,
            medium = PendingMedium.PHONE,
            statusMessage = "Requesting WhatsApp OTP"
        )
    }


    private fun startEmailAuth(email: String) {
        if (!validateEmail(email)) return

        lastEmail = email
        uiState = uiState.copy(authType = null, deliveryChannel = null)

        val request = OtplessRequest().apply {
            setEmail(email)
        }

        runRequest(
            request = request,
            medium = PendingMedium.EMAIL,
            statusMessage = "Requesting email OTP"
        )
    }


    // -------------------------------------------------------------------------
    // Verify OTP
    // -------------------------------------------------------------------------

    private fun verifyOtp(otp: String) {

        if (otp.isBlank()) {
            uiState = uiState.copy(
                lastError = "Enter the OTP before verifying"
            )
            return
        }

        // OTP_AUTO_READ already fires an automatic verify in the background (see
        // onOtplessResponse). If that one already went through — or is still in
        // flight for this exact code — a second manual verify (e.g. pasting the
        // same code) submits an OTP whose transaction token the server already
        // consumed, which comes back as a generic "invalid token" error rather
        // than anything OTP-shaped. Skip the redundant call instead.
        if (uiState.isAuthenticated) {
            appendLog(
                "VERIFY_SKIPPED",
                null,
                "Already authenticated — ignoring duplicate OTP submission"
            )
            return
        }

        if (otpVerifyInFlight == otp) {
            appendLog(
                "VERIFY_SKIPPED",
                null,
                "This OTP is already being verified (likely auto-submitted via OTP_AUTO_READ) — ignoring duplicate"
            )
            return
        }

        otpVerifyInFlight = otp

        val medium = uiState.pendingMedium

        val request = when (medium) {

            PendingMedium.EMAIL -> {
                OtplessRequest().apply {
                    setEmail(lastEmail)
                    setOtp(otp)
                }
            }

            else -> {
                OtplessRequest().apply {
                    setPhoneNumber(
                        number = lastPhoneNumber,
                        countryCode = lastCountryCode
                    )

                    setOtp(otp)
                }
            }
        }

        runRequest(
            request = request,
            medium = medium ?: PendingMedium.PHONE,
            statusMessage = "Verifying OTP"
        )
    }


    // -------------------------------------------------------------------------
    // OAuth
    // -------------------------------------------------------------------------

    private fun startOAuth(
        channelType: OtplessChannelType
    ) {

        val request = OtplessRequest().apply {
            setChannelType(channelType)
        }

        runRequest(
            request = request,
            medium = PendingMedium.OAUTH,
            statusMessage = "Starting OAuth"
        )
    }


    // -------------------------------------------------------------------------
    // OTPLESS request
    // -------------------------------------------------------------------------

    private fun runRequest(
        request: OtplessRequest,
        medium: PendingMedium,
        statusMessage: String
    ) {

        uiState = uiState.copy(
            isBusy = true,
            statusMessage = statusMessage,
            lastError = null,
            pendingMedium = medium
        )

        armResponseTimeout()

        /*
         * IMPORTANT:
         *
         * The new SDK uses suspend start().
         * There is no startAsync().
         */
        lifecycleScope.launch {

            OtplessSDK.start(
                request = request,
                callback = ::onOtplessResponse
            )
        }
    }


    // -------------------------------------------------------------------------
    // OTPLESS response callback
    // -------------------------------------------------------------------------

    private fun onOtplessResponse(
        response: OtplessResponse
    ) {

        val type = response.responseType
        val code = response.statusCode

        appendLog(
            type = type?.name ?: "UNKNOWN",
            statusCode = code,
            detail = response.toLogDetail()
        )

        val isErrorStatus =
            code != null && code != 200

        val errorCode =
            response
                .optField("errorCode")
                ?.toIntOrNull()
                ?: code?.takeIf {
                    isErrorStatus
                }

        val errorMessage =
            response.optField("errorMessage")

        // Any event at all means the SDK is alive and progressing — cancel the
        // watchdog and, once this event is processed, re-arm it only if we're
        // still waiting on something further (e.g. delivery/auto-read after a
        // successful INITIATE).
        responseTimeoutJob?.cancel()

        runOnUiThread {

            when (type) {

                ResponseTypes.SDK_READY -> {

                    uiState = uiState.copy(
                        sdkReady = true,
                        isBusy = false,
                        statusMessage = "SDK ready",
                        lastError = null
                    )
                }


                ResponseTypes.FAILED -> {

                    uiState = uiState.copy(
                        sdkReady = false,
                        isBusy = false,
                        statusMessage = "SDK initialization failed",
                        lastError =
                            errorMessage
                                ?: describeErrorCode(
                                    errorCode ?: 5003
                                )
                    )
                }


                ResponseTypes.INITIATE -> {

                    if (isErrorStatus) {

                        uiState = uiState.copy(
                            isBusy = false,
                            statusMessage = "Request failed",
                            lastError =
                                errorMessage
                                    ?: describeErrorCode(errorCode)
                        )

                    } else {

                        val authType =
                            response.optField("authType")

                        val deliveryChannel =
                            response.optField("deliveryChannel")

                        uiState = uiState.copy(
                            isBusy = true,

                            authType =
                                authType
                                    ?: uiState.authType,

                            deliveryChannel =
                                deliveryChannel
                                    ?: uiState.deliveryChannel,

                            statusMessage =
                                "Authentication initiated",

                            lastError = null
                        )
                    }
                }


                ResponseTypes.OTP_AUTO_READ -> {

                    val autoOtp =
                        response.optField("otp")

                    if (!autoOtp.isNullOrBlank()) {

                        uiState = uiState.copy(
                            statusMessage =
                                "OTP auto-read via SMS Retriever — verifying automatically " +
                                    "(no need to paste it manually)",
                            autoDetectedOtp = autoOtp,
                            autoDetectedOtpSeq = uiState.autoDetectedOtpSeq + 1,
                        )

                        verifyOtp(autoOtp)
                    }
                }


                ResponseTypes.VERIFY -> {

                    // Whether it succeeded or failed, this verify call is done —
                    // let a subsequent manual submission (of a *different* OTP,
                    // e.g. after a resend) through.
                    otpVerifyInFlight = null

                    if (isErrorStatus) {

                        uiState = uiState.copy(
                            isBusy = false,
                            statusMessage = "OTP verification failed",
                            lastError =
                                errorMessage
                                    ?: describeErrorCode(errorCode)
                        )

                    } else {

                        // A 200 here means the OTP was accepted. Previously this
                        // left isBusy=true and waited on a separate ONETAP event
                        // to actually finish the flow — but ONETAP doesn't
                        // reliably follow, which left the UI spinning forever
                        // even though verification had already succeeded. Treat
                        // this as done now; if ONETAP does still arrive, it just
                        // fills in userId on top of an already-finished state.
                        uiState = uiState.copy(
                            isBusy = false,
                            isAuthenticated = true,
                            statusMessage =
                                "OTP verified"
                        )
                    }
                }


                ResponseTypes.ONETAP -> {

                    otpVerifyInFlight = null

                    val userId =
                        response.optField("userId")

                    uiState = uiState.copy(
                        isBusy = false,
                        isAuthenticated = true,
                        userId = userId,
                        statusMessage =
                            "Authenticated successfully",
                        lastError = null
                    )
                }


                ResponseTypes.DELIVERY_STATUS -> {

                    val deliveryChannel =
                        response.optField("deliveryChannel")

                    val delivered =
                        response.optField(
                            "communicationDelivered"
                        )

                    uiState = uiState.copy(
                        deliveryChannel =
                            deliveryChannel
                                ?: uiState.deliveryChannel,

                        statusMessage =
                            "Delivery status: " +
                                    (delivered ?: "unknown")
                    )
                }


                ResponseTypes.FALLBACK_TRIGGERED -> {

                    val deliveryChannel =
                        response.optField("deliveryChannel")

                    uiState = uiState.copy(
                        deliveryChannel =
                            deliveryChannel
                                ?: uiState.deliveryChannel,

                        // SNA -> OTP fallback means an OTP is now on its way even
                        // though the original INITIATE reported authType=SILENT_AUTH;
                        // flip it here so the Verify OTP section (gated on
                        // authType == "OTP") actually appears.
                        authType = "OTP",

                        statusMessage =
                            "Authentication fallback triggered — OTP requested"
                    )
                }


                null -> {

                    uiState = uiState.copy(
                        isBusy = false,
                        lastError =
                            "Unrecognized response from OTPLESS"
                    )
                }


                else -> {
                    // Ignore response types not used by this UI.
                }
            }

            if (uiState.isBusy) {
                armResponseTimeout()
            }
        }
    }


    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun appendLog(
        type: String,
        statusCode: Int?,
        detail: String
    ) {

        log.add(
            0,
            LogEntry(
                System.currentTimeMillis(),
                type,
                statusCode,
                detail
            )
        )
    }


    /**
     * Writes the current event log to a file in the app's cache dir and opens the
     * share sheet so a tester can send it (email, WhatsApp, Drive, etc.) to
     * whoever's debugging the report. Nothing new is redacted here — every
     * [LogEntry.detail] was already scrubbed of OTPs/tokens when it was appended
     * (see [com.otpless.headlessdemo.toLogDetail]), so this just serializes what's
     * already on screen.
     */
    private fun shareLogs() {

        try {
            val dir = File(cacheDir, "logs").apply { mkdirs() }

            val fileName = "otpless_debug_log_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                ".txt"

            val file = File(dir, fileName)

            file.bufferedWriter().use { writer ->
                writer.appendLine("OTPless Headless Demo — debug log")
                writer.appendLine("Exported: ${Date()}")
                writer.appendLine(
                    "App: $packageName (${BuildConfig.VERSION_NAME}, " +
                        "versionCode ${BuildConfig.VERSION_CODE})"
                )
                writer.appendLine("APP_ID: ${maskAppId(BuildConfig.OTPLESS_APP_ID)}")
                writer.appendLine(
                    "Device: ${Build.MANUFACTURER} ${Build.MODEL}, " +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                )
                writer.appendLine(
                    "SDK ready: ${uiState.sdkReady}, authenticated: ${uiState.isAuthenticated}"
                )
                writer.appendLine("-".repeat(60))

                // On screen the log is newest-first; export it chronologically
                // (oldest first) since that reads more naturally as a transcript.
                log.asReversed().forEach { entry ->
                    writer.appendLine(
                        "[${entry.timeLabel}] ${entry.type}" +
                            (entry.statusCode?.let { " [$it]" } ?: "") +
                            "  ${entry.detail}"
                    )
                }
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "OTPless debug log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share OTPless debug log"))

        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't export log: ${e.message}", Toast.LENGTH_LONG).show()
            appendLog("LOG_EXPORT_FAILED", null, "${e::class.simpleName}: ${e.message}")
        }
    }


    private fun validatePhone(
        phone: String,
        countryCode: String
    ): Boolean {

        if (
            countryCode.isBlank() ||
            phone.isBlank() ||
            !phone.all(Char::isDigit) ||
            phone.length < 6
        ) {

            uiState = uiState.copy(
                lastError =
                    "Enter a valid phone number and country code"
            )

            return false
        }

        return true
    }


    private fun validateEmail(
        email: String
    ): Boolean {

        if (
            !android.util.Patterns.EMAIL_ADDRESS
                .matcher(email)
                .matches()
        ) {

            uiState = uiState.copy(
                lastError =
                    "Enter a valid email address"
            )

            return false
        }

        return true
    }


    private fun maskAppId(
        appId: String
    ): String {

        return if (appId.length <= 4) {
            "****"
        } else {
            appId.take(2) +
                    "****" +
                    appId.takeLast(2)
        }
    }
}