// 5TB Google Drive Vault Bridge via Google Apps Script.
// - Warms up OAuth accessToken in memory on app open (`POST {"action": "getUploadParams"}`).
// - Supports Doctor 2MB Resumable Uploads with Pause/Resume/Cancel + `makePublic`.
// - Supports Student direct streaming (`files/{fileId}?alt=media`) & streaming offline downloads
//   saved into the 4KB-encrypted IndexedDB Vault.

import {
  CENTRAL_APPS_SCRIPT_URL,
  DRIVE_FILES_API_ENDPOINT,
  DRIVE_RESUMABLE_UPLOAD_ENDPOINT,
  FIREBASE_CONFIG,
  RESUMABLE_UPLOAD_CHUNK_BYTES,
} from '../config';
import type { Lecture, OfflineDownloadRecord } from '../types/models';
import { buildDriveStreamUrl, computeResumableUploadRanges, extractDriveFileId } from './logic';
import { saveEncryptedLectureToVault } from './offlineVault';

export interface DriveUploadParams {
  accessToken: string;
  folderId: string;
  fetchedAt: number;
}

const TOKEN_TTL_MS = 45 * 60 * 1000; // 45 minutes safe refresh window
let cachedParams: DriveUploadParams | null = null;
let inflightWarmup: Promise<DriveUploadParams | null> | null = null;

/**
 * Calls the Apps Script Bridge with `text/plain;charset=UTF-8` to avoid CORS preflight
 * rejection on Google Apps Script endpoints (works smoothly on iOS Safari & Desktop).
 */
async function postToAppsScript(payload: Record<string, unknown>): Promise<Record<string, unknown>> {
  const res = await fetch(CENTRAL_APPS_SCRIPT_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'text/plain;charset=UTF-8',
    },
    body: JSON.stringify(payload),
    redirect: 'follow',
  });
  const text = await res.text();
  let json: Record<string, unknown> = {};
  try {
    json = JSON.parse(text) as Record<string, unknown>;
  } catch {
    throw new Error('استجابة غير متوقعة من جسر Google Drive (5TB)');
  }
  if (!res.ok || json.error) {
    throw new Error(String(json.error || `خطأ في الاتصال بمخزن الـ 5TB (${res.status})`));
  }
  return json;
}

export async function fetchDriveUploadParams(forceRefresh = false): Promise<DriveUploadParams> {
  if (!forceRefresh && cachedParams && Date.now() - cachedParams.fetchedAt < TOKEN_TTL_MS) {
    return cachedParams;
  }

  const data = await postToAppsScript({ action: 'getUploadParams' });
  const accessToken = String(data.accessToken ?? data.access_token ?? '').trim();
  const folderId = String(data.folderId ?? data.folder_id ?? '').trim();

  if (!accessToken) {
    throw new Error('لم يُرجع جسر Google Drive رمز الوصول (accessToken)');
  }

  cachedParams = {
    accessToken,
    folderId,
    fetchedAt: Date.now(),
  };
  return cachedParams;
}

/**
 * Warms up the Drive OAuth accessToken silently in the background right when the app opens,
 * storing it in memory so student video playback starts immediately without delay.
 */
export function warmUpDriveTokenInBackground(): Promise<DriveUploadParams | null> {
  if (cachedParams && Date.now() - cachedParams.fetchedAt < TOKEN_TTL_MS) {
    return Promise.resolve(cachedParams);
  }
  if (inflightWarmup) return inflightWarmup;

  inflightWarmup = fetchDriveUploadParams(false)
    .then((params) => params)
    .catch(() => null)
    .finally(() => {
      inflightWarmup = null;
    });
  return inflightWarmup;
}

export function getCachedDriveToken(): string | null {
  if (!cachedParams) return null;
  if (Date.now() - cachedParams.fetchedAt > TOKEN_TTL_MS) return null;
  return cachedParams.accessToken;
}

export async function makeDriveFilePublic(fileId: string): Promise<void> {
  try {
    await postToAppsScript({ action: 'makePublic', fileId });
  } catch {
    // Non-fatal if already shared inside the 5TB folder
  }
}

/**
 * Resolves a playable online video URL for a lecture.
 * For `gdrive://<fileId>` or Google Drive URLs, attaches the warmed-up accessToken
 * (or API key fallback) so iOS Safari `<video playsInline>` streams natively with Range support.
 */
