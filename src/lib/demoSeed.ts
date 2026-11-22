// Deterministic demo seed used ONLY when the user or verification harness explicitly
// opens `?demo=student` or `?demo=admin`. Normal production launch always uses live Firebase.

import { DEFAULT_MEDICAL_CATEGORIES, PRIMARY_ADMIN_EMAIL } from '../config';
import type {
  AdminEntry,
  AuthUser,
  Category,
  CourseRequest,
  ExamItem,
  Lecture,
  OfflineDownloadRecord,
  StudentRequest,
  TimestampedLectureNote,
} from '../types/models';
import { formatDateTime12HrArabic } from './logic';

export interface DemoSeedBundle {
  user: AuthUser;
  categories: Category[];
  lectures: Lecture[];
  exams: ExamItem[];
  studentRequests: StudentRequest[];
  courseRequests: CourseRequest[];
  admins: AdminEntry[];
  offlineDownloads: OfflineDownloadRecord[];
  notes: TimestampedLectureNote[];
}

/**
 * Generates a valid minimal in-memory PDF Blob so PDF preview & download work offline in demo mode.
 */
export function buildSampleMedicalExamPdfBlob(examTitle: string): Blob {
  const safeTitle = examTitle.replace(/[^\x20-\x7E]/g, '') || 'Elghaliz Medicine Exam';
  const pdfContent = `%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj
4 0 obj << /Length 220 >> stream
BT
/F1 20 Tf
50 720 Td
(Elghaliz Medicine - Timed Medical Exam) Tj
/F1 13 Tf
0 -36 Td
(Exam: ${safeTitle}) Tj
0 -28 Td
(Q1: Which vitamin deficiency causes Wernicke encephalopathy?) Tj
0 -22 Td
(A) Vitamin B1 (Thiamine)   B) Vitamin B6   C) Vitamin C   D) Vitamin D) Tj
ET
endstream endobj
5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
xref
0 6
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000241 00000 n 
0000000514 00000 n 
trailer << /Size 6 /Root 1 0 R >>
startxref
584
%%EOF`;
  return new Blob([pdfContent], { type: 'application/pdf' });
}

