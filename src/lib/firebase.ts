// Live Firebase Web SDK integration connected to the EXACT Android project & named Firestore database:
//   projectId:  gen-lang-client-0295730039
//   databaseId: ai-studio-android-elghaliz-72f133e1-54f8-4875-8f82-71b60492c2b8
//   primaryAdmin: abdallahxh909@gmail.com

import { initializeApp, getApps } from 'firebase/app';
import {
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut,
  type User,
} from 'firebase/auth';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  initializeFirestore,
  getFirestore,
  onSnapshot,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  orderBy,
  type Firestore,
  type Unsubscribe,
  type Timestamp,
} from 'firebase/firestore';
import {
  DEFAULT_MEDICAL_CATEGORIES,
  FIREBASE_CONFIG,
  FIRESTORE_DATABASE_ID,
  PRIMARY_ADMIN_EMAIL,
} from '../config';
import type {
  AdminEntry,
  AuthUser,
  Category,
  CourseRequest,
  ExamItem,
  Lecture,
  StudentRequest,
} from '../types/models';
import {
  emailToSafeAdminDocId,
  evaluateStrictDeviceLock,
  sha256Hex,
} from './logic';

const app = getApps().length > 0 ? getApps()[0] : initializeApp(FIREBASE_CONFIG);
export const auth = getAuth(app);

let dbInstance: Firestore;
try {
  dbInstance = initializeFirestore(app, {}, FIRESTORE_DATABASE_ID);
} catch {
  dbInstance = getFirestore(app, FIRESTORE_DATABASE_ID);
}
export const db = dbInstance;

const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: 'select_account' });

function toEpochMs(val: unknown): number | null {
  if (typeof val === 'number') return val;
  if (val && typeof (val as Timestamp).toMillis === 'function') {
    return (val as Timestamp).toMillis();
  }
  return null;
}

export function mapFirebaseUser(u: User): AuthUser {
  return {
    uid: u.uid,
    email: (u.email ?? '').trim(),
    displayName: (u.displayName ?? u.email?.split('@')[0] ?? 'طالب').trim(),
    photoURL: u.photoURL ?? undefined,
  };
}

export function isPrimaryAdminEmail(email: string | undefined | null): boolean {
  return (email ?? '').trim().toLowerCase() === PRIMARY_ADMIN_EMAIL.toLowerCase();
}

export function subscribeAuthState(cb: (user: AuthUser | null) => void): Unsubscribe {
  return onAuthStateChanged(auth, (u) => {
    cb(u ? mapFirebaseUser(u) : null);
  });
}

export interface RedirectAuthOutcome {
  user: AuthUser | null;
  errorCode?: string;
  errorMessage?: string;
}

export async function consumeRedirectAuthResult(): Promise<RedirectAuthOutcome> {
  try {
    const res = await getRedirectResult(auth);
    return { user: res?.user ? mapFirebaseUser(res.user) : null };
  } catch (err: unknown) {
    const fbErr = err as { code?: string; message?: string };
    return {
      user: null,
      errorCode: fbErr?.code || 'auth/redirect-failed',
      errorMessage: fbErr?.message,
    };
  }
}

function isIosStandalone(): boolean {
  if (typeof window === 'undefined') return false;
  const nav = window.navigator as Navigator & { standalone?: boolean };
  const isIos = /iphone|ipad|ipod/i.test(nav.userAgent);
  const standalone =
    nav.standalone === true || window.matchMedia?.('(display-mode: standalone)').matches;
  return isIos && standalone;
}

/**
 * Signs in with Google via popup; if signInWithPopup fails for any reason,
 * automatically falls back to signInWithRedirect. Any failure preserves error.code.
 */
export async function signInWithGoogle(): Promise<AuthUser | null> {
  if (isIosStandalone()) {
    await signInWithRedirect(auth, googleProvider);
    return null;
  }
  try {
    const res = await signInWithPopup(auth, googleProvider);
    return mapFirebaseUser(res.user);
  } catch (popupErr: unknown) {
    const popupCode = (popupErr as { code?: string })?.code ?? '';
    // Only skip redirect for configuration errors where redirect would leave the page without showing error.code
    if (
      popupCode === 'auth/unauthorized-domain' ||
      popupCode === 'auth/invalid-api-key' ||
      popupCode === 'auth/operation-not-allowed'
    ) {
      throw popupErr;
    }
    // Automatically fall back to signInWithRedirect on any popup failure
    try {
      await signInWithRedirect(auth, googleProvider);
      return null;
    } catch (redirectErr: unknown) {
      throw redirectErr ?? popupErr;
    }
  }
}

