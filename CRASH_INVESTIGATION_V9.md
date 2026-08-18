# ApnaServo Android crash investigation — version 1.0.8 / code 9

Date: 2026-08-17

## Evidence and root cause

The only complete production crash stack supplied for this investigation is:

`Firebase Database runloop 22.0.1` → `WebsocketConnection.send` → `JsonMapper.serializeJson` → `org.json.JSONWriter.<init>(JSONWriter.java:98)` → `java.lang.NoSuchFieldError`.

This is a binary classpath collision, not a missing-data/UI exception. Socket.IO declares the obsolete JVM artifact `org.json:json:20090211`. Android itself supplies `org.json`. If the obsolete dependency is packaged, it shadows the framework implementation used by Firebase Database and Firebase calls a field that the shadow class does not contain.

1. Exact root exception: `java.lang.NoSuchFieldError`.
2. Root class: `org.json.JSONWriter`.
3. Root method/line: `JSONWriter.<init>(JSONWriter.java:98)`.
4. Firebase call site: `com.google.firebase.database.util.JsonMapper.serializeJsonValue(JsonMapper.java:57)` while `WebsocketConnection.send` is serializing connection statistics/data.
5. Project fix location: `app/build.gradle`, Socket.IO dependency block, current line 80.
6. Why intermittent: the bad constructor is reached when the Realtime Database WebSocket connects/reconnects or sends statistics/data. Device/network/session timing determines when that runloop path executes, so launch or a Firebase-backed feature may work before the process reaches it.
7. Trigger: startup/background sync or any booking/support/tracking flow that activates Realtime Database, especially after reconnect/network switching.
8. Why UI/null/try-catch fixes did not solve it: the exception occurs on Firebase's internal database runloop thread, outside screen click-handler error boundaries. Only removing the conflicting packaged class corrects the runtime linkage.

## Root-cause fix verification

The Socket.IO declaration excludes `org.json:json`. Verification against `releaseRuntimeClasspath` reports **no matching org.json dependency**. Inspection of the final AAB reports **0 packaged `org/json/` entries**. Firebase Database remains at the BoM-compatible version and the Firebase feature was not removed or disabled.

This proves the supplied Firebase `NoSuchFieldError` root cause is absent from the code-9 AAB. It does not prove that an unobserved, different crash has the same cause.

## Audit findings

- Architecture: one Android `Activity` builds the customer screens; no Fragments, Compose, ViewModels, WorkManager workers, WebViews or RecyclerViews exist in this project.
- Android components: `MainActivity` and `ApnaServoMessagingService`.
- Async systems reviewed: OkHttp callbacks/retries, Socket.IO, Firebase Auth, Firestore listeners, RTDB listeners, Storage tasks, FCM, SMS Retriever, location callbacks, geocoder executor and main-thread delayed jobs.
- Teardown removes Firestore/RTDB listeners, disconnects Socket.IO, stops network/SMS receivers, stops the geocoder executor, clears handlers and evicts bitmap cache.
- Reverse geocoding is executed on a bounded background executor rather than the UI thread.
- Remote and notification images use bounded Glide requests; the compact local bitmap cache is a byte-bounded `LruCache` and is cleared on memory pressure.
- Runtime location, camera and Android 13 notification permissions are checked before their protected operations.
- Network requests execute on OkHttp threads, parse malformed responses into callback errors, distinguish common HTTP errors, and avoid automatic retries for non-idempotent booking/payment/status operations.
- R8 keeps Android/Firebase manifest components and Socket.IO reflective classes. Release mapping generation/upload is active.

## Crashlytics diagnostics added

Release Crashlytics now records only non-sensitive context:

- current screen name;
- app version and version code;
- Android SDK;
- authenticated/anonymous boolean;
- low-RAM-device boolean;
- coarse API operation group such as `GET /bookings` (no booking ID, query, phone, token, OTP or user data).

No password, OTP, token, authorization header, API key, phone number or private profile data is logged. The release build executed `uploadCrashlyticsMappingFileRelease`, and `mapping.txt` exists for de-obfuscating release traces.

## Test/build results

- Clean build: passed.
- Unit tests: 19 tests, 0 failures, 0 errors, 0 skipped.
- Debug build: passed.
- Release APK compile/package validation: passed.
- Release lint: 0 errors, 187 non-blocking warnings.
- R8/minification/resource shrinking: passed.
- Signed release AAB: passed `verifyPlayRelease` and `jarsigner` verification.
- AAB identity: version name `1.0.8`, version code `9`, minSdk 23, targetSdk 36.
- Final AAB SHA-256: `CC9DDB984043030BCC5FF68F4345ED38ED89C9FE4CB24617870A3DB815A3AFE1`.

## Device and reproduction status

An Android 17 / API 37 x86_64 emulator (`sdk_gphone16k_x86_64`) was subsequently available. The debug and signed release packages installed successfully and Android started `MainActivity` successfully. No `FATAL EXCEPTION`, native fatal signal or ANR was logged. The emulator kernel then killed the foreground process through low-memory killer with `reason: min watermark is breached even after kill`. Debug RSS at the kill ranged from roughly 138–260 MB; the minified release RSS at the kill was roughly 82 MB. This is an OS memory-pressure process death, not an application exception, and explains a silent return to the launcher on a severely constrained/unstable device. The same emulator reported heavy swap pressure and later failed to boot when restarted with a larger RAM override, so it is not sufficient evidence of a normal-device app leak.

No source feature was disabled and `largeHeap` was deliberately not enabled, because that would only postpone/worsen system pressure. Startup uses bounded artwork, a byte-bounded bitmap cache, R8-minified release code and memory-pressure cache eviction. A physical low-RAM/OEM device is still required to distinguish genuine field-device pressure from this emulator's pathological watermark state.

No physical device was connected. Therefore no honest claim is made for physical-device OTP delivery, OEM-specific behavior, real Wi-Fi/mobile handover, camera/location permission denial or long-session testing. Crashlytics console access was also unavailable, so delivery of a forced test crash was not performed.

## Remaining risk and next evidence required

The supplied `NoSuchFieldError` is root-caused and removed from the final artifact. The runtime test also identified OS low-memory killing as a separate reason the app can silently close without a Java crash. If Android shows “keeps stopping” after installing this exact code-9 build, it must be treated as a potentially different exception. Required evidence is the complete Crashlytics event or `adb logcat` section from `FATAL EXCEPTION` through the deepest `Caused by`, including device model, Android version, app version/code and user action. For a silent launcher return, capture the `lowmemorykiller`/`ActivityManager` lines and `dumpsys meminfo`. The new diagnostics attach screen/API context without private data. This report intentionally does **not** claim that every possible unrelated crash is eliminated without that production evidence.
