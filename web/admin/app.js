// Rokkhon admin panel. Everything here is read from and written to the app's own Firestore
// database; firestore.rules lets only accounts listed in /admins/{uid} make these changes.
// Push notifications go through /api/admin-push, which holds the service-account key.

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth, GoogleAuthProvider, signInWithPopup, signInWithRedirect, onAuthStateChanged, signOut,
  connectAuthEmulator, signInWithCredential,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore, doc, collection, query, where, orderBy, limit, onSnapshot, getDoc, setDoc, updateDoc,
  deleteDoc, serverTimestamp, connectFirestoreEmulator,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyAMjcxudd8dWc959sW6keztNeMmQnjilJw",
  authDomain: "btshop-5b889.firebaseapp.com",
  projectId: "btshop-5b889",
  storageBucket: "btshop-5b889.firebasestorage.app",
};
const DATABASE_ID = "ai-studio-android-rokkhon-57cdc872-fddf-4dcf-83c1-3037e342e558";
const NETWORK = "BD_ROKKHON";
const GROUPS = ["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"];
const DISTRICTS = window.ROKKHON_DISTRICTS || [];
const TAB_NAMES = { home: "হোম", find: "ডোনার খুঁজুন", sos: "SOS", profile: "প্রোফাইল", rewards: "রিওয়ার্ড" };

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app, DATABASE_ID);

// Local testing against the Firebase emulators: http://localhost:<port>/admin/?emulator
if (location.hostname === "localhost" && new URLSearchParams(location.search).has("emulator")) {
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  connectFirestoreEmulator(db, "127.0.0.1", 8085);
  window.emulatorSignIn = (uid) => signInWithCredential(auth, GoogleAuthProvider.credential(JSON.stringify({ sub: uid, email: `${uid}@example.com`, email_verified: true })));
}

const state = {
  policy: null,
  policyExists: false,
  donors: [],
  devices: {}, // uid -> deviceId
  requests: [],
  offers: [],
  claims: [],
  announcements: [],
  editingOffer: null,
  claimFilter: "pending",
  reqFilter: "OPEN",
  donorFilter: "all",
  pushTarget: "all",
};
let unsubs = [];

// ------------------------------------------------------------------ helpers

const $ = (id) => document.getElementById(id);
const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const initials = (name) => (String(name || "?").trim().split(/\s+/).map((w) => w[0]).join("").slice(0, 2) || "?").toUpperCase();

function toast(msg) {
  const t = $("toast");
  t.textContent = msg;
  t.classList.add("show");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => t.classList.remove("show"), 2600);
}

function when(ts) {
  const d = ts?.toDate ? ts.toDate() : typeof ts === "string" ? new Date(ts) : null;
  if (!d || isNaN(d)) return "";
  const mins = Math.round((Date.now() - d.getTime()) / 60000);
  if (mins < 1) return "এইমাত্র";
  if (mins < 60) return `${mins} মিনিট আগে`;
  if (mins < 24 * 60) return `${Math.round(mins / 60)} ঘণ্টা আগে`;
  return d.toLocaleDateString("bn-BD", { day: "numeric", month: "short", year: "numeric" });
}
const millis = (ts) => (ts?.toMillis ? ts.toMillis() : 0);

function fail(e) {
  console.warn(e);
  const code = e?.code || "";
  toast(code.includes("permission") ? "অনুমতি নেই: firestore.rules ডিপ্লয় করা আছে তো?" : "হয়নি, আবার চেষ্টা করুন");
}

$("dlgCancel").onclick = () => $("dlg").close("cancel");

/** Small modal with optional fields; resolves with the values, or null when cancelled. */
function ask({ title, text = "", fields = [], ok = "ঠিক আছে", danger = false }) {
  const dlg = $("dlg");
  $("dlgTitle").textContent = title;
  $("dlgText").textContent = text;
  $("dlgFields").innerHTML = fields.map((f) => `
    <div style="margin-top:6px"><label class="f" for="dlg_${f.id}">${esc(f.label)}</label>
    <input class="in" id="dlg_${f.id}" type="${f.type || "text"}" value="${esc(f.value ?? "")}" ${f.required ? "required" : ""} ${f.max ? `maxlength="${f.max}"` : ""} /></div>`).join("");
  $("dlgOk").textContent = ok;
  $("dlgOk").classList.toggle("danger", danger);
  return new Promise((resolve) => {
    dlg.onclose = () => {
      if (dlg.returnValue !== "ok") return resolve(null);
      const out = {};
      fields.forEach((f) => (out[f.id] = $(`dlg_${f.id}`).value.trim()));
      resolve(out);
    };
    dlg.returnValue = "";
    dlg.showModal();
    const first = dlg.querySelector("input");
    if (first) first.focus();
  });
}

