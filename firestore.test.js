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
const ADMIN_UID = "admin_salman";

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

test("Unauthenticated user: cannot read or write users, chats, messages, or app_config", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("messages").get());
  await assertFails(unauthDb.collection("app_config").doc("promo").get());
  await assertFails(
    unauthDb.collection("app_config").doc("promo").set({
      promoText: "Unauthorized",
      updatedBy: "anon",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("UserProfile: owner create/update/get succeeds; cross-user PII read and shadow fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();

  const created = new Date(Date.now() - 10000);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user PII read rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  // Listing /users rejected
  await assertFails(aliceDb.collection("users").get());
  // Shadow field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("CloudChat & CloudMessage: owner CRUD and subcollection list succeed; cross-user, ghost id, future ts fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const chatId = "chat_abc123";
  const msgId = "msg_def456";

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc(chatId).set({
      chatKey: chatId,
      rawChatId: "com.whatsapp::Aarav",
      userId: ALICE_UID,
      title: "Aarav",
      packageName: "com.whatsapp",
      appLabel: "WhatsApp",
      groupChat: false,
      pinned: false,
      lastMessageText: "Hello",
      lastSenderName: "Aarav",
      lastMessageType: "TEXT",
      lastTimestamp: 1764100000000,
      unreadCount: 1,
      deletedCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc(chatId).update({
      pinned: true,
      unreadCount: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc(msgId).set({
      userId: ALICE_UID,
      chatKey: chatId,
      rawChatId: "com.whatsapp::Aarav",
      senderName: "Aarav",
      body: "Hello",
      timestamp: 1764100000000,
      packageName: "com.whatsapp",
      messageType: "TEXT",
      deletedInWhatsApp: false,
      viewOnce: false,
      dedupKey: "dedup_1",
      createdAt: created,
      updatedAt: created,
    })
  );

  // List queries match Kotlin repositories
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("messages").get());

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("messages").doc(msgId).get());

  // Reject extra @DocumentId 'id' field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_bad").set({
      id: "chat_bad",
      chatKey: "chat_bad",
      rawChatId: "com.whatsapp::Bad",
      userId: ALICE_UID,
      title: "Bad",
      packageName: "com.whatsapp",
      appLabel: "WhatsApp",
      groupChat: false,
      pinned: false,
      lastMessageText: "Bad",
      lastSenderName: "Bad",
      lastMessageType: "TEXT",
      lastTimestamp: 1764100000000,
      unreadCount: 0,
      deletedCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_future").set({
      userId: ALICE_UID,
      chatKey: chatId,
      rawChatId: "com.whatsapp::Aarav",
      senderName: "Aarav",
      body: "Future",
      timestamp: 1764100000000,
      packageName: "com.whatsapp",
      messageType: "TEXT",
      deletedInWhatsApp: false,
      viewOnce: false,
      dedupKey: "dedup_future",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("AppPromoConfig: verified admin writes; spoofed unverified email and normal user writes fail", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "salmankhaskheli885@gmail.com",
    email_verified: true,
  }).firestore();

  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: "salmankhaskheli885@gmail.com",
    email_verified: false,
  }).firestore();

  const normalDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  const created = new Date(Date.now() - 10000);

  // Unverified spoofed admin rejected
  await assertFails(
    spoofedAdminDb.collection("app_config").doc("promo").set({
      promoText: "Spoofed promo",
      updatedBy: "spoof_uid",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Normal user write rejected
  await assertFails(
    normalDb.collection("app_config").doc("promo").set({
      promoText: "User promo",
      updatedBy: ALICE_UID,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Verified bootstrapped admin succeeds
  await assertSucceeds(
    adminDb.collection("app_config").doc("promo").set({
      promoText: "Dev Salman Khan: 03112765988",
      updatedBy: ADMIN_UID,
      promoFileName: "brochure.txt",
      promoFileMimeType: "text/plain",
      promoFileSizeBytes: 24,
      promoFileBase64: "SGVsbG8=",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Signed-in normal user can read promo config (get), cannot list collection
  await assertSucceeds(normalDb.collection("app_config").doc("promo").get());
  await assertFails(normalDb.collection("app_config").get());
});
