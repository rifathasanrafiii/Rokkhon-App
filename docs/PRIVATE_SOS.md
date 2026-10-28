# Private SOS and live emergency tracking

A private SOS shared only with Rokkhon accounts the user chose. It is not a community feature:
nobody else can find, list or open a session. It reuses the existing Firebase project and sign-in
(Google / phone), and leaves the blood-donor features and the hidden community SOS
(`FeatureFlags.SOS_ENABLED`) alone.

Entry points: the shield icon on Home (hold-to-start screen), Profile → *SOS Access*, an SOS
notification, or a `https://rokkhon.com/live/{sessionId}` link.

## How it works

| Part | Where | Notes |
|---|---|---|
| Trusted accounts and permissions | Realtime Database `sosAccess`, `sosAccessIndex`, `sosLookup` | Find someone by their exact verified email or phone (no name search, no listing). Invite → they accept/decline. Per person: live location, live audio, alerts, history. Revoke at any time. |
| Session | `sosSessions/{id}`, `sosActive/{uid}`, `sosHistory/{uid}` | Starts after a 3-second hold. 128-bit random id. Runs until the sender ends it. |
| Live GPS | `liveLocations/{id}/latest` (+ last 30 points, one a minute, in `trail`) | Foreground service `PrivateSosService` (type location). Every 5 s while moving, 10 s when still or when the battery is ≤20 %. The last known position is shown first, clearly marked. Time is in server time. |
| Live audio | WebRTC (`io.getstream:stream-webrtc-android`, Apache-2.0), signalling in `sosSignaling/{id}/{listenerUid}` | One-way, a peer connection per listener, DTLS-SRTP encrypted phone-to-phone. Nothing is stored on Firebase. The mic only captures while someone is connected. |
| Recording | Listener's phone only (`files/sos_recordings`, AAC .m4a) | Only if the sender allowed recording; the listener confirms a consent notice; the sender sees "Recording". Play, share, save to Downloads/Rokkhon, delete. Never uploaded. |
| Map | MapLibre + OpenFreeMap tiles | Free, no API key. "Open in Google Maps" hands over only the current coordinates. |
| Push alerts | FCM, sent by `web/api/sos-notify.js` | Needs the trusted server below. The service-account key is never in the app. |
| Web link | `web/live/index.html` | Same rules: sign in with an authorised account, otherwise nothing is shown. Shows status, map, accuracy, time. Audio is app-only. |

Access control lives in `database.rules.json` (Realtime Database rules), next to the data it
protects; Firestore rules do not cover Realtime Database paths. Highlights:

- Only the session owner writes its location, session and signalling offers.
- Reading a location needs an *accepted* grant with the location permission and an *active*
  session (or the history permission for ended ones). Pending, declined, revoked, ended → refused.
- Audio signalling needs the audio permission and a session with audio turned on.
- Listener "recording" can only be true when the owner allowed recording.
- Events are append-only; push tokens are private to their owner.

Revocation is one atomic write that deletes the grant (and that listener's signalling). The rules
then refuse further reads, and both apps also react to the change: the sender closes that audio
connection, the listener's screen switches to "Access removed" and stops listening/recording.

## Setup (one time, free)

1. **Create the Realtime Database**: Firebase console → Build → Realtime Database → Create
   (Spark plan, free; pick a region, *locked mode*). Download `google-services.json` again into
   `app/` (it now contains `firebase_url`), or put the URL in `app/src/main/res/values/sos_config.xml`
   (`sos_rtdb_url`).
2. **Deploy the rules**: `firebase deploy --only database` (uses `database.rules.json`).
3. **Push alerts (optional but recommended)**: deploy `web/` to Vercel (Hobby, free) on the
   rokkhon.com domain, and set these environment variables there (the admin panel also needs
   `FIRESTORE_DATABASE_ID`, see `docs/ADMIN_PANEL.md`):
   - `FIREBASE_SERVICE_ACCOUNT`: Firebase console → Project settings → Service accounts →
     Generate new private key (paste the JSON). Keep it only in Vercel.
   - `FIREBASE_DATABASE_URL`: the Realtime Database URL.
   The same deployment serves the `/live/{id}` page; set `databaseURL` in `web/live/index.html`.
   Add `rokkhon.com` to Firebase Auth → Settings → Authorized domains so Google sign-in works there.
4. **TURN relay (optional)**: see limitations. If you run a free self-hosted coturn, put its URL and
   credentials in `sos_config.xml` (`sos_turn_url`, `sos_turn_username`, `sos_turn_credential`).

If step 3 is skipped the app says so on the SOS screen ("push server isn't available"): trusted
people still get an in-app alert while Rokkhon is running on their phone, and the sender can share
the link or call 999.

## Limitations (honest list)

- **Not guaranteed.** Location and audio need the internet, permissions and Android not killing
  the app. The app never claims otherwise and always offers 999.
- **Push needs a server.** FCM sends must come from a trusted server; free Firebase (Spark) has no
  Cloud Functions, so the Vercel function above is used. Without it, alerts reach only phones where
  Rokkhon is running.
- **Audio on strict networks.** Free public STUN covers most connections. When both phones are
  behind strict NAT (some mobile carriers), audio needs a TURN relay; no paid relay is bundled. The
  app then shows "Couldn't connect", retries with back-off and keeps location live. Fix: a
  self-hosted coturn (free software, needs a small server).
- **Restart after the app is killed.** Android 14+ may refuse to restart a location/microphone
  service from the background. The app then posts "Your SOS is still on — tap to resume", and on
  next launch offers Resume / End.
- **Web page**: Google sign-in only, no audio.
- **Real-device testing still to do** (cannot be done from the build machine): screen locked,
  app in background, weak network/airplane-mode toggles, revoking while live, 3+ listeners,
  Android 10 / 12 / 14 / 15, Doze.

## Tests

```bash
# Realtime Database rules (11 tests: lookup, invite/accept, revoke, GPS privacy, signalling, recording consent, ...)
firebase emulators:exec --only database --project demo-no-project "node --test database.test.js"
# Pure logic (session ids, email/phone keys, link parsing, adaptive GPS interval)
gradle testDebugUnitTest --tests '*SosLogicTest*'
# SOS screens render, including the "not set up" states
gradle testDebugUnitTest --tests '*SosScreenshotTest*'
```