function districtName(topic) {
  const d = DISTRICTS.find((x) => x.topic === topic);
  return d ? d.nameBn : topic.replace(/^dist_/, "");
}
function groupFromTopic(topic) {
  const m = /^bg_(A|B|AB|O)_(POS|NEG)$/.exec(topic);
  return m ? m[1] + (m[2] === "POS" ? "+" : "-") : topic;
}
function targetLabel(target) {
  if (!target || target === "all") return "সবাই";
  return target.split("&").map((t) => (t.startsWith("bg_") ? groupFromTopic(t) : districtName(t))).join(" · ");
}
const groupTopic = (g) => "bg_" + g.slice(0, -1) + (g.endsWith("+") ? "_POS" : "_NEG");

// ------------------------------------------------------------------ policy (admin_controls/global_policy)

function policyDefaults() {
  return {
    rewardCheckInPoints: 25, rewardBloodPledgePoints: 100, rewardSosRespondPoints: 50,
    globalLocationOff: false, locationOffUserIds: [], suspendedUserIds: [], bannedDeviceIds: [],
    userPointsOverrides: {}, userStreakOverrides: {}, userTrustOverrides: {}, suspendReasons: {}, banReasons: {},
  };
}
const policy = () => ({ ...policyDefaults(), ...(state.policy || {}) });

/** Writes whole fields (maps and lists included), so removed entries are really removed. */
async function savePolicy(fields, okMsg) {
  const ref = doc(db, "admin_controls", "global_policy");
  const data = { ...fields, updatedAt: new Date().toISOString() };
  try {
    if (state.policyExists) await updateDoc(ref, data);
    else await setDoc(ref, { ...policyDefaults(), ...data });
    toast(okMsg);
  } catch (e) {
    fail(e);
  }
}

// ------------------------------------------------------------------ auth

$("signInBtn").onclick = async () => {
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: "select_account" });
  try {
    await signInWithPopup(auth, provider);
  } catch (e) {
    if (e?.code === "auth/popup-blocked" || e?.code === "auth/cancelled-popup-request") return signInWithRedirect(auth, provider);
    if (e?.code === "auth/unauthorized-domain") return toast("এই ডোমেইন Firebase Auth → Authorized domains-এ যোগ করুন");
    if (e?.code !== "auth/popup-closed-by-user") toast("সাইন ইন হয়নি: " + (e?.code || e));
  }
};
$("signOutBtn").onclick = $("gateSignOut").onclick = () => signOut(auth);

onAuthStateChanged(auth, async (user) => {
  unsubs.forEach((u) => u());
  unsubs = [];
  $("gateUid").hidden = true;
  $("gateSignOut").hidden = true;
  if (!user) {
    $("shell").hidden = true;
    $("gate").hidden = false;
    $("signInBtn").hidden = false;
    $("gateText").textContent = "অ্যাডমিন Google অ্যাকাউন্ট দিয়ে সাইন ইন করুন।";
    return;
  }
  $("gateText").textContent = "যাচাই করা হচ্ছে…";
  $("signInBtn").hidden = true;
  let isAdmin = false;
  try {
    isAdmin = (await getDoc(doc(db, "admins", user.uid))).exists();
  } catch (e) {
    console.warn(e);
  }
  if (!isAdmin) {
    $("gateText").textContent = user.email || user.uid;
    $("gateUidText").textContent = `admins/${user.uid}`;
    $("gateUid").hidden = false;
    $("gateSignOut").hidden = false;
    return;
  }
  $("meName").textContent = user.displayName || "Admin";
  $("meEmail").textContent = user.email || "";
  $("gate").hidden = true;
  $("shell").hidden = false;
  subscribe();
});

function subscribe() {
  const onErr = (what) => (e) => { console.warn(what, e); toast(`${what} পড়া যায়নি`); };
  unsubs = [
    onSnapshot(doc(db, "admin_controls", "global_policy"), (s) => {
      state.policyExists = s.exists();
      state.policy = s.exists() ? s.data() : null;
      renderAll();
    }, onErr("নীতি")),
    onSnapshot(query(collection(db, "donor_nodes"), where("networkKey", "==", NETWORK)), (s) => {
      state.donors = s.docs.map((d) => ({ id: d.id, ...d.data() }));
      renderAll();
    }, onErr("ডোনার")),
    onSnapshot(collection(db, "device_registry"), (s) => {
      const out = {};
      s.docs.forEach((d) => { const v = d.data(); if (v.deviceId) out[v.userId || d.id] = String(v.deviceId).toUpperCase(); });
      state.devices = out;
      renderAll();
    }, onErr("ডিভাইস")),
    onSnapshot(query(collection(db, "blood_requests"), where("networkKey", "==", NETWORK)), (s) => {
      state.requests = s.docs.map((d) => ({ id: d.id, ...d.data() })).sort((a, b) => millis(b.createdAt) - millis(a.createdAt));
      renderAll();
    }, onErr("অনুরোধ")),
    onSnapshot(collection(db, "reward_offers"), (s) => {
      state.offers = s.docs.map((d) => ({ id: d.id, ...d.data() })).sort((a, b) => (a.order || 0) - (b.order || 0) || a.points - b.points);
      renderAll();
    }, onErr("রিওয়ার্ড")),
    onSnapshot(collection(db, "reward_claims"), (s) => {
      state.claims = s.docs.map((d) => ({ id: d.id, ...d.data() })).sort((a, b) => millis(b.createdAt) - millis(a.createdAt));
      renderAll();
    }, onErr("ক্লেইম")),
    onSnapshot(query(collection(db, "announcements"), orderBy("createdAt", "desc"), limit(30)), (s) => {
      state.announcements = s.docs.map((d) => ({ id: d.id, ...d.data() }));
      renderAll();
    }, onErr("পুশ")),
  ];
}

