import { create } from 'zustand';
import type { Unsubscribe } from 'firebase/firestore';
import type {
  AdminEntry,
  AppTab,
  ApprovalStatus,
  AuthUser,
  Category,
  CourseRequest,
  DownloadTaskState,
  ExamItem,
  Lecture,
  OfflineDownloadRecord,
  StudentRequest,
  TimestampedLectureNote,
  ToastMessage,
  UploadTaskState,
  UserRoleMode,
} from '../types/models';
import { getPermanentWebDeviceId } from '../lib/deviceId';
import {
  clearAllOfflineLectures,
  deleteOfflineLecture,
  listOfflineDownloads,
  requestPersistentStorage,
  saveEncryptedLectureToVault,
  type PersistentStorageStatus,
} from '../lib/offlineVault';
import {
  downloadLectureToEncryptedVault,
  uploadVideoResumable2MB,
  warmUpDriveTokenInBackground,
} from '../lib/driveVault';
import {
  addAdminDb,
  checkAndBootstrapAdminStatus,
  consumeRedirectAuthResult,
  createCategoryDb,
  createLectureDb,
  deleteCategoryDb,
  deleteExamDb,
  deleteLectureDb,
  fetchExamPdfBlobDb,
  isPrimaryAdminEmail,
  removeAdminDb,
  requestCourseEnrollmentDb,
  setStudentCourseAccessDb,
  signInWithGoogle,
  signOutFirebase,
  subscribeAdmins,
  subscribeAllCourseRequests,
  subscribeAllStudentRequests,
  subscribeAuthState,
  subscribeCategories,
  subscribeExams,
  subscribeLectures,
  subscribeOwnStudentRequest,
  subscribeStudentCourseRequests,
  syncStudentRequestAndEnforceDeviceLock,
  updateCourseRequestStatusDb,
  updateStudentStatusDb,
  uploadTimedPdfExamDb,
} from '../lib/firebase';
import {
  loadCompletedLectureIds,
  loadPlaybackPositions,
  loadPreferences,
  loadTimestampedNotes,
  markLectureCompletedLocal,
  savePlaybackPosition,
  savePreference,
  saveTimestampedNotes,
} from '../lib/prefs';
import { evaluateStrictDeviceLock } from '../lib/logic';
import { buildSampleMedicalExamPdfBlob, createDemoSeed } from '../lib/demoSeed';

interface TransferFlags {
  paused: boolean;
  cancelled: boolean;
}

const uploadControl: TransferFlags = { paused: false, cancelled: false };
const downloadControls = new Map<string, TransferFlags>();
let liveUnsubs: Unsubscribe[] = [];
let authUnsub: Unsubscribe | null = null;

function clearLiveSubscriptions(): void {
  for (const unsub of liveUnsubs) {
    try {
      unsub();
    } catch {
      /* ignore */
    }
  }
  liveUnsubs = [];
}

function applyThemeToDocument(isDarkMode: boolean, fontScale: number, language: 'ar' | 'en'): void {
  if (typeof document === 'undefined') return;
  const html = document.documentElement;
  html.classList.toggle('dark', isDarkMode);
  html.style.fontSize = `${Math.round(fontScale * 100)}%`;
  html.lang = language;
  html.dir = language === 'ar' ? 'rtl' : 'ltr';
}

export interface AppStoreState {
  // Init & Mode
  initialized: boolean;
  isDemoMode: boolean;
  demoType: 'student' | 'admin' | null;
  authBusy: boolean;
  authError: string | null;

  // Identity & Strict 1-Device Lock
  user: AuthUser | null;
  isAdminUser: boolean;
  activeRole: UserRoleMode;
  approvalStatus: ApprovalStatus;
  currentDeviceId: string;
  isDeviceLocked: boolean;
  lockedDeviceId: string | null;
  registeredDeviceIds: string[];

  // Navigation
  activeTab: AppTab;
  openedCourseId: string | null;
  initialLectureIdForCourse: string | null;

  // Preferences
  isDarkMode: boolean;
  fontScale: number;
  language: 'ar' | 'en';
  wifiOnlyDownload: boolean;
  autoDeleteWatched: boolean;

  // Data Collections
  categories: Category[];
  lectures: Lecture[];
  exams: ExamItem[];
  studentRequests: StudentRequest[];
  courseRequests: CourseRequest[];
  admins: AdminEntry[];

  // Offline Vault & Storage
  offlineDownloads: OfflineDownloadRecord[];
  storageStatus: PersistentStorageStatus;
  downloadTasks: Record<string, DownloadTaskState>;
  uploadTask: UploadTaskState | null;

  // Learning State
  playbackPositions: Record<string, number>;
  completedLectureIds: string[];
  notes: TimestampedLectureNote[];

  // Toasts
  toasts: ToastMessage[];

  // Actions
  bootstrapApp: () => Promise<void>;
  signInGoogle: (preferredRole: UserRoleMode) => Promise<void>;
  signOut: () => Promise<void>;
  refreshApprovalStatus: () => Promise<void>;
  verifyAsAdminNow: () => Promise<void>;
  setActiveRole: (role: UserRoleMode) => void;
  setActiveTab: (tab: AppTab) => void;
  openCoursePlayer: (courseId: string, lectureId?: string | null) => void;
  closeCoursePlayer: () => void;