export async function resolveOnlinePlaybackUrl(rawUri: string): Promise<string> {
  const trimmed = rawUri.trim();
  const fileId = extractDriveFileId(trimmed);
  if (!fileId) {
    return trimmed;
  }

  let token = getCachedDriveToken();
  if (!token) {
    const warmed = await warmUpDriveTokenInBackground();
    token = warmed?.accessToken ?? null;
  }
  return buildDriveStreamUrl(fileId, token, FIREBASE_CONFIG.apiKey);
}

export interface TransferControl {
  isPaused: () => boolean;
  isCancelled: () => boolean;
}

async function waitWhilePaused(control: TransferControl): Promise<void> {
  while (control.isPaused() && !control.isCancelled()) {
    await new Promise((r) => setTimeout(r, 200));
  }
}

export interface ResumableUploadCallbacks {
  onProgress: (uploadedBytes: number, totalBytes: number, percent: number, label: string) => void;
}

/**
 * Uploads a doctor's video file directly to the 5TB Google Drive vault using
 * Resumable Upload in 2MB chunks (`RESUMABLE_UPLOAD_CHUNK_BYTES`), with Pause/Resume/Cancel.
 */
export async function uploadVideoResumable2MB(
  file: File,
  lectureTitle: string,
  control: TransferControl,
  callbacks: ResumableUploadCallbacks,
): Promise<{ fileId: string; videoSourceUri: string; fileSizeMb: number }> {
  callbacks.onProgress(0, file.size, 0, 'جاري تهيئة الاتصال بمخزن الـ 5TB...');
  const { accessToken, folderId } = await fetchDriveUploadParams(false);

  if (control.isCancelled()) {
    throw new Error('تم إلغاء الرفع');
  }

  const safeName = `${lectureTitle.trim() || 'lecture'}_${Date.now()}.mp4`;
  const metadata: Record<string, unknown> = {
    name: safeName,
    mimeType: file.type || 'video/mp4',
  };
  if (folderId) {
    metadata.parents = [folderId];
  }

  const initRes = await fetch(DRIVE_RESUMABLE_UPLOAD_ENDPOINT, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json; charset=UTF-8',
      'X-Upload-Content-Type': file.type || 'video/mp4',
      'X-Upload-Content-Length': String(file.size),
    },
    body: JSON.stringify(metadata),
  });

  if (!initRes.ok) {
    const errTxt = await initRes.text().catch(() => '');
    throw new Error(`فشل بدء جلسة الرفع (${initRes.status}): ${errTxt.slice(0, 120)}`);
  }

  const uploadUrl = initRes.headers.get('Location');
  if (!uploadUrl) {
    throw new Error('لم يرد رابط جلسة الرفع القابل للاستئناف (Location)');
  }

  const ranges = computeResumableUploadRanges(file.size, RESUMABLE_UPLOAD_CHUNK_BYTES);
  let uploadedFileId = '';

  for (const range of ranges) {
    await waitWhilePaused(control);
    if (control.isCancelled()) {
      throw new Error('تم إلغاء رفع المحاضرة');
    }

    const chunkBlob = file.slice(range.start, range.end + 1);
    const putRes = await fetch(uploadUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': file.type || 'video/mp4',
        'Content-Range': range.contentRangeHeader,
      },
      body: chunkBlob,
    });

    if (putRes.status === 308) {
      // Resume Incomplete -> chunk accepted
      const uploadedBytes = range.end + 1;
      const pct = Math.min(99, Math.round((uploadedBytes / file.size) * 100));
      callbacks.onProgress(
        uploadedBytes,
        file.size,
        pct,
        `جاري الرفع (دفعة ${range.index + 1}/${ranges.length} - 2MB)... ${pct}%`,
      );
      continue;
    }

    if (putRes.status === 200 || putRes.status === 201) {
      const doneJson = (await putRes.json().catch(() => ({}))) as { id?: string };
      uploadedFileId = doneJson.id ?? '';
      callbacks.onProgress(file.size, file.size, 99, 'جاري تفعيل البث المباشر للطلاب...');
      break;
    }

    const failText = await putRes.text().catch(() => '');
    throw new Error(`فشل رفع الدفعة ${range.index + 1} (${putRes.status}): ${failText.slice(0, 100)}`);
  }

  if (!uploadedFileId) {
    throw new Error('اكتمل الرفع لكن لم يتم استلام معرّف الملف من Google Drive');
  }

  await makeDriveFilePublic(uploadedFileId);
  callbacks.onProgress(file.size, file.size, 100, 'اكتمل الرفع بنجاح ✓');

  return {
    fileId: uploadedFileId,
    videoSourceUri: `gdrive://${uploadedFileId}`,
    fileSizeMb: Number((file.size / (1024 * 1024)).toFixed(2)),
  };
}

