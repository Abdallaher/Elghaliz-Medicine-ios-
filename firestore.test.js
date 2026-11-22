const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "doctor_admin_1";
const STUDENT_UID = "student_user_2";
const OTHER_STUDENT_UID = "student_user_3";
const ADMIN_EMAIL_HASH = "a".repeat(64);
const SECOND_ADMIN_HASH = "b".repeat(64);

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

async function seedAdminBootstrap() {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("system_state").doc("config").set({
      primaryAdminUid: ADMIN_UID,
      adminEmailHashes: [ADMIN_EMAIL_HASH, SECOND_ADMIN_HASH],
      scopeKey: "global",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("admin_members").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      emailHash: ADMIN_EMAIL_HASH,
      isPrimary: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("admins").doc("primary_admin").set({
      email: "dr.elghaliz@med.edu",
      emailHash: ADMIN_EMAIL_HASH,
      roleTitle: "المدير الرئيسي",
      addedByUid: ADMIN_UID,
      isPrimary: true,
      scopeKey: "global",
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated user: cannot read categories, lectures, or meetings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("categories").where("visibility", "==", "published").get());
  await assertFails(unauthDb.collection("lectures").where("visibility", "==", "published").get());
  await assertFails(unauthDb.collection("meetings").where("visibility", "==", "published").get());
});

test("Student Approval Workflow & Per-Course Request Workflow: enforced by security rules", async () => {
  await seedAdminBootstrap();
  const now = new Date();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const otherStudentDb = testEnv.authenticatedContext(OTHER_STUDENT_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  // Admin creates a Category (Course)
  await assertSucceeds(
    adminDb.collection("categories").doc("cat_cardio").set({
      name: "كورس أمراض القلب",
      description: "محاضرات تخطيط القلب والباطنة",
      createdByUid: ADMIN_UID,
      createdByName: "د. الغليظ",
      visibility: "published",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student registers with pending status
  await assertSucceeds(
    studentDb.collection("student_requests").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      email: "student2@med.edu",
      displayName: "أحمد محمد",
      status: "pending",
      scopeKey: "global",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unapproved student cannot request a course yet
  const courseReqId = `${STUDENT_UID}_cat_cardio`;
  await assertFails(
    studentDb.collection("course_requests").doc(courseReqId).set({
      userId: STUDENT_UID,
      studentEmail: "student2@med.edu",
      studentName: "أحمد محمد",
      categoryId: "cat_cardio",
      categoryName: "كورس أمراض القلب",
      status: "pending",
      scopeKey: "global",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin approves student's main account
  await assertSucceeds(
    adminDb.collection("student_requests").doc(STUDENT_UID).update({
      status: "approved",
      updatedAt: new Date(),
    })
  );

  // Approved student requests enrollment in cat_cardio -> pending
  await assertSucceeds(
    studentDb.collection("course_requests").doc(courseReqId).set({
      userId: STUDENT_UID,
      studentEmail: "student2@med.edu",
      studentName: "أحمد محمد",
      categoryId: "cat_cardio",
      categoryName: "كورس أمراض القلب",
      status: "pending",
      scopeKey: "global",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student cannot self-approve their course request
  await assertFails(
    studentDb.collection("course_requests").doc(courseReqId).update({
      status: "approved",
      updatedAt: new Date(),
    })
  );

  // Another student cannot read STUDENT_UID's course request
  await assertFails(otherStudentDb.collection("course_requests").doc(courseReqId).get());

  // Admin approves student's course enrollment request
  await assertSucceeds(
    adminDb.collection("course_requests").doc(courseReqId).update({
      status: "approved",
      updatedAt: new Date(),
    })
  );

  // Admin uploads a lecture with video chunks in cat_cardio
  await assertSucceeds(
    adminDb.collection("lectures").doc("lec_ecg_1").set({
      title: "محاضرة تخطيط القلب",
      description: "شرح عملي",
      categoryId: "cat_cardio",
      categoryName: "كورس أمراض القلب",
      uploadedByUid: ADMIN_UID,
      doctorName: "د. الغليظ",
      videoSourceUri: "file:///data/user/0/app/lec_ecg_1.mp4",
      durationSeconds: 120,
      fileSizeMb: 5,
      chunkCount: 1,
      isRecordedMeeting: false,
      visibility: "published",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("lectures").doc("lec_ecg_1").collection("video_chunks").doc("chunk_0").set({
      lectureId: "lec_ecg_1",
      chunkIndex: 0,
      dataBase64: "AAAAHGZ0eXBtcDQyAAAAAG1wNDJpc29t",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Enrolled student can get the lecture and its video chunk
  await assertSucceeds(studentDb.collection("lectures").doc("lec_ecg_1").get());
  await assertSucceeds(
    studentDb.collection("lectures").doc("lec_ecg_1").collection("video_chunks").doc("chunk_0").get()
  );
});

test("Native Live Meeting & Chat: only approved students and admins can join or send chat messages", async () => {
  await seedAdminBootstrap();
  const now = new Date();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();

  await assertSucceeds(
    adminDb.collection("meetings").doc("meet_native_1").set({
      title: "محاضرة الباطنة المباشرة",
      hostUid: ADMIN_UID,
      hostName: "د. الغليظ",
      presentationMode: "slides",
      currentSlideIndex: 0,
      studentsMuted: false,
      raisedHandsCount: 0,
      status: "live",
      isRecording: true,
      isScreenSharing: true,
      participantCount: 1,
      visibility: "published",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    studentDb.collection("meetings").doc("meet_native_1").collection("messages").doc("msg_1").set({
      meetingId: "meet_native_1",
      senderUid: STUDENT_UID,
      senderName: "أحمد",
      senderRole: "student",
      text: "سؤال طبي حول تخطيط القلب",
      isHandRaise: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("student_requests").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      email: "student2@med.edu",
      displayName: "أحمد",
      status: "approved",
      scopeKey: "global",
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertSucceeds(
    studentDb.collection("meetings").doc("meet_native_1").collection("messages").doc("msg_1").set({
      meetingId: "meet_native_1",
      senderUid: STUDENT_UID,
      senderName: "أحمد",
      senderRole: "student",
      text: "سؤال طبي حول تخطيط القلب",
      isHandRaise: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Timed PDF Exams & Smart Notifications: admin creates exam and notification; enrolled student reads exam and PDF chunks", async () => {
  await seedAdminBootstrap();
  const now = new Date();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const unenrolledDb = testEnv.authenticatedContext(OTHER_STUDENT_UID).firestore();

  await assertSucceeds(
    adminDb.collection("categories").doc("cat_pharma").set({
      name: "Pharmacology",
      description: "علم الأدوية السريري",
      createdByUid: ADMIN_UID,
      createdByName: "د. الغليظ",
      visibility: "published",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Approve STUDENT_UID and enroll in cat_pharma
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("student_requests").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      email: "student2@med.edu",
      displayName: "أحمد",
      status: "approved",
      scopeKey: "global",
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("student_requests").doc(OTHER_STUDENT_UID).set({
      userId: OTHER_STUDENT_UID,
      email: "student3@med.edu",
      displayName: "خالد",
      status: "approved",
      scopeKey: "global",
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("course_requests").doc(`${STUDENT_UID}_cat_pharma`).set({
      userId: STUDENT_UID,
      email: "student2@med.edu",
      displayName: "أحمد",
      categoryId: "cat_pharma",
      categoryTitle: "Pharmacology",
      status: "approved",
      scopeKey: "all_courses",
      createdAt: now,
      updatedAt: now,
    });
  });

  const startMillis = Date.now();
  const endMillis = startMillis + 3600000;

  // Admin creates an ExamItem
  await assertSucceeds(
    adminDb.collection("exams").doc("exam_pharma_1").set({
      title: "اختبار علم الأدوية النصفي",
      description: "أسئلة سريرية شاملة بصيغة PDF",
      categoryId: "cat_pharma",
      categoryName: "Pharmacology",
      uploadedByUid: ADMIN_UID,
      doctorName: "د. الغليظ",
      pdfFileName: "pharmacology_midterm.pdf",
      fileSizeKb: 240,
      chunkCount: 1,
      startTimeMillis: startMillis,
      endTimeMillis: endMillis,
      scheduleLabel: "الإثنين 2026/11/09 من 10:00 ص إلى 11:00 ص",
      visibility: "published",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("exams").doc("exam_pharma_1").collection("pdf_chunks").doc("chunk_0").set({
      examId: "exam_pharma_1",
      chunkIndex: 0,
      dataBase64: "JVBERi0xLjQK",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin publishes a smart course notification
  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_exam_1").set({
      title: "اختبار جديد في Pharmacology",
      body: "تم رفع اختبار علم الأدوية النصفي",
      categoryId: "cat_pharma",
      categoryName: "Pharmacology",
      eventType: "new_exam",
      targetItemId: "exam_pharma_1",
      createdByUid: ADMIN_UID,
      visibility: "published",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unenrolled student cannot read the PDF chunk
  await assertFails(
    unenrolledDb.collection("exams").doc("exam_pharma_1").collection("pdf_chunks").doc("chunk_0").get()
  );

  // Enrolled student can read the exam, its PDF chunk, and the notification
  await assertSucceeds(studentDb.collection("exams").doc("exam_pharma_1").get());
  await assertSucceeds(
    studentDb.collection("exams").doc("exam_pharma_1").collection("pdf_chunks").doc("chunk_0").get()
  );
  await assertSucceeds(studentDb.collection("notifications").doc("notif_exam_1").get());
});

test("Central 5TB Google Drive Vault Config (system_state/drive_config): strictly restricted to admins for both read and write", async () => {
  await seedAdminBootstrap();
  const now = new Date();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const driveConfigPayload = {
    primaryAdminEmail: "abdallahxh909@gmail.com",
    primaryAdminUid: ADMIN_UID,
    vaultFolderId: "1A2B3C_Elghaliz_5TB_Vault_Folder",
    vaultFolderName: "Elghaliz_Medicine_5TB_Vault",
    accessToken: "",
    refreshToken: "",
    tokenExpiresAtMillis: Date.now() + 3600000,
    isEnabled: true,
    scopeKey: "global",
    createdAt: now,
    updatedAt: now,
  };

  // Student cannot write drive_config
  await assertFails(
    studentDb.collection("system_state").doc("drive_config").set(driveConfigPayload)
  );

  // Admin creates drive_config
  await assertSucceeds(
    adminDb.collection("system_state").doc("drive_config").set(driveConfigPayload)
  );

  // Unauthenticated or unapproved student cannot read drive_config
  await assertFails(unauthDb.collection("system_state").doc("drive_config").get());
  await assertFails(studentDb.collection("system_state").doc("drive_config").get());

  // Even an approved student cannot read drive_config (prevents token/config leakage)
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("student_requests").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      email: "student2@med.edu",
      displayName: "أحمد",
      status: "approved",
      scopeKey: "global",
      registeredDeviceIds: [],
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertFails(studentDb.collection("system_state").doc("drive_config").get());
  await assertSucceeds(adminDb.collection("system_state").doc("drive_config").get());

  // Student can register the 1st device when registeredDeviceIds is initially empty
  await assertSucceeds(
    studentDb.collection("student_requests").doc(STUDENT_UID).update({
      registeredDeviceIds: ["dev_1"],
      updatedAt: new Date(),
    })
  );

  // Once the 1st device is registered, student cannot add a 2nd device or replace the 1st device
  await assertFails(
    studentDb.collection("student_requests").doc(STUDENT_UID).update({
      registeredDeviceIds: ["dev_1", "dev_2"],
      updatedAt: new Date(),
    })
  );
  await assertFails(
    studentDb.collection("student_requests").doc(STUDENT_UID).update({
      registeredDeviceIds: ["dev_2"],
      updatedAt: new Date(),
    })
  );
  // Updating without changing registeredDeviceIds still succeeds
  await assertSucceeds(
    studentDb.collection("student_requests").doc(STUDENT_UID).update({
      registeredDeviceIds: ["dev_1"],
      updatedAt: new Date(),
    })
  );

  // Student cannot create or delete categories, lectures, or exams
  await assertFails(
    studentDb.collection("categories").doc("cat_hack").set({
      name: "Hack",
      description: "Unauthorized",
      createdByUid: STUDENT_UID,
      createdByName: "Student",
      visibility: "published",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(studentDb.collection("lectures").doc("lec_hack").delete());
  await assertFails(studentDb.collection("exams").doc("exam_hack").delete());

  // In-App APK Auto-Updater (/system_state/app_update):
  // Only Admin can create/update/delete app_update; signed-in students can read it
  const appUpdatePayload = {
    latestVersionCode: 2,
    latestVersionName: "1.1",
    apkDriveFileId: "1ApkDriveFileIdExample999",
    releaseNotes: "تحسين سرعة المشغل ودعم التحديث التلقائي",
    forceUpdate: false,
    updatedAt: now,
  };
  await assertFails(studentDb.collection("system_state").doc("app_update").set(appUpdatePayload));
  await assertSucceeds(adminDb.collection("system_state").doc("app_update").set(appUpdatePayload));
  await assertSucceeds(studentDb.collection("system_state").doc("app_update").get());
  await assertFails(unauthDb.collection("system_state").doc("app_update").get());
  await assertFails(studentDb.collection("system_state").doc("app_update").delete());
});