export async function signOutFirebase(): Promise<void> {
  await signOut(auth);
}

/**
 * Checks if the signed-in user is the primary admin or a delegated admin,
 * and initializes `/system_state/config` + `/admins/{id}` + default medical courses
 * if signed in as the exclusive primary admin (`abdallahxh909@gmail.com`).
 */
export async function checkAndBootstrapAdminStatus(user: AuthUser): Promise<boolean> {
  const emailLower = user.email.trim().toLowerCase();
  const primary = isPrimaryAdminEmail(emailLower);

  if (primary) {
    try {
      const emailHash = await sha256Hex(emailLower);
      const adminDocId = await emailToSafeAdminDocId(emailLower);

      const sysRef = doc(db, 'system_state', 'config');
      const sysSnap = await getDoc(sysRef);
      if (!sysSnap.exists()) {
        await setDoc(sysRef, {
          primaryAdminUid: user.uid,
          primaryAdminEmail: PRIMARY_ADMIN_EMAIL,
          adminEmailHashes: [emailHash],
          updatedAt: serverTimestamp(),
        });
      } else {
        const existingHashes = (sysSnap.data().adminEmailHashes as string[] | undefined) ?? [];
        const nextHashes = existingHashes.includes(emailHash)
          ? existingHashes
          : [...existingHashes, emailHash];
        await updateDoc(sysRef, {
          primaryAdminUid: user.uid,
          adminEmailHashes: nextHashes,
          updatedAt: serverTimestamp(),
        }).catch(() => {});
      }

      const adminRef = doc(db, 'admins', adminDocId);
      const adminSnap = await getDoc(adminRef);
      if (!adminSnap.exists()) {
        await setDoc(adminRef, {
          email: PRIMARY_ADMIN_EMAIL,
          emailHash,
          roleTitle: 'المدير الرئيسي',
          addedByUid: user.uid,
          isPrimary: true,
          scopeKey: 'admin_only',
          createdAt: serverTimestamp(),
        });
      }

      // Ensure default medical categories exist (idempotent)
      const catsSnap = await getDocs(collection(db, 'categories'));
      if (catsSnap.empty) {
        for (const cat of DEFAULT_MEDICAL_CATEGORIES) {
          await setDoc(doc(db, 'categories', cat.id), {
            name: cat.name,
            description: cat.description,
            createdByUid: user.uid,
            createdByName: user.displayName || 'د. الغليظ',
            visibility: 'published',
            createdAt: serverTimestamp(),
          });
        }
      }
    } catch {
      // Primary admin remains recognized even if bootstrap had a transient network error
    }
    return true;
  }

  // Non-primary: check `/admins/{adm_hash}` or `/system_state/config.adminEmailHashes`
  try {
    const adminDocId = await emailToSafeAdminDocId(emailLower);
    const adminSnap = await getDoc(doc(db, 'admins', adminDocId));
    if (adminSnap.exists()) return true;

    const emailHash = await sha256Hex(emailLower);
    const sysSnap = await getDoc(doc(db, 'system_state', 'config'));
    if (sysSnap.exists()) {
      const hashes = (sysSnap.data().adminEmailHashes as string[] | undefined) ?? [];
      if (hashes.includes(emailHash)) return true;
    }
  } catch {
    /* ignore */
  }
  return false;
}

export interface StudentSyncOutcome {
  status: 'pending' | 'approved' | 'suspended';
  deviceAllowed: boolean;
  lockedDeviceId: string | null;
  registeredDeviceIds: string[];
}

/**
 * Ensures `/student_requests/{uid}` exists and enforces Strict Permanent 1-Device Lock.
 * - First sign-in registers `[currentDeviceId]` (max 1).
 * - Returning sign-in on same device is allowed.
 * - Sign-in on any different device is rejected (permanent lock; no unlink allowed).
 */