  // Prefs
  toggleDarkMode: () => void;
  setFontScale: (scale: number) => void;
  setLanguage: (lang: 'ar' | 'en') => void;
  setWifiOnlyDownload: (val: boolean) => void;
  setAutoDeleteWatched: (val: boolean) => void;

  // Student & Course Actions
  requestCourseEnrollment: (category: Category) => Promise<void>;
  createCategory: (name: string, description: string) => Promise<void>;
  deleteCategory: (categoryId: string) => Promise<void>;
  uploadLectureVideo: (payload: {
    file: File;
    title: string;
    description: string;
    category: Category;
    durationSeconds: number;
  }) => Promise<boolean>;
  pauseUpload: () => void;
  resumeUpload: () => void;
  cancelUpload: () => void;
  deleteLecture: (lectureId: string) => Promise<void>;

  // Offline Vault Actions
  refreshOfflineVault: () => Promise<void>;
  startOfflineDownload: (lecture: Lecture) => Promise<void>;
  pauseOfflineDownload: (lectureId: string) => void;
  resumeOfflineDownload: (lectureId: string) => void;
  cancelOfflineDownload: (lectureId: string) => void;
  removeOfflineDownload: (lectureId: string) => Promise<void>;
  clearAllDownloads: () => Promise<void>;

  // Exams Actions
  uploadTimedPdfExam: (payload: {
    title: string;
    description: string;
    category: Category;
    pdfFile: File;
    startTimeMillis: number;
    endTimeMillis: number;
    scheduleLabel: string;
  }) => Promise<void>;
  fetchExamPdfBlob: (exam: ExamItem) => Promise<Blob>;
  deleteExam: (examId: string) => Promise<void>;

  // Admin Actions
  updateStudentStatus: (uid: string, status: 'approved' | 'suspended' | 'pending') => Promise<void>;
  bulkUpdatePendingStudents: (status: 'approved' | 'suspended') => Promise<void>;
  updateCourseRequestStatus: (reqId: string, status: 'approved' | 'suspended') => Promise<void>;
  setStudentCourseAccess: (
    student: StudentRequest,
    category: Category,
    status: 'approved' | 'suspended',
  ) => Promise<void>;
  addAdmin: (email: string, roleTitle: string) => Promise<void>;
  removeAdmin: (admin: AdminEntry) => Promise<void>;

  // Learning Progress & Notes
  saveLectureProgress: (lectureId: string, seconds: number, durationSeconds: number) => Promise<void>;
  addLectureNote: (lectureId: string, second: number, text: string) => void;
  deleteLectureNote: (noteId: string) => void;

  // Toasts
  pushToast: (text: string, tone?: ToastMessage['tone']) => void;
  dismissToast: (id: string) => void;
}

const initialPrefs = loadPreferences();
applyThemeToDocument(initialPrefs.isDarkMode, initialPrefs.fontScale, initialPrefs.language);

