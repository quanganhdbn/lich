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

test("Unauthenticated user: cannot read trips", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("trips").get());
});

test("Authenticated user: cannot read another user's trips", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("trips").doc("trip_1").set({
      userId: BOB_UID,
      tripDate: "2026-10-12",
      startTime: "08:00",
      startLocation: "Keangnam",
      destination: "Noi Bai",
      status: "SCHEDULED"
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("trips").doc("trip_1").get());
});

test("Authenticated user: can create and read their own trips", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").set({
      userId: ALICE_UID,
      tripDate: "2026-10-12",
      startTime: "08:00",
      startLocation: "Keangnam",
      destination: "Noi Bai",
      status: "SCHEDULED"
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1").get()
  );
});

test("Authenticated user: cannot create trip with mismatched userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_2").set({
      userId: BOB_UID,
      tripDate: "2026-10-12",
      startTime: "08:00",
      startLocation: "Keangnam",
      destination: "Noi Bai",
      status: "SCHEDULED"
    })
  );
});