export async function syncStudentRequestAndEnforceDeviceLock(
  user: AuthUser,
  currentDeviceId: string,
): Promise<StudentSyncOutcome> {
  const ref = doc(db, 'student_requests', user.uid);
  const snap = await getDoc(ref);

  if (!snap.exists()) {
    await setDoc(ref, {
      userId: user.uid,
      email: user.email,
      displayName: user.displayName,
      status: 'pending',
      scopeKey: 'admin_only',
      registeredDeviceIds: [currentDeviceId],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    return {
      status: 'pending',
      deviceAllowed: true,
      lockedDeviceId: currentDeviceId,
      registeredDeviceIds: [currentDeviceId],
    };
  }

  const data = snap.data();
  const statusRaw = String(data.status ?? 'pending');
  const status: 'pending' | 'approved' | 'suspended' =
    statusRaw === 'approved' || statusRaw === 'suspended' ? statusRaw : 'pending';
  const existingDevices = Array.isArray(data.registeredDeviceIds)
    ? (data.registeredDeviceIds as string[])
    : [];

  const lockEval = evaluateStrictDeviceLock(existingDevices, currentDeviceId);

  if (lockEval.allowed && lockEval.shouldRegisterFirstDevice) {
    // Only allowed by Firestore rules when registeredDeviceIds was previously empty
    await updateDoc(ref, {
      registeredDeviceIds: lockEval.registeredDevices,
      updatedAt: serverTimestamp(),
    }).catch(() => {});
  }

  return {
    status,
    deviceAllowed: lockEval.allowed,
    lockedDeviceId: lockEval.lockedDeviceId,
    registeredDeviceIds: lockEval.registeredDevices,
  };
}

export function subscribeOwnStudentRequest(
  uid: string,
  onUpdate: (req: StudentRequest | null) => void,
): Unsubscribe {
  return onSnapshot(
    doc(db, 'student_requests', uid),
    (snap) => {
      if (!snap.exists()) {
        onUpdate(null);
        return;
      }
      const d = snap.data();
      onUpdate({
        id: snap.id,
        userId: String(d.userId ?? snap.id),
        email: String(d.email ?? ''),
        displayName: String(d.displayName ?? ''),
        status: (d.status as StudentRequest['status']) ?? 'pending',
        scopeKey: String(d.scopeKey ?? 'admin_only'),
        registeredDeviceIds: Array.isArray(d.registeredDeviceIds)
          ? (d.registeredDeviceIds as string[])
          : [],
        createdAt: toEpochMs(d.createdAt),
        updatedAt: toEpochMs(d.updatedAt),
      });
    },
    () => {},
  );
}

export function subscribeCategories(onUpdate: (items: Category[]) => void): Unsubscribe {
  return onSnapshot(
    collection(db, 'categories'),
    (snap) => {
      const items: Category[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          name: String(data.name ?? ''),
          description: String(data.description ?? ''),
          createdByUid: String(data.createdByUid ?? ''),
          createdByName: String(data.createdByName ?? ''),
          visibility: String(data.visibility ?? 'published'),
          createdAt: toEpochMs(data.createdAt),
        };
      });
      onUpdate(items.sort((a, b) => (a.createdAt ?? 0) - (b.createdAt ?? 0)));
    },
    () => {},
  );
}

export function subscribeLectures(onUpdate: (items: Lecture[]) => void): Unsubscribe {
  return onSnapshot(
    collection(db, 'lectures'),
    (snap) => {
      const items: Lecture[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          title: String(data.title ?? ''),
          description: String(data.description ?? ''),
          categoryId: String(data.categoryId ?? ''),
          categoryName: String(data.categoryName ?? ''),
          uploadedByUid: String(data.uploadedByUid ?? ''),
          doctorName: String(data.doctorName ?? ''),
          videoSourceUri: String(data.videoSourceUri ?? ''),
          durationSeconds: Number(data.durationSeconds ?? 0),
          fileSizeMb: Number(data.fileSizeMb ?? 0),
          chunkCount: Number(data.chunkCount ?? 0),
          isRecordedMeeting: Boolean(data.isRecordedMeeting),
          visibility: String(data.visibility ?? 'published'),
          createdAt: toEpochMs(data.createdAt),
        };
      });
      onUpdate(items.sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0)));
    },
    () => {},
  );
}