export const useAppStore = create<AppStoreState>((set, get) => ({
  initialized: false,
  isDemoMode: false,
  demoType: null,
  authBusy: false,
  authError: null,

  user: null,
  isAdminUser: false,
  activeRole: initialPrefs.preferredRole,
  approvalStatus: 'loading',
  currentDeviceId: '',
  isDeviceLocked: false,
  lockedDeviceId: null,
  registeredDeviceIds: [],

  activeTab: 'LIBRARY',
  openedCourseId: null,
  initialLectureIdForCourse: null,

  isDarkMode: initialPrefs.isDarkMode,
  fontScale: initialPrefs.fontScale,
  language: initialPrefs.language,
  wifiOnlyDownload: initialPrefs.wifiOnlyDownload,
  autoDeleteWatched: initialPrefs.autoDeleteWatched,

  categories: [],
  lectures: [],
  exams: [],
  studentRequests: [],
  courseRequests: [],
  admins: [],

  offlineDownloads: [],
  storageStatus: { supported: false, persisted: false, usageBytes: 0, quotaBytes: 0 },
  downloadTasks: {},
  uploadTask: null,

  playbackPositions: loadPlaybackPositions(),
  completedLectureIds: loadCompletedLectureIds(),
  notes: loadTimestampedNotes(),

  toasts: [],

  pushToast: (text, tone = 'info') => {
    const id = `t_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
    set((s) => ({ toasts: [...s.toasts.slice(-2), { id, text, tone }] }));
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
    }, 3800);
  },

  dismissToast: (id) => {
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
  },

  bootstrapApp: async () => {
    if (get().initialized) return;

    // 1. Warm up 5TB Google Drive OAuth token immediately in the background
    void warmUpDriveTokenInBackground();

    // 2. Load/persist permanent WEB_<uuid> Device ID in both localStorage & IndexedDB
    const deviceId = await getPermanentWebDeviceId();
    const storageStatus = await requestPersistentStorage();
    set({ currentDeviceId: deviceId, storageStatus });

    // 3. Check explicit demo verification query param (?demo=student or ?demo=admin)
    const params =
      typeof window !== 'undefined' ? new URLSearchParams(window.location.search) : null;
    const demoParam = params?.get('demo');
    if (demoParam === 'student' || demoParam === 'admin') {
      const seed = createDemoSeed(demoParam, deviceId);
      const savedVault = await listOfflineDownloads(seed.user.uid);
      const mergedDownloads = savedVault.length > 0 ? savedVault : seed.offlineDownloads;
      set({
        initialized: true,
        isDemoMode: true,
        demoType: demoParam,
        user: seed.user,
        isAdminUser: demoParam === 'admin',
        activeRole: demoParam === 'admin' ? 'ADMIN' : 'STUDENT',
        approvalStatus: 'approved',
        isDeviceLocked: false,
        lockedDeviceId: deviceId,
        registeredDeviceIds: [deviceId],
        categories: seed.categories,
        lectures: seed.lectures,
        exams: seed.exams,
        studentRequests: seed.studentRequests,
        courseRequests: seed.courseRequests,
        admins: seed.admins,
        offlineDownloads: mergedDownloads,
        notes: get().notes.length > 0 ? get().notes : seed.notes,
        playbackPositions:
          Object.keys(get().playbackPositions).length > 0
            ? get().playbackPositions
            : { lec_nut_2: 520 },
        completedLectureIds:
          get().completedLectureIds.length > 0 ? get().completedLectureIds : ['lec_nut_1'],
      });
      return;
    }

    // 4. Consume any pending iOS Safari / fallback redirect auth result
    const redirectOutcome = await consumeRedirectAuthResult();
    if (redirectOutcome.errorCode) {
      const msg = `تعذّر إتمام تسجيل الدخول عبر Google (${redirectOutcome.errorCode})`;
      set({ authError: msg });
      get().pushToast(msg, 'error');
    }

    // 5. Subscribe to real Firebase Auth state
    if (authUnsub) authUnsub();
    authUnsub = subscribeAuthState(async (authUser) => {
      clearLiveSubscriptions();

      if (!authUser) {
        set({
          initialized: true,
          user: null,
          isAdminUser: false,
          approvalStatus: 'loading',
          isDeviceLocked: false,
          lockedDeviceId: null,
          registeredDeviceIds: [],
          openedCourseId: null,
          categories: [],
          lectures: [],
          exams: [],
          studentRequests: [],
          courseRequests: [],
          admins: [],
        });
        return;
      }

      set({ user: authUser, approvalStatus: 'loading', authError: null });

      const offlineList = await listOfflineDownloads(authUser.uid);
      set({ offlineDownloads: offlineList });

      // Check admin status first (primary admin abdallahxh909@gmail.com or delegated admin)
      const isAdmin = await checkAndBootstrapAdminStatus(authUser);
      const preferred = loadPreferences().preferredRole;

      if (isAdmin) {
        const chosenRole: UserRoleMode = preferred === 'STUDENT' ? 'STUDENT' : 'ADMIN';
        set({
          initialized: true,
          isAdminUser: true,
          activeRole: chosenRole,
          approvalStatus: 'approved',
          isDeviceLocked: false,
          lockedDeviceId: deviceId,
          registeredDeviceIds: [deviceId],
        });

        liveUnsubs = [
          subscribeCategories((categories) => set({ categories })),
          subscribeLectures((lectures) => set({ lectures })),
          subscribeExams((exams) => set({ exams })),
          subscribeAllStudentRequests((studentRequests) => set({ studentRequests })),
          subscribeAllCourseRequests((courseRequests) => set({ courseRequests })),
          subscribeAdmins((admins) => set({ admins })),
        ];
        return;
      }

      // Student flow: enforce Strict Permanent 1-Device Lock against /student_requests/{uid}
      try {
        const outcome = await syncStudentRequestAndEnforceDeviceLock(authUser, deviceId);
        set({
          initialized: true,
          isAdminUser: false,
          activeRole: 'STUDENT',
          approvalStatus: outcome.status,
          isDeviceLocked: !outcome.deviceAllowed,
          lockedDeviceId: outcome.lockedDeviceId,
          registeredDeviceIds: outcome.registeredDeviceIds,
        });

        if (!outcome.deviceAllowed) {
          return;
        }

        // Live listener on student's own approval status & device lock
        const ownSub = subscribeOwnStudentRequest(authUser.uid, (req) => {
          if (!req) return;
          const evalLock = evaluateStrictDeviceLock(req.registeredDeviceIds, deviceId);
          const prevStatus = get().approvalStatus;
          set({
            approvalStatus: req.status,
            isDeviceLocked: !evalLock.allowed,
            lockedDeviceId: evalLock.lockedDeviceId,
            registeredDeviceIds: evalLock.registeredDevices,
          });
          if (req.status === 'approved' && prevStatus !== 'approved') {
            // Subscribe to content streams as soon as student is approved
            attachStudentContentListeners(authUser.uid);
          }
        });
        liveUnsubs.push(ownSub);

        if (outcome.status === 'approved') {
          attachStudentContentListeners(authUser.uid);
        }
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'تعذّر التحقق من حالة الحساب';
        set({
          initialized: true,
          approvalStatus: 'pending',
          authError: msg,
        });
      }
    });

    function attachStudentContentListeners(uid: string): void {
      liveUnsubs.push(
        subscribeCategories((categories) => set({ categories })),
        subscribeLectures((lectures) => set({ lectures })),
        subscribeExams((exams) => set({ exams })),
        subscribeStudentCourseRequests(uid, (courseRequests) => set({ courseRequests })),
      );
    }
  },

  signInGoogle: async (preferredRole) => {
    savePreference('preferredRole', preferredRole);
    set({ authBusy: true, authError: null, activeRole: preferredRole });
    try {
      await signInWithGoogle();
    } catch (err) {
      const fbErr = err as { code?: string; message?: string };
      const code = fbErr?.code || 'unknown-error';
      let reason = 'تعذّر تسجيل الدخول عبر Google.';
      if (code === 'auth/unauthorized-domain') {
        reason = 'نطاق الموقع غير مضاف في Authorized Domains داخل Firebase Console.';
      } else if (code === 'auth/popup-closed-by-user') {
        reason = 'تم إغلاق نافذة تسجيل الدخول قبل إتمام العملية.';
      } else if (code === 'auth/network-request-failed') {
        reason = 'تحقق من اتصالك بالإنترنت وحاول مجدداً.';
      }
      const msg = `${reason} (${code})`;
      set({ authError: msg });
      get().pushToast(msg, 'error');
    } finally {
      set({ authBusy: false });
    }
  },

  signOut: async () => {
    clearLiveSubscriptions();
    if (!get().isDemoMode) {
      await signOutFirebase().catch(() => {});
    }
    set({
      user: null,
      isDemoMode: false,
      demoType: null,
      isAdminUser: false,
      approvalStatus: 'loading',
      isDeviceLocked: false,
      lockedDeviceId: null,
      openedCourseId: null,
      activeTab: 'LIBRARY',
      authError: null,
    });
    if (typeof window !== 'undefined' && window.location.search.includes('demo=')) {
      const url = new URL(window.location.href);
      url.searchParams.delete('demo');
      window.history.replaceState({}, '', url.pathname + url.search);
    }
  },

  refreshApprovalStatus: async () => {
    const { user, currentDeviceId, isDemoMode } = get();
    if (!user || isDemoMode) return;
    try {
      const outcome = await syncStudentRequestAndEnforceDeviceLock(user, currentDeviceId);
      set({
        approvalStatus: outcome.status,
        isDeviceLocked: !outcome.deviceAllowed,
        lockedDeviceId: outcome.lockedDeviceId,
        registeredDeviceIds: outcome.registeredDeviceIds,
      });
      if (outcome.status === 'approved') {
        get().pushToast('تم تفعيل حسابك بنجاح ✓', 'success');
      } else if (outcome.status === 'suspended') {
        get().pushToast('تم إيقاف الحساب من قِبل الإدارة', 'error');
      } else {
        get().pushToast('لا يزال الطلب قيد المراجعة من الدكتور', 'info');
      }
    } catch {
      get().pushToast('تعذّر تحديث حالة الحساب، تحقق من الإنترنت', 'error');
    }
  },

  verifyAsAdminNow: async () => {
    const { user, isDemoMode } = get();
    if (!user || isDemoMode) return;
    const isAdmin = await checkAndBootstrapAdminStatus(user);
    if (isAdmin) {
      savePreference('preferredRole', 'ADMIN');
      set({
        isAdminUser: true,
        activeRole: 'ADMIN',
        approvalStatus: 'approved',
        isDeviceLocked: false,
      });
      clearLiveSubscriptions();
      liveUnsubs = [
        subscribeCategories((categories) => set({ categories })),
        subscribeLectures((lectures) => set({ lectures })),
        subscribeExams((exams) => set({ exams })),
        subscribeAllStudentRequests((studentRequests) => set({ studentRequests })),
        subscribeAllCourseRequests((courseRequests) => set({ courseRequests })),
        subscribeAdmins((admins) => set({ admins })),
      ];
      get().pushToast('تم التحقق من صلاحية المدير بنجاح ✓', 'success');
    } else {
      get().pushToast('هذا البريد غير مضاف في قائمة المدراء حالياً', 'warning');
    }
  },

  setActiveRole: (role) => {
    if (role === 'ADMIN' && !get().isAdminUser) return;
    savePreference('preferredRole', role);
    set({ activeRole: role, openedCourseId: null });
  },

  setActiveTab: (tab) => set({ activeTab: tab, openedCourseId: null }),

  openCoursePlayer: (courseId, lectureId = null) =>
    set({
      openedCourseId: courseId,
      initialLectureIdForCourse: lectureId,
      activeTab: 'LIBRARY',
    }),

  closeCoursePlayer: () =>
    set({
      openedCourseId: null,
      initialLectureIdForCourse: null,
    }),

  toggleDarkMode: () => {
    const next = !get().isDarkMode;
    savePreference('isDarkMode', next);
    applyThemeToDocument(next, get().fontScale, get().language);
    set({ isDarkMode: next });
  },

  setFontScale: (scale) => {
    savePreference('fontScale', scale);
    applyThemeToDocument(get().isDarkMode, scale, get().language);
    set({ fontScale: scale });
  },

  setLanguage: (lang) => {
    savePreference('language', lang);
    applyThemeToDocument(get().isDarkMode, get().fontScale, lang);
    set({ language: lang });
  },

  setWifiOnlyDownload: (val) => {
    savePreference('wifiOnlyDownload', val);
    set({ wifiOnlyDownload: val });
  },

  setAutoDeleteWatched: (val) => {
    savePreference('autoDeleteWatched', val);
    set({ autoDeleteWatched: val });
    get().pushToast(
      val
        ? 'سيتم حذف المحاضرات المنزّلة تلقائياً بعد إتمام مشاهدتها لتوفير مساحة الآيفون'
        : 'تم إيقاف الحذف التلقائي بعد المشاهدة',
      'info',
    );
  },

  requestCourseEnrollment: async (category) => {
    const { user, isDemoMode } = get();
    if (!user) return;
    if (isDemoMode) {
      const id = `${user.uid}_${category.id}`;
      set((s) => ({
        courseRequests: [
          ...s.courseRequests.filter((r) => r.id !== id),
          {
            id,
            userId: user.uid,
            studentEmail: user.email,
            studentName: user.displayName,
            categoryId: category.id,
            categoryName: category.name,
            status: 'pending',
            scopeKey: 'admin_only',
            createdAt: Date.now(),
            updatedAt: Date.now(),
          },
        ],
      }));
      get().pushToast(`تم إرسال طلب الانضمام لكورس ${category.name} ✓`, 'success');
      return;
    }

    try {
      await requestCourseEnrollmentDb(user, category);
      get().pushToast(`تم إرسال طلب الانضمام لكورس ${category.name} ✓`, 'success');
    } catch {
      get().pushToast('فشل إرسال طلب الانضمام للكورس', 'error');
    }
  },

  createCategory: async (name, description) => {
    const { user, isDemoMode } = get();
    if (!user || !name.trim()) return;
    if (isDemoMode) {
      const next: Category = {
        id: `cat_${Date.now()}`,
        name: name.trim(),
        description: description.trim(),
        createdByUid: user.uid,
        createdByName: user.displayName,
        visibility: 'published',
        createdAt: Date.now(),
      };
      set((s) => ({ categories: [...s.categories, next] }));
      get().pushToast(`تمت إضافة كورس ${next.name} بنجاح ✓`, 'success');
      return;
    }
    await createCategoryDb(user, name, description);
    get().pushToast(`تمت إضافة كورس ${name.trim()} بنجاح ✓`, 'success');
  },

  deleteCategory: async (categoryId) => {
    if (get().isDemoMode) {
      set((s) => ({ categories: s.categories.filter((c) => c.id !== categoryId) }));
      get().pushToast('تم حذف الكورس ✓', 'success');
      return;
    }
    await deleteCategoryDb(categoryId);
    get().pushToast('تم حذف الكورس ✓', 'success');
  },

  uploadLectureVideo: async ({ file, title, description, category, durationSeconds }) => {
    const { user, isDemoMode } = get();
    if (!user) return false;

    uploadControl.paused = false;
    uploadControl.cancelled = false;

    set({
      uploadTask: {
        lectureTitle: title,
        categoryName: category.name,
        status: 'UPLOADING',
        progressPercent: 0,
        uploadedBytes: 0,
        totalBytes: file.size,
        statusLabel: 'جاري تجهيز الرفع بدفعات 2MB...',
      },
    });

    if (isDemoMode) {
      for (const pct of [25, 60, 100]) {
        if (uploadControl.cancelled) {
          set({ uploadTask: null });
          return false;
        }
        await new Promise((r) => setTimeout(r, 250));
        set((s) => ({
          uploadTask: s.uploadTask
            ? {
                ...s.uploadTask,
                progressPercent: pct,
                uploadedBytes: Math.round((file.size * pct) / 100),
                status: pct === 100 ? 'COMPLETED' : 'UPLOADING',
                statusLabel: pct === 100 ? 'اكتمل الرفع بنجاح ✓' : `جاري الرفع بدفعات 2MB... ${pct}%`,
              }
            : null,
        }));
      }
      const newLec: Lecture = {
        id: `lec_${Date.now()}`,
        title: title.trim(),
        description: description.trim(),
        categoryId: category.id,
        categoryName: category.name,
        uploadedByUid: user.uid,
        doctorName: user.displayName,
        videoSourceUri: URL.createObjectURL(file),
        durationSeconds: durationSeconds || 1800,
        fileSizeMb: Number((file.size / (1024 * 1024)).toFixed(2)),
        chunkCount: 1,
        isRecordedMeeting: false,
        visibility: 'published',
        createdAt: Date.now(),
      };
      set((s) => ({ lectures: [newLec, ...s.lectures] }));
      get().pushToast('تم رفع المحاضرة ونشرها في الكورس ✓', 'success');
      setTimeout(() => set({ uploadTask: null }), 2200);
      return true;
    }

    try {
      const uploaded = await uploadVideoResumable2MB(
        file,
        title,
        {
          isPaused: () => uploadControl.paused,
          isCancelled: () => uploadControl.cancelled,
        },
        {
          onProgress: (uploadedBytes, totalBytes, progressPercent, statusLabel) => {
          set((s) => ({
            uploadTask: s.uploadTask
              ? {
                  ...s.uploadTask,
                  uploadedBytes,
                  totalBytes,
                  progressPercent,
                  statusLabel,
                  status: uploadControl.paused ? 'PAUSED' : 'UPLOADING',
                }
              : null,
          }));
        },
      });

      await createLectureDb(user, {
        title,
        description,
        category,
        videoSourceUri: uploaded.videoSourceUri,
        durationSeconds: durationSeconds || 0,
        fileSizeMb: uploaded.fileSizeMb,
      });

      set((s) => ({
        uploadTask: s.uploadTask
          ? { ...s.uploadTask, status: 'COMPLETED', progressPercent: 100, statusLabel: 'تم النشر بنجاح ✓' }
          : null,
      }));
      get().pushToast(`تم رفع "${title}" إلى مخزن الـ 5TB ونشرها للطلاب ✓`, 'success');
      setTimeout(() => set({ uploadTask: null }), 2500);
      return true;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'فشل رفع المحاضرة';
      if (uploadControl.cancelled) {
        set({ uploadTask: null });
        get().pushToast('تم إلغاء الرفع', 'info');
      } else {
        set((s) => ({
          uploadTask: s.uploadTask
            ? { ...s.uploadTask, status: 'ERROR', errorMessage: message, statusLabel: message }
            : null,
        }));
        get().pushToast(message, 'error');
      }
      return false;
    }
  },

  pauseUpload: () => {
    uploadControl.paused = true;
    set((s) => ({
      uploadTask: s.uploadTask
        ? { ...s.uploadTask, status: 'PAUSED', statusLabel: 'متوقف مؤقتاً (يمكنك الاستئناف)' }
        : null,
    }));
  },

  resumeUpload: () => {
    uploadControl.paused = false;
    set((s) => ({
      uploadTask: s.uploadTask
        ? { ...s.uploadTask, status: 'UPLOADING', statusLabel: 'جاري استئناف الرفع...' }
        : null,
    }));
  },

  cancelUpload: () => {
    uploadControl.cancelled = true;
    uploadControl.paused = false;
    set({ uploadTask: null });
  },

  deleteLecture: async (lectureId) => {
    if (get().isDemoMode) {
      set((s) => ({ lectures: s.lectures.filter((l) => l.id !== lectureId) }));
      get().pushToast('تم حذف المحاضرة ✓', 'success');
      return;
    }
    await deleteLectureDb(lectureId);
    get().pushToast('تم حذف المحاضرة ✓', 'success');
  },

  refreshOfflineVault: async () => {
    const uid = get().user?.uid;
    const [downloads, storageStatus] = await Promise.all([
      listOfflineDownloads(uid),
      requestPersistentStorage(),
    ]);
    set({ offlineDownloads: downloads, storageStatus });
  },

  startOfflineDownload: async (lecture) => {
    const { user, wifiOnlyDownload, isDemoMode } = get();
    if (!user) return;

    // Warn if Wi-Fi only is enabled and Network Information API reports cellular
    const conn = (navigator as Navigator & { connection?: { type?: string } }).connection;
    if (wifiOnlyDownload && conn?.type === 'cellular') {
      get().pushToast('خيار التنزيل عبر Wi-Fi فقط مفعّل في الإعدادات', 'warning');
      return;
    }

    const flags: TransferFlags = { paused: false, cancelled: false };
    downloadControls.set(lecture.id, flags);

    set((s) => ({
      downloadTasks: {
        ...s.downloadTasks,
        [lecture.id]: {
          lectureId: lecture.id,
          lectureTitle: lecture.title,
          categoryName: lecture.categoryName,
          status: 'DOWNLOADING',
          progressPercent: 0,
          downloadedBytes: 0,
          totalBytes: Math.round((lecture.fileSizeMb || 10) * 1024 * 1024),
          statusLabel: 'جاري بدء التنزيل المشفر...',
        },
      },
    }));

    // Request persistent storage proactively before saving large videos
    void requestPersistentStorage().then((st) => set({ storageStatus: st }));

    try {
      let savedRecord: OfflineDownloadRecord;
      if (isDemoMode) {
        // Perform real 4KB Header Lock encryption & IndexedDB save on a sample payload
        const demoPayload = new Uint8Array(128 * 1024);
        demoPayload.set([0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x6d, 0x70, 0x34, 0x32], 0);
        const demoBlob = new Blob([demoPayload], { type: 'video/mp4' });
        for (const pct of [28, 65, 95]) {
          if (flags.cancelled) throw new Error('تم إلغاء التنزيل');
          await new Promise((r) => setTimeout(r, 220));
          set((s) => ({
            downloadTasks: {
              ...s.downloadTasks,
              [lecture.id]: {
                ...s.downloadTasks[lecture.id],
                progressPercent: pct,
                statusLabel: `جاري التنزيل أوفلاين وتشفير 4KB... ${pct}%`,
              },
            },
          }));
        }
        savedRecord = await saveEncryptedLectureToVault(
          {
            lectureId: lecture.id,
            userId: user.uid,
            title: lecture.title,
            description: lecture.description,
            categoryId: lecture.categoryId,
            categoryName: lecture.categoryName,
            doctorName: lecture.doctorName,
            durationSeconds: lecture.durationSeconds,
            mimeType: 'video/mp4',
          },
          demoBlob,
        );
      } else {
        savedRecord = await downloadLectureToEncryptedVault(
          lecture,
          user.uid,
          {
            isPaused: () => flags.paused,
            isCancelled: () => flags.cancelled,
          },
          (downloadedBytes, totalBytes, progressPercent, statusLabel) => {
            set((s) => ({
              downloadTasks: {
                ...s.downloadTasks,
                [lecture.id]: {
                  lectureId: lecture.id,
                  lectureTitle: lecture.title,
                  categoryName: lecture.categoryName,
                  status: flags.paused ? 'PAUSED' : 'DOWNLOADING',
                  progressPercent,
                  downloadedBytes,
                  totalBytes,
                  statusLabel,
                },
              },
            }));
          },
        );
      }

      set((s) => {
        const nextTasks = { ...s.downloadTasks };
        delete nextTasks[lecture.id];
        return {
          downloadTasks: nextTasks,
          offlineDownloads: [
            savedRecord,
            ...s.offlineDownloads.filter((d) => d.lectureId !== lecture.id),
          ],
        };
      });
      get().pushToast('تم تنزيل المحاضرة وتشفيرها داخل التطبيق ✓', 'success');
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'فشل تنزيل المحاضرة';
      set((s) => {
        const nextTasks = { ...s.downloadTasks };
        delete nextTasks[lecture.id];
        return { downloadTasks: nextTasks };
      });
      if (!flags.cancelled) {
        get().pushToast(msg, 'error');
      }
    } finally {
      downloadControls.delete(lecture.id);
    }
  },

  pauseOfflineDownload: (lectureId) => {
    const flags = downloadControls.get(lectureId);
    if (!flags) return;
    flags.paused = true;
    set((s) => ({
      downloadTasks: s.downloadTasks[lectureId]
        ? {
            ...s.downloadTasks,
            [lectureId]: {
              ...s.downloadTasks[lectureId],
              status: 'PAUSED',
              statusLabel: 'متوقف مؤقتاً',
            },
          }
        : s.downloadTasks,
    }));
  },

  resumeOfflineDownload: (lectureId) => {
    const flags = downloadControls.get(lectureId);
    if (!flags) return;
    flags.paused = false;
    set((s) => ({
      downloadTasks: s.downloadTasks[lectureId]
        ? {
            ...s.downloadTasks,
            [lectureId]: {
              ...s.downloadTasks[lectureId],
              status: 'DOWNLOADING',
              statusLabel: 'جاري استئناف التنزيل...',
            },
          }
        : s.downloadTasks,
    }));
  },

  cancelOfflineDownload: (lectureId) => {
    const flags = downloadControls.get(lectureId);
    if (flags) {
      flags.cancelled = true;
      flags.paused = false;
    }
    set((s) => {
      const next = { ...s.downloadTasks };
      delete next[lectureId];
      return { downloadTasks: next };
    });
  },

  removeOfflineDownload: async (lectureId) => {
    await deleteOfflineLecture(lectureId).catch(() => {});
    set((s) => ({
      offlineDownloads: s.offlineDownloads.filter((d) => d.lectureId !== lectureId),
    }));
    get().pushToast('تم حذف المحاضرة من التخزين الداخلي ✓', 'success');
  },

  clearAllDownloads: async () => {
    const uid = get().user?.uid;
    await clearAllOfflineLectures(uid).catch(() => {});
    set({ offlineDownloads: [] });
    get().pushToast('تم تفريغ جميع المحاضرات المنزّلة لتحرير المساحة ✓', 'success');
  },

  uploadTimedPdfExam: async (payload) => {
    const { user, isDemoMode } = get();
    if (!user) return;
    if (isDemoMode) {
      const next: ExamItem = {
        id: `exam_${Date.now()}`,
        title: payload.title.trim(),
        description: payload.description.trim(),
        categoryId: payload.category.id,
        categoryName: payload.category.name,
        uploadedByUid: user.uid,
        doctorName: user.displayName,
        pdfFileName: payload.pdfFile.name || 'exam.pdf',
        fileSizeKb: Math.max(1, Math.round(payload.pdfFile.size / 1024)),
        chunkCount: 1,
        startTimeMillis: payload.startTimeMillis,
        endTimeMillis: payload.endTimeMillis,
        scheduleLabel: payload.scheduleLabel,
        visibility: 'published',
        createdAt: Date.now(),
      };
      set((s) => ({ exams: [next, ...s.exams] }));
      get().pushToast('تم رفع الاختبار وجدولته بنجاح ✓', 'success');
      return;
    }
    await uploadTimedPdfExamDb(user, payload);
    get().pushToast('تم رفع الاختبار PDF وجدولته للطلاب ✓', 'success');
  },

  fetchExamPdfBlob: async (exam) => {
    if (get().isDemoMode) {
      return buildSampleMedicalExamPdfBlob(exam.title);
    }
    return fetchExamPdfBlobDb(exam.id);
  },

  deleteExam: async (examId) => {
    if (get().isDemoMode) {
      set((s) => ({ exams: s.exams.filter((e) => e.id !== examId) }));
      get().pushToast('تم حذف الاختبار ✓', 'success');
      return;
    }
    await deleteExamDb(examId);
    get().pushToast('تم حذف الاختبار ✓', 'success');
  },

  updateStudentStatus: async (uid, status) => {
    if (get().isDemoMode) {
      set((s) => ({
        studentRequests: s.studentRequests.map((st) =>
          st.userId === uid ? { ...st, status, updatedAt: Date.now() } : st,
        ),
      }));
      get().pushToast(
        status === 'approved' ? 'تمت الموافقة على الطالب ✓' : 'تم تحديث حالة الطالب',
        'success',
      );
      return;
    }
    await updateStudentStatusDb(uid, status);
    get().pushToast('تم تحديث حالة الطالب ✓', 'success');
  },

  bulkUpdatePendingStudents: async (status) => {
    const pending = get().studentRequests.filter((s) => s.status === 'pending');
    for (const st of pending) {
      await get().updateStudentStatus(st.userId, status);
    }
  },

  updateCourseRequestStatus: async (reqId, status) => {
    if (get().isDemoMode) {
      set((s) => ({
        courseRequests: s.courseRequests.map((r) =>
          r.id === reqId ? { ...r, status, updatedAt: Date.now() } : r,
        ),
      }));
      get().pushToast(
        status === 'approved' ? 'تمت الموافقة على انضمام الطالب للكورس ✓' : 'تم رفض الطلب',
        'success',
      );
      return;
    }
    await updateCourseRequestStatusDb(reqId, status);
    get().pushToast('تم تحديث طلب الكورس ✓', 'success');
  },

  setStudentCourseAccess: async (student, category, status) => {
    if (get().isDemoMode) {
      const id = `${student.userId}_${category.id}`;
      set((s) => ({
        courseRequests: [
          ...s.courseRequests.filter((r) => r.id !== id),
          {
            id,
            userId: student.userId,
            studentEmail: student.email,
            studentName: student.displayName,
            categoryId: category.id,
            categoryName: category.name,
            status,
            scopeKey: 'admin_only',
            createdAt: Date.now(),
            updatedAt: Date.now(),
          },
        ],
      }));
      get().pushToast(
        status === 'approved'
          ? `تم تفعيل كورس ${category.name} للطالب ✓`
          : `تم إيقاف كورس ${category.name} عن الطالب`,
        'info',
      );
      return;
    }
    await setStudentCourseAccessDb(student, category, status);
    get().pushToast('تم تحديث صلاحية الكورس ✓', 'success');
  },

  addAdmin: async (email, roleTitle) => {
    const { user, isDemoMode } = get();
    if (!user || !email.trim()) return;
    const normalized = email.trim().toLowerCase();
    if (isDemoMode) {
      set((s) => ({
        admins: [
          ...s.admins.filter((a) => a.email.toLowerCase() !== normalized),
          {
            id: `adm_${Date.now()}`,
            email: normalized,
            emailHash: normalized,
            roleTitle: roleTitle.trim() || 'دكتور / مشرف',
            addedByUid: user.uid,
            isPrimary: isPrimaryAdminEmail(normalized),
            scopeKey: 'admin_only',
            createdAt: Date.now(),
          },
        ],
      }));
      get().pushToast(`تمت إضافة ${normalized} إلى قائمة المدراء ✓`, 'success');
      return;
    }
    await addAdminDb(user.uid, normalized, roleTitle);
    get().pushToast(`تمت إضافة ${normalized} إلى قائمة المدراء ✓`, 'success');
  },

  removeAdmin: async (admin) => {
    if (admin.isPrimary || isPrimaryAdminEmail(admin.email)) {
      get().pushToast('لا يمكن حذف المدير الرئيسي للتطبيق', 'error');
      return;
    }
    if (get().isDemoMode) {
      set((s) => ({ admins: s.admins.filter((a) => a.id !== admin.id) }));
      get().pushToast('تم إزالة المشرف ✓', 'success');
      return;
    }
    await removeAdminDb(admin);
    get().pushToast('تم إزالة المشرف ✓', 'success');
  },

  saveLectureProgress: async (lectureId, seconds, durationSeconds) => {
    const nextPositions = savePlaybackPosition(lectureId, seconds);
    let nextCompleted = get().completedLectureIds;
    const ratio = durationSeconds > 0 ? seconds / durationSeconds : 0;

    if (ratio >= 0.9 && !nextCompleted.includes(lectureId)) {
      nextCompleted = markLectureCompletedLocal(lectureId);
      // Auto-delete offline copy after watching if user enabled iOS storage saver
      if (get().autoDeleteWatched) {
        const hasOffline = get().offlineDownloads.some((d) => d.lectureId === lectureId);
        if (hasOffline) {
          await deleteOfflineLecture(lectureId).catch(() => {});
          set((s) => ({
            offlineDownloads: s.offlineDownloads.filter((d) => d.lectureId !== lectureId),
          }));
          get().pushToast('تم إتمام المحاضرة وحذف نسختها المنزّلة تلقائياً لتوفير المساحة ✓', 'info');
        }
      }
    }

    set({
      playbackPositions: nextPositions,
      completedLectureIds: nextCompleted,
    });
  },

  addLectureNote: (lectureId, second, text) => {
    const trimmed = text.trim();
    if (!trimmed) return;
    const nextNote: TimestampedLectureNote = {
      id: `note_${Date.now()}`,
      lectureId,
      second: Math.max(0, Math.floor(second)),
      text: trimmed,
      createdAt: Date.now(),
    };
    const next = [nextNote, ...get().notes];
    saveTimestampedNotes(next);
    set({ notes: next });
    get().pushToast('تم حفظ الملاحظة المرتبطة بالوقت ✓', 'success');
  },

  deleteLectureNote: (noteId) => {
    const next = get().notes.filter((n) => n.id !== noteId);
    saveTimestampedNotes(next);
    set({ notes: next });
  },
}));