// ------------------------------------------------------------------ navigation

function go(view) {
  document.querySelectorAll(".nav").forEach((b) => b.classList.toggle("active", b.dataset.view === view));
  document.querySelectorAll("[data-page]").forEach((p) => (p.hidden = p.dataset.page !== view));
  window.scrollTo(0, 0);
  try { localStorage.setItem("rokkhon_admin_view", view); } catch (e) {}
}
document.querySelectorAll(".nav").forEach((b) => (b.onclick = () => go(b.dataset.view)));
document.addEventListener("click", (e) => { const g = e.target.closest("[data-go]"); if (g) go(g.dataset.go); });
try { const v = localStorage.getItem("rokkhon_admin_view"); if (v && document.querySelector(`[data-page="${v}"]`)) go(v); } catch (e) {}

function segment(id, key, render) {
  $(id).addEventListener("click", (e) => {
    const b = e.target.closest("button");
    if (!b) return;
    $(id).querySelectorAll("button").forEach((x) => x.classList.toggle("on", x === b));
    state[key] = Object.values(b.dataset)[0];
    render();
  });
}

// ------------------------------------------------------------------ render

let renderQueued = false;
function renderAll() {
  if (renderQueued) return;
  renderQueued = true;
  requestAnimationFrame(() => {
    renderQueued = false;
    renderDash();
    renderOffers();
    renderClaims();
    renderDonors();
    renderRequests();
    renderSettings();
    renderPushHistory();
    renderReach();
  });
}

const pending = () => state.claims.filter((c) => c.status === "pending");

function renderDash() {
  const ready = state.donors.filter((d) => d.isAvailableDonor !== false).length;
  $("stDonors").textContent = state.donors.length;
  $("stReady").textContent = ready;
  $("stOpen").textContent = state.requests.filter((r) => r.status === "OPEN").length;
  $("stClaims").textContent = pending().length;
  $("stDevices").textContent = Object.keys(state.devices).length;
  const n = pending().length;
  $("claimCount").textContent = n;
  $("claimCount").hidden = n === 0;

  $("dashClaims").innerHTML = pending().slice(0, 5).map(claimRow).join("") || `<div class="empty">কোনো অপেক্ষমাণ ক্লেইম নেই</div>`;
  $("dashRequests").innerHTML = state.requests.slice(0, 5).map(requestRow).join("") || `<div class="empty">কোনো অনুরোধ নেই</div>`;
  const max = Math.max(1, ...GROUPS.map((g) => state.donors.filter((d) => d.bloodGroup === g).length));
  $("dashGroups").innerHTML = GROUPS.map((g) => {
    const c = state.donors.filter((d) => d.bloodGroup === g).length;
    return `<div style="display:flex;align-items:center;gap:10px;margin:6px 0"><b style="width:34px">${g}</b>
      <div style="flex:1;height:8px;background:var(--subtle);border-radius:9px;overflow:hidden"><div style="height:100%;width:${(c / max) * 100}%;background:var(--ink)"></div></div>
      <span class="hint" style="width:28px;text-align:right">${c}</span></div>`;
  }).join("");
  $("dashPush").innerHTML = state.announcements.slice(0, 3).map(pushRow).join("") || `<div class="empty">এখনো কোনো পুশ পাঠানো হয়নি</div>`;
}

function claimRow(c) {
  const statusPill = { pending: `<span class="pill warn">অপেক্ষমাণ</span>`, approved: `<span class="pill ok">অনুমোদিত</span>`, fulfilled: `<span class="pill dark">দেওয়া হয়েছে</span>`, rejected: `<span class="pill bad">বাতিল</span>` }[c.status] || "";
  const donor = state.donors.find((d) => d.id === c.userId || d.userId === c.userId);
  const now = (policy().userPointsOverrides || {})[c.userId];
  const actions = c.status === "pending"
    ? `<button class="btn primary" data-act="claim-approve" data-id="${esc(c.id)}">অনুমোদন</button><button class="btn danger" data-act="claim-reject" data-id="${esc(c.id)}">বাতিল</button>`
    : c.status === "approved"
      ? `<button class="btn primary" data-act="claim-fulfil" data-id="${esc(c.id)}">দেওয়া হয়েছে</button>`
      : `<button class="btn" data-act="claim-delete" data-id="${esc(c.id)}" title="মুছে দিলে আবার ক্লেইম করতে পারবেন">মুছুন</button>`;
  return `<div class="row"><div class="avatar dark">${esc(initials(c.userName))}</div>
    <div class="grow"><div class="t">${esc(c.userName)} · ${esc(c.offerTitle)}</div>
    <div class="s">${c.cost} পয়েন্ট · ক্লেইমের সময় ছিল ${c.userPoints}${now != null ? ` · ওভাররাইড ${now}` : ""}${c.phone || donor?.contactPhone ? ` · ${esc(c.phone || donor.contactPhone)}` : ""} · ${when(c.createdAt)}${c.note ? ` · “${esc(c.note)}”` : ""}</div></div>
    ${statusPill}<div class="actions">${actions}</div></div>`;
}