export function subscribeExams(onUpdate: (items: ExamItem[]) => void): Unsubscribe {
  return onSnapshot(
    collection(db, 'exams'),
    (snap) => {
      const items: ExamItem[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          title: String(data.title ?? ''),
          description: String(data.description ?? ''),
          categoryId: String(data.categoryId ?? ''),
          categoryName: String(data.categoryName ?? ''),
          uploadedByUid: String(data.uploadedByUid ?? ''),
          doctorName: String(data.doctorName ?? ''),
          pdfFileName: String(data.pdfFileName ?? 'exam.pdf'),
          fileSizeKb: Number(data.fileSizeKb ?? 0),
          chunkCount: Number(data.chunkCount ?? 0),
          startTimeMillis: Number(data.startTimeMillis ?? 0),
          endTimeMillis: Number(data.endTimeMillis ?? 0),
          scheduleLabel: String(data.scheduleLabel ?? ''),
          visibility: String(data.visibility ?? 'published'),
          createdAt: toEpochMs(data.createdAt),
        };
      });
      onUpdate(items.sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0)));
    },
    () => {},
  );
}

export function subscribeStudentCourseRequests(
  uid: string,
  onUpdate: (items: CourseRequest[]) => void,
): Unsubscribe {
  const q = query(collection(db, 'course_requests'), where('userId', '==', uid));
  return onSnapshot(
    q,
    (snap) => {
      onUpdate(
        snap.docs.map((d) => {
          const data = d.data();
          return {
            id: d.id,
            userId: String(data.userId ?? ''),
            studentEmail: String(data.studentEmail ?? ''),
            studentName: String(data.studentName ?? ''),
            categoryId: String(data.categoryId ?? ''),
            categoryName: String(data.categoryName ?? ''),
            status: (data.status as CourseRequest['status']) ?? 'pending',
            scopeKey: String(data.scopeKey ?? 'admin_only'),
            createdAt: toEpochMs(data.createdAt),
            updatedAt: toEpochMs(data.updatedAt),
          };
        }),
      );
    },
    () => {},
  );
}

export function subscribeAllCourseRequests(
  onUpdate: (items: CourseRequest[]) => void,
): Unsubscribe {
  return onSnapshot(
    collection(db, 'course_requests'),
    (snap) => {
      const items: CourseRequest[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          userId: String(data.userId ?? ''),
          studentEmail: String(data.studentEmail ?? ''),
          studentName: String(data.studentName ?? ''),
          categoryId: String(data.categoryId ?? ''),
          categoryName: String(data.categoryName ?? ''),
          status: (data.status as CourseRequest['status']) ?? 'pending',
          scopeKey: String(data.scopeKey ?? 'admin_only'),
          createdAt: toEpochMs(data.createdAt),
          updatedAt: toEpochMs(data.updatedAt),
        };
      });
      onUpdate(items.sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0)));
    },
    () => {},
  );
}

export function subscribeAllStudentRequests(
  onUpdate: (items: StudentRequest[]) => void,
): Unsubscribe {
  return onSnapshot(
    collection(db, 'student_requests'),
    (snap) => {
      const items: StudentRequest[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          userId: String(data.userId ?? d.id),
          email: String(data.email ?? ''),
          displayName: String(data.displayName ?? ''),
          status: (data.status as StudentRequest['status']) ?? 'pending',
          scopeKey: String(data.scopeKey ?? 'admin_only'),
          registeredDeviceIds: Array.isArray(data.registeredDeviceIds)
            ? (data.registeredDeviceIds as string[])
            : [],
          createdAt: toEpochMs(data.createdAt),
          updatedAt: toEpochMs(data.updatedAt),
        };
      });
      onUpdate(items.sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0)));
    },
    () => {},
  );
}

export function subscribeAdmins(onUpdate: (items: AdminEntry[]) => void): Unsubscribe {
  return onSnapshot(
    collection(db, 'admins'),
    (snap) => {
      const items: AdminEntry[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          email: String(data.email ?? ''),
          emailHash: String(data.emailHash ?? ''),
          roleTitle: String(data.roleTitle ?? 'مشرف'),
          addedByUid: String(data.addedByUid ?? ''),
          isPrimary: Boolean(data.isPrimary),
          scopeKey: String(data.scopeKey ?? 'admin_only'),
          createdAt: toEpochMs(data.createdAt),
        };
      });
      onUpdate(items.sort((a, b) => Number(b.isPrimary) - Number(a.isPrimary)));
    },
    () => {},
  );
}

