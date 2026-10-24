# রক্ষণ (Rokkhon)

Bangladesh blood-donor network + personal safety SOS app (Kotlin, Jetpack Compose, Firebase).

- **রক্ত (Blood):** find available, *compatible* donors by area; post a blood request that notifies matching donors in that area; share requests to WhatsApp/Facebook; one pledge per account.
- **SOS:** in-app SOS or 3x power/volume press (5-second cancel window), real microphone evidence, live GPS until "I'm safe", one-tap 999 call and SMS with a location link to up to 3 trusted contacts.
- **1 km Bluetooth mesh:** an SOS is broadcast over BLE with the sender's position, a packet id and a hop count. Every Rokkhon phone that hears it alerts its user and re-broadcasts it while it is within 1 km of the sender, so it reaches every app user in that circle without internet; it stops at the 1 km edge or after 6 hops (3 hops for a phone that doesn't know its own location).
- **Safety network (background):** a foreground service (`data/guard/RokkhonGuardService`) keeps listening and relaying while the app is closed, keeps a low-power location fix, and for signed-in users alerts on new cloud SOS within 1 km. It restarts after a reboot and can be turned off from the SOS tab, Profile or its notification.
- **Map:** real OpenStreetMap tiles (osmdroid) with donor, patient and SOS pins.
- **Demo mode:** without Google sign-in (or without Firebase config) the app shows a clearly labelled sample community; nothing is uploaded.

## Build

Requirements: JDK 17+, Android SDK (platform `android-36.1`), Gradle 9.3.1.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
```

`app/google-services.json` is optional. Without it the app builds and runs in demo mode; with it Google sign-in and the shared Firestore network are enabled.

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

The Find tab downloads real OpenStreetMap tiles, so rendering it needs network access. Each screenshot also gets a `.tags.json` with the bounds of every tagged element (used by the click-through simulator).

Fonts: Hind Siliguri (SIL Open Font License, see `licenses/HindSiliguri-OFL.txt`).