function requestRow(r) {
  const pill = r.status === "OPEN" ? `<span class="pill bad">${esc(r.urgencyLevel || "OPEN")}</span>` : `<span class="pill ok">পূরণ হয়েছে</span>`;
  return `<div class="row"><div class="avatar">${esc(r.bloodGroup)}</div>
    <div class="grow"><div class="t">${esc(r.patientName)} · ${esc(r.hospitalName)}</div>
    <div class="s">${esc(r.unitsNeeded)} ব্যাগ · ${esc(r.district)} · ${esc(r.requesterName)} ${esc(r.contactPhone)} · ${r.respondersCount || 0} জন সাড়া · ${when(r.createdAt)}</div></div>
    ${pill}</div>`;
}

function pushRow(a) {
  return `<div class="row"><div class="grow"><div class="t">${esc(a.title)}</div><div class="s">${esc(a.body)}</div>
    <div class="s">${esc(targetLabel(a.target))} · ${esc(TAB_NAMES[a.tab] || "হোম")} · ${when(a.createdAt)}${a.sentBy ? ` · ${esc(a.sentBy)}` : ""}</div></div></div>`;
}

function renderPushHistory() {
  $("pushHistory").innerHTML = state.announcements.map((a) => pushRow(a).replace(/<\/div><\/div>$/, `</div><div class="actions"><button class="btn danger" data-act="ann-delete" data-id="${esc(a.id)}" title="অ্যাপের নোটিফিকেশন পেজ থেকে সরাবে">সরান</button></div></div>`)).join("")
    || `<div class="empty">এখনো কোনো পুশ পাঠানো হয়নি</div>`;
}

function renderOffers() {
  $("offerCount").textContent = `${state.offers.filter((o) => o.active).length} টি চালু`;
  $("offerList").innerHTML = state.offers.map((o) => {
    const claims = state.claims.filter((c) => c.offerId === o.id);
    return `<div class="offer ${o.active ? "" : "off"}"><div class="badge">${o.points}<small>পয়েন্ট</small></div>
      <div class="grow" style="min-width:0"><div class="t" style="font-weight:600">${esc(o.titleBn || o.title)}</div>
      <div class="s hint">${esc(o.partner || "")}${o.partner ? " · " : ""}${esc(o.descriptionBn || o.description || o.title)}</div>
      <div class="s hint">${claims.length} ক্লেইম · ${claims.filter((c) => c.status === "pending").length} অপেক্ষমাণ ${o.active ? "" : "· <b>লুকানো</b>"}</div></div>
      <div class="actions" style="display:flex;flex-direction:column;gap:6px">
        <button class="btn" data-act="offer-edit" data-id="${esc(o.id)}">এডিট</button>
        <button class="btn" data-act="offer-toggle" data-id="${esc(o.id)}">${o.active ? "লুকান" : "চালু"}</button>
        <button class="btn danger" data-act="offer-delete" data-id="${esc(o.id)}">মুছুন</button></div></div>`;
  }).join("") || `<div class="empty">এখনো কোনো কার্ড নেই। ডানদিকের ফর্ম থেকে প্রথম রিওয়ার্ড কার্ড বানান।</div>`;

  const p = policy();
  for (const [id, key] of [["eCheckIn", "rewardCheckInPoints"], ["ePledge", "rewardBloodPledgePoints"], ["eSos", "rewardSosRespondPoints"]]) {
    if (document.activeElement !== $(id)) $(id).value = p[key];
  }
}

function renderClaims() {
  const list = state.claimFilter === "all" ? state.claims : state.claims.filter((c) => c.status === state.claimFilter);
  $("claimList").innerHTML = list.map(claimRow).join("") || `<div class="empty">এই তালিকায় কিছু নেই</div>`;
}

