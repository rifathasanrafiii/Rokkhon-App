const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or list donor_nodes, blood_requests, or sos_alerts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("donor_nodes").where("networkKey", "==", "BD_ROKKHON").get());
  await assertFails(unauthDb.collection("blood_requests").where("networkKey", "==", "BD_ROKKHON").get());
  await assertFails(unauthDb.collection("sos_alerts").where("networkKey", "==", "BD_ROKKHON").get());
});

test("Authenticated user can create clinical blood_request with hemoglobin, problem, ward, and neededDateTime", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("blood_requests").doc("req_clinical_1").set({
      userId: ALICE_UID,
      requesterName: "Nadia Islam",
      patientName: "Tahmid Hasan",
      bloodGroup: "O-",
      unitsNeeded: 2,
      hemoglobinLevel: "6.5 g/dL",
      patientProblem: "Dengue Shock Syndrome & Platelet Drop",
      hospitalName: "Dhaka Medical College Hospital",
      wardCabinNumber: "Ward 14, Bed 09",
      neededDateTime: "আজ রাত ১০টার মধ্যে (Within 2 Hours)",
      district: "Shahbagh, Dhaka",
      contactPhone: "+8801711000000",
      urgencyLevel: "CRITICAL",
      latitude: 23.7389,
      longitude: 90.3948,
      status: "OPEN",
      respondersCount: 0,
      networkKey: "BD_ROKKHON",
      createdAt: now,
      updatedAt: now,
    })
  );
});

function sosDoc(uid, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    senderName: "Rahim Uddin",
    bloodGroup: "O+",
    emergencyType: "ROAD_DANGER",
    district: "Mirpur 10, Dhaka",
    latitude: 23.8069,
    longitude: 90.3687,
    hasLiveCameraVerification: false,
    hasLiveAudioVerification: true,
    peakAudioDecibels: 74,
    triggerMethod: "3X_POWER_BUTTON",
    verificationNote: "Mic audio attached (74 dB peak)",
    bleBeaconHash: "BLE-98AF",
    status: "ACTIVE",
    responderCount: 0,
    falseAlarmReports: 0,
    networkKey: "BD_ROKKHON",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function bloodRequestDoc(uid, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    requesterName: "Nadia Islam",
    patientName: "Tahmid Hasan",
    bloodGroup: "O-",
    unitsNeeded: 2,
    hemoglobinLevel: "6.5 g/dL",
    patientProblem: "Dengue",
    hospitalName: "Dhaka Medical College Hospital",
    wardCabinNumber: "Ward 14, Bed 09",
    neededDateTime: "Now",
    district: "Shahbagh, Dhaka",
    contactPhone: "+8801711000000",
    urgencyLevel: "CRITICAL",
    latitude: 23.7389,
    longitude: 90.3948,
    status: "OPEN",
    respondersCount: 0,
    networkKey: "BD_ROKKHON",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

async function seed(path, data) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(path).set(data);
  });
}

function vote(db, parentPath, markerCollection, voterUid, counterField, nextCount = 1) {
  const now = new Date();
  const batch = db.batch();
  batch.set(db.doc(`${parentPath}/${markerCollection}/${voterUid}`), {
    userId: voterUid,
    createdAt: now,
  });
  batch.update(db.doc(parentPath), {
    [counterField]: nextCount,
    updatedAt: now,
  });
  return batch.commit();
}

test("SOS can be sent without mic/camera evidence (victims without permission still need help)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("sos_alerts").doc("sos_no_evidence").set(
      sosDoc(ALICE_UID, { hasLiveAudioVerification: false, peakAudioDecibels: 0, verificationNote: "No audio evidence" })
    )
  );
  await assertSucceeds(aliceDb.collection("sos_alerts").doc("sos_with_audio").set(sosDoc(ALICE_UID)));
});

