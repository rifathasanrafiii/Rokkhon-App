// Realtime Database rules tests for the private SOS system.
// Run: firebase emulators:exec --only database --project demo-no-project "node --test database.test.js"
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const { ref, set, get, update, remove, serverTimestamp } = require("firebase/database");

let env;
const OWNER = "owner_uid_1";
const VIEWER = "viewer_uid_2";
const STRANGER = "stranger_uid_3";
const SID = "0123456789abcdef0123456789abcdef";

const [host, portStr] = (process.env.FIREBASE_DATABASE_EMULATOR_HOST || "127.0.0.1:9000").split(":");

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-no-project",
    database: { rules: fs.readFileSync("./database.rules.json", "utf8"), host, port: parseInt(portStr, 10) },
  });
});
after(async () => env && env.cleanup());
beforeEach(async () => env.clearDatabase());

const db = (uid, token = {}) => env.authenticatedContext(uid, token).database();
const anon = () => env.unauthenticatedContext().database();

function grant(status, perms = {}) {
  return {
    ownerName: "Owner",
    viewerName: "Viewer",
    status,
    location: perms.location ?? true,
    audio: perms.audio ?? true,
    alerts: perms.alerts ?? true,
    history: perms.history ?? false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function session(extra = {}) {
  return {
    ownerUid: OWNER,
    ownerName: "Owner",
    status: "active",
    startedAt: serverTimestamp(),
    audio: true,
    recordingAllowed: false,
    ...extra,
  };
}

async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.database()));
}

async function acceptedViewer(perms) {
  await seed(async (d) => {
    await set(ref(d, `sosAccess/${OWNER}/${VIEWER}`), { ...grant("accepted", perms), createdAt: 1, updatedAt: 1 });
    await set(ref(d, `sosSessions/${SID}`), { ...session(), startedAt: 1 });
    await set(ref(d, `sosActive/${OWNER}`), { sessionId: SID, startedAt: 1 });
    await set(ref(d, `liveLocations/${SID}/latest`), { lat: 23.8, lng: 90.4, acc: 12, ts: 1, src: "gps" });
  });
}

test("lookup: only the verified owner of an email can publish it, nobody can list", async () => {
  const me = db(OWNER, { email: "Rafi.Test@gmail.com", email_verified: true });
  await assertSucceeds(set(ref(me, "sosLookup/email/rafi,test@gmail,com"), { uid: OWNER, name: "Rafi" }));
  // Someone else's address, or an unverified one, is refused.
  await assertFails(set(ref(me, "sosLookup/email/other@gmail,com"), { uid: OWNER, name: "Rafi" }));
  const unverified = db(STRANGER, { email: "x@y.com", email_verified: false });
  await assertFails(set(ref(unverified, "sosLookup/email/x@y,com"), { uid: STRANGER, name: "X" }));
  // Claiming another uid is refused.
  await assertFails(set(ref(me, "sosLookup/email/rafi,test@gmail,com"), { uid: STRANGER, name: "Rafi" }));
  // Exact lookup works for signed-in users, listing does not, signed-out reads nothing.
  await assertSucceeds(get(ref(db(VIEWER), "sosLookup/email/rafi,test@gmail,com")));
  await assertFails(get(ref(db(VIEWER), "sosLookup/email")));
  await assertFails(get(ref(anon(), "sosLookup/email/rafi,test@gmail,com")));
});

test("lookup: phone numbers come from the auth token", async () => {
  const me = db(OWNER, { phone_number: "+8801712345678" });
  await assertSucceeds(set(ref(me, "sosLookup/phone/8801712345678"), { uid: OWNER, name: "Rafi" }));
  await assertFails(set(ref(me, "sosLookup/phone/8801999999999"), { uid: OWNER, name: "Rafi" }));
});

test("invite: owner creates a pending invite and cannot accept it", async () => {
  const owner = db(OWNER);
  await assertSucceeds(set(ref(owner, `sosAccess/${OWNER}/${VIEWER}`), grant("pending")));
  await assertSucceeds(set(ref(owner, `sosAccessIndex/${VIEWER}/${OWNER}`), true));
  await assertFails(set(ref(owner, `sosAccess/${OWNER}/${VIEWER}/status`), "accepted"));
  await assertFails(set(ref(owner, `sosAccess/${OWNER}/${OWNER}`), grant("pending")));
  await assertFails(set(ref(owner, `sosAccess/${OWNER}/${STRANGER}`), grant("accepted")));
  // A stranger cannot invite on the owner's behalf.
  await assertFails(set(ref(db(STRANGER), `sosAccess/${OWNER}/${STRANGER}`), grant("pending")));
});