function renderDonors() {
  const p = policy();
  const q = $("donorSearch").value.trim().toLowerCase();
  const g = $("donorGroup").value;
  const suspended = new Set(p.suspendedUserIds || []);
  const banned = new Set((p.bannedDeviceIds || []).map((x) => String(x).toUpperCase()));
  const locOff = new Set(p.locationOffUserIds || []);
  const list = state.donors.filter((d) => {
    const uid = d.userId || d.id;
    const dev = state.devices[uid];
    if (g && d.bloodGroup !== g) return false;
    if (state.donorFilter === "suspended" && !suspended.has(uid)) return false;
    if (state.donorFilter === "banned" && !(dev && banned.has(dev))) return false;
    if (!q) return true;
    return [d.displayName, d.contactPhone, d.district, uid, dev].some((v) => String(v || "").toLowerCase().includes(q));
  });
  $("donorList").innerHTML = list.slice(0, 300).map((d) => {
    const uid = d.userId || d.id;
    const dev = state.devices[uid];
    const pts = (p.userPointsOverrides || {})[uid];
    const isSus = suspended.has(uid);
    const isBan = dev && banned.has(dev);
    return `<div class="row"><div class="avatar ${d.isAvailableDonor !== false ? "dark" : ""}">${esc(d.bloodGroup)}</div>
      <div class="grow"><div class="t">${esc(d.displayName || "নাম নেই")} ${isSus ? `<span class="pill bad">সাসপেন্ড</span>` : ""} ${isBan ? `<span class="pill bad">ব্যান</span>` : ""} ${locOff.has(uid) ? `<span class="pill">লোকেশন বন্ধ</span>` : ""}</div>
      <div class="s">${esc(d.district)} · ${esc(d.contactPhone)} · শেষ দান ${esc(d.lastDonationDate || "—")}${pts != null ? ` · পয়েন্ট ${pts}` : ""}</div>
      <div class="s">${esc(uid)}${dev ? ` · ${esc(dev)}` : ""}</div></div>
      <div class="actions">
        <button class="btn" data-act="donor-points" data-id="${esc(uid)}">পয়েন্ট</button>
        <button class="btn" data-act="donor-loc" data-id="${esc(uid)}">${locOff.has(uid) ? "লোকেশন চালু" : "লোকেশন বন্ধ"}</button>
        <button class="btn ${isSus ? "" : "danger"}" data-act="donor-suspend" data-id="${esc(uid)}">${isSus ? "আনসাসপেন্ড" : "সাসপেন্ড"}</button>
        ${dev ? `<button class="btn ${isBan ? "" : "danger"}" data-act="device-ban" data-id="${esc(dev)}">${isBan ? "আনব্যান" : "ডিভাইস ব্যান"}</button>` : ""}
      </div></div>`;
  }).join("") || `<div class="empty">কোনো ডোনার পাওয়া যায়নি</div>`;
}

function renderRequests() {
  const list = state.reqFilter === "all" ? state.requests : state.requests.filter((r) => r.status === state.reqFilter);
  $("reqList").innerHTML = list.map((r) => requestRow(r).replace(/<\/div>$/, `<div class="actions">
      ${r.status === "OPEN" ? `<button class="btn" data-act="req-close" data-id="${esc(r.id)}">পূরণ হয়েছে</button>` : ""}
      <button class="btn danger" data-act="req-delete" data-id="${esc(r.id)}">মুছুন</button></div></div>`)).join("")
    || `<div class="empty">এই তালিকায় কোনো অনুরোধ নেই</div>`;
}

function renderSettings() {
  const p = policy();
  $("globalLoc").checked = !!p.globalLocationOff;
  const reasons = p.banReasons || {};
  $("bannedList").innerHTML = (p.bannedDeviceIds || []).map((id) => {
    const owner = Object.entries(state.devices).find(([, dev]) => dev === String(id).toUpperCase());
    const donor = owner && state.donors.find((d) => (d.userId || d.id) === owner[0]);
    return `<div class="row"><div class="grow"><div class="t">${esc(id)}</div><div class="s">${esc(donor?.displayName || "")}${reasons[id] ? ` · ${esc(reasons[id])}` : ""}</div></div>
      <div class="actions"><button class="btn" data-act="device-ban" data-id="${esc(id)}">আনব্যান</button></div></div>`;
  }).join("") || `<div class="empty">কোনো ডিভাইস ব্যান করা নেই</div>`;
  const devs = Object.entries(state.devices);
  $("deviceCount").textContent = `${devs.length} টি`;
  $("deviceList").innerHTML = devs.slice(0, 100).map(([uid, dev]) => {
    const donor = state.donors.find((d) => (d.userId || d.id) === uid);
    return `<div class="row"><div class="grow"><div class="t">${esc(dev)}</div><div class="s">${esc(donor?.displayName || "ডোনার প্রোফাইল নেই")} · ${esc(uid)}</div></div></div>`;
  }).join("") || `<div class="empty">কোনো ফোন রেজিস্টার হয়নি</div>`;
}

// ------------------------------------------------------------------ actions

