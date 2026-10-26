// Trusted server for private SOS push alerts (Vercel serverless function, free Hobby plan).
//
// The Android app sends its Firebase ID token and a session id. This function checks that the
// caller owns that session, finds the accounts the caller trusts with alerts, and sends them a
// high-priority FCM data message. The service-account key stays here, in an environment variable,
// and never in the app.
//
// Environment variables (Vercel → Project → Settings → Environment Variables):
//   FIREBASE_SERVICE_ACCOUNT  the service-account JSON from Firebase console (one line)
//   FIREBASE_DATABASE_URL     e.g. https://btshop-5b889-default-rtdb.asia-southeast1.firebasedatabase.app

const admin = require("firebase-admin");

const MAX_SENDS_PER_SESSION = 6;
const SESSION_ID = /^[a-f0-9]{32}$/;

function app() {
  if (admin.apps.length) return admin.app();
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  const databaseURL = process.env.FIREBASE_DATABASE_URL;
  if (!raw || !databaseURL) throw new Error("server_not_configured");
  return admin.initializeApp({ credential: admin.credential.cert(JSON.parse(raw)), databaseURL });
}

module.exports = async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ error: "method" });
  let a;
  try {
    a = app();
  } catch (e) {
    return res.status(503).json({ error: "server_not_configured" });
  }

  const auth = req.headers.authorization || "";
  const idToken = auth.startsWith("Bearer ") ? auth.slice(7) : null;
  if (!idToken) return res.status(401).json({ error: "no_token" });
  let caller;
  try {
    caller = await a.auth().verifyIdToken(idToken);
  } catch (e) {
    return res.status(401).json({ error: "bad_token" });
  }

  const body = typeof req.body === "string" ? JSON.parse(req.body || "{}") : req.body || {};
  const sessionId = String(body.sessionId || "");
  const event = body.event === "ended" ? "ended" : "started";
  if (!SESSION_ID.test(sessionId)) return res.status(400).json({ error: "session" });

  const db = a.database();
  const session = (await db.ref(`sosSessions/${sessionId}`).get()).val();
  if (!session || session.ownerUid !== caller.uid) return res.status(403).json({ error: "not_owner" });
  if (event === "started" && session.status !== "active") return res.status(409).json({ error: "not_active" });

  // A small per-session budget so a stuck client can't spam the people who trust it.
  const counter = db.ref(`sosNotifyLog/${sessionId}/count`);
  const tx = await counter.transaction((n) => ((n || 0) >= MAX_SENDS_PER_SESSION ? undefined : (n || 0) + 1));
  if (!tx.committed) return res.status(429).json({ error: "limit" });

  const grants = (await db.ref(`sosAccess/${caller.uid}`).get()).val() || {};
  const viewers = Object.entries(grants)
    .filter(([, g]) => g && g.status === "accepted" && (g.alerts === true || g.location === true || g.audio === true))
    .map(([uid]) => uid);

  let sent = 0;
  let failed = 0;
  let noDevice = 0;
  const statusUpdates = {};

  for (const uid of viewers) {
    const tokens = (await db.ref(`fcmTokens/${uid}`).get()).val() || {};
    const entries = Object.entries(tokens).filter(([, t]) => t && typeof t.token === "string");
    if (!entries.length) {
      noDevice++;
      statusUpdates[`sosSessions/${sessionId}/notify/${uid}`] = "no_device";
      continue;
    }
    const result = await a.messaging().sendEachForMulticast({
      tokens: entries.map(([, t]) => t.token),
      data: {
        type: event === "ended" ? "sos_ended" : "sos_started",
        sessionId,
        ownerName: String(session.ownerName || "").slice(0, 60),
      },
      android: { priority: "high", ttl: 10 * 60 * 1000 },
    });
    // Forget tokens of uninstalled apps so the next SOS doesn't wait on them.
    result.responses.forEach((r, i) => {
      const code = r.error && r.error.code;
      if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
        statusUpdates[`fcmTokens/${uid}/${entries[i][0]}`] = null;
      }
    });
    if (result.successCount > 0) {
      sent++;
      if (event === "started") statusUpdates[`sosSessions/${sessionId}/notify/${uid}`] = "sent";
    } else {
      failed++;
      if (event === "started") statusUpdates[`sosSessions/${sessionId}/notify/${uid}`] = "failed";
    }
  }

  if (Object.keys(statusUpdates).length) await db.ref().update(statusUpdates);
  return res.status(200).json({ sent, failed, noDevice });
};
