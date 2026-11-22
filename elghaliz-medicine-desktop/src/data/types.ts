// Domain model types, ported from app/src/main/java/com/example/data/model/MedicalModels.kt
// Timestamps are represented as epoch milliseconds (number | null) for portability.

export type TimestampMs = number | null;

export type UserRoleMode = 'STUDENT' | 'ADMIN';

export type ApprovalStatus = 'pending' | 'approved' | 'suspended';

export type Visibility = 'published' | 'hidden';

export interface SystemState {
  id: string;
  primaryAdminUid: string;
  adminEmailHashes: string[];
  scopeKey: string;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export interface CentralDriveConfig {
  id: string;
  primaryAdminEmail: string;
  primaryAdminUid: string;
  vaultFolderId: string;
  vaultFolderName: string;
  accessToken: string;
  refreshToken: string;
  tokenExpiresAtMillis: number;
  isEnabled: boolean;
  scopeKey: string;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export interface AdminMember {
  id: string;
  userId: string;
  emailHash: string;
  isPrimary: boolean;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export interface AdminEntry {
  id: string;
  email: string;
  emailHash: string;
  roleTitle: string;
  addedByUid: string;
  isPrimary: boolean;
  scopeKey: string;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export interface StudentRequest {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  status: ApprovalStatus;
  scopeKey: string;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export interface CourseRequest {
  id: string;
  userId: string;
  studentEmail: string;
  studentName: string;
  categoryId: string;
  categoryName: string;
  status: ApprovalStatus;
  scopeKey: string;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export interface Category {
  id: string;
  name: string;
  description: string;
  createdByUid: string;
  createdByName: string;
  visibility: Visibility;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
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
  visibility: Visibility;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export interface LectureVideoChunk {
  id: string;
  lectureId: string;
  chunkIndex: number;
  dataBase64: string;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export interface Meeting {
  id: string;
  title: string;
  hostUid: string;
  hostName: string;
  presentationMode: 'camera' | 'slides';
  currentSlideIndex: number;
  studentsMuted: boolean;
  raisedHandsCount: number;
  status: 'live' | 'ended';
  isRecording: boolean;
  isScreenSharing: boolean;
  participantCount: number;
  visibility: Visibility;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export interface MeetingMessage {
  id: string;
  meetingId: string;
  senderUid: string;
  senderName: string;
  senderRole: 'admin' | 'student';
  text: string;
  isHandRaise: boolean;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export interface OfflineDownload {
  id: string;
  userId: string;
  lectureId: string;
  title: string;
  description: string;
  categoryName: string;
  doctorName: string;
  internalFilePath: string;
  durationSeconds: number;
  fileSizeMb: number;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
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
  visibility: Visibility;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export function isExamOpen(exam: ExamItem, now = Date.now()): boolean {
  return now >= exam.startTimeMillis && now <= exam.endTimeMillis;
}
export function isExamUpcoming(exam: ExamItem, now = Date.now()): boolean {
  return now < exam.startTimeMillis;
}
export function isExamExpired(exam: ExamItem, now = Date.now()): boolean {
  return now > exam.endTimeMillis;
}
export function examStatus(exam: ExamItem, now = Date.now()): 'open' | 'upcoming' | 'expired' {
  if (now < exam.startTimeMillis) return 'upcoming';
  if (now > exam.endTimeMillis) return 'expired';
  return 'open';
}

export interface ExamPdfChunk {
  id: string;
  examId: string;
  chunkIndex: number;
  dataBase64: string;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
}

export interface CourseNotification {
  id: string;
  title: string;
  body: string;
  categoryId: string;
  categoryName: string;
  eventType: 'new_lecture' | 'new_exam';
  targetItemId: string;
  createdByUid: string;
  visibility: Visibility;
  createdAt?: TimestampMs;
  updatedAt?: TimestampMs;
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
  downloadedChunks: number;
  totalChunks: number;
  statusLabel: string;
  retryCount: number;
}

export const MAX_VIDEO_FILE_SIZE_MB = 100_000;

// ---- App-level helper types (not persisted) ----
export interface AuthUser {
  uid: string;
  email: string;
  displayName: string;
}

export interface CourseProgress {
  lectureId: string;
  positionSeconds: number;
  completed: boolean;
  updatedAt: number;
}
