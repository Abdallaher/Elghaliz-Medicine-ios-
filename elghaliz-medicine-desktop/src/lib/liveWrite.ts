// Live Firestore WRITE operations (used only in LIVE mode). Fire-and-forget from the store.
import { doc, setDoc, deleteDoc, updateDoc, serverTimestamp, collection, getDocs, query, where } from 'firebase/firestore';
import { fbDb } from '../firebase';
import type { Category, ExamItem, Lecture } from '../data/types';

function db() {
  if (!fbDb) throw new Error('Firestore not initialized');
  return fbDb;
}

export async function addCategory(c: Category): Promise<void> {
  await setDoc(doc(db(), 'categories', c.id), {
    name: c.name,
    description: c.description,
    createdByUid: c.createdByUid,
    createdByName: c.createdByName,
    visibility: c.visibility,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

export async function deleteCategory(id: string): Promise<void> {
  // delete its lectures first, then the category
  const snaps = await getDocs(query(collection(db(), 'lectures'), where('categoryId', '==', id)));
  await Promise.all(snaps.docs.map((d) => deleteDoc(d.ref)));
  await deleteDoc(doc(db(), 'categories', id));
}

export async function addLecture(l: Lecture): Promise<void> {
  await setDoc(doc(db(), 'lectures', l.id), {
    title: l.title,
    description: l.description,
    categoryId: l.categoryId,
    categoryName: l.categoryName,
    uploadedByUid: l.uploadedByUid,
    doctorName: l.doctorName,
    videoSourceUri: l.videoSourceUri,
    durationSeconds: l.durationSeconds,
    fileSizeMb: l.fileSizeMb,
    chunkCount: 0,
    isRecordedMeeting: false,
    visibility: l.visibility,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

export async function deleteLecture(id: string): Promise<void> {
  await deleteDoc(doc(db(), 'lectures', id));
}

export async function addExam(e: ExamItem): Promise<void> {
  await setDoc(doc(db(), 'exams', e.id), {
    title: e.title,
    description: e.description,
    categoryId: e.categoryId,
    categoryName: e.categoryName,
    uploadedByUid: e.uploadedByUid,
    doctorName: e.doctorName,
    pdfFileName: e.pdfFileName,
    fileSizeKb: e.fileSizeKb,
    chunkCount: e.chunkCount,
    startTimeMillis: e.startTimeMillis,
    endTimeMillis: e.endTimeMillis,
    scheduleLabel: e.scheduleLabel,
    visibility: e.visibility,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

export async function deleteExam(id: string): Promise<void> {
  await deleteDoc(doc(db(), 'exams', id));
}

export async function setStudentStatus(uid: string, status: string): Promise<void> {
  await setDoc(doc(db(), 'student_requests', uid), { status, updatedAt: serverTimestamp() }, { merge: true });
}

export async function requestCourseEnrollment(input: {
  id: string;
  userId: string;
  categoryId: string;
  categoryName: string;
  studentEmail: string;
  studentName: string;
}): Promise<void> {
  await setDoc(doc(db(), 'course_requests', input.id), {
    userId: input.userId,
    categoryId: input.categoryId,
    categoryName: input.categoryName,
    studentEmail: input.studentEmail,
    studentName: input.studentName,
    status: 'pending',
    scopeKey: 'global',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

export async function setCourseStatus(requestId: string, status: string): Promise<void> {
  try {
    await updateDoc(doc(db(), 'course_requests', requestId), { status, updatedAt: serverTimestamp() });
  } catch {
    // doc may not exist yet (admin granting without a prior request) — create it minimally
    await setDoc(doc(db(), 'course_requests', requestId), { status, updatedAt: serverTimestamp() }, { merge: true });
  }
}