document.addEventListener("click", async (e) => {
  const b = e.target.closest("[data-act]");
  if (!b) return;
  const id = b.dataset.id;
  const p = policy();
  switch (b.dataset.act) {
    case "claim-approve": {
      const c = state.claims.find((x) => x.id === id);
      const ok = await ask({ title: "ক্লেইম অনুমোদন", text: `${c.userName} — ${c.offerTitle}। অনুমোদন দিলে তার ফোনে ${c.cost} পয়েন্ট কেটে যাবে।`, fields: [{ id: "note", label: "বার্তা (ঐচ্ছিক, অ্যাপে দেখাবে)", max: 140 }], ok: "অনুমোদন দিন" });
      if (!ok) return;
      const data = { status: "approved", decidedAt: serverTimestamp() };
      if (ok.note) data.note = ok.note;
      return updateDoc(doc(db, "reward_claims", id), data).then(() => toast("অনুমোদন দেওয়া হয়েছে")).catch(fail);
    }
    case "claim-reject": {
      const ok = await ask({ title: "ক্লেইম বাতিল", text: "কারণ লিখলে ব্যবহারকারী অ্যাপে দেখতে পাবেন।", fields: [{ id: "note", label: "কারণ", max: 140 }], ok: "বাতিল করুন", danger: true });
      if (!ok) return;
      return updateDoc(doc(db, "reward_claims", id), { status: "rejected", note: ok.note || "", decidedAt: serverTimestamp() }).then(() => toast("বাতিল করা হয়েছে")).catch(fail);
    }
    case "claim-fulfil":
      return updateDoc(doc(db, "reward_claims", id), { status: "fulfilled", decidedAt: serverTimestamp() }).then(() => toast("দেওয়া হয়েছে হিসেবে রাখা হলো")).catch(fail);
    case "claim-delete":
      if (!(await ask({ title: "ক্লেইম মুছবেন?", text: "মুছে দিলে ব্যবহারকারী এই রিওয়ার্ড আবার ক্লেইম করতে পারবেন। কাটা পয়েন্ট ফেরত যাবে না।", ok: "মুছুন", danger: true }))) return;
      return deleteDoc(doc(db, "reward_claims", id)).then(() => toast("মুছে দেওয়া হয়েছে")).catch(fail);

    case "offer-edit": {
      const o = state.offers.find((x) => x.id === id);
      state.editingOffer = id;
      $("offerFormTitle").textContent = "কার্ড এডিট";
      $("oTitleBn").value = o.titleBn || "";
      $("oTitle").value = o.title || "";
      $("oDescBn").value = o.descriptionBn || "";
      $("oDesc").value = o.description || "";
      $("oPartner").value = o.partner || "";
      $("oPoints").value = o.points;
      $("oOrder").value = o.order || 0;
      $("oActive").checked = !!o.active;
      $("oCancel").hidden = false;
      $("oTitleBn").focus();
      return;
    }
    case "offer-toggle": {
      const o = state.offers.find((x) => x.id === id);
      return updateDoc(doc(db, "reward_offers", id), { active: !o.active, updatedAt: serverTimestamp() }).then(() => toast(o.active ? "অ্যাপ থেকে লুকানো হয়েছে" : "অ্যাপে চালু হয়েছে")).catch(fail);
    }
    case "offer-delete":
      if (!(await ask({ title: "কার্ড মুছবেন?", text: "পুরনো ক্লেইমগুলো থেকে যাবে। শুধু লুকাতে চাইলে \"লুকান\" চাপুন।", ok: "মুছুন", danger: true }))) return;
      return deleteDoc(doc(db, "reward_offers", id)).then(() => toast("মুছে দেওয়া হয়েছে")).catch(fail);

    case "ann-delete":
      return deleteDoc(doc(db, "announcements", id)).then(() => toast("অ্যাপের নোটিফিকেশন পেজ থেকে সরানো হয়েছে")).catch(fail);

    case "req-close":
      return updateDoc(doc(db, "blood_requests", id), { status: "FULFILLED", updatedAt: serverTimestamp() }).then(() => toast("পূরণ হয়েছে হিসেবে বন্ধ")).catch(fail);
    case "req-delete":
      if (!(await ask({ title: "অনুরোধ মুছবেন?", text: "অ্যাপ থেকে সবার জন্য সরে যাবে।", ok: "মুছুন", danger: true }))) return;
      return deleteDoc(doc(db, "blood_requests", id)).then(() => toast("মুছে দেওয়া হয়েছে")).catch(fail);

    case "donor-points": {
      const cur = (p.userPointsOverrides || {})[id];
      const ok = await ask({ title: "পয়েন্ট ঠিক করুন", text: "ফোনে এই সংখ্যাটাই বসবে। খালি রাখলে ওভাররাইড উঠে যাবে, ফোনের নিজের হিসাব চলবে।", fields: [{ id: "pts", label: "পয়েন্ট", type: "number", value: cur ?? "" }, { id: "streak", label: "স্ট্রিক (ঐচ্ছিক)", type: "number", value: (p.userStreakOverrides || {})[id] ?? "" }] });
      if (!ok) return;
      const pts = { ...(p.userPointsOverrides || {}) };
      const st = { ...(p.userStreakOverrides || {}) };
      if (ok.pts === "") delete pts[id]; else pts[id] = Math.max(0, Math.min(1000000, parseInt(ok.pts, 10) || 0));
      if (ok.streak === "") delete st[id]; else st[id] = Math.max(0, Math.min(3650, parseInt(ok.streak, 10) || 0));
      return savePolicy({ userPointsOverrides: pts, userStreakOverrides: st }, "পয়েন্ট সেভ হয়েছে");
    }
    case "donor-loc": {
      const set = new Set(p.locationOffUserIds || []);
      set.has(id) ? set.delete(id) : set.add(id);
      return savePolicy({ locationOffUserIds: [...set] }, set.has(id) ? "এই ব্যবহারকারীর লোকেশন বন্ধ" : "লোকেশন চালু");
    }
    case "donor-suspend": {
      const set = new Set(p.suspendedUserIds || []);
      const reasons = { ...(p.suspendReasons || {}) };
      if (set.has(id)) {
        set.delete(id);
        delete reasons[id];
      } else {
        const ok = await ask({ title: "অ্যাকাউন্ট সাসপেন্ড", text: "অ্যাপ খুললে ব্যবহারকারী কারণটি দেখবেন।", fields: [{ id: "reason", label: "কারণ", max: 120 }], ok: "সাসপেন্ড", danger: true });
        if (!ok) return;
        set.add(id);
        if (ok.reason) reasons[id] = ok.reason;
      }
      return savePolicy({ suspendedUserIds: [...set], suspendReasons: reasons }, set.has(id) ? "সাসপেন্ড করা হয়েছে" : "আনসাসপেন্ড করা হয়েছে");
    }
    case "device-ban":
      return toggleBan(id);
  }
});

