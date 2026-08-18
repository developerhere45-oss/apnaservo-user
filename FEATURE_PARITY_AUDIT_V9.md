# ApnaServo User App — Feature Parity Audit (version 1.0.8 / code 9)

Audit date: 2026-08-17  
Compared baseline: `ApnaServo-User-v1.0.7-code8-PLAY-CONSOLE-FINAL.aab`  
Current bundle: `ApnaServo-User-v1.0.8-code9-FEATURE-PARITY-FINAL.aab`

## Scope and evidence

The audit inspected Android components, all reachable `MainActivity` screen states and click handlers, `BackendClient`, Socket.IO, Firebase Auth/FCM/Firestore/Realtime Database/Storage usage, feature flags, permissions, local encrypted preferences, unit tests, recent Git changes, and the contents of the old and current AABs. The partner application is a separate Android project and is not packaged in this customer AAB; this audit covers the customer-side partner assignment, tracking, chat, and call flows.

## Feature registry and parity matrix

`MainActivity.java` owns the listed screens/actions unless another source is named. `BackendClient.java` supplies REST calls, `BackendSocketClient.java` supplies realtime socket events, `BookingStore.java` supplies Firebase booking persistence, and `ApnaServoMessagingService.java` supplies FCM/deep-link behavior.

| # | Feature | Screen / user action | Backend / database dependency | Old | Current / reachable | Result |
|---:|---|---|---|:---:|:---:|---|
| 1 | Branded splash and adaptive icon | App launch | Local resources/manifest | Yes | Yes | Keep; safe-zone artwork retained |
| 2 | Mobile-number login | Login → Send OTP | `/otp/send`, Firebase Auth, encrypted prefs | Yes | Yes | Keep |
| 3 | OTP verification and autofill | OTP → Verify | `/otp/verify`, SMS Retriever/Firebase Auth | Yes | Yes | Keep |
| 4 | Customer profile/session | Profile, restart, logout | `/users/me`, `/users/profile`, encrypted prefs | Yes | Yes | Keep |
| 5 | Home service catalogue | Home/service cards | Local catalogue + control config | Yes | Yes | Keep |
| 6 | Service search | Search and service selection | Local catalogue; `search` flag | Yes | Yes | Keep |
| 7 | General service booking | Service → details → address → confirm | `/bookings`, RTDB/Firestore | Yes | Yes | Keep |
| 8 | Category availability | Tap available/unavailable service | App Control `services`; booking 409/503 | Yes | Yes | Keep |
| 9 | Current-location address | Use current location | Android location + geocoder + `/location` | Yes | Yes | Keep; geocoder off UI thread |
| 10 | Manual address | Enter property/location | Booking API/Firebase | Yes | Yes | Keep |
| 11 | Saved addresses | Profile/booking address chooser | `/users/profile`, encrypted cache | Yes | Yes | Keep |
| 12 | Verified primary contact | Booking contact details | Firebase login phone + booking payload | Yes | Yes | Keep; primary stays login number |
| 13 | Booking history | Bottom Bookings tab | `/bookings/user`, Firebase fallback | Yes | Yes | Keep |
| 14 | Booking detail | Select booking | Booking model/API | Yes | Yes | Keep |
| 15 | Booking tracking/status | Track Booking | REST + RTDB + Socket.IO | Yes | Yes | Keep |
| 16 | Partner assignment display | Booking assignment screen | Booking status/partner payload | Yes | Yes | Keep |
| 17 | Partner/support calling | Call action | `/calls`, Android dialer | Yes | Yes | Keep |
| 18 | Booking chat | Chat action/send message | `/chat/*`, Socket.IO, Firestore/RTDB | Yes | Yes | Keep |
| 19 | Quote/counter-offer | Review/accept counter quote | `/quote/counter`, booking state | Yes | Yes | Keep |
| 20 | Payment submission/status | Payment action | `/payment`, `/payment-submitted` | Yes | Yes | Keep |
| 21 | Booking completion | Completed-state UI | Booking status/Firebase | Yes | Yes | Keep |
| 22 | Rating and review | Completed booking → Review | `/reviews/bookings/*` | Yes | Yes | Keep |
| 23 | Cancellation | Eligible booking → Cancel | Booking status endpoint/registry | Yes | Yes | Keep |
| 24 | Book again | Booking detail → Book Again | Catalogue + booking flow | Yes | Yes | Keep |
| 25 | AI/customer support | Support screen/messages | Support API + Firestore/RTDB | Yes | Yes | Keep |
| 26 | Complaint escalation to admin | Complaint → submit | `/users/support-tickets/sync`, support collections | Yes | Yes | Keep |
| 27 | Support attachment upload | Attach photo/file | Firebase Storage `support_uploads/customer` | Yes | Yes | Keep |
| 28 | Legal, policies and delete account | Profile → Legal/Support/Delete | Policy URL + `/users/delete-account-request` | Yes | Yes | Keep; never feature-gated |
| 29 | Commercial enquiry | Home banner/card | `/contact`; `commercial_services` flag | Yes | Yes | Keep |
| 30 | Laundry item booking | Laundry → choose items | Booking API/Firebase | Yes | Yes | Keep; professional local icons |
| 31 | Laundry tracking/payment | Laundry booking tracking | Booking/payment/realtime APIs | Yes | Yes | Keep |
| 32 | Cleaning type booking | Cleaning → select type | Booking API/Firebase | Yes | Yes | **Restored shrink-safe artwork** |
| 33 | Roadside service flow | Roadside service card | Booking API/Firebase | Yes | Yes | Keep |
| 34 | Appliance variants | Appliance service selection | Booking API/Firebase | Yes | Yes | Keep |
| 35 | Push inbox, deep links, rich images | Notification tap/inbox | FCM + `/notifications/*` | Yes | Yes | **Restored bounded Glide image** |
| 36 | Offline/retry/cached flow | Network unavailable → Try Again | Network monitor + cached state | Yes | Yes | Keep |
| 37 | Global App Control states | Admin LIVE/maintenance/coming-soon | `/app-control/config` + cache | No | Yes | New |
| 38 | Remote feature registry | Admin enable/disable supported features | App Control feature map + local manifest | No | Yes | New |
| 39 | Dynamic banners/announcements | Home/admin content and CTA | App Control content + dismiss cache | No | Yes | New |
| 40 | Force/soft Play update UI | Admin update prompt → Play Store | App Control update + allowlisted Play URL | No | Yes | New |