test("invite: viewer accepts or declines, cannot change permissions, can leave", async () => {
  const owner = db(OWNER);
  const viewer = db(VIEWER);
  await set(ref(owner, `sosAccess/${OWNER}/${VIEWER}`), grant("pending", { history: false }));
  await assertSucceeds(get(ref(viewer, `sosAccess/${OWNER}/${VIEWER}`)));
  await assertFails(get(ref(viewer, `sosAccess/${OWNER}`)));
  await assertFails(set(ref(viewer, `sosAccess/${OWNER}/${VIEWER}/history`), true));
  await assertSucceeds(
    update(ref(viewer, `sosAccess/${OWNER}/${VIEWER}`), { status: "accepted", updatedAt: serverTimestamp() })
  );
  // Once accepted the viewer can no longer flip the status, only leave.
  await assertFails(set(ref(viewer, `sosAccess/${OWNER}/${VIEWER}/status`), "pending"));
  await assertSucceeds(remove(ref(viewer, `sosAccess/${OWNER}/${VIEWER}`)));
  await assertFails(get(ref(db(STRANGER), `sosAccess/${OWNER}/${VIEWER}`)));
});

test("session: only the owner creates and ends it; ended cannot restart", async () => {
  const owner = db(OWNER);
  await assertFails(set(ref(db(STRANGER), `sosSessions/${SID}`), session()));
  await assertFails(set(ref(owner, `sosSessions/not-a-secure-id`), session()));
  await assertSucceeds(
    update(ref(owner), {
      [`sosSessions/${SID}`]: session(),
      [`sosActive/${OWNER}`]: { sessionId: SID, startedAt: serverTimestamp() },
      [`sosHistory/${OWNER}/${SID}`]: { startedAt: serverTimestamp() },
    })
  );
  await assertSucceeds(update(ref(owner, `sosSessions/${SID}`), { status: "ended", endedAt: serverTimestamp() }));
  await assertFails(update(ref(owner, `sosSessions/${SID}`), { status: "active" }));
  // sosActive cannot point at a session that is not active.
  await assertFails(set(ref(owner, `sosActive/${OWNER}`), { sessionId: SID, startedAt: 1 }));
});

test("location: owner publishes, accepted viewer with permission reads, others cannot", async () => {
  await acceptedViewer({ location: true });
  const owner = db(OWNER);
  await assertSucceeds(
    set(ref(owner, `liveLocations/${SID}/latest`), { lat: 23.81, lng: 90.41, acc: 8, ts: Date.now(), src: "gps" })
  );
  await assertFails(
    set(ref(db(VIEWER), `liveLocations/${SID}/latest`), { lat: 1, lng: 1, acc: 8, ts: Date.now(), src: "gps" })
  );
  await assertFails(
    set(ref(owner, `liveLocations/${SID}/latest`), { lat: 123, lng: 90.41, acc: 8, ts: Date.now(), src: "gps" })
  );
  await assertSucceeds(get(ref(db(VIEWER), `liveLocations/${SID}/latest`)));
  await assertFails(get(ref(db(STRANGER), `liveLocations/${SID}/latest`)));
  await assertFails(get(ref(anon(), `liveLocations/${SID}/latest`)));
});

test("location: no permission, pending, revoked or ended means no GPS", async () => {
  await acceptedViewer({ location: false, history: false });
  await assertFails(get(ref(db(VIEWER), `liveLocations/${SID}/latest`)));

  await seed((d) => set(ref(d, `sosAccess/${OWNER}/${VIEWER}/location`), true));
  await assertSucceeds(get(ref(db(VIEWER), `liveLocations/${SID}/latest`)));

  await seed((d) => set(ref(d, `sosAccess/${OWNER}/${VIEWER}/status`), "pending"));
  await assertFails(get(ref(db(VIEWER), `liveLocations/${SID}/latest`)));

  await seed((d) => set(ref(d, `sosAccess/${OWNER}/${VIEWER}/status`), "accepted"));
  // Owner revokes: access disappears immediately.
  await assertSucceeds(
    update(ref(db(OWNER)), {
      [`sosAccess/${OWNER}/${VIEWER}`]: null,
      [`sosAccessIndex/${VIEWER}/${OWNER}`]: null,
    })
  );
  await assertFails(get(ref(db(VIEWER), `liveLocations/${SID}/latest`)));
  await assertFails(get(ref(db(VIEWER), `sosSessions/${SID}`)));
  await assertFails(get(ref(db(VIEWER), `sosActive/${OWNER}`)));

  // Ended session: live permission alone no longer shows the location, history permission does.
  await acceptedViewer({ location: true, history: false });
  await seed((d) => update(ref(d, `sosSessions/${SID}`), { status: "ended", endedAt: 2 }));
  await assertFails(get(ref(db(VIEWER), `liveLocations/${SID}/latest`)));
  await seed((d) => set(ref(d, `sosAccess/${OWNER}/${VIEWER}/history`), true));
  await assertSucceeds(get(ref(db(VIEWER), `liveLocations/${SID}/latest`)));
  // And the owner can no longer write locations into an ended session.
  await assertFails(
    set(ref(db(OWNER), `liveLocations/${SID}/latest`), { lat: 1, lng: 1, acc: 1, ts: Date.now(), src: "gps" })
  );
});

