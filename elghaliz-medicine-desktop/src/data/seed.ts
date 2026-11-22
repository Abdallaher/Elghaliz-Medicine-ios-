import { DEFAULT_CATEGORIES } from './defaults';
import type {
  AdminEntry,
  Category,
  CourseNotification,
  CourseProgress,
  CourseRequest,
  ExamItem,
  Lecture,
  Meeting,
  MeetingMessage,
  OfflineDownload,
  StudentRequest,
} from './types';

// A single demo video used for playback testing (public sample).
export const DEMO_VIDEO_URL =
  'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4';

export interface DemoUserSeed {
  uid: string;
  email: string;
  displayName: string;
}

export const DEMO_STUDENT: DemoUserSeed = {
  uid: 'demo-student-uid-0001',
  email: 'student@elghaliz.demo',
  displayName: 'طالب تجريبي',
};

// Forced-admin account (same email as firestore rules bootstrap).
export const DEMO_ADMIN: DemoUserSeed = {
  uid: 'demo-admin-uid-0001',
  email: 'abdallahxh909@gmail.com',
  displayName: 'د. عبدالله الغليظ',
};

export interface Dataset {
  categories: Category[];
  lectures: Lecture[];
  exams: ExamItem[];
  studentRequests: StudentRequest[];
  courseRequests: CourseRequest[];
  /** userId -> categoryId -> status */
  enrollments: Record<string, Record<string, 'pending' | 'approved' | 'suspended'>>;
  /** userId -> StudentRequest status (mirror for quick lookup) */
  studentApproval: Record<string, 'pending' | 'approved' | 'suspended'>;
  admins: AdminEntry[];
  downloads: OfflineDownload[];
  progress: Record<string, CourseProgress>; // key: `${userId}:${lectureId}`
  meetings: Meeting[];
  meetingMessages: Record<string, MeetingMessage[]>;
  notifications: CourseNotification[];
}

const now = Date.now();

function lecture(
  id: string,
  title: string,
  categoryId: string,
  categoryName: string,
  doctor: string,
  minutes: number,
  sizeMb: number,
): Lecture {
  return {
    id,
    title,
    description: 'محاضرة مرئية',
    categoryId,
    categoryName,
    uploadedByUid: 'demo-admin-uid-0001',
    doctorName: doctor,
    videoSourceUri: DEMO_VIDEO_URL,
    durationSeconds: minutes * 60,
    fileSizeMb: sizeMb,
    chunkCount: 0,
    isRecordedMeeting: false,
    visibility: 'published',
    createdAt: now - 1000 * 60 * 60 * 24 * 3,
  };
}