### Counts

- Old feature count: **36**
- Current feature count: **40**
- Restored feature count: **2** (cleaning artwork packaging; rich notification artwork)
- New feature count: **4**
- Removed feature count: **0**
- Features with unresolved regression: **0 found by source/AAB/build audit**
- Accidentally disabled features remaining: **0**
- Accidentally deleted features remaining: **0**

## Reachability and feature flags

Reachable screen states include login, OTP, home, all-services, detail, booking, confirm, booking submission/confirmation, assignment, tracking, booking chat, payment/completion, history/detail, notifications, profile/legal/addresses, support, commercial, service-preparing, global status, and pre-launch. Remote flags only gate implemented optional capabilities (`home_banners`, `booking_history`, `commercial_services`, `service_booking`, `search`, `notifications`, `booking_chat`, `address_management`, `payment`, `reviews`). Authentication, legal, account deletion, and core safety/support flows are not remotely disabled. Cached config is used offline; absent/malformed config defaults to the normal safe flow.

## Old versus current package-size investigation

| Package section | code 8 baseline | code 9 optimized (before parity restore) | Finding |
|---|---:|---:|---|
| Raw AAB | 16,336,170 bytes | 12,995,647 bytes | 3,340,523-byte reduction |
| DEX | 4,768,512 bytes | 5,022,104 bytes | Current executable code is larger, not broadly removed |
| Resources | 10,766,508 bytes | 7,134,673 bytes | Main reduction is unused/duplicate image and dependency resources |
| Native libraries | 22,900 bytes | 22,900 bytes | No ABI/native capability removed |
| Root/metadata | 502,853 bytes | 502,871 bytes | Effectively unchanged |

