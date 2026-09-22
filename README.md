# OTPless Android Headless SDK — Demo App

A reference Android application demonstrating how to integrate the **OTPless Android Headless SDK** into a Kotlin + Jetpack Compose application.

This demo covers the complete phone authentication journey, including:

- **Silent Network Authentication (SNA)**
- **SMS OTP**
- **WhatsApp OTP**
- **SNA → OTP fallback**
- **Automatic OTP reading and verification**
- **Manual OTP verification**
- **Authentication status and SDK event logging**

The app includes an on-screen event log that shows the SDK events generated during authentication. Sensitive values such as OTPs and tokens are automatically redacted from the log.

> **SDK:** `io.github.otpless-tech:otpless-headless-sdk:0.9.0`  
> **Platform:** Android  
> **Language:** Kotlin  
> **UI:** Jetpack Compose

Official documentation: https://otpless.com/docs/frontend-sdks/app-sdks/android/new/headless/intro

---

## Table of contents

1. [Overview](#overview)
2. [Authentication flows](#authentication-flows)
3. [Prerequisites](#prerequisites)
4. [OTPless dashboard setup](#otpless-dashboard-setup)
5. [Run the demo](#run-the-demo)
6. [Test the authentication flows](#test-the-authentication-flows)
7. [Understanding the demo screen](#understanding-the-demo-screen)
8. [Integrating OTPless into your Android app](#integrating-otpless-into-your-android-app)
   1. [Add the SDK dependency](#1-add-the-sdk-dependency)
   2. [Configure the App ID](#2-configure-the-app-id)
   3. [Configure Android network security](#3-configure-android-network-security)
   4. [Initialize the SDK](#4-initialize-the-sdk)
   5. [Create an authentication request](#5-create-an-authentication-request)
   6. [Start the request](#6-start-the-request)
   7. [Handle SDK responses](#7-handle-sdk-responses)
   8. [Verify an OTP](#8-verify-an-otp)
   9. [Connect the SDK to your UI](#9-connect-the-sdk-to-your-ui)
9. [Error handling](#error-handling)
10. [Troubleshooting](#troubleshooting)
11. [Security considerations](#security-considerations)
12. [Project structure](#project-structure)
13. [Dashboard screenshots](#dashboard-screenshots)
14. [Build](#build)

---

## Overview

The demo is a single-screen application that provides a simple way to configure and test the OTPless Headless SDK.

At a high level, the integration follows this sequence:

```text
┌──────────────────────┐
│ Initialize OTPless  │
└──────────┬───────────┘
           │
           ▼
     SDK_READY event
           │
           ▼
┌──────────────────────┐
│ Select auth flow     │
│                      │
│ • SNA                │
│ • SMS OTP            │
│ • WhatsApp OTP       │
└──────────┬───────────┘
           │
           ▼
   SDK response events
           │
           ├───────────────┐
           │               │
           ▼               ▼
       SNA success      OTP required
           │               │
           │               ▼
           │          OTP received
           │               │
           │               ▼
           │          Verify OTP
           │               │
           └───────┬───────┘
                   ▼
             Authentication
                complete
```

The demo keeps SDK calls in `MainActivity.kt` and keeps the Compose UI focused on displaying state and invoking actions.

---

## Authentication flows

| Flow | Description |
|---|---|
| **SNA** | Authenticates the device through the carrier's mobile-data connection without requiring the user to enter an OTP, when SNA is supported and available. |
| **SNA → OTP** | Attempts SNA first. If SNA is unavailable, the SDK falls back to OTP. |
| **SMS OTP** | Sends an OTP to the user's phone number through SMS. |
| **WhatsApp OTP** | Sends an OTP to the user's phone number through WhatsApp. |
| **OTP verification** | Verifies the OTP automatically when it is read by the SDK, or manually when entered by the user. |

### SDK events

The demo displays the SDK callback events in the **Event log**.

The main events used by the demo are:

| Event | Description |
|---|---|
| `SDK_READY` | SDK initialization completed successfully. |
| `FAILED` | SDK initialization failed. |
| `INITIATE` | An authentication request has been accepted and processing has started. |
| `OTP_AUTO_READ` | The SDK detected an OTP from an incoming SMS. |
| `DELIVERY_STATUS` | OTPless reported the delivery status of an OTP. |
| `FALLBACK_TRIGGERED` | SNA was unavailable and the SDK switched to OTP. |
| `VERIFY` | The result of an OTP verification request. |
| `ONETAP` | Authentication completed successfully. |

The event log is useful when testing an integration because it shows the order of SDK callbacks together with their status codes and redacted payload information.

---

## Prerequisites

Before running the demo, make sure you have:

- Android Studio installed.
- JDK 17 or later.
- Android SDK API 37 installed.
- An OTPless account and application.
- A configured OTPless **App ID**.
- A physical Android device for phone authentication testing.

### Physical device requirement

A physical device is required for the phone authentication flows demonstrated by this app.

The Android emulator cannot provide the SIM/mobile-network environment required for:

- SNA
- SMS OTP
- WhatsApp OTP

---

## OTPless dashboard setup

Complete the following configuration before running the demo.

### 1. Create or select an OTPless application

Open the [OTPless dashboard](https://otpless.com/) and create an application.

Copy the application's **APP_ID** from the left sidebar under **Configurations → Copy App ID**.

![Dashboard sidebar with Copy App ID highlighted](docs/screenshots/dashboard-app-info.png)

Keep the App ID available for the local setup described in [Run the demo](#run-the-demo).

### 2. Configure the Android application

Under the application's **Android configuration**, register:

- **Package name:** `com.otpless.headlessdemo`
- **SHA-256 signing certificate fingerprint**

You can obtain the signing fingerprints with:

```bash
./gradlew signingReport
```

Register the fingerprints for the builds you intend to test. For example, register both the debug and release fingerprints when both are used.

### 3. Enable authentication channels

Under **Configurations → Channels**, configure the phone-number authentication channel.

For this demo, enable:

- **OTP** as the verification method
- **Silent Network Auth**
- **SMS**
- **WhatsApp**

![Phone number channel configuration — OTP method with Silent Network Auth, SMS and WhatsApp enabled](docs/screenshots/dashboard-channels.png)

> **Important:** A channel that is not enabled in the OTPless dashboard can fail even when the application code is correct. For example, error `4003` indicates that the requested channel is not enabled for the application.

### 4. SNA configuration

If SNA is enabled for the application, there is no additional dashboard configuration required beyond enabling the channel.

The Android application also needs the network security configuration described in [Configure Android network security](#3-configure-android-network-security).

---

## Run the demo

### 1. Create `local.properties`

Copy the example configuration:

```bash
cp local.properties.example local.properties
```

Then edit `local.properties`:

```properties
OTPLESS_APP_ID=your_real_app_id
sdk.dir=/path/to/your/Android/sdk
```

`local.properties` is git-ignored. **Do not commit your real App ID to source control.**

### 2. Configure Java

The project requires JDK 17 or later.

For example, on macOS with Android Studio's bundled JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

If your JDK is installed elsewhere, set `JAVA_HOME` to that installation.

### 3. Build the application

```bash
./gradlew assembleDebug
```

Install the generated debug APK on a physical Android device.

---

## Test the authentication flows

Once the application is installed and the SDK has initialized successfully, the **SDK Ready** status should be shown in the application.

### Test SNA

SNA uses the device's cellular network.

1. Use a physical Android device.
2. Turn **Wi-Fi off**.
3. Keep **mobile data enabled**.
4. Enter the phone number and country code.
5. Tap **SNA → OTP**.
6. Watch the Event log for the SDK response.

When SNA is successful, the flow can complete without an OTP.

A typical successful flow includes:

```text
INITIATE
ONETAP
```

If SNA is unavailable, the SDK can trigger:

```text
FALLBACK_TRIGGERED
```

The demo then continues with OTP verification.

### Test SMS OTP

1. Enter the country code.
2. Enter the phone number.
3. Tap **SMS OTP**.
4. Wait for the SMS.
5. If supported, the SDK detects the OTP automatically and the demo submits it for verification.
6. Otherwise, enter the OTP manually and tap **Verify**.

The event log shows the callbacks generated during the flow.

### Test WhatsApp OTP

1. Make sure WhatsApp is installed on the test device.
2. Enter the country code.
3. Enter the phone number.
4. Tap **WhatsApp OTP**.
5. Wait for the OTP.
6. Enter the OTP and tap **Verify** when manual verification is required.

### Test SNA fallback

To test the fallback path:

1. Use a device and network configuration where SNA is unavailable.
2. Start the **SNA → OTP** flow.
3. Watch for the `FALLBACK_TRIGGERED` event.
4. The demo changes the authentication state to OTP.
5. Complete the flow using the received OTP.

The exact availability of SNA depends on the device, carrier, network connection, and configured authentication environment.

---

## Understanding the demo screen

The application contains four main areas.

### Status

The Status card shows:

- Whether the SDK is ready.
- Whether an authentication request is in progress.
- The current authentication type.
- The current delivery channel.
- Authentication result.
- The latest error, when applicable.
- A **Cancel** action for a request that is taking too long.

### Phone authentication

The Phone authentication card contains:

- Country code
- Phone number
- **SNA → OTP**
- **SMS OTP**
- **WhatsApp OTP**

The authentication buttons remain disabled until the SDK has reported `SDK_READY`.

### OTP verification

The Verify OTP card appears when the current authentication flow requires OTP verification.

The OTP can be:

- Detected automatically through the SDK's SMS Retriever integration.
- Entered manually by the user.

### Event log

The Event log displays SDK callbacks in real time.

Each entry includes:

- Event type
- Status code
- Timestamp
- Redacted response information

The demo also provides **Share logs**, which exports the redacted log as a text file for sharing with a developer or tester.

---

# Integrating OTPless into your Android app

The following section describes the integration used by this demo and can be used as a reference when adding OTPless Headless SDK to another Android application.

## 1. Add the SDK dependency

Add the OTPless Headless SDK to `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.otpless-tech:otpless-headless-sdk:0.9.0")
}
```

---

## 2. Configure the App ID

The demo reads the OTPless App ID from `local.properties` or an environment variable and exposes it through `BuildConfig`.

This keeps the real App ID out of source control.

```kotlin
val otplessAppId: String =
    (localProperties.getProperty("OTPLESS_APP_ID")
        ?: System.getenv("OTPLESS_APP_ID")
        ?: "YOUR_OTPLESS_APP_ID")

android {
    defaultConfig {
        buildConfigField(
            "String",
            "OTPLESS_APP_ID",
            "\"$otplessAppId\""
        )
    }

    buildFeatures {
        buildConfig = true
    }
}
```

The SDK is then initialized using:

```kotlin
BuildConfig.OTPLESS_APP_ID
```

---

## 3. Configure Android network security

SNA requires the application to permit the network traffic used by the carrier authentication flow.

The demo points the Android application to a custom network security configuration.

In `AndroidManifest.xml`:

```xml
<application
    android:networkSecurityConfig="@xml/otpless_network_security_config">
```

The demo's `res/xml/otpless_network_security_config.xml` contains:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

### Why is this required?

SNA uses the device's cellular data path. The carrier gateway can handle the request dynamically, so the demo allows the required cleartext traffic at the base configuration level.

If your application has stricter network-security requirements, review this configuration with your security team and check with OTPless support for the appropriate configuration for your target markets and carriers.

If your application does **not** use SNA, this network security configuration and its manifest reference can be removed.

---

## 4. Initialize the SDK

Initialize the SDK once, typically during `Activity` startup.

The demo performs initialization from `MainActivity.onCreate()`:

```kotlin
lifecycleScope.launch {
    OtplessSDK.initialize(
        appId = BuildConfig.OTPLESS_APP_ID,
        activity = this@MainActivity,
        callback = ::onOtplessResponse
    )
}
```

### Important points

The `initialize()` function is a suspend function in SDK `0.9.0`, so the demo calls it from a coroutine.

The callback is supplied directly to `initialize()`:

```kotlin
callback = ::onOtplessResponse
```

The same callback is reused for subsequent requests.

Registering the callback as part of initialization ensures that the application can receive the initial `SDK_READY` or `FAILED` event.

The demo does not enable authentication actions until it receives `SDK_READY`.

---

## 5. Create an authentication request

Authentication requests are created using `OtplessRequest`.

### SNA / automatic authentication

For the automatic authentication flow, the demo provides the phone number and country code without forcing a delivery channel:

```kotlin
OtplessRequest().apply {
    setPhoneNumber(
        number = phone,
        countryCode = countryCode
    )
}
```

### SMS OTP

To explicitly request SMS:

```kotlin
OtplessRequest().apply {
    setPhoneNumber(
        number = phone,
        countryCode = countryCode
    )
    setDeliveryChannel("SMS")
}
```

### WhatsApp OTP

To explicitly request WhatsApp:

```kotlin
OtplessRequest().apply {
    setPhoneNumber(
        number = phone,
        countryCode = countryCode
    )
    setDeliveryChannel("WHATSAPP")
}
```

### OTP verification

OTP verification uses the same phone number and country code together with the OTP:

```kotlin
OtplessRequest().apply {
    setPhoneNumber(
        number = lastPhoneNumber,
        countryCode = lastCountryCode
    )
    setOtp(otp)
}
```

In the demo, these requests are created by:

- `startAutoAuth()`
- `startSmsOtp()`
- `startWhatsAppOtp()`
- `verifyOtp()`

---

## 6. Start the request

The demo routes authentication requests through a common `runRequest()` helper.

A simplified version is:

```kotlin
private fun runRequest(
    request: OtplessRequest,
    medium: PendingMedium,
    statusMessage: String
) {
    uiState = uiState.copy(
        isBusy = true,
        statusMessage = statusMessage,
        // ...
    )

    armResponseTimeout()

    lifecycleScope.launch {
        OtplessSDK.start(
            request = request,
            callback = ::onOtplessResponse
        )
    }
}
```

`start()` dispatches the request. The result is delivered asynchronously through the callback.

This means application state should be updated from the SDK callback rather than assuming that the `start()` call itself represents the authentication result.

### Demo timeout

The demo also uses a 45-second watchdog:

```text
armResponseTimeout()
```

This is **demo application logic, not an OTPless SDK requirement**.

It gives the user a visible timeout message if no SDK callback is received within the configured period.

The watchdog is cancelled when an SDK event arrives.

---

## 7. Handle SDK responses

The callback is the central part of the integration:

```kotlin
private fun onOtplessResponse(response: OtplessResponse) {
    // Handle SDK event
}
```

The response provides:

```kotlin
response.responseType
response.statusCode
response.response
```

Where:

- `responseType` identifies the SDK event.
- `statusCode` contains the status associated with the event.
- `response` contains the event payload as a `JSONObject`.

Because the payload fields can differ between event types, the demo reads optional values defensively.

### Response handling

The demo handles the main response types as follows:

| Response type | Demo behavior |
|---|---|
| `SDK_READY` | Marks the SDK as ready and enables authentication controls. |
| `FAILED` | Displays the initialization error and keeps the SDK unavailable. |
| `INITIATE` | Reads `authType` and `deliveryChannel` to determine the current authentication flow. |
| `OTP_AUTO_READ` | Reads the OTP internally and starts OTP verification automatically. |
| `DELIVERY_STATUS` | Updates the UI with OTP delivery information. |
| `FALLBACK_TRIGGERED` | Changes the local authentication state to OTP so the user can complete verification. |
| `VERIFY` | Treats `statusCode == 200` as successful OTP verification; otherwise displays the mapped error. |
| `ONETAP` | Marks authentication as complete and reads `userId` when available. |

A simplified callback looks like this:

```kotlin
private fun onOtplessResponse(response: OtplessResponse) {
    val type = response.responseType
    val code = response.statusCode

    appendLog(
        type?.name ?: "UNKNOWN",
        code,
        response.toLogDetail()
    )

    runOnUiThread {
        when (type) {
            ResponseTypes.SDK_READY -> {
                uiState = uiState.copy(
                    sdkReady = true,
                    isBusy = false
                    // ...
                )
            }

            ResponseTypes.INITIATE -> {
                val authType = response.optField("authType")
                val deliveryChannel =
                    response.optField("deliveryChannel")

                uiState = uiState.copy(
                    authType = authType,
                    deliveryChannel = deliveryChannel
                    // ...
                )
            }

            ResponseTypes.OTP_AUTO_READ -> {
                val autoOtp = response.optField("otp")

                if (!autoOtp.isNullOrBlank()) {
                    verifyOtp(autoOtp)
                }
            }

            ResponseTypes.VERIFY -> {
                if (code == 200) {
                    uiState = uiState.copy(
                        isAuthenticated = true
                        // ...
                    )
                } else {
                    uiState = uiState.copy(
                        lastError = describeErrorCode(
                            response.optField("errorCode")?.toIntOrNull()
                        )
                        // ...
                    )
                }
            }

            ResponseTypes.ONETAP -> {
                val userId = response.optField("userId")

                uiState = uiState.copy(
                    isAuthenticated = true,
                    userId = userId
                    // ...
                )
            }

            // FAILED, DELIVERY_STATUS and
            // FALLBACK_TRIGGERED are handled similarly.

            else -> {
                // Ignore events that are not required by the UI.
            }
        }
    }
}
```

### Automatic OTP verification

When `OTP_AUTO_READ` is received, the demo automatically starts verification.

The demo also prevents the same OTP from being submitted more than once while verification is already in progress. This is important because automatic verification and a user's manual submission can otherwise race with each other.

---

## 8. Verify an OTP

The demo's OTP verification function validates the local state before sending the OTP:

```kotlin
private fun verifyOtp(otp: String) {
    if (otp.isBlank()) {
        // Show inline error
        return
    }

    if (uiState.isAuthenticated) {
        return
    }

    if (otpVerifyInFlight == otp) {
        return
    }

    otpVerifyInFlight = otp

    val request = OtplessRequest().apply {
        setPhoneNumber(
            number = lastPhoneNumber,
            countryCode = lastCountryCode
        )
        setOtp(otp)
    }

    runRequest(
        request = request,
        medium = uiState.pendingMedium ?: PendingMedium.PHONE,
        statusMessage = "Verifying OTP"
    )
}
```

The result is returned through the same response callback.

A successful verification commonly produces a `VERIFY` event and may be followed by `ONETAP`.

---

## 9. Connect the SDK to your UI

The demo uses Jetpack Compose for its UI.

`DemoScreen.kt` is a stateless UI layer that receives:

- Current `AuthUiState`
- Event log entries
- Authentication action callbacks

For example:

```text
onStartAuto
onSendSmsOtp
onSendWhatsAppOtp
onVerifyOtp
onCancel
onShareLogs
```

The UI does not call the OTPless SDK directly.

Instead:

```text
Compose UI
    │
    │ user action
    ▼
MainActivity
    │
    │ OtplessRequest
    ▼
OTPless SDK
    │
    │ callback
    ▼
MainActivity
    │
    │ update AuthUiState
    ▼
Compose UI
```

This keeps SDK-specific logic in one place while allowing the UI to remain focused on application state and presentation.

---

# Error handling

OTPless responses can include:

- A `statusCode`
- An `errorCode`
- An `errorMessage`

The demo maps known error codes to user-friendly messages through `describeErrorCode()`.

For example:

```kotlin
fun describeErrorCode(code: Int?): String = when (code) {
    4003 ->
        "This channel is not enabled for this app in the OTPless dashboard"

    7118 ->
        "Incorrect OTP"

    7303 ->
        "OTP has expired"

    401 ->
        "Unauthorized — invalid APP_ID"

    else ->
        "Unrecognized error code ($code) — see OTPless error-codes reference"
}
```

For the complete list of documented error codes, see:

https://otpless.com/docs/frontend-sdks/app-sdks/android/new/references/error-codes

---

# Troubleshooting

## SDK does not become ready

Check the following:

1. The `OTPLESS_APP_ID` is correct.
2. The App ID belongs to the OTPless application you configured.
3. The Android package name is registered in the OTPless dashboard.
4. The correct SHA-256 signing certificate is registered.
5. The application has network connectivity.

The application waits for `SDK_READY` before enabling authentication actions.

---

## Error `4003` — channel is not enabled

Verify the OTPless dashboard configuration.

Under **Configurations → Channels**, make sure the channel you are testing is enabled for the application.

For this demo, the relevant channels are:

- Silent Network Auth
- SMS
- WhatsApp

---

## SNA does not work

Check:

- You are using a physical device.
- Wi-Fi is turned off.
- Mobile data is enabled.
- The carrier/network supports the SNA flow.
- SNA is enabled for the application in the OTPless dashboard.
- The application's network security configuration is present.

If SNA is unavailable, the demo may receive `FALLBACK_TRIGGERED` and continue with OTP.

---

## SMS OTP is not received

Check:

- The phone number and country code are correct.
- SMS is enabled for the application in the OTPless dashboard.
- The device can receive SMS messages.
- The test device is a physical device rather than an emulator.

If the OTP arrives but is not automatically detected, you can enter it manually and use **Verify**.

---

## WhatsApp OTP is not received

Check:

- WhatsApp is installed on the test device.
- The phone number is associated with the expected WhatsApp account.
- WhatsApp is enabled in the OTPless dashboard.
- The phone number and country code are correct.

---

## OTP verification fails

Check:

- The OTP is entered correctly.
- The OTP has not expired.
- The OTP has not already been consumed by an earlier verification attempt.

The demo prevents duplicate automatic/manual submissions where possible.

For an expired OTP, see the OTPless error-code reference.

---

## No callback is received

The demo includes a 45-second timeout to surface this situation.

If the timeout occurs:

1. Check the Event log for any earlier SDK events.
2. Verify the App ID.
3. Verify the dashboard configuration.
4. Verify the selected authentication channel.
5. Check the device's network connection.
6. Confirm that the physical device is suitable for the selected flow.

---

# Security considerations

The demo is designed to avoid exposing authentication secrets in its event log.

### OTPs and tokens are redacted

The event logger recursively inspects SDK response JSON.

Keys containing values such as:

```text
otp
token
secret
password
code
pin
```

are redacted before the response is displayed or exported.

The log therefore contains:

```text
***redacted***
```

instead of the sensitive value.

### Automatically read OTPs are not stored in UI state

When `OTP_AUTO_READ` is received, the demo extracts the OTP only to start verification.

The raw OTP is not written to the event log.

### App ID handling

The real OTPless App ID is read from:

- `local.properties`, or
- the `OTPLESS_APP_ID` environment variable.

The App ID is not intended to be committed to source control.

The exported event log also masks the App ID.

> **Production note:** Review the demo's logging, network-security, secret-management, and error-reporting behavior against your application's security requirements before using the same approach in production.

---

# Project structure

The main OTPless integration is contained in `MainActivity.kt`.

```text
app/src/main/java/com/otpless/headlessdemo/
│
├── MainActivity.kt
│   └── SDK initialization, requests, callbacks and authentication flow
│
├── AuthUiState.kt
│   └── UI state and local authentication state
│
├── OtplessEvents.kt
│   └── Event logging, JSON redaction and error-code handling
│
└── ui/
    └── DemoScreen.kt
        └── Jetpack Compose UI
```

Android configuration:

```text
app/src/main/res/xml/
└── otpless_network_security_config.xml

app/src/main/
└── AndroidManifest.xml
```

### Responsibility by component

| Component | Responsibility |
|---|---|
| `MainActivity.kt` | OTPless SDK integration and authentication flow |
| `AuthUiState.kt` | UI state representation |
| `OtplessEvents.kt` | SDK event logging, redaction and error mapping |
| `DemoScreen.kt` | Compose UI |
| `otpless_network_security_config.xml` | Network configuration required by SNA |

---

# Dashboard screenshots

The demo includes the following dashboard screenshots:

### App ID configuration

`docs/screenshots/dashboard-app-info.png`

Shows where to find and copy the OTPless App ID.

### Channel configuration

`docs/screenshots/dashboard-channels.png`

Shows the phone-number authentication configuration with OTP, Silent Network Auth, SMS and WhatsApp enabled.

### Android application configuration

For a customer-facing distribution of this README, it is recommended to also add a screenshot of the Android configuration screen showing where the package name and SHA-256 fingerprint are registered.

Save it as:

```text
docs/screenshots/dashboard-android-config.png
```

Then add:

```markdown
![Android app configuration — package name and SHA-256 fingerprint](docs/screenshots/dashboard-android-config.png)
```

Other useful screenshots may include:

- The dashboard home/application list.
- A successful test authentication in the dashboard logs or analytics view.

---

# Build

To build the debug application:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

The project requires:

- **JDK:** 17 or later
- **compileSdk / targetSdk:** API 37
- **minSdk:** 23

The Android SDK location is configured through `sdk.dir` in `local.properties`.

---

## Next steps

After successfully running this demo, you can use the integration patterns in `MainActivity.kt` as a reference for adding OTPless Headless Authentication to your own Android application.

For the latest SDK documentation and integration guidance, refer to the official OTPless documentation:

https://otpless.com/docs/frontend-sdks/app-sdks/android/new/headless/intro
