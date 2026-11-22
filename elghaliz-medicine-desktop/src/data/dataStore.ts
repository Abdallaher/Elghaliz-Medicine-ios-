import { create } from 'zustand';
import type {
  AdminEntry,
  Category,
  CourseNotification,
  DownloadTaskState,
  ExamItem,
  Lecture,
  Meeting,
  MeetingMessage,
} from './types';
import { buildInitialDataset, type Dataset } from './seed';
import { DEMO_MODE } from '../config';

// Fire-and-forget live write (no-op in DEMO mode). Firestore is code-split on demand.
function liveWrite(call: (m: typeof import('../lib/liveWrite')) => unknown): void {
  if (DEMO_MODE) return;
  void import('../lib/liveWrite')
    .then((m) => {
      void call(m);
    })
    .catch(() => {});
}

const uid = (prefix: string) =>
  `${prefix}_${Math.random().toString(16).slice(2, 10)}${Math.random().toString(16).slice(2, 10)}`;

// Active simulate-progress timers (module-level, not reactive).
const timers = new Map<string, number>();

const PERSIST_KEY = 'elghaliz.demo.data.v1';

function loadPersistedData(): Dataset | null {
  try {
    if (typeof localStorage === 'undefined') return null;
    const raw = localStorage.getItem(PERSIST_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Dataset;
    if (!parsed || !Array.isArray(parsed.categories)) return null;
    return parsed;
  } catch {
    return null;
  }
}

interface DataState {
  data: Dataset;
  downloadTasks: Record<string, DownloadTaskState>;

  addCategory(name: string, description: string, byName: string, byUid: string): Category;
  deleteCategory(id: string): void;

  addLecture(input: {
    title: string;
    description: string;
    categoryId: string;
    categoryName: string;
    doctorName: string;
    uploadedByUid: string;
    durationSeconds: number;
    fileSizeMb: number;
    videoSourceUri: string;
  }): Lecture;
  deleteLecture(id: string): void;

  addExam(input: {
    title: string;
    description: string;
    categoryId: string;
    categoryName: string;
    doctorName: string;
    uploadedByUid: string;
    pdfFileName: string;
    fileSizeKb: number;
    startTimeMillis: number;
    endTimeMillis: number;
  }): ExamItem;
  deleteExam(id: string): void;

  approveStudent(id: string): void;
  suspendStudent(id: string): void;
  approveAllPending(): number;
  suspendAllPending(): number;

  requestEnrollment(userId: string, categoryId: string, categoryName: string, name: string, email: string): void;
  setEnrollment(userId: string, categoryId: string, status: 'pending' | 'approved' | 'suspended'): void;
  approveCourseRequest(id: string): void;
  rejectCourseRequest(id: string): void;

  addAdmin(email: string, roleTitle: string, addedByUid: string): AdminEntry;
  removeAdmin(id: string): void;

  startDownload(lecture: Lecture, userId: string): void;
  pauseDownload(lectureId: string): void;
  resumeDownload(lectureId: string): void;
  cancelDownload(lectureId: string): void;
  removeDownload(lectureId: string): void;

  saveProgress(userId: string, lectureId: string, seconds: number, completed: boolean): void;

  startMeeting(input: { title: string; hostName: string; hostUid: string; mode: 'camera' | 'slides' }): Meeting;
  joinMeeting(id: string): void;
  endMeeting(id: string): void;
  updateMeeting(id: string, patch: Partial<Meeting>): void;
  sendMeetingMessage(meetingId: string, text: string, senderName: string, senderRole: 'admin' | 'student', isHandRaise?: boolean): void;

  hydrate(patch: Partial<Dataset>): void;

  reset(): void;
}

export const useDataStore = create<DataState>((set, get) => {
  function addNotification(eventType: 'new_lecture' | 'new_exam', categoryId: string, categoryName: string, title: string, body: string, targetId: string, byUid: string) {
    const n: CourseNotification = {
      id: uid('notif'),
      title,
      body,
      categoryId,
      categoryName,
      eventType,
      targetItemId: targetId,
      createdByUid: byUid,
      visibility: 'published',
      createdAt: Date.now(),
    };
    set((s) => ({ data: { ...s.data, notifications: [n, ...s.data.notifications] } }));
  }

  function startTimer(lectureId: string) {
    if (timers.has(lectureId)) return;
    const t = window.setInterval(() => {
      const task = get().downloadTasks[lectureId];
      if (!task || task.status !== 'DOWNLOADING') {
        window.clearInterval(t);
        timers.delete(lectureId);
        return;
      }
      const next = Math.min(100, task.progressPercent + 5 + Math.floor(Math.random() * 6));
      if (next >= 100) {
        window.clearInterval(t);
        timers.delete(lectureId);
        const done = get().data.downloads.find((d) => d.lectureId === lectureId);
        set((s) => {
          const lec = s.data.lectures.find((l) => l.id === lectureId);
          const downloads = done
            ? s.data.downloads
            : lec
              ? [
                  {
                    id: lectureId,
                    userId: '',
                    lectureId,
                    title: lec.title,
                    description: lec.description,
                    categoryName: lec.categoryName,
                    doctorName: lec.doctorName,
                    internalFilePath: `vault://${lectureId}.elgdrm`,
                    durationSeconds: lec.durationSeconds,
                    fileSizeMb: lec.fileSizeMb,
                    createdAt: Date.now(),
                  },
                  ...s.data.downloads,
                ]
              : s.data.downloads;
          return {
            data: { ...s.data, downloads },
            downloadTasks: {
              ...s.downloadTasks,
              [lectureId]: {
                ...task,
                status: 'COMPLETED',
                progressPercent: 100,
                downloadedChunks: task.totalChunks,
                statusLabel: 'تم التنزيل (100%)',
              },
            },
          };
        });
      } else {
        set((s) => ({
          downloadTasks: {
            ...s.downloadTasks,
            [lectureId]: {
              ...task,
              progressPercent: next,
              statusLabel: `جاري التنزيل (${next}%)`,
            },
          },
        }));
      }
    }, 320);
    timers.set(lectureId, t);
  }

  return {
    data: loadPersistedData() ?? buildInitialDataset(),
    downloadTasks: {},

    addCategory(name, description, byName, byUid) {
      const c: Category = {
        id: uid('cat'),
        name: name.trim(),
        description: description.trim() || `مقرر أكاديمي في منصة Elghaliz Medicine`,
        createdByUid: byUid,
        createdByName: byName,
        visibility: 'published',
        createdAt: Date.now(),
      };
      set((s) => ({ data: { ...s.data, categories: [...s.data.categories, c] } }));
      liveWrite((m) => m.addCategory(c));
      return c;
    },

    deleteCategory(id) {
      set((s) => ({
        data: {
          ...s.data,
          categories: s.data.categories.filter((c) => c.id !== id),
          lectures: s.data.lectures.filter((l) => l.categoryId !== id),
          exams: s.data.exams.filter((e) => e.categoryId !== id),
        },
      }));
      liveWrite((m) => m.deleteCategory(id));
    },

    addLecture(input) {
      const l: Lecture = {
        id: uid('lec'),
        title: input.title.trim(),
        description: input.description.trim() || 'محاضرة مرئية',
        categoryId: input.categoryId,
        categoryName: input.categoryName,
        uploadedByUid: input.uploadedByUid,
        doctorName: input.doctorName,
        videoSourceUri: input.videoSourceUri,
        durationSeconds: input.durationSeconds,
        fileSizeMb: input.fileSizeMb,
        chunkCount: 0,
        isRecordedMeeting: false,
        visibility: 'published',
        createdAt: Date.now(),
      };
      set((s) => ({ data: { ...s.data, lectures: [l, ...s.data.lectures] } }));
      addNotification(
        'new_lecture',
        input.categoryId,
        input.categoryName,
        `محاضرة جديدة في ${input.categoryName}`,
        `تم رفع محاضرة "${input.title}" بواسطة ${input.doctorName}`,
        l.id,
        input.uploadedByUid,
      );
      liveWrite((m) => m.addLecture(l));
      return l;
    },

    deleteLecture(id) {
      set((s) => ({
        data: {
          ...s.data,
          lectures: s.data.lectures.filter((l) => l.id !== id),
          downloads: s.data.downloads.filter((d) => d.lectureId !== id),
        },
      }));
      liveWrite((m) => m.deleteLecture(id));
    },

    addExam(input) {
      const e: ExamItem = {
        id: uid('exam'),
        title: input.title.trim(),
        description: input.description.trim() || 'اختبار طبي رسمي بصيغة PDF قابل للتنزيل المباشر خلال وقت الاختبار',
        categoryId: input.categoryId,
        categoryName: input.categoryName,
        uploadedByUid: input.uploadedByUid,
        doctorName: input.doctorName,
        pdfFileName: input.pdfFileName,
        fileSizeKb: input.fileSizeKb,
        chunkCount: 1,
        startTimeMillis: input.startTimeMillis,
        endTimeMillis: input.endTimeMillis,
        scheduleLabel: '',
        visibility: 'published',
        createdAt: Date.now(),
      };
      set((s) => ({ data: { ...s.data, exams: [e, ...s.data.exams] } }));
      addNotification(
        'new_exam',
        input.categoryId,
        input.categoryName,
        `اختبار جديد في ${input.categoryName}`,
        `تم تحديد اختبار "${input.title}"`,
        e.id,
        input.uploadedByUid,
      );
      liveWrite((m) => m.addExam(e));
      return e;
    },

    deleteExam(id) {
      set((s) => ({ data: { ...s.data, exams: s.data.exams.filter((e) => e.id !== id) } }));
      liveWrite((m) => m.deleteExam(id));
    },

    approveStudent(id) {
      liveWrite((m) => m.setStudentStatus(id, 'approved'));
      set((s) => ({
        data: {
          ...s.data,
          studentRequests: s.data.studentRequests.map((r) => (r.id === id ? { ...r, status: 'approved' } : r)),
          studentApproval: { ...s.data.studentApproval, [id]: 'approved' },
        },
      }));
    },

    suspendStudent(id) {
      liveWrite((m) => m.setStudentStatus(id, 'suspended'));
      set((s) => ({
        data: {
          ...s.data,
          studentRequests: s.data.studentRequests.map((r) => (r.id === id ? { ...r, status: 'suspended' } : r)),
          studentApproval: { ...s.data.studentApproval, [id]: 'suspended' },
        },
      }));
    },

    approveAllPending() {
      const pending = get().data.studentRequests.filter((r) => r.status === 'pending');
      set((s) => ({
        data: {
          ...s.data,
          studentRequests: s.data.studentRequests.map((r) => (r.status === 'pending' ? { ...r, status: 'approved' } : r)),
          studentApproval: {
            ...s.data.studentApproval,
            ...Object.fromEntries(pending.map((r) => [r.id, 'approved' as const])),
          },
        },
      }));
      return pending.length;
    },

    suspendAllPending() {
      const pending = get().data.studentRequests.filter((r) => r.status === 'pending');
      set((s) => ({
        data: {
          ...s.data,
          studentRequests: s.data.studentRequests.map((r) => (r.status === 'pending' ? { ...r, status: 'suspended' } : r)),
          studentApproval: {
            ...s.data.studentApproval,
            ...Object.fromEntries(pending.map((r) => [r.id, 'suspended' as const])),
          },
        },
      }));
      return pending.length;
    },

    requestEnrollment(userId, categoryId, categoryName, name, email) {
      const requestId = `${userId}_${categoryId}`;
      liveWrite((m) =>
        m.requestCourseEnrollment({ id: requestId, userId, categoryId, categoryName, studentEmail: email, studentName: name }),
      );
      set((s) => {
        const exists = s.data.courseRequests.some((r) => r.id === requestId);
        const courseRequests = exists
          ? s.data.courseRequests.map((r) => (r.id === requestId ? { ...r, status: 'pending' as const } : r))
          : [
              {
                id: requestId,
                userId,
                studentEmail: email,
                studentName: name,
                categoryId,
                categoryName,
                status: 'pending' as const,
                scopeKey: 'global',
                createdAt: Date.now(),
              },
              ...s.data.courseRequests,
            ];
        const userEnroll = { ...(s.data.enrollments[userId] || {}), [categoryId]: 'pending' as const };
        return {
          data: {
            ...s.data,
            courseRequests,
            enrollments: { ...s.data.enrollments, [userId]: userEnroll },
          },
        };
      });
    },

    setEnrollment(userId, categoryId, status) {
      const requestId = `${userId}_${categoryId}`;
      liveWrite((m) => m.setCourseStatus(requestId, status));
      set((s) => {
        const userEnroll = { ...(s.data.enrollments[userId] || {}), [categoryId]: status };
        const hasReq = s.data.courseRequests.some((r) => r.id === requestId);
        const courseRequests = hasReq
          ? s.data.courseRequests.map((r) => (r.id === requestId ? { ...r, status } : r))
          : s.data.courseRequests;
        return {
          data: {
            ...s.data,
            courseRequests,
            enrollments: { ...s.data.enrollments, [userId]: userEnroll },
          },
        };
      });
    },

    approveCourseRequest(id) {
      const req = get().data.courseRequests.find((r) => r.id === id);
      if (!req) return;
      get().setEnrollment(req.userId, req.categoryId, 'approved');
    },

    rejectCourseRequest(id) {
      const req = get().data.courseRequests.find((r) => r.id === id);
      if (!req) return;
      get().setEnrollment(req.userId, req.categoryId, 'suspended');
    },

    addAdmin(email, roleTitle, addedByUid) {
      const a: AdminEntry = {
        id: uid('adm'),
        email: email.trim(),
        emailHash: '',
        roleTitle: roleTitle.trim() || 'دكتور / مشرف أكاديمي',
        addedByUid,
        isPrimary: false,
        scopeKey: 'global',
        createdAt: Date.now(),
      };
      set((s) => ({ data: { ...s.data, admins: [...s.data.admins, a] } }));
      return a;
    },

    removeAdmin(id) {
      const target = get().data.admins.find((a) => a.id === id);
      if (target?.isPrimary) return;
      set((s) => ({ data: { ...s.data, admins: s.data.admins.filter((a) => a.id !== id) } }));
    },

    startDownload(lecture, userId) {
      const existing = get().data.downloads.find((d) => d.lectureId === lecture.id);
      if (existing) return;
      set((s) => ({
        downloadTasks: {
          ...s.downloadTasks,
          [lecture.id]: {
            lectureId: lecture.id,
            lectureTitle: lecture.title,
            categoryName: lecture.categoryName,
            status: 'DOWNLOADING',
            progressPercent: 0,
            downloadedChunks: 0,
            totalChunks: 10,
            statusLabel: 'جاري التنزيل (0%)',
            retryCount: 0,
          },
        },
        data: { ...s.data, downloads: s.data.downloads.map((d) => d) },
      }));
      // remember userId on the task via a closure is fine; store not needed for demo
      void userId;
      startTimer(lecture.id);
    },

    pauseDownload(lectureId) {
      const t = timers.get(lectureId);
      if (t) {
        window.clearInterval(t);
        timers.delete(lectureId);
      }
      set((s) => {
        const task = s.downloadTasks[lectureId];
        if (!task) return {};
        return {
          downloadTasks: {
            ...s.downloadTasks,
            [lectureId]: { ...task, status: 'PAUSED', statusLabel: `متوقف مؤقتاً (${task.progressPercent}%)` },
          },
        };
      });
    },

    resumeDownload(lectureId) {
      set((s) => {
        const task = s.downloadTasks[lectureId];
        if (!task) return {};
        return {
          downloadTasks: {
            ...s.downloadTasks,
            [lectureId]: { ...task, status: 'DOWNLOADING', statusLabel: `جاري التنزيل (${task.progressPercent}%)` },
          },
        };
      });
      startTimer(lectureId);
    },

    cancelDownload(lectureId) {
      const t = timers.get(lectureId);
      if (t) {
        window.clearInterval(t);
        timers.delete(lectureId);
      }
      set((s) => {
        const next = { ...s.downloadTasks };
        delete next[lectureId];
        return {
          downloadTasks: next,
          data: { ...s.data, downloads: s.data.downloads.filter((d) => d.lectureId !== lectureId) },
        };
      });
    },

    removeDownload(lectureId) {
      set((s) => ({
        data: { ...s.data, downloads: s.data.downloads.filter((d) => d.lectureId !== lectureId) },
      }));
    },

    saveProgress(userId, lectureId, seconds, completed) {
      const key = `${userId}:${lectureId}`;
      set((s) => ({
        data: {
          ...s.data,
          progress: {
            ...s.data.progress,
            [key]: { lectureId, positionSeconds: seconds, completed, updatedAt: Date.now() },
          },
        },
      }));
    },

    startMeeting({ title, hostName, hostUid, mode }) {
      const m: Meeting = {
        id: uid('meet'),
        title: title.trim() || 'بث مباشر لمحاضرة طبية تفاعلية',
        hostUid,
        hostName,
        presentationMode: mode,
        currentSlideIndex: 0,
        studentsMuted: false,
        raisedHandsCount: 0,
        status: 'live',
        isRecording: false,
        isScreenSharing: mode === 'slides',
        participantCount: 1,
        visibility: 'published',
        createdAt: Date.now(),
      };
      set((s) => ({ data: { ...s.data, meetings: [m, ...s.data.meetings] } }));
      return m;
    },

    joinMeeting(id) {
      set((s) => ({
        data: {
          ...s.data,
          meetings: s.data.meetings.map((m) => (m.id === id ? { ...m, participantCount: m.participantCount + 1 } : m)),
        },
      }));
    },

    endMeeting(id) {
      set((s) => ({
        data: {
          ...s.data,
          meetings: s.data.meetings.map((m) =>
            m.id === id ? { ...m, status: 'ended', isRecording: false, isScreenSharing: false } : m,
          ),
        },
      }));
    },

    updateMeeting(id, patch) {
      set((s) => ({
        data: { ...s.data, meetings: s.data.meetings.map((m) => (m.id === id ? { ...m, ...patch } : m)) },
      }));
    },

    sendMeetingMessage(meetingId, text, senderName, senderRole, isHandRaise = false) {
      const msg: MeetingMessage = {
        id: uid('msg'),
        meetingId,
        senderUid: senderRole === 'admin' ? 'host' : 'me',
        senderName,
        senderRole,
        text: text.trim(),
        isHandRaise,
        createdAt: Date.now(),
      };
      set((s) => ({
        data: {
          ...s.data,
          meetingMessages: {
            ...s.data.meetingMessages,
            [meetingId]: [...(s.data.meetingMessages[meetingId] || []), msg],
          },
        },
      }));
    },

    hydrate(patch) {
      set((s) => ({ data: { ...s.data, ...patch } }));
    },

    reset() {
      timers.forEach((t) => window.clearInterval(t));
      timers.clear();
      set({ data: buildInitialDataset(), downloadTasks: {} });
    },
  };
});

// Helper selectors
export function enrollmentStatus(userId: string, categoryId: string): 'none' | 'pending' | 'approved' | 'suspended' {
  const map = useDataStore.getState().data.enrollments[userId];
  return (map?.[categoryId] as never) || 'none';
}

// Persist the demo dataset so downloads / progress / requests survive reloads.
let persistTimer: number | undefined;
if (typeof window !== 'undefined') {
  useDataStore.subscribe((state) => {
    if (persistTimer) window.clearTimeout(persistTimer);
    persistTimer = window.setTimeout(() => {
      try {
        localStorage.setItem(PERSIST_KEY, JSON.stringify(state.data));
      } catch {
        /* ignore quota / privacy-mode errors */
      }
    }, 400);
  });
}