test("signaling: viewer needs the audio permission and an audio-enabled session", async () => {
  await acceptedViewer({ audio: true });
  const viewer = db(VIEWER);
  await assertSucceeds(set(ref(viewer, `sosSignaling/${SID}/${VIEWER}/request`), serverTimestamp()));
  await assertFails(set(ref(viewer, `sosSignaling/${SID}/${STRANGER}/request`), serverTimestamp()));
  await assertFails(get(ref(viewer, `sosSignaling/${SID}`)));
  await assertSucceeds(get(ref(db(OWNER), `sosSignaling/${SID}`)));
  await assertSucceeds(
    set(ref(db(OWNER), `sosSignaling/${SID}/${VIEWER}/offer`), { type: "offer", sdp: "v=0", n: 1 })
  );
  await assertFails(set(ref(db(STRANGER), `sosSignaling/${SID}/${STRANGER}/request`), serverTimestamp()));

  await seed((d) => set(ref(d, `sosAccess/${OWNER}/${VIEWER}/audio`), false));
  await assertFails(get(ref(viewer, `sosSignaling/${SID}/${VIEWER}`)));
  await seed((d) => set(ref(d, `sosAccess/${OWNER}/${VIEWER}/audio`), true));
  await seed((d) => set(ref(d, `sosSessions/${SID}/audio`), false));
  await assertFails(set(ref(viewer, `sosSignaling/${SID}/${VIEWER}/request`), serverTimestamp()));
});

test("listener status: recording flag only when the owner allowed recording", async () => {
  await acceptedViewer({ audio: true });
  const viewer = db(VIEWER);
  const path = `sosSessions/${SID}/listeners/${VIEWER}`;
  await assertSucceeds(set(ref(viewer, path), { state: "connected", recording: false, ts: serverTimestamp() }));
  await assertFails(set(ref(viewer, path), { state: "connected", recording: true, ts: serverTimestamp() }));
  await seed((d) => set(ref(d, `sosSessions/${SID}/recordingAllowed`), true));
  await assertSucceeds(set(ref(viewer, path), { state: "connected", recording: true, ts: serverTimestamp() }));
  await assertFails(set(ref(viewer, `sosSessions/${SID}/status`), "ended"));
  await assertSucceeds(set(ref(viewer, `sosSessions/${SID}/seen/${VIEWER}`), serverTimestamp()));
  await assertFails(set(ref(db(STRANGER), `sosSessions/${SID}/seen/${STRANGER}`), serverTimestamp()));
});

test("events are append-only and history follows the history permission", async () => {
  await acceptedViewer({ history: false });
  const viewer = db(VIEWER);
  const ev = { type: "viewer_opened", by: VIEWER, ts: serverTimestamp() };
  await assertSucceeds(set(ref(viewer, `sosEvents/${SID}/e1`), ev));
  await assertFails(set(ref(viewer, `sosEvents/${SID}/e1`), ev));
  await assertFails(set(ref(viewer, `sosEvents/${SID}/e2`), { ...ev, by: OWNER }));
  await assertFails(get(ref(viewer, `sosHistory/${OWNER}`)));
  await seed((d) => set(ref(d, `sosAccess/${OWNER}/${VIEWER}/history`), true));
  await assertSucceeds(get(ref(viewer, `sosHistory/${OWNER}`)));
  await assertFails(get(ref(db(STRANGER), `sosHistory/${OWNER}`)));
});

test("push tokens are private to their owner", async () => {
  await assertSucceeds(set(ref(db(OWNER), `fcmTokens/${OWNER}/t1`), { token: "abc", ts: 1 }));
  await assertFails(get(ref(db(VIEWER), `fcmTokens/${OWNER}`)));
  await assertFails(set(ref(db(VIEWER), `fcmTokens/${OWNER}/t2`), { token: "abc", ts: 1 }));
});
