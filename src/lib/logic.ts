import { DRIVE_FILES_API_ENDPOINT, RESUMABLE_UPLOAD_CHUNK_BYTES } from '../config';
import type { ExamItem } from '../types/models';

export interface DeviceLockEvaluation {
  allowed: boolean;
  shouldRegisterFirstDevice: boolean;
  registeredDevices: string[];
  lockedDeviceId: string | null;
}

/**
 * Strict Permanent 1-Device Lock per Email:
 * - If registeredDeviceIds is empty: register currentDeviceId (max 1) and allow.
 * - If registeredDeviceIds contains currentDeviceId: allow.
 * - Otherwise: permanently reject (no unlink option for student or doctor).
 */
export function evaluateStrictDeviceLock(
  registeredDeviceIds: string[] | undefined | null,
  currentDeviceId: string,
): DeviceLockEvaluation {
  const cleanCurrent = currentDeviceId.trim();
  const cleanExisting = Array.from(
    new Set((registeredDeviceIds ?? []).map((d) => d.trim()).filter(Boolean)),
  );

  if (cleanExisting.length === 0) {
    return {
      allowed: true,
      shouldRegisterFirstDevice: true,
      registeredDevices: cleanCurrent ? [cleanCurrent] : [],
      lockedDeviceId: cleanCurrent || null,
    };
  }

  if (cleanCurrent && cleanExisting.includes(cleanCurrent)) {
    return {
      allowed: true,
      shouldRegisterFirstDevice: false,
      registeredDevices: cleanExisting.slice(0, 1),
      lockedDeviceId: cleanExisting[0],
    };
  }

  return {
    allowed: false,
    shouldRegisterFirstDevice: false,
    registeredDevices: cleanExisting.slice(0, 1),
    lockedDeviceId: cleanExisting[0],
  };
}

/**
 * Extracts a Google Drive File ID from gdrive://, Drive share URLs, or Drive API URLs.
 */
