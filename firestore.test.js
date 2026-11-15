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
const ADMIN_UID = "admin_789";

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

test("Unauthenticated caller cannot read user profiles or plans", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("plans").get());
});

test("Owner can create and read own user profile with role=user and subscriptionTier=free", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com", email_verified: true }).firestore();
  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@test.com",
      role: "user",
      subscriptionTier: "free",
      aiCreditsRemaining: 20,
      storageUsedBytes: 0,
      storageLimitBytes: 104857600,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Cross-user isolation: Bob cannot read or write Alice user profile", async () => {
  const now = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com", email_verified: true }).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@test.com",
      role: "user",
      subscriptionTier: "free",
      createdAt: now,
      updatedAt: now,
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@test.com", email_verified: true }).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Hacked",
      role: "user",
      subscriptionTier: "free",
      createdAt: now,
    })
  );
});

test("Local-only vault enforcement: personal vault subcollections are denied in Firestore even for owner", async () => {
  const now = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com", email_verified: true }).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      title: "Private Note",
      content: "Must stay local",
      createdAt: now,
    })
  );
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("reminders").get());
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("contacts").get());
});

test("Non-admin user cannot self-elevate role to admin or subscriptionTier to monthly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com", email_verified: true }).firestore();
  const now = new Date(Date.now() - 2000);
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      role: "admin",
      subscriptionTier: "free",
      createdAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      role: "user",
      subscriptionTier: "monthly",
      createdAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("subscriptions").doc("current").set({
      userId: ALICE_UID,
      planId: "monthly",
      status: "ACTIVE",
      verifiedByServer: true,
    })
  );
});

test("Email spoofing test: unverified admin email is rejected from admin collections", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: "suliyaamir900@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(spoofDb.collection("audit_logs").get());
  await assertFails(
    spoofDb.collection("plans").doc("monthly").set({
      planId: "monthly",
      name: "Premium Monthly",
      priceInr: 99,
      interval: "monthly",
      aiMonthlyCredits: 500,
      storageLimitMb: 5120,
      adFree: true,
      active: true,
    })
  );
});

test("Verified admin Firebase email (suliyaamir900@gmail.com) can create admin profile, manage plans, and read users/audit logs", async () => {
  const now = new Date(Date.now() - 2000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "suliyaamir900@gmail.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "YaadRakshak Admin",
      email: "suliyaamir900@gmail.com",
      role: "admin",
      subscriptionTier: "free",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("plans").doc("monthly").set({
      planId: "monthly",
      name: "Premium Monthly",
      priceInr: 99,
      interval: "monthly",
      aiMonthlyCredits: 500,
      storageLimitMb: 5120,
      adFree: true,
      active: true,
    })
  );
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(adminDb.collection("audit_logs").get());
  await assertSucceeds(
    adminDb.collection("app_config").doc("payments_public").set({
      paymentUpiId: "admin@upi",
      payeeName: "YaadRakshak",
      bankName: "HDFC",
    })
  );
  await assertSucceeds(
    adminDb.collection("app_config").doc("groq").set({
      apiKey: "gsk_secret",
    })
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@test.com", email_verified: true }).firestore();
  await assertSucceeds(aliceDb.collection("app_config").doc("payments_public").get());
  await assertFails(aliceDb.collection("app_config").doc("groq").get());
  await assertFails(aliceDb.collection("app_config").doc("youpi_pg").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      role: "user",
      subscriptionTier: "free",
      adFree: true,
      createdAt: now,
    })
  );
});