The size reduction is explained by release minification, optimized resource shrinking, WebP/asset cleanup, and removal of unused transitive UI resources. Android App Bundles also produce device-specific split downloads, so Play Console download size is smaller than the raw AAB and is not a feature count. The current DEX is larger than the baseline, which is direct evidence that the new control/update/stability code remains packaged.

After restoring the four shrink-safe cleaning artworks and rich-notification loader, the final signed AAB is **13,156,560 bytes**. It remains 3,179,610 bytes smaller than the code-8 baseline while preserving the audited functionality.

One genuine resource-shrinking risk was found: cleaning artwork was referenced only via `getIdentifier`. It was changed to direct `R.drawable` references and all four images were verified inside the final AAB. R8 remains enabled; Android/Firebase manifest components and Socket.IO reflective classes retain explicit keep rules. Disabling R8 globally was not necessary.

## Recent-change regression review

| Recent change | Files | Functionality affected | Old → current behavior | Regression status |
|---|---|---|---|---|
| App Control integration | `AppControlConfig`, `AppControlRepository`, `MainActivity` | Global/service/admin states | Local-only → cached live admin control | Intended, retained |
| Feature registry | `FeatureRegistry`, `MainActivity` | Optional feature reachability | Always on → remotely controllable with safe defaults | Intended, retained |
| Branded update prompts | `MainActivity` | Force/soft update | Generic/no prompt → Play-linked branded modal | Intended, retained |
| Laundry item polish | `MainActivity` | Laundry selector | AI-looking assets → local drawn professional icons | User-requested UX change, flow retained |
| Long-session/image hardening | `MainActivity`, messaging service | Memory/crash behavior | Manual/unbounded bitmap work → bounded cached loading | Feature retained |
| Optimized shrinking | Gradle configuration | Package size | Unoptimized resources → unused resources removed | Cleaning dynamic resource issue fixed |

## Files restored/modified/deleted and dependency/API changes

- Files restored as separate old files: **none**; behavior was merged into current sources.
- Files modified for parity repair: `MainActivity.java`, `ApnaServoMessagingService.java`.
- Files deleted by this audit: **none**.
- Dependency added in the preceding quality change and retained: Glide 4.16.0 for bounded/cached images.
- Dependencies removed by this audit: **none**.
- API endpoints removed/changed by this audit: **none**.
- Firebase paths/operations removed/changed by this audit: **none**.
- Permissions/components removed by this audit: **none**.

## Validation result

- `testDebugUnitTest`: **19 tests, 0 failures, 0 errors, 0 skipped**.
- Debug Java/resource compilation and debug packaging: passed during the combined verification run.
- `lintRelease`: **0 errors, 187 warnings** (warnings are non-blocking compatibility/style advisories).
- `bundleRelease`, R8 minification, optimized resource shrink, signing validation and custom `verifyPlayRelease`: **passed**.
- Final AAB contains all four dynamically affected cleaning artwork resources.
- Release signing: passed using the configured Play upload signing setup.
- Version preserved: `versionName 1.0.8`, `versionCode 9`, `minSdk 23`, `targetSdk 36`.

Physical-device OTP/SIM delivery, OEM launcher rendering, phone call handoff, Play upgrade-from-production, and real partner acceptance require external devices/accounts and cannot be truthfully proven by a local static/build audit. Their code, permissions, routes, API/Firebase integrations, and packaging remain present; they should be included in release-track/device QA rather than represented as removed or silently disabled.