export function extractDriveFileId(rawUri: string | undefined | null): string | null {
  const trimmed = (rawUri ?? '').trim();
  if (!trimmed) return null;

  if (trimmed.startsWith('gdrive://')) {
    const id = trimmed.slice('gdrive://'.length).split(/[/?#]/)[0]?.trim();
    return id || null;
  }

  const filePathMatch = trimmed.match(/\/file\/d\/([a-zA-Z0-9_-]{10,})/);
  if (filePathMatch?.[1]) return filePathMatch[1];

  const apiMatch = trimmed.match(/\/drive\/v3\/files\/([a-zA-Z0-9_-]{10,})/);
  if (apiMatch?.[1]) return apiMatch[1];

  const idQueryMatch = trimmed.match(/[?&]id=([a-zA-Z0-9_-]{10,})/);
  if (idQueryMatch?.[1]) return idQueryMatch[1];

  return null;
}

/**
 * Builds the direct Google Drive streaming URL for a given file ID.
 * Uses warmed-up OAuth access_token when available for instant playback on iOS Safari,
 * with API key fallback for publicly shared files.
 */
export function buildDriveStreamUrl(
  fileId: string,
  accessToken?: string | null,
  apiKey?: string | null,
): string {
  const cleanId = encodeURIComponent(fileId.trim());
  const cleanToken = (accessToken ?? '').trim();
  if (cleanToken) {
    return `${DRIVE_FILES_API_ENDPOINT}/${cleanId}?alt=media&access_token=${encodeURIComponent(cleanToken)}`;
  }
  const cleanKey = (apiKey ?? '').trim();
  if (cleanKey) {
    return `${DRIVE_FILES_API_ENDPOINT}/${cleanId}?alt=media&key=${encodeURIComponent(cleanKey)}`;
  }
  return `${DRIVE_FILES_API_ENDPOINT}/${cleanId}?alt=media`;
}

export interface UploadChunkRange {
  index: number;
  start: number;
  end: number; // inclusive
  total: number;
  contentRangeHeader: string;
}

/**
 * Computes Google Drive Resumable Upload 2MB chunk ranges.
 */
export function computeResumableUploadRanges(
  totalBytes: number,
  chunkSizeBytes = RESUMABLE_UPLOAD_CHUNK_BYTES,
): UploadChunkRange[] {
  if (totalBytes <= 0) return [];
  const step = Math.max(256 * 1024, chunkSizeBytes);
  const ranges: UploadChunkRange[] = [];
  let start = 0;
  let index = 0;
  while (start < totalBytes) {
    const end = Math.min(start + step - 1, totalBytes - 1);
    ranges.push({
      index,
      start,
      end,
      total: totalBytes,
      contentRangeHeader: `bytes ${start}-${end}/${totalBytes}`,
    });
    start = end + 1;
    index++;
  }
  return ranges;
}

export type ExamTimingStatus = 'upcoming' | 'open' | 'expired';

export function getExamTimingStatus(
  exam: Pick<ExamItem, 'startTimeMillis' | 'endTimeMillis'>,
  nowMs = Date.now(),
): ExamTimingStatus {
  if (nowMs < exam.startTimeMillis) return 'upcoming';
  if (nowMs > exam.endTimeMillis) return 'expired';
  return 'open';
}

/**
 * Filters exams by category and auto-hides expired exams for students (keeps visible for admins).
 */
export function filterVisibleExams(
  exams: ExamItem[],
  selectedCategoryId: string | null,
  isAdminMode: boolean,
  nowMs = Date.now(),
): ExamItem[] {
  const byCategory = selectedCategoryId
    ? exams.filter((e) => e.categoryId === selectedCategoryId)
    : exams;
  if (isAdminMode) return byCategory;
  return byCategory.filter((e) => getExamTimingStatus(e, nowMs) !== 'expired');
}

/**
 * Formats an epoch timestamp in 12-hour Arabic format with ص / م matching Android ExamsScreen.
 */
export function formatDateTime12HrArabic(epochMs: number): string {
  if (!epochMs || Number.isNaN(epochMs)) return 'غير محدد';
  const d = new Date(epochMs);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hours24 = d.getHours();
  const minutes = String(d.getMinutes()).padStart(2, '0');
  const isPm = hours24 >= 12;
  const hours12 = hours24 % 12 === 0 ? 12 : hours24 % 12;
  const period = isPm ? 'م' : 'ص';
  return `${year}/${month}/${day} - ${String(hours12).padStart(2, '0')}:${minutes} ${period}`;
}

export function formatCountdownSeconds(remainingSeconds: number): string {
  const safe = Math.max(0, Math.floor(remainingSeconds));
  const hrs = Math.floor(safe / 3600);
  const mins = Math.floor((safe % 3600) / 60);
  const secs = safe % 60;
  return `${String(hrs).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

export function formatDurationHms(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hrs = Math.floor(safe / 3600);
  const mins = Math.floor((safe % 3600) / 60);
  const secs = safe % 60;
  if (hrs > 0) {
    return `${String(hrs).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

export function formatLectureCountAr(count: number): string {
  if (count <= 0) return 'لا توجد محاضرات بعد';
  if (count === 1) return 'محاضرة واحدة';
  if (count === 2) return 'محاضرتان';
  if (count <= 10) return `${count} محاضرات`;
  return `${count} محاضرة`;
}

export function formatUniqueUserId(uid: string): string {
  const raw = (uid ?? '').trim();
  if (raw.length >= 10) {
    return `ELG-${raw.slice(0, 5).toUpperCase()}-${raw.slice(-5).toUpperCase()}`;
  }
  if (raw) {
    return `ELG-${raw.toUpperCase()}`;
  }
  return 'ELG-GUEST-0000';
}

export async function sha256Hex(rawEmail: string): Promise<string> {
  const normalized = rawEmail.trim().toLowerCase();
  const bytes = new TextEncoder().encode(normalized);
  const digest = await globalThis.crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export async function emailToSafeAdminDocId(rawEmail: string): Promise<string> {
  const hash = await sha256Hex(rawEmail);
  return `adm_${hash.slice(0, 28)}`;
}

export function formatBytesToMb(bytes: number): string {
  if (!bytes || bytes <= 0) return '0.0';
  return (bytes / (1024 * 1024)).toFixed(1);
}
