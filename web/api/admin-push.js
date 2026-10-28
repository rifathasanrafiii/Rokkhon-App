// Trusted server for admin push notifications (Vercel serverless function).
//
// The admin panel sends its Firebase ID token with a title, a message and a target. This function
// checks that the caller is listed in /admins/{uid}, sends one FCM message to a topic the app
// subscribes to (see app/.../data/push/PushTopics.kt), and saves it to /announcements so it also
// shows in the app's notification centre. The service-account key stays in Vercel's environment.
//
// Environment variables (Vercel → Project → Settings → Environment Variables):
//   FIREBASE_SERVICE_ACCOUNT  the service-account JSON from Firebase console (one line)
//   FIRESTORE_DATABASE_ID     the app's Firestore database id (not "(default)")

const admin = require("firebase-admin");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const GROUP = /^bg_(A|B|AB|O)_(POS|NEG)$/;
const DISTRICT = /^dist_[A-Za-z0-9_-]{1,60}$/;
const TABS = ["home", "find", "sos", "profile", "rewards"];
const MAX_PER_HOUR = 20;

function app() {
  if (admin.apps.length) return admin.app();
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw || !process.env.FIRESTORE_DATABASE_ID) throw new Error("server_not_configured");
  const options = { credential: admin.credential.cert(JSON.parse(raw)) };
  if (process.env.FIREBASE_DATABASE_URL) options.databaseURL = process.env.FIREBASE_DATABASE_URL;
  return admin.initializeApp(options);
}

/** "all", one blood group, one district, or a blood group in a district. */
function parseTarget(group, district) {
  const g = group ? String(group) : "";
  const d = district ? String(district) : "";
  if (g && !GROUP.test(g)) return null;
  if (d && !DISTRICT.test(d)) return null;
  if (g && d) return { key: `${g}&${d}`, send: { condition: `'${g}' in topics && '${d}' in topics` } };
  if (g || d) return { key: g || d, send: { topic: g || d } };
  return { key: "all", send: { topic: "all" } };
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

  const db = getFirestore(a, process.env.FIRESTORE_DATABASE_ID);
  const adminDoc = await db.doc(`admins/${caller.uid}`).get();
  if (!adminDoc.exists) return res.status(403).json({ error: "not_admin" });

  let body;
  try {
    body = typeof req.body === "string" ? JSON.parse(req.body || "{}") : req.body || {};
  } catch (e) {
    return res.status(400).json({ error: "body" });
  }
  const title = String(body.title || "").trim().slice(0, 80);
  const text = String(body.body || "").trim().slice(0, 400);
  const tab = TABS.includes(body.tab) ? body.tab : "home";
  if (!title || !text) return res.status(400).json({ error: "empty" });
  const target = parseTarget(body.bloodGroup, body.district);
  if (!target) return res.status(400).json({ error: "target" });

  // A ceiling so a leaked admin session can't flood every phone.
  const hourAgo = new Date(Date.now() - 60 * 60 * 1000);
  const recent = await db.collection("announcements").where("createdAt", ">", hourAgo).count().get();
  if (recent.data().count >= MAX_PER_HOUR) return res.status(429).json({ error: "limit" });

  let messageId;
  try {
    messageId = await a.messaging().send({
      ...target.send,
      // Data-only, so the app shows it on its own "Rokkhon updates" channel and opens the right tab.
      data: { type: "broadcast", title, body: text, tab },
      android: { priority: "high", ttl: 24 * 60 * 60 * 1000 },
    });
  } catch (e) {
    return res.status(502).json({ error: "fcm", detail: String((e && e.code) || e) });
  }

  const ref = await db.collection("announcements").add({
    title,
    body: text,
    target: target.key,
    tab,
    sentBy: String(caller.email || caller.uid).slice(0, 80),
    createdAt: FieldValue.serverTimestamp(),
  });
  return res.status(200).json({ ok: true, id: ref.id, target: target.key, messageId });
};
