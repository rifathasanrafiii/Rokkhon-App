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

test("3x Hardware SOS Alert requires silent camera/audio blackbox attachment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  await assertFails(
    aliceDb.collection("sos_alerts").doc("sos_unverified_1").set({
      userId: ALICE_UID,
      senderName: "Rahim Uddin",
      bloodGroup: "O+",
      emergencyType: "ROAD_DANGER",
      district: "Mirpur 10, Dhaka",
      latitude: 23.8069,
      longitude: 90.3687,
      hasLiveCameraVerification: false,
      hasLiveAudioVerification: false,
      peakAudioDecibels: 0,
      triggerMethod: "3X_POWER_BUTTON",
      verificationNote: "Unverified tap",
      bleBeaconHash: "BLE-98AF",
      status: "ACTIVE",
      responderCount: 0,
      falseAlarmReports: 0,
      networkKey: "BD_ROKKHON",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("sos_alerts").doc("sos_verified_1").set({
      userId: ALICE_UID,
      senderName: "Rahim Uddin",
      bloodGroup: "O+",
      emergencyType: "ROAD_DANGER",
      district: "Mirpur 10, Dhaka",
      latitude: 23.8069,
      longitude: 90.3687,
      hasLiveCameraVerification: true,
      hasLiveAudioVerification: true,
      peakAudioDecibels: 74,
      triggerMethod: "3X_POWER_BUTTON",
      verificationNote: "Auto 3s Silent Cam + Audio Blackbox Attached",
      bleBeaconHash: "BLE-98AF",
      status: "ACTIVE",
      responderCount: 0,
      falseAlarmReports: 0,
      networkKey: "BD_ROKKHON",
      createdAt: now,
      updatedAt: now,
    })
  );
});