export function buildInitialDataset(): Dataset {
  const categories: Category[] = DEFAULT_CATEGORIES.map((c) => ({ ...c }));

  const lectures: Lecture[] = [
    lecture('lec_pharma_1', 'مقدمة في علم الأدوية السريري', 'cat_pharmacology', 'Pharmacology', 'د. عبدالله الغليظ', 42, 320),
    lecture('lec_pharma_2', 'مضادات الالتهاب غير الستيرويدية (NSAIDs)', 'cat_pharmacology', 'Pharmacology', 'د. عبدالله الغليظ', 55, 410),
    lecture('lec_nutrition_1', 'أساسيات التغذية العلاجية', 'cat_nutrition', 'Nutrition', 'د. سارة أحمد', 38, 260),
    lecture('lec_micro_1', 'مقدمة في الأحياء الدقيقة الطبية', 'cat_microbiology', 'Microbiology', 'د. عمر خالد', 47, 350),
  ];

  const exams: ExamItem[] = [
    {
      id: 'exam_pharma_mid',
      title: 'اختبار الفارماكولوجي النصفي',
      description: 'اختبار منتصف الفصل — يشمل أدوية الجهاز العصبي الذاتي.',
      categoryId: 'cat_pharmacology',
      categoryName: 'Pharmacology',
      uploadedByUid: 'demo-admin-uid-0001',
      doctorName: 'د. عبدالله الغليظ',
      pdfFileName: 'pharma_midterm.pdf',
      fileSizeKb: 420,
      chunkCount: 1,
      startTimeMillis: now - 1000 * 60 * 30,
      endTimeMillis: now + 1000 * 60 * 60 * 2,
      scheduleLabel: '',
      visibility: 'published',
      createdAt: now - 1000 * 60 * 60 * 5,
    },
    {
      id: 'exam_nutrition_final',
      title: 'اختبار التغذية النهائي',
      description: 'الاختبار النهائي لكورس التغذية العلاجية.',
      categoryId: 'cat_nutrition',
      categoryName: 'Nutrition',
      uploadedByUid: 'demo-admin-uid-0001',
      doctorName: 'د. سارة أحمد',
      pdfFileName: 'nutrition_final.pdf',
      fileSizeKb: 560,
      chunkCount: 1,
      startTimeMillis: now + 1000 * 60 * 60 * 24,
      endTimeMillis: now + 1000 * 60 * 60 * 26,
      scheduleLabel: '',
      visibility: 'published',
      createdAt: now - 1000 * 60 * 60 * 20,
    },
  ];

  const studentRequests: StudentRequest[] = [
    {
      id: 'sr_1',
      userId: 'sr_1',
      email: 'ahmad.ali@example.com',
      displayName: 'أحمد علي',
      status: 'pending',
      scopeKey: 'global',
      createdAt: now - 1000 * 60 * 60 * 6,
    },
    {
      id: 'sr_2',
      userId: 'sr_2',
      email: 'mona.hassan@example.com',
      displayName: 'منى حسن',
      status: 'pending',
      scopeKey: 'global',
      createdAt: now - 1000 * 60 * 60 * 3,
    },
    {
      id: 'sr_3',
      userId: 'sr_3',
      email: 'khaled.omar@example.com',
      displayName: 'خالد عمر',
      status: 'approved',
      scopeKey: 'global',
      createdAt: now - 1000 * 60 * 60 * 48,
    },
  ];

  const courseRequests: CourseRequest[] = [
    {
      id: 'cr_1',
      userId: 'sr_3',
      studentEmail: 'khaled.omar@example.com',
      studentName: 'خالد عمر',
      categoryId: 'cat_microbiology',
      categoryName: 'Microbiology',
      status: 'pending',
      scopeKey: 'global',
      createdAt: now - 1000 * 60 * 60 * 4,
    },
  ];

  const enrollments: Dataset['enrollments'] = {
    // Demo student: enrolled in Nutrition + Pharmacology, gated on the rest.
    [DEMO_STUDENT.uid]: {
      cat_nutrition: 'approved',
      cat_pharmacology: 'approved',
    },
    sr_3: { cat_microbiology: 'pending' },
  };

  const studentApproval: Dataset['studentApproval'] = {
    [DEMO_STUDENT.uid]: 'approved',
    [DEMO_ADMIN.uid]: 'approved',
    sr_1: 'pending',
    sr_2: 'pending',
    sr_3: 'approved',
  };

  const admins: AdminEntry[] = [
    {
      id: 'adm_primary',
      email: 'abdallahxh909@gmail.com',
      emailHash: '',
      roleTitle: 'المدير الرئيسي',
      addedByUid: 'system',
      isPrimary: true,
      scopeKey: 'global',
      createdAt: now - 1000 * 60 * 60 * 24 * 30,
    },
  ];

  const notifications: CourseNotification[] = [
    {
      id: 'notif_1',
      title: 'محاضرة جديدة في كورس Pharmacology',
      body: 'تمت إضافة محاضرة: مضادات الالتهاب غير الستيرويدية (NSAIDs)',
      categoryId: 'cat_pharmacology',
      categoryName: 'Pharmacology',
      eventType: 'new_lecture',
      targetItemId: 'lec_pharma_2',
      createdByUid: 'demo-admin-uid-0001',
      visibility: 'published',
      createdAt: now - 1000 * 60 * 60 * 2,
    },
  ];

  return {
    categories,
    lectures,
    exams,
    studentRequests,
    courseRequests,
    enrollments,
    studentApproval,
    admins,
    downloads: [],
    progress: {},
    meetings: [],
    meetingMessages: {},
    notifications,
  };
}