async function toggleBan(rawId, reason) {
  const id = String(rawId || "").trim().toUpperCase();
  if (!/^DEV-BD-[A-Z0-9]{1,16}$/.test(id)) return toast("ডিভাইস আইডি DEV-BD-… ধরনের হতে হবে");
  const p = policy();
  const set = new Set((p.bannedDeviceIds || []).map((x) => String(x).toUpperCase()));
  const reasons = { ...(p.banReasons || {}) };
  if (set.has(id) && reason === undefined) {
    set.delete(id);
    delete reasons[id];
  } else {
    if (reason === undefined) {
      const ok = await ask({ title: "ডিভাইস ব্যান", text: `${id} — এই ফোনে অ্যাপ আর চলবে না।`, fields: [{ id: "reason", label: "কারণ", max: 120 }], ok: "ব্যান", danger: true });
      if (!ok) return;
      reason = ok.reason;
    }
    set.add(id);
    if (reason) reasons[id] = reason;
  }
  return savePolicy({ bannedDeviceIds: [...set], banReasons: reasons }, set.has(id) ? "ডিভাইস ব্যান করা হয়েছে" : "ডিভাইস আনব্যান করা হয়েছে");
}

// ------------------------------------------------------------------ forms

function resetOfferForm() {
  state.editingOffer = null;
  $("offerForm").reset();
  $("oPoints").value = 500;
  $("oOrder").value = 0;
  $("oActive").checked = true;
  $("offerFormTitle").textContent = "নতুন কার্ড";
  $("oCancel").hidden = true;
}
$("oCancel").onclick = resetOfferForm;
$("newOfferBtn").onclick = () => { resetOfferForm(); go("rewards"); $("oTitleBn").focus(); };

$("offerForm").onsubmit = async (e) => {
  e.preventDefault();
  const points = parseInt($("oPoints").value, 10);
  if (!(points >= 1 && points <= 100000)) return toast("পয়েন্ট ১ থেকে ১,০০,০০০ এর মধ্যে দিন");
  const title = $("oTitle").value.trim();
  const titleBn = $("oTitleBn").value.trim();
  const data = {
    title, titleBn, points, active: $("oActive").checked,
    description: $("oDesc").value.trim(), descriptionBn: $("oDescBn").value.trim(),
    partner: $("oPartner").value.trim(), order: parseInt($("oOrder").value, 10) || 0,
    updatedAt: serverTimestamp(),
  };
  const id = state.editingOffer || (title.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 40) || "reward") + "-" + Math.random().toString(36).slice(2, 6);
  const editing = !!state.editingOffer;
  $("oSave").disabled = true;
  try {
    await setDoc(doc(db, "reward_offers", id), data);
    toast(editing ? "কার্ড আপডেট হয়েছে" : "নতুন কার্ড অ্যাপে চালু হয়েছে");
    resetOfferForm();
  } catch (err) {
    fail(err);
  } finally {
    $("oSave").disabled = false;
  }
};

$("earnForm").onsubmit = (e) => {
  e.preventDefault();
  const clamp = (v, lo, hi, d) => Math.max(lo, Math.min(hi, parseInt(v, 10) || d));
  savePolicy({
    rewardCheckInPoints: clamp($("eCheckIn").value, 5, 1000, 25),
    rewardBloodPledgePoints: clamp($("ePledge").value, 10, 2000, 100),
    rewardSosRespondPoints: clamp($("eSos").value, 10, 2000, 50),
  }, "পয়েন্টের নিয়ম সেভ হয়েছে");
};