test("SOS responder and false votes: one per account, never by the sender", async () => {
  await seed("sos_alerts/sos_vote_1", sosDoc(ALICE_UID));
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // A bare counter bump without a marker is rejected.
  await assertFails(
    bobDb.doc("sos_alerts/sos_vote_1").update({
      responderCount: 1,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(vote(bobDb, "sos_alerts/sos_vote_1", "responders", BOB_UID, "responderCount", 1));
  // Second vote from the same account is rejected.
  await assertFails(vote(bobDb, "sos_alerts/sos_vote_1", "responders", BOB_UID, "responderCount", 2));
  await assertSucceeds(vote(bobDb, "sos_alerts/sos_vote_1", "false_reports", BOB_UID, "falseAlarmReports", 1));
  await assertFails(vote(bobDb, "sos_alerts/sos_vote_1", "false_reports", BOB_UID, "falseAlarmReports", 2));
  // The sender cannot vote on their own SOS.
  await assertFails(vote(aliceDb, "sos_alerts/sos_vote_1", "responders", ALICE_UID, "responderCount", 2));
  // Nobody can write a marker for someone else.
  await assertFails(vote(bobDb, "sos_alerts/sos_vote_1", "responders", "carol_789", "responderCount", 2));
});

test("Blood pledge: one per account, requester cannot inflate the count", async () => {
  await seed("blood_requests/req_pledge_1", bloodRequestDoc(ALICE_UID));
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(vote(bobDb, "blood_requests/req_pledge_1", "pledges", BOB_UID, "respondersCount", 1));
  await assertFails(vote(bobDb, "blood_requests/req_pledge_1", "pledges", BOB_UID, "respondersCount", 2));
  await assertFails(
    aliceDb.doc("blood_requests/req_pledge_1").update({
      respondersCount: 50,
      updatedAt: new Date(),
    })
  );
  // Requester can still close the request.
  await assertSucceeds(
    aliceDb.doc("blood_requests/req_pledge_1").update({
      status: "FULFILLED",
      updatedAt: new Date(),
    })
  );
});

test("Donors cannot set or raise their own trust score", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const base = {
    userId: ALICE_UID,
    displayName: "Alice",
    bloodGroup: "A+",
    isAvailableDonor: true,
    district: "Uttara, Dhaka",
    contactPhone: "01711111111",
    latitude: 23.87,
    longitude: 90.39,
    lastDonationDate: "",
    trustScore: 100,
    verifiedSosCount: 0,
    networkKey: "BD_ROKKHON",
    createdAt: now,
    updatedAt: now,
  };
  await assertFails(aliceDb.doc(`donor_nodes/${ALICE_UID}`).set({ ...base, verifiedSosCount: 50 }));
  await assertSucceeds(aliceDb.doc(`donor_nodes/${ALICE_UID}`).set(base));
  await assertFails(
    aliceDb.doc(`donor_nodes/${ALICE_UID}`).update({
      verifiedSosCount: 9,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.doc(`donor_nodes/${ALICE_UID}`).update({
      isAvailableDonor: false,
      updatedAt: new Date(),
    })
  );
});

test("Only admins listed in /admins can change the admin policy; every signed-in app can read it", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`admins/${BOB_UID}`).set({ addedAt: new Date() });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const policy = { suspendedUserIds: [ALICE_UID], updatedAt: new Date().toISOString() };

  await assertFails(aliceDb.doc("admin_controls/global_policy").set(policy));
  await assertFails(aliceDb.doc(`admins/${ALICE_UID}`).set({ addedAt: new Date() }));
  await assertSucceeds(bobDb.doc("admin_controls/global_policy").set(policy));
  await assertSucceeds(aliceDb.doc("admin_controls/global_policy").get());
  await assertFails(unauthDb.doc("admin_controls/global_policy").get());
  await assertFails(aliceDb.doc("admin_controls/global_policy").update({ suspendedUserIds: [] }));
  await assertSucceeds(bobDb.doc(`admins/${BOB_UID}`).get());
  await assertFails(aliceDb.doc(`admins/${BOB_UID}`).get());
});

test("Device registry: owner writes their own device ID, only admins can list it", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`admins/${BOB_UID}`).set({ addedAt: new Date() });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const entry = { userId: ALICE_UID, deviceId: "DEV-BD-8F92A1C4", updatedAt: new Date() };

  await assertSucceeds(aliceDb.doc(`device_registry/${ALICE_UID}`).set(entry));
  await assertFails(aliceDb.doc(`device_registry/${BOB_UID}`).set({ ...entry, userId: BOB_UID }));
  await assertFails(aliceDb.doc(`device_registry/${ALICE_UID}`).set({ ...entry, deviceId: "<script>" }));
  await assertFails(aliceDb.doc(`device_registry/${ALICE_UID}`).set({ ...entry, trustScore: 100 }));
  await assertFails(aliceDb.collection("device_registry").get());
  await assertSucceeds(bobDb.collection("device_registry").get());
});