export function createDemoSeed(mode: 'student' | 'admin', deviceId: string): DemoSeedBundle {
  const now = Date.now();
  const isAdmin = mode === 'admin';

  const user: AuthUser = isAdmin
    ? {
        uid: 'demo_admin_elghaliz',
        email: PRIMARY_ADMIN_EMAIL,
        displayName: 'د. عبدالله الغليظ',
      }
    : {
        uid: 'demo_student_ahmed',
        email: 'ahmed.med2026@gmail.com',
        displayName: 'أحمد محمود السعيد',
      };

  const categories = DEFAULT_MEDICAL_CATEGORIES.map((c, idx) => ({
    ...c,
    createdAt: now - (5 - idx) * 86_400_000,
  }));

  const lectures: Lecture[] = [
    {
      id: 'lec_nut_1',
      title: 'المحاضرة 1: أساسيات التغذية العلاجية والفيتامينات الذائبة في الماء',
      description: 'شرح شامل لدور فيتامين B1 وB12 وحمض الفوليك في التمثيل الغذائي والوقاية من الاعتلال العصبي.',
      categoryId: 'cat_nutrition',
      categoryName: 'Nutrition',
      uploadedByUid: 'demo_admin_elghaliz',
      doctorName: 'د. عبدالله الغليظ',
      videoSourceUri: 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4',
      durationSeconds: 2640,
      fileSizeMb: 48.5,
      chunkCount: 1,
      isRecordedMeeting: false,
      visibility: 'published',
      createdAt: now - 3 * 86_400_000,
    },
    {
      id: 'lec_nut_2',
      title: 'المحاضرة 2: التغذية السريرية لمرضى السكري والكلى',
      description: 'حساب السعرات الحرارية وتوزيع البروتينات والكربوهيدرات في الحالات الحرجة.',
      categoryId: 'cat_nutrition',
      categoryName: 'Nutrition',
      uploadedByUid: 'demo_admin_elghaliz',
      doctorName: 'د. عبدالله الغليظ',
      videoSourceUri: 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4',
      durationSeconds: 3120,
      fileSizeMb: 62.0,
      chunkCount: 1,
      isRecordedMeeting: false,
      visibility: 'published',
      createdAt: now - 2 * 86_400_000,
    },
    {
      id: 'lec_nut_3',
      title: 'المحاضرة 3: سوء التغذية البروتيني (Kwashiorkor & Marasmus)',
      description: 'المقارنة السريرية والمخبرية وبروتوكول إعادة التغذية الآمن.',
      categoryId: 'cat_nutrition',
      categoryName: 'Nutrition',
      uploadedByUid: 'demo_admin_elghaliz',
      doctorName: 'د. عبدالله الغليظ',
      videoSourceUri: 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4',
      durationSeconds: 1980,
      fileSizeMb: 34.2,
      chunkCount: 1,
      isRecordedMeeting: false,
      visibility: 'published',
      createdAt: now - 86_400_000,
    },
    {
      id: 'lec_micro_1',
      title: 'المحاضرة 1: البكتيريا موجبة الجرام (Staph & Strep)',
      description: 'عوامل الضراوة والمضادات الحيوية المختارة في الالتهابات السريرية.',
      categoryId: 'cat_microbiology',
      categoryName: 'Microbiology',
      uploadedByUid: 'demo_admin_elghaliz',
      doctorName: 'د. عبدالله الغليظ',
      videoSourceUri: 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4',
      durationSeconds: 2850,
      fileSizeMb: 54.0,
      chunkCount: 1,
      isRecordedMeeting: false,
      visibility: 'published',
      createdAt: now - 2 * 86_400_000,
    },
    {
      id: 'lec_pharm_1',
      title: 'المحاضرة 1: حركية الدواء (Pharmacokinetics & Half-life)',
      description: 'الامتصاص والتوزيع والأيض والإخراج وحساب جرعة التحميل.',
      categoryId: 'cat_pharmacology',
      categoryName: 'Pharmacology',
      uploadedByUid: 'demo_admin_elghaliz',
      doctorName: 'د. عبدالله الغليظ',
      videoSourceUri: 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4',
      durationSeconds: 2400,
      fileSizeMb: 41.8,
      chunkCount: 1,
      isRecordedMeeting: false,
      visibility: 'published',
      createdAt: now - 86_400_000,
    },
  ];

  const openStart = now - 30 * 60 * 1000;
  const openEnd = now + 95 * 60 * 1000;
  const upcomingStart = now + 4 * 60 * 60 * 1000;
  const upcomingEnd = now + 6 * 60 * 60 * 1000;
  const expiredStart = now - 6 * 60 * 60 * 1000;
  const expiredEnd = now - 2 * 60 * 60 * 1000;

  const exams: ExamItem[] = [
    {
      id: 'exam_nut_midterm',
      title: 'اختبار منتصف الترم - التغذية العلاجية (Nutrition Midterm)',
      description: 'يشمل محاضرات الفيتامينات والتغذية السريرية لمرضى السكري والكلى.',
      categoryId: 'cat_nutrition',
      categoryName: 'Nutrition',
      uploadedByUid: 'demo_admin_elghaliz',
      doctorName: 'د. عبدالله الغليظ',
      pdfFileName: 'Nutrition_Midterm_2026.pdf',
      fileSizeKb: 420,
      chunkCount: 1,
      startTimeMillis: openStart,
      endTimeMillis: openEnd,
      scheduleLabel: `من ${formatDateTime12HrArabic(openStart)} إلى ${formatDateTime12HrArabic(openEnd)}`,
      visibility: 'published',
      createdAt: now - 3600_000,
    },
    {
      id: 'exam_nut_final_quiz',
      title: 'كويز مراجعة سوء التغذية البروتيني (Scheduled PDF)',
      description: 'يُفتح تلقائياً في الموعد المحدد ويُخفى بعد انتهاء الوقت.',
      categoryId: 'cat_nutrition',
      categoryName: 'Nutrition',
      uploadedByUid: 'demo_admin_elghaliz',
      doctorName: 'د. عبدالله الغليظ',
      pdfFileName: 'Nutrition_Quiz_2.pdf',
      fileSizeKb: 280,
      chunkCount: 1,
      startTimeMillis: upcomingStart,
      endTimeMillis: upcomingEnd,
      scheduleLabel: `من ${formatDateTime12HrArabic(upcomingStart)} إلى ${formatDateTime12HrArabic(upcomingEnd)}`,
      visibility: 'published',
      createdAt: now - 1800_000,
    },
    {
      id: 'exam_nut_expired',
      title: 'اختبار الأسبوع الأول (منتهي الموعد - يظهر للمدير فقط)',
      description: 'هذا الاختبار انتهى موعده ويختفي تلقائياً من شاشة الطالب.',
      categoryId: 'cat_nutrition',
      categoryName: 'Nutrition',
      uploadedByUid: 'demo_admin_elghaliz',
      doctorName: 'د. عبدالله الغليظ',
      pdfFileName: 'Nutrition_Week1.pdf',
      fileSizeKb: 190,
      chunkCount: 1,
      startTimeMillis: expiredStart,
      endTimeMillis: expiredEnd,
      scheduleLabel: `من ${formatDateTime12HrArabic(expiredStart)} إلى ${formatDateTime12HrArabic(expiredEnd)}`,
      visibility: 'published',
      createdAt: now - 86_400_000,
    },
  ];

  const studentRequests: StudentRequest[] = [
    {
      id: 'demo_student_ahmed',
      userId: 'demo_student_ahmed',
      email: 'ahmed.med2026@gmail.com',
      displayName: 'أحمد محمود السعيد',
      status: 'approved',
      scopeKey: 'admin_only',
      registeredDeviceIds: [deviceId],
      createdAt: now - 7 * 86_400_000,
      updatedAt: now - 7 * 86_400_000,
    },
    {
      id: 'demo_student_sara',
      userId: 'demo_student_sara',
      email: 'sara.med2026@gmail.com',
      displayName: 'سارة خالد العتيبي',
      status: 'pending',
      scopeKey: 'admin_only',
      registeredDeviceIds: ['WEB_c8a4f219-9d0e-4b32-a111-94e20312c8aa'],
      createdAt: now - 3600_000,
      updatedAt: now - 3600_000,
    },
    {
      id: 'demo_student_omar',
      userId: 'demo_student_omar',
      email: 'omar.clinic@gmail.com',
      displayName: 'عمر طارق النجار',
      status: 'suspended',
      scopeKey: 'admin_only',
      registeredDeviceIds: ['WEB_7b30e105-44c1-4a77-82f0-112233445566'],
      createdAt: now - 12 * 86_400_000,
      updatedAt: now - 86_400_000,
    },
  ];

  const courseRequests: CourseRequest[] = [
    {
      id: 'demo_student_ahmed_cat_nutrition',
      userId: 'demo_student_ahmed',
      studentEmail: 'ahmed.med2026@gmail.com',
      studentName: 'أحمد محمود السعيد',
      categoryId: 'cat_nutrition',
      categoryName: 'Nutrition',
      status: 'approved',
      scopeKey: 'admin_only',
      createdAt: now - 6 * 86_400_000,
      updatedAt: now - 6 * 86_400_000,
    },
    {
      id: 'demo_student_ahmed_cat_microbiology',
      userId: 'demo_student_ahmed',
      studentEmail: 'ahmed.med2026@gmail.com',
      studentName: 'أحمد محمود السعيد',
      categoryId: 'cat_microbiology',
      categoryName: 'Microbiology',
      status: 'pending',
      scopeKey: 'admin_only',
      createdAt: now - 5400_000,
      updatedAt: now - 5400_000,
    },
    {
      id: 'demo_student_sara_cat_pharmacology',
      userId: 'demo_student_sara',
      studentEmail: 'sara.med2026@gmail.com',
      studentName: 'سارة خالد العتيبي',
      categoryId: 'cat_pharmacology',
      categoryName: 'Pharmacology',
      status: 'pending',
      scopeKey: 'admin_only',
      createdAt: now - 2400_000,
      updatedAt: now - 2400_000,
    },
  ];

  const admins: AdminEntry[] = [
    {
      id: 'adm_primary',
      email: PRIMARY_ADMIN_EMAIL,
      emailHash: 'primary',
      roleTitle: 'المدير الرئيسي',
      addedByUid: 'demo_admin_elghaliz',
      isPrimary: true,
      scopeKey: 'admin_only',
      createdAt: now - 30 * 86_400_000,
    },
    {
      id: 'adm_assistant',
      email: 'dr.assistant@elghaliz.med',
      emailHash: 'assistant_hash',
      roleTitle: 'دكتور مساعد',
      addedByUid: 'demo_admin_elghaliz',
      isPrimary: false,
      scopeKey: 'admin_only',
      createdAt: now - 10 * 86_400_000,
    },
  ];

  const offlineDownloads: OfflineDownloadRecord[] = [
    {
      lectureId: 'lec_nut_1',
      userId: user.uid,
      title: lectures[0].title,
      description: lectures[0].description,
      categoryId: 'cat_nutrition',
      categoryName: 'Nutrition',
      doctorName: lectures[0].doctorName,
      durationSeconds: lectures[0].durationSeconds,
      fileSizeBytes: Math.round(48.5 * 1024 * 1024),
      fileSizeMb: 48.5,
      mimeType: 'video/mp4',
      downloadedAt: now - 4 * 3600_000,
      encryptedHeaderBytes: 4096,
    },
  ];

  const notes: TimestampedLectureNote[] = [
    {
      id: 'note_demo_1',
      lectureId: 'lec_nut_1',
      second: 145,
      text: 'مهم للامتحان: نقص فيتامين B1 يسبب اعتلال فيرنيكه الدماغي (Wernicke)',
      createdAt: now - 7200_000,
    },
    {
      id: 'note_demo_2',
      lectureId: 'lec_nut_1',
      second: 612,
      text: 'مراجعة الفرق بين امتصاص B12 و Intrinsic Factor في المعدة',
      createdAt: now - 3600_000,
    },
  ];

  return {
    user,
    categories,
    lectures,
    exams,
    studentRequests,
    courseRequests,
    admins,
    offlineDownloads,
    notes,
  };
}
