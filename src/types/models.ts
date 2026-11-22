export type UserRoleMode = 'STUDENT' | 'ADMIN';
export type ApprovalStatus = 'loading' | 'pending' | 'approved' | 'suspended';
export type AppTab = 'LIBRARY' | 'EXAMS' | 'DOWNLOADS_ADMINS' | 'SETTINGS';

export interface AuthUser {
  uid: string;
  email: string;
  displayName: string;
  photoURL?: string;
}

export interface Category {
  id: string;
  name: string;
  description: string;
  createdByUid: string;
  createdByName: string;
  visibility: string;
  createdAt?: number | null;
}

export interface Lecture {
  id: string;
  title: string;
  description: string;
  categoryId: string;
  categoryName: string;
  uploadedByUid: string;
  doctorName: string;
  videoSourceUri: string;
  durationSeconds: number;
  fileSizeMb: number;
  chunkCount: number;
  isRecordedMeeting: boolean;
  visibility: string;
  createdAt?: number | null;
}

export interface StudentRequest {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  status: 'pending' | 'approved' | 'suspended';
  scopeKey: string;
  registeredDeviceIds: string[];
  createdAt?: number | null;
  updatedAt?: number | null;
}

export interface CourseRequest {
  id: string;
  userId: string;
  studentEmail: string;
  studentName: string;
  categoryId: string;
  categoryName: string;
  status: 'pending' | 'approved' | 'suspended';
  scopeKey: string;
  createdAt?: number | null;
  updatedAt?: number | null;
}

export interface AdminEntry {
  id: string;
  email: string;
  emailHash: string;
  roleTitle: string;
  addedByUid: string;
  isPrimary: boolean;
  scopeKey: string;
  createdAt?: number | null;
}

export interface ExamItem {
  id: string;
  title: string;
  description: string;
  categoryId: string;
  categoryName: string;
  uploadedByUid: string;
  doctorName: string;
  pdfFileName: string;
  fileSizeKb: number;
  chunkCount: number;
  startTimeMillis: number;
  endTimeMillis: number;
  scheduleLabel: string;
  visibility: string;
  createdAt?: number | null;
}

export interface OfflineDownloadRecord {
  lectureId: string;
  userId: string;
  title: string;
  description: string;
  categoryId: string;
  categoryName: string;
  doctorName: string;
  durationSeconds: number;
  fileSizeBytes: number;
  fileSizeMb: number;
  mimeType: string;
  downloadedAt: number;
  encryptedHeaderBytes: number;
}

export type DownloadTaskStatus =
  | 'IDLE'
  | 'DOWNLOADING'
  | 'PAUSED'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export interface DownloadTaskState {
  lectureId: string;
  lectureTitle: string;
  categoryName: string;
  status: DownloadTaskStatus;
  progressPercent: number;
  downloadedBytes: number;
  totalBytes: number;
  statusLabel: string;
}

export type UploadTaskStatus =
  | 'IDLE'
  | 'UPLOADING'
  | 'PAUSED'
  | 'COMPLETED'
  | 'ERROR';

export interface UploadTaskState {
  lectureTitle: string;
  categoryName: string;
  status: UploadTaskStatus;
  progressPercent: number;
  uploadedBytes: number;
  totalBytes: number;
  statusLabel: string;
  errorMessage?: string;
}

export interface TimestampedLectureNote {
  id: string;
  lectureId: string;
  second: number;
  text: string;
  createdAt: number;
}

export interface ToastMessage {
  id: string;
  text: string;
  tone: 'info' | 'success' | 'error' | 'warning';
}