// --- Write Operations ---

export async function requestCourseEnrollmentDb(
  user: AuthUser,
  category: Category,
): Promise<void> {
  const docId = `${user.uid}_${category.id}`;
  await setDoc(doc(db, 'course_requests', docId), {
    userId: user.uid,
    studentEmail: user.email,
    studentName: user.displayName,
    categoryId: category.id,
    categoryName: category.name,
    status: 'pending',
    scopeKey: 'admin_only',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

export async function createCategoryDb(
  user: AuthUser,
  name: string,
  description: string,
): Promise<void> {
  const id = `cat_${Date.now()}`;
  await setDoc(doc(db, 'categories', id), {
    name: name.trim(),
    description: description.trim(),
    createdByUid: user.uid,
    createdByName: user.displayName || 'د. الغليظ',
    visibility: 'published',
    createdAt: serverTimestamp(),
  });
}

export async function deleteCategoryDb(categoryId: string): Promise<void> {
  await deleteDoc(doc(db, 'categories', categoryId));
}

export async function createLectureDb(
  user: AuthUser,
  payload: {
    title: string;
    description: string;
    category: Category;
    videoSourceUri: string;
    durationSeconds: number;
    fileSizeMb: number;
  },
): Promise<string> {
  const id = `lec_${Date.now()}`;
  await setDoc(doc(db, 'lectures', id), {
    title: payload.title.trim(),
    description: payload.description.trim(),
    categoryId: payload.category.id,
    categoryName: payload.category.name,
    uploadedByUid: user.uid,
    doctorName: user.displayName || 'د. الغليظ',
    videoSourceUri: payload.videoSourceUri,
    durationSeconds: payload.durationSeconds,
    fileSizeMb: payload.fileSizeMb,
    chunkCount: 1,
    isRecordedMeeting: false,
    visibility: 'published',
    createdAt: serverTimestamp(),
  });
  return id;
}

export async function deleteLectureDb(lectureId: string): Promise<void> {
  await deleteDoc(doc(db, 'lectures', lectureId));
}

export async function updateStudentStatusDb(
  studentUid: string,
  status: 'approved' | 'suspended' | 'pending',
): Promise<void> {
  // Never touch registeredDeviceIds so Firestore Strict 1-Device Lock rule always passes
  await updateDoc(doc(db, 'student_requests', studentUid), {
    status,
    updatedAt: serverTimestamp(),
  });
}

export async function updateCourseRequestStatusDb(
  requestId: string,
  status: 'approved' | 'suspended' | 'pending',
): Promise<void> {
  await updateDoc(doc(db, 'course_requests', requestId), {
    status,
    updatedAt: serverTimestamp(),
  });
}

export async function setStudentCourseAccessDb(
  student: StudentRequest,
  category: Category,
  status: 'approved' | 'suspended',
): Promise<void> {
  const docId = `${student.userId}_${category.id}`;
  await setDoc(
    doc(db, 'course_requests', docId),
    {
      userId: student.userId,
      studentEmail: student.email,
      studentName: student.displayName,
      categoryId: category.id,
      categoryName: category.name,
      status,
      scopeKey: 'admin_only',
      updatedAt: serverTimestamp(),
      createdAt: serverTimestamp(),
    },
    { merge: true },
  );
}

export async function addAdminDb(
  addedByUid: string,
  email: string,
  roleTitle: string,
): Promise<void> {
  const normalized = email.trim().toLowerCase();
  const hash = await sha256Hex(normalized);
  const docId = await emailToSafeAdminDocId(normalized);

  await setDoc(doc(db, 'admins', docId), {
    email: normalized,
    emailHash: hash,
    roleTitle: roleTitle.trim() || 'دكتور / مشرف',
    addedByUid,
    isPrimary: isPrimaryAdminEmail(normalized),
    scopeKey: 'admin_only',
    createdAt: serverTimestamp(),
  });

  const sysRef = doc(db, 'system_state', 'config');
  const sysSnap = await getDoc(sysRef);
  if (sysSnap.exists()) {
    const hashes = (sysSnap.data().adminEmailHashes as string[] | undefined) ?? [];
    if (!hashes.includes(hash)) {
      await updateDoc(sysRef, {
        adminEmailHashes: [...hashes, hash],
        updatedAt: serverTimestamp(),
      });
    }
  }
}

export async function removeAdminDb(admin: AdminEntry): Promise<void> {
  if (admin.isPrimary || isPrimaryAdminEmail(admin.email)) {
    throw new Error('لا يمكن حذف المدير الرئيسي للتطبيق');
  }
  await deleteDoc(doc(db, 'admins', admin.id));

  const sysRef = doc(db, 'system_state', 'config');
  const sysSnap = await getDoc(sysRef);
  if (sysSnap.exists()) {
    const hashes = (sysSnap.data().adminEmailHashes as string[] | undefined) ?? [];
    await updateDoc(sysRef, {
      adminEmailHashes: hashes.filter((h) => h !== admin.emailHash),
      updatedAt: serverTimestamp(),
    });
  }
}

const PDF_CHUNK_CHAR_SIZE = 500_000; // ~375 KB raw per Firestore doc (well under 1MB limit)

function uint8ToBase64(bytes: Uint8Array): string {
  let binary = '';
  const step = 0x8000;
  for (let i = 0; i < bytes.length; i += step) {
    binary += String.fromCharCode(...bytes.subarray(i, i + step));
  }
  return btoa(binary);
}

function base64ToUint8(base64: string): Uint8Array {
  const bin = atob(base64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    out[i] = bin.charCodeAt(i);
  }
  return out;
}

export async function uploadTimedPdfExamDb(
  user: AuthUser,
  payload: {
    title: string;
    description: string;
    category: Category;
    pdfFile: File;
    startTimeMillis: number;
    endTimeMillis: number;
    scheduleLabel: string;
  },
): Promise<string> {
  const examId = `exam_${Date.now()}`;
  const rawBytes = new Uint8Array(await payload.pdfFile.arrayBuffer());
  const fullB64 = uint8ToBase64(rawBytes);

  const chunks: string[] = [];
  for (let offset = 0; offset < fullB64.length; offset += PDF_CHUNK_CHAR_SIZE) {
    chunks.push(fullB64.slice(offset, offset + PDF_CHUNK_CHAR_SIZE));
  }

  await setDoc(doc(db, 'exams', examId), {
    title: payload.title.trim(),
    description: payload.description.trim(),
    categoryId: payload.category.id,
    categoryName: payload.category.name,
    uploadedByUid: user.uid,
    doctorName: user.displayName || 'د. الغليظ',
    pdfFileName: payload.pdfFile.name || 'exam.pdf',
    fileSizeKb: Math.max(1, Math.round(payload.pdfFile.size / 1024)),
    chunkCount: chunks.length,
    startTimeMillis: payload.startTimeMillis,
    endTimeMillis: payload.endTimeMillis,
    scheduleLabel: payload.scheduleLabel,
    visibility: 'published',
    createdAt: serverTimestamp(),
  });

  for (let i = 0; i < chunks.length; i++) {
    await setDoc(doc(db, 'exams', examId, 'pdf_chunks', `chunk_${String(i).padStart(4, '0')}`), {
      index: i,
      categoryId: payload.category.id,
      data: chunks[i],
    });
  }

  return examId;
}

export async function fetchExamPdfBlobDb(examId: string): Promise<Blob> {
  const q = query(collection(db, 'exams', examId, 'pdf_chunks'), orderBy('index', 'asc'));
  const snap = await getDocs(q);
  if (snap.empty) {
    throw new Error('لم يتم العثور على ملف PDF لهذا الاختبار');
  }
  const combinedB64 = snap.docs.map((d) => String(d.data().data ?? '')).join('');
  const bytes = base64ToUint8(combinedB64);
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return new Blob([copy.buffer], { type: 'application/pdf' });
}

export async function deleteExamDb(examId: string): Promise<void> {
  const chunksSnap = await getDocs(collection(db, 'exams', examId, 'pdf_chunks'));
  for (const d of chunksSnap.docs) {
    await deleteDoc(d.ref);
  }
  await deleteDoc(doc(db, 'exams', examId));
}
