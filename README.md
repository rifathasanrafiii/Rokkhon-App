# রক্ষণ (Rokkhon)

Bangladesh blood-donor network (Kotlin, Jetpack Compose, Firebase). A personal-safety SOS system is built in but hidden for now: set `FeatureFlags.SOS_ENABLED = true` in `app/src/main/java/com/example/FeatureFlags.kt` to show it again.

- **রক্ত (Blood):** find available, *compatible* donors by area; post a blood request that notifies matching donors in that area; share requests to WhatsApp/Facebook; one pledge per account.
- **SOS:** in-app SOS or 3x power/volume press (5-second cancel window), real microphone evidence, live GPS until "I'm safe", one-tap 999 call and SMS with a location link to up to 3 trusted contacts.
- **1 km Bluetooth mesh:** an SOS is broadcast over BLE with the sender's position, a packet id and a hop count. Every Rokkhon phone that hears it alerts its user and re-broadcasts it while it is within 1 km of the sender, so it reaches every app user in that circle without internet; it stops at the 1 km edge or after 6 hops (3 hops for a phone that doesn't know its own location).
- **Safety network (background):** a foreground service (`data/guard/RokkhonGuardService`) keeps listening and relaying while the app is closed, keeps a low-power location fix, and for signed-in users alerts on new cloud SOS within 1 km. It restarts after a reboot and can be turned off from the SOS tab, Profile or its notification.
- **Find:** search every donor and open request by name, area, hospital and blood group, across all 8 divisions, 64 districts and 494 upazilas (area data: nuhil/bangladesh-geocode, MIT).
- **90-day rule:** after "I donated today" a donor shows as resting with a countdown and becomes available again automatically after 90 days.
- **Rewards:** points, daily check-in streak and badges on their own tab.
- **Real data only:** every donor, request and SOS comes from Firestore. Without sign-in (or without Firebase config) lists stay empty and only the offline SOS tools work (Bluetooth mesh, 999, SMS).
- **Admin web (`admin-web/`):** rewards, location off, account suspend and device ban, written to `admin_controls/global_policy` and applied live in the app.

## Build and install

Requirements: JDK 17+, Android SDK (platform `android-36.1`), Gradle 9.3.1.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle assemblePreview        # app/build/outputs/apk/preview/app-preview.apk (optimised, smooth)
adb install -r app/build/outputs/apk/preview/app-preview.apk
```

`assemblePreview` is the release build (R8, not debuggable) signed with the repo's `debug.keystore`, so it installs without the upload key. `assembleDebug` also works but Compose is much slower in debug builds.

## Firebase setup

The app uses Firebase project `btshop-5b889` with the Firestore database named in `app/src/main/res/values/firebase_applet_config.xml`.

1. Firebase Console → Project settings → add an Android app with package `com.aistudio.rokkhon.bdxqpz`, download `google-services.json` into `app/`.
2. Add the SHA-1 and SHA-256 of the signing key (`keytool -list -v -keystore debug.keystore -storepass android` for preview/debug builds).
3. Authentication → Sign-in method: enable **Phone** and **Google**.
4. Deploy the rules: `firebase deploy --only firestore:rules`.

Without `google-services.json` the app still builds and runs, but sign-in and the shared network are off.

## Admin setup

Open `admin-web/index.html` (any static host or locally) and sign in with Google. Only accounts listed in Firestore can change the policy: create a document `admins/{uid}` (any fields) in the Firebase Console for each admin's Auth UID. Apps register their hardware ID in `device_registry/{uid}`, which is what device bans match.

## Tests

```bash
# Unit tests (blood compatibility, 90-day rule, phone validation, ...) + Firestore repository tests
firebase emulators:exec --only firestore,auth --project demo-no-project "gradle testDebugUnitTest"

# Firestore security rules tests (one vote per account, trust score not client-writable, ...)
firebase emulators:exec --only firestore,auth --project demo-no-project "node --test firestore.test.js"
```

## Screenshots

`ScreenshotTourTest` renders every main page with Robolectric (no emulator needed). It is opt-in:

```bash
ROKKHON_SCREENSHOTS=$PWD/screenshots gradle testDebugUnitTest --tests '*ScreenshotTourTest*'
ROKKHON_SCREENSHOT_LANG=en ROKKHON_SCREENSHOTS=$PWD/screenshots-en gradle testDebugUnitTest --tests '*ScreenshotTourTest*'
```

Each screenshot also gets a `.tags.json` with the bounds of every tagged element (used by the click-through simulator).

## Design

Monochrome, after Uber's Base design system: black content and buttons on white, grey surfaces, colour only where it means something (green = can donate now, red = emergency / sign out, blue = your GPS dot). Shared pieces live in `ui/components/RokkhonKit.kt` and `ui/theme/Color.kt` (`RokkhonPalette`).

Fonts: Google Sans (SIL Open Font License, see `licenses/GoogleSans-OFL.txt`).
Icons: Uicons (regular rounded) by Flaticon, converted to vectors in `ui/icons/UIcons.kt`; free with attribution (credit on the Profile screen, licence in `licenses/Uicons-Flaticon.txt`).