$("globalLoc").onchange = (e) => savePolicy({ globalLocationOff: e.target.checked }, e.target.checked ? "সবার লোকেশন শেয়ার বন্ধ" : "লোকেশন শেয়ার চালু");
$("banForm").onsubmit = (e) => { e.preventDefault(); toggleBan($("banId").value, $("banReason").value.trim()); };
$("unbanBtn").onclick = () => {
  const id = $("banId").value.trim().toUpperCase();
  if (!(policy().bannedDeviceIds || []).map((x) => String(x).toUpperCase()).includes(id)) return toast("এই ডিভাইস ব্যান করা নেই");
  toggleBan(id);
};

$("donorSearch").oninput = renderDonors;
$("donorGroup").innerHTML += GROUPS.map((g) => `<option>${g}</option>`).join("");
$("donorGroup").onchange = renderDonors;
segment("donorSeg", "donorFilter", renderDonors);
segment("claimSeg", "claimFilter", renderClaims);
segment("reqSeg", "reqFilter", renderRequests);

// ------------------------------------------------------------------ push

$("pGroup").innerHTML = GROUPS.map((g) => `<option value="${groupTopic(g)}">${g}</option>`).join("");
const divisions = [...new Set(DISTRICTS.map((d) => d.division))];
$("pDist").innerHTML = divisions.map((div) => `<optgroup label="${esc(div)}">${DISTRICTS.filter((d) => d.division === div).map((d) => `<option value="${esc(d.topic)}">${esc(d.nameBn)} (${esc(d.name)})</option>`).join("")}</optgroup>`).join("");

segment("pTargetSeg", "pushTarget", () => {
  $("pGroupWrap").hidden = !["bg", "both"].includes(state.pushTarget);
  $("pDistWrap").hidden = !["dist", "both"].includes(state.pushTarget);
  renderReach();
});
$("pGroup").onchange = $("pDist").onchange = renderReach;

function preview() {
  $("pvTitle").textContent = $("pTitle").value.trim() || "শিরোনাম";
  $("pvBody").textContent = $("pBody").value.trim() || "বার্তা এখানে দেখাবে";
  $("pBodyCount").textContent = $("pBody").value.length;
}
$("pTitle").oninput = $("pBody").oninput = preview;
$("phoneTime").textContent = new Date().toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });

function renderReach() {
  const t = state.pushTarget;
  const g = ["bg", "both"].includes(t) ? groupFromTopic($("pGroup").value) : null;
  const dist = ["dist", "both"].includes(t) ? DISTRICTS.find((d) => d.topic === $("pDist").value) : null;
  const n = state.donors.filter((d) => (!g || d.bloodGroup === g) && (!dist || String(d.district || "").toLowerCase().includes(dist.name.toLowerCase()) || String(d.district || "").includes(dist.nameBn))).length;
  $("pReach").textContent = t === "all"
    ? "অ্যাপ ইনস্টল থাকা সব ফোনে যাবে।"
    : `${g ? g + " গ্রুপ" : ""}${g && dist ? ", " : ""}${dist ? dist.nameBn + " জেলা" : ""} বেছে রাখা ফোনে যাবে (প্রায় ${n} জন ডোনার প্রোফাইল মিলেছে)।`;
}

$("pushForm").onsubmit = async (e) => {
  e.preventDefault();
  const t = state.pushTarget;
  const payload = {
    title: $("pTitle").value.trim(),
    body: $("pBody").value.trim(),
    tab: $("pTab").value,
    bloodGroup: ["bg", "both"].includes(t) ? $("pGroup").value : "",
    district: ["dist", "both"].includes(t) ? $("pDist").value : "",
  };
  if (!payload.title || !payload.body) return toast("শিরোনাম ও বার্তা লিখুন");
  const ok = await ask({ title: "পুশ পাঠাবেন?", text: `${targetLabel([payload.bloodGroup, payload.district].filter(Boolean).join("&") || "all")} — “${payload.title}”। পাঠানোর পর ফেরত আনা যায় না।`, ok: "পাঠান" });
  if (!ok) return;
  $("pSend").disabled = true;
  try {
    const token = await auth.currentUser.getIdToken();
    const res = await fetch("/api/admin-push", { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` }, body: JSON.stringify(payload) });
    const out = await res.json().catch(() => ({}));
    if (res.ok) {
      toast("পুশ পাঠানো হয়েছে");
      $("pushForm").reset();
      preview();
    } else {
      toast({
        server_not_configured: "সার্ভার সেট হয়নি: Vercel-এ FIREBASE_SERVICE_ACCOUNT ও FIRESTORE_DATABASE_ID দিন",
        not_admin: "এই অ্যাকাউন্ট অ্যাডমিন না",
        limit: "এক ঘণ্টায় ২০টির বেশি পুশ পাঠানো যায় না",
        fcm: "FCM পাঠাতে পারেনি: " + (out.detail || ""),
      }[out.error] || `পাঠানো যায়নি (${res.status})`);
    }
  } catch (err) {
    toast("সার্ভারে পৌঁছানো যায়নি। প্যানেলটি Vercel থেকে খুলুন।");
  } finally {
    $("pSend").disabled = false;
  }
};