/**
 * Streams a lecture video via `fetch`, tracks live progress, encrypts the first 4096 bytes
 * via SHA-256 XOR (`ElghalizMedicalVault_AES256_Key_v1|{lectureId}`), and stores it in IndexedDB.
 */
export async function downloadLectureToEncryptedVault(
  lecture: Lecture,
  userId: string,
  control: TransferControl,
  onProgress: (downloadedBytes: number, totalBytes: number, percent: number, label: string) => void,
): Promise<OfflineDownloadRecord> {
  onProgress(0, 0, 0, 'جاري الاتصال بمخزن المحاضرات...');

  const fileId = extractDriveFileId(lecture.videoSourceUri);
  let res: Response;

  if (fileId) {
    let token = getCachedDriveToken();
    if (!token) {
      const warmed = await warmUpDriveTokenInBackground();
      token = warmed?.accessToken ?? null;
    }

    if (token) {
      res = await fetch(`${DRIVE_FILES_API_ENDPOINT}/${encodeURIComponent(fileId)}?alt=media`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
    } else {
      res = await fetch(buildDriveStreamUrl(fileId, null, FIREBASE_CONFIG.apiKey));
    }
  } else {
    res = await fetch(lecture.videoSourceUri);
  }

  if (!res.ok) {
    throw new Error(`تعذّر تنزيل المحاضرة (${res.status})`);
  }

  const contentType = res.headers.get('content-type') || 'video/mp4';
  const totalBytesHeader = Number(res.headers.get('content-length') || '0');
  const estimatedTotal =
    totalBytesHeader > 0
      ? totalBytesHeader
      : Math.max(1, Math.round((lecture.fileSizeMb || 10) * 1024 * 1024));

  const chunks: Uint8Array[] = [];
  let downloadedBytes = 0;

  if (res.body && typeof res.body.getReader === 'function') {
    const reader = res.body.getReader();
    while (true) {
      await waitWhilePaused(control);
      if (control.isCancelled()) {
        await reader.cancel().catch(() => {});
        throw new Error('تم إلغاء التنزيل');
      }
      const { done, value } = await reader.read();
      if (done) break;
      if (value && value.byteLength > 0) {
        chunks.push(value);
        downloadedBytes += value.byteLength;
        const denom = Math.max(estimatedTotal, downloadedBytes);
        const pct = Math.min(98, Math.round((downloadedBytes / denom) * 100));
        onProgress(downloadedBytes, denom, pct, `جاري التنزيل أوفلاين... ${pct}%`);
      }
    }
  } else {
    const buf = new Uint8Array(await res.arrayBuffer());
    chunks.push(buf);
    downloadedBytes = buf.byteLength;
  }

  if (control.isCancelled()) {
    throw new Error('تم إلغاء التنزيل');
  }

  onProgress(downloadedBytes, downloadedBytes, 99, 'جاري تشفير الترويسة (4KB Header Lock)...');
  const blobParts: BlobPart[] = chunks.map((chunk) => {
    const copy = new Uint8Array(chunk.byteLength);
    copy.set(chunk);
    return copy.buffer;
  });
  const rawBlob = new Blob(blobParts, { type: contentType });

  const saved = await saveEncryptedLectureToVault(
    {
      lectureId: lecture.id,
      userId,
      title: lecture.title,
      description: lecture.description,
      categoryId: lecture.categoryId,
      categoryName: lecture.categoryName,
      doctorName: lecture.doctorName,
      durationSeconds: lecture.durationSeconds,
      mimeType: contentType,
    },
    rawBlob,
  );

  onProgress(saved.fileSizeBytes, saved.fileSizeBytes, 100, 'تم الحفظ المشفر داخل التطبيق ✓');
  return saved;
}
