# Implementation Plan: Proximity-Scoped BLE + Internet Alerts & Anti-Fake-Report SOS Verification

## Overview
We are upgrading **Rokkhon (রক্ষন)** to eliminate notification fatigue while protecting both emergency responders and genuine SOS victims from malicious/fake "False" reports:
1. **Fake-Report-Resistant "False" System (No Blind 3-Report Trust Penalty)**:
   - Each user account can submit a **False (ভুয়া)** report **only once** per SOS alert, and must first inspect the **5s Audio/Video Evidence** or be within the local area before flagging.
   - Reaching **3 or more false reports will NOT automatically deduct the SOS sender's Trust Points** or delete the SOS, because attackers or trolls could otherwise submit fake "False" reports against a real victim in danger.
   - Instead, the SOS stays active on the map for **24 hours** and displays a **Side-by-Side Community & Evidence Status** (`যাচ্ছি / Nearby: Y` vs `ভুয়া / False: X`) along with an **Evidence & Responder Shield**:
     - **Protected by 5s Blackbox & Responders**: If the SOS has verified 5s Camera + Audio evidence or even **1 real responder (`Nearby / যাচ্ছি`)**, the victim's Trust Score is **100% protected from fake reports**.
     - **Reporter Accountability (Anti-Abuse)**: If someone flags an SOS as "False" when it is backed by verified 5s evidence and real responders, the *false reporter* receives an anti-abuse warning instead of the victim losing points.
     - **24-Hour Final Audit**: Only if an SOS expires after 24 hours with **0 responders**, **unverified/silent telemetry**, and **overwhelming local false consensus** is a cautionary review note placed on the sender.
2. **1 km Proximity-Scoped SOS Notifications (Bluetooth BLE + Internet)**:
   - When an SOS is triggered, **only installed app users within a `<= 1.0 km` radius** receive push/in-app notifications and BLE mesh alerts—whether offline via Bluetooth Low Energy (BLE) or online via Internet.
   - Users further than 1 km away are not disturbed by notifications, though they can still view SOS pins across their selected area on the **Find** map.
3. **Area-Wide Matching Blood Donor Notifications (Bluetooth BLE + Internet)**:
   - When a patient posts a blood request, the alert is broadcast across the **entire selected area/district** over both **Bluetooth BLE Mesh** and **Internet**.
   - Notifications are delivered **only to available donors in that area whose blood group matches the patient's requested blood group**.

---

## Architecture / Flow Diagram

```text
  [SOS Triggered / Blood Request Posted]
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
 [Bluetooth BLE Mesh]    [Internet / Cloud Sync]
        │                       │
        └───────────┬───────────┘
                    ▼
     ┌──────────────────────────────┐
     │  Smart Proximity & Match     │
     │  Notification Filter         │
     ├──────────────────────────────┤
     │ • SOS Alert:                 │
     │   Notify ONLY if Distance    │
     │   <= 1.0 km (BLE & Internet) │
     │                              │
     │ • Blood Request:             │
     │   Notify ONLY if Same Area   │
     │   + Matching Blood Group     │
     └──────────────┬───────────────┘
                    ▼
     ┌──────────────────────────────┐
     │  Anti-Fake-Report Shield     │
     ├──────────────────────────────┤
     │ • 1 False Vote Per Account   │
     │ • Must View 5s Evidence or   │
     │   Be Nearby to Flag False    │
     │ • 5s Audio/Video + Nearby    │
     │   Responders Shield Victim   │
     │   Trust Score from Fake      │
     │   Reports                    │
     │ • Auto-Expires After 24h     │
     └──────────────────────────────┘
```

---

## Key Features & User Flows

### 1. Fake-Report-Resistant SOS Verification & 24-Hour Lifecycle
- **One Vote Per Account**: Once a logged-in user taps **False (ভুয়া)** on an SOS card, their account ID is locked for that SOS (`Reported False • ভুয়া দেওয়া হয়েছে`) so a single person cannot spam-click the counter.
- **Evidence Inspection Gate**: Before a user can mark an SOS as False, the app prompts them to inspect the **5s Video + Audio Blackbox** (or verifies they are within the local area), preventing blind remote trolling.
- **No Automatic Trust Point Loss at 3 Reports**:
  - If 3+ people tap False, the SOS is **not** deleted and the victim's Trust Score is **not** automatically slashed.
  - Instead, the card displays a transparent verification banner comparing **Real Responders (`Nearby: Y`)**, **5s Camera/Audio Telemetry (`Verified 82 dB`)**, and **Disputed Flags (`False: X`)**.
  - **Victim Shield**: As long as the SOS has verified 5s audio/video blackbox telemetry or at least 1 nearby responder (`Nearby / যাচ্ছি >= 1`), the sender's Trust Score remains completely safe (`Shielded by 5s Evidence / Responders`).
  - **Fake Reporter Deterrent**: Users who repeatedly flag verified SOS alerts as false receive a warning that malicious false reporting lowers their own reporter credibility.
