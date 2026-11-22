// Google Drive upload (web) using the OAuth `drive.file` scope captured at Google sign-in.
import { FIREBASE_CONFIG } from '../config';

const API_KEY = FIREBASE_CONFIG.apiKey;
let driveToken = '';

export function setDriveToken(t: string): void {
  driveToken = t;
  try {
    localStorage.setItem('elg_gdrive_token', t);
  } catch {
    /* ignore */
  }
}

export function getDriveToken(): string {
  if (driveToken) return driveToken;
  try {
    return localStorage.getItem('elg_gdrive_token') || '';
  } catch {
    return '';
  }
}

export interface DriveUploadResult {
  fileId: string;
  url: string;
  sizeMb: number;
}

/**
 * Uploads a video file to Google Drive via a resumable upload, makes it
 * publicly readable, and returns a direct media URL usable in a <video> tag.
 */
export async function uploadVideoToDrive(file: File, onProgress?: (pct: number) => void): Promise<DriveUploadResult> {
  const token = getDriveToken();
  if (!token) throw new Error('drive_token_missing');

  // 1) Initiate resumable upload session
  const initRes = await fetch(
    'https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true&fields=id',
    {
      method: 'POST',
      headers: {
        Authorization: 'Bearer ' + token,
        'Content-Type': 'application/json; charset=UTF-8',
        'X-Upload-Content-Type': file.type || 'video/mp4',
        'X-Upload-Content-Length': String(file.size),
      },
      body: JSON.stringify({ name: file.name || `lecture_${Date.now()}.mp4`, mimeType: file.type || 'video/mp4' }),
    },
  );
  if (!initRes.ok) throw new Error('drive_init_' + initRes.status);
  const sessionUrl = initRes.headers.get('Location');
  if (!sessionUrl) throw new Error('drive_no_session');

  onProgress?.(10);

  // 2) Upload the bytes (single PUT; fine for moderate files)
  const upRes = await fetch(sessionUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type || 'video/mp4' },
    body: file,
  });
  if (!upRes.ok) throw new Error('drive_upload_' + upRes.status);
  onProgress?.(90);
  const data = (await upRes.json()) as { id?: string };
  const fileId = data.id;
  if (!fileId) throw new Error('drive_no_id');

  // 3) Make the file publicly readable (anyone with the link)
  try {
    await fetch(`https://www.googleapis.com/drive/v3/files/${fileId}/permissions?supportsAllDrives=true`, {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
      body: JSON.stringify({ role: 'reader', type: 'anyone' }),
    });
  } catch {
    /* ignore permission errors */
  }

  onProgress?.(100);

  const url = `https://www.googleapis.com/drive/v3/files/${fileId}?alt=media&key=${API_KEY}`;
  const sizeMb = Math.max(1, Math.round(file.size / (1024 * 1024)));
  return { fileId, url, sizeMb };
}
