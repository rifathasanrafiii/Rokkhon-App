# Admin panel (`web/admin`)

A web panel for the Rokkhon team, deployed with the rest of `web/` on Vercel. It reads and writes the
app's own Firestore database, so every change shows up in the app live.

| Page | What it does | Where it is stored |
| --- | --- | --- |
| Dashboard | Donors, ready donors, open requests, pending claims, registered phones, donors per blood group | read only |
| Push | Sends a notification to everyone, one blood group, one district, or a blood group in a district; picks the app tab it opens; lists what was sent | FCM topics via `/api/admin-push`, plus `announcements` |
| Reward cards | Creates, edits, hides and deletes the cards on the app's Rewards page; sets the points for check-in, pledges and SOS replies | `reward_offers`, `admin_controls/global_policy` |
| Claims | Approves (with a message), rejects (with a reason), marks as handed over, or deletes reward claims | `reward_claims` |
| Donors | Sets a donor's points or streak, turns their location off, suspends their account, bans their phone | `admin_controls/global_policy` |
| Blood requests | Marks a request fulfilled or deletes it | `blood_requests` |
| Devices & policy | Turns location sharing off for everyone, bans a phone by its ID, lists registered phones | `admin_controls/global_policy`, `device_registry` |

## How the app is connected

- **Reward cards.** Active `reward_offers` appear as cards on the Rewards page. A user with enough points taps
  *Claim*, which writes `reward_claims/{offerId}_{uid}` (one claim per reward per user; the rules check
  the cost and the user's points). Points live on the phone, so a person reviews every claim. When it is
  approved the app takes the points once, and the card shows the status and the admin's message.
- **Push.** The app subscribes each phone to the FCM topics `all`, `bg_<group>` (e.g. `bg_O_NEG`) and
  `dist_<district>` from the donor's profile and chosen area (`data/push/PushTopics.kt`). The district list
  in the panel (`web/admin/districts.js`) is generated from the app's area data and checked by
  `PushTopicsTest`. Pushes arrive on the "Rokkhon updates" channel and also appear in the app's
  notification centre, only for the people they were sent to.
- **Policy.** Suspensions, device bans, location off and points are the same `global_policy` fields the
  app already reads.

## Setup

1. **Deploy the Firestore rules** (new collections `reward_offers`, `reward_claims`, `announcements`):
   `firebase deploy --only firestore:rules`
2. **Add admins.** Firebase console → Firestore → the app's database → collection `admins` → a document
   whose ID is the admin's Auth UID (any field). The panel shows the UID to add when a non-admin signs in.
3. **Deploy `web/` to Vercel.** New project → import this repo → *Root Directory* `web` → Deploy.
   Environment variables (Settings → Environment Variables):
   - `FIREBASE_SERVICE_ACCOUNT`: Firebase console → Project settings → Service accounts → Generate new
     private key; paste the whole JSON. It stays on Vercel and is never in the app or the panel.
   - `FIRESTORE_DATABASE_ID`: `ai-studio-android-rokkhon-57cdc872-fddf-4dcf-83c1-3037e342e558`
   - `FIREBASE_DATABASE_URL`: the Realtime Database URL (used by the private SOS alerts).
   Redeploy after adding them.
4. **Allow sign-in on the Vercel domain.** Firebase console → Authentication → Settings → Authorized
   domains → add your domain (e.g. `rokkhon.vercel.app` or `rokkhon.com`).
5. Open `https://<your-domain>/admin/` and sign in with Google.

The push function allows at most 20 pushes per hour, so a leaked admin session can't flood every phone.
Phones get topic pushes after they open the new app version once (that is when they subscribe).

## Local testing

```bash
cd web && python3 -m http.server 5055 &
firebase emulators:start --only firestore,auth --project btshop-5b889
# open http://localhost:5055/admin/?emulator and call emulatorSignIn("me") in the console
```