- **24-Hour Auto-Removal**: Every SOS remains visible on the Find map until the creator marks `"আমি নিরাপদ (I Am Safe)"` or until **24 hours** have elapsed, after which it automatically moves out of the active map into history.

### 2. 1 km Radius SOS Alert Routing (BLE + Internet)
- Whenever an SOS is broadcast over Bluetooth BLE or synced over the Internet, each device calculates the real-time Haversine distance between the user's current coordinates and the SOS coordinates.
- **Within 1.0 km (`<= 1.0 km`)**: Triggers an immediate high-priority notification, sound/vibration alert, and live banner so nearby people who can actually reach the victim respond fast.
- **Beyond 1.0 km (`> 1.0 km`)**: Suppresses push/BLE notifications so distant users are not spammed, while still rendering the SOS marker on the **Find -> SOS** map for situational awareness.

### 3. Area-Wide Matching Blood Donor Broadcast (BLE + Internet)
- When an urgent blood request is created, it broadcasts over both **Bluetooth BLE Mesh** and **Internet** tagged with the target **Area/District** and **Blood Group**.
- Receiving devices check:
  1. Is the donor in the **same selected Area/District**?
  2. Does the donor's **Blood Group match** the requested blood group?
- Only matching donors in that area receive the alert notification, while all users can browse open requests in the **Find -> Patients** tab.

---

## Data & State Management
- **Proximity & Expiry Calculations**:
  - Haversine distance calculator (`calculateDistanceKm`) for exact `<= 1.0 km` SOS filtering across both Firestore streams and BLE scan callbacks.
  - 24-hour expiry check (`createdAtMillis + 24h`) applied to active SOS streams so alerts remain live for 24 hours without premature deletion at 3 reports.
- **Verification & Credibility State**:
  - Per-account tracking of `reportedFalseSosIds` and `inspectedEvidenceSosIds` so users can only submit 1 false flag per SOS after checking evidence or being nearby.
  - Computed `verificationStatus` on each `SosAlert`:
    - `VERIFIED_SHIELDED`: Has 5s Camera + Audio evidence or `responderCount > 0` (Sender Trust Score is 100% protected even if 3+ false reports are submitted).
    - `COMMUNITY_DISPUTED`: Has 3+ false reports vs 0 responders; shows a transparent cautionary badge without deleting the post until the 24-hour window finishes.
- **Dual-Mode BLE Packet Encoding**:
  - Compact BLE manufacturer payload supporting both **`SOS_1KM`** (GPS coordinates + emergency code + 5s media verified bit) and **`BLOOD_AREA`** (Area ID + Blood Group + Units Needed).

---

## UI & Screen Structure
- **Find Screen -> SOS Tab & Map**:
  - **Distance Pill**: Shows exact distance (`0.4 km • Within 1km Alert Zone` or `1.8 km away`) on every SOS card and map callout.
  - **Nearby (`যাচ্ছি`) & False (`ভুয়া`) Action Row**:
    - `Nearby (Y)` button for responders heading to help.
    - `False (X)` button showing the live count (`False (3)`, `False (4)`, etc.) that locks after 1 tap per account and prompts the user to check the 5s Video/Audio Blackbox first if they haven't viewed it.
  - **Evidence & Responder Shield Banner**:
    - When `falseAlarmReports >= 3`, instead of punishing the victim, shows:
      *"🛡️ ৩+ ভুয়া রিপোর্ট জমা পড়লেও ৫ সেকেন্ডের অডিও/ভিডিও প্রমাণ ও রেসপন্ডার থাকায় প্রেরকের ট্রাস্ট পয়েন্ট সুরক্ষিত আছে • ২৪ ঘণ্টা পর অটো-রিমুভ হবে"* (when shielded by evidence/responders), or
      *"⚠️ ৩+ ডিসপিউট রিপোর্ট (মোট: X) • ৫ সেকেন্ডের ভিডিও/অডিও যাচাই করুন • ২৪ ঘণ্টা পর অটো-রিমুভ হবে"* (when disputed with 0 responders).
- **Find Screen -> Patients & Donors Tabs**:
  - Displays a live status chip confirming **1 km SOS BLE/Net** and **Area-Wide Matching Blood BLE/Net** routing.
