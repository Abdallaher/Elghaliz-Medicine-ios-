// Smart iOS & Web Offline Encrypted Vault backed by IndexedDB (`ElghalizOfflineVaultDB`).
// Stores lectures with their first 4096 bytes encrypted via SHA-256 XOR (`ElghalizMedicalVault_AES256_Key_v1|{lectureId}`).
// Decrypts ONLY the 4KB header in RAM on playback — zero full-file RAM copy (iOS Safari safe).

import { VAULT_HEADER_LOCK_BYTES } from '../config';
import type { OfflineDownloadRecord } from '../types/models';
import { decryptBlobHeader4KBToPlayableBlob, encryptBlobHeader4KB } from './drm';

const VAULT_DB_NAME = 'ElghalizOfflineVaultDB';
const VAULT_DB_VERSION = 1;
const STORE_META = 'lecture_meta';
const STORE_BLOBS = 'lecture_blobs';

let vaultDbPromise: Promise<IDBDatabase> | null = null;

function openVaultDb(): Promise<IDBDatabase> {
  if (vaultDbPromise) return vaultDbPromise;
  vaultDbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(VAULT_DB_NAME, VAULT_DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_META)) {
        db.createObjectStore(STORE_META, { keyPath: 'lectureId' });
      }
      if (!db.objectStoreNames.contains(STORE_BLOBS)) {
        db.createObjectStore(STORE_BLOBS);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => {
      vaultDbPromise = null;
      reject(req.error);
    };
  });
  return vaultDbPromise;
}

export interface PersistentStorageStatus {
  supported: boolean;
  persisted: boolean;
  usageBytes: number;
  quotaBytes: number;
}

/**
 * Requests persistent browser storage (`navigator.storage.persist()`) so iOS Safari / Chrome
 * won't evict downloaded lectures under storage pressure.
 */
export async function requestPersistentStorage(): Promise<PersistentStorageStatus> {
  const storage = navigator.storage;
  if (!storage) {
    return { supported: false, persisted: false, usageBytes: 0, quotaBytes: 0 };
  }

  let persisted = false;
  try {
    if (typeof storage.persisted === 'function') {
      persisted = await storage.persisted();
    }
    if (!persisted && typeof storage.persist === 'function') {
      persisted = await storage.persist();
    }
  } catch {
    persisted = false;
  }

  let usageBytes = 0;
  let quotaBytes = 0;
  try {
    if (typeof storage.estimate === 'function') {
      const est = await storage.estimate();
      usageBytes = est.usage ?? 0;
      quotaBytes = est.quota ?? 0;
    }
  } catch {
    /* ignore */
  }

  return {
    supported: typeof storage.persist === 'function',
    persisted,
    usageBytes,
    quotaBytes,
  };
}

export async function listOfflineDownloads(userId?: string): Promise<OfflineDownloadRecord[]> {
  try {
    const db = await openVaultDb();
    const all = await new Promise<OfflineDownloadRecord[]>((resolve, reject) => {
      const tx = db.transaction(STORE_META, 'readonly');
      const req = tx.objectStore(STORE_META).getAll();
      req.onsuccess = () => resolve((req.result as OfflineDownloadRecord[]) ?? []);
      req.onerror = () => reject(req.error);
    });
    const filtered = userId ? all.filter((item) => item.userId === userId) : all;
    return filtered.sort((a, b) => b.downloadedAt - a.downloadedAt);
  } catch {
    return [];
  }
}

export async function saveEncryptedLectureToVault(
  record: Omit<OfflineDownloadRecord, 'encryptedHeaderBytes' | 'fileSizeBytes' | 'fileSizeMb' | 'downloadedAt'>,
  rawVideoBlob: Blob,
): Promise<OfflineDownloadRecord> {
  const encryptedBlob = await encryptBlobHeader4KB(record.lectureId, rawVideoBlob);
  const fullRecord: OfflineDownloadRecord = {
    ...record,
    fileSizeBytes: encryptedBlob.size,
    fileSizeMb: Number((encryptedBlob.size / (1024 * 1024)).toFixed(2)),
    mimeType: rawVideoBlob.type || record.mimeType || 'video/mp4',
    downloadedAt: Date.now(),
    encryptedHeaderBytes: Math.min(encryptedBlob.size, VAULT_HEADER_LOCK_BYTES),
  };

  const db = await openVaultDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction([STORE_META, STORE_BLOBS], 'readwrite');
    tx.objectStore(STORE_META).put(fullRecord);
    tx.objectStore(STORE_BLOBS).put(encryptedBlob, fullRecord.lectureId);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error('فشل حفظ المحاضرة المشفرة داخل التطبيق'));
    tx.onabort = () => reject(tx.error ?? new Error('مساحة التخزين غير كافية لحفظ المحاضرة'));
  });

  return fullRecord;
}

/**
 * Reads an encrypted lecture Blob from IndexedDB, decrypts ONLY the first 4096 bytes in RAM,
 * and returns a temporary Object URL suitable for `<video src={...} playsInline />`.
 * Caller must revoke the URL when switching lectures or closing the player.
 */
export async function openDecryptedOfflineLectureUrl(
  lectureId: string,
): Promise<{ objectUrl: string; record: OfflineDownloadRecord } | null> {
  const db = await openVaultDb();

  const record = await new Promise<OfflineDownloadRecord | undefined>((resolve, reject) => {
    const tx = db.transaction(STORE_META, 'readonly');
    const req = tx.objectStore(STORE_META).get(lectureId);
    req.onsuccess = () => resolve(req.result as OfflineDownloadRecord | undefined);
    req.onerror = () => reject(req.error);
  });

  if (!record) return null;

  const encryptedBlob = await new Promise<Blob | undefined>((resolve, reject) => {
    const tx = db.transaction(STORE_BLOBS, 'readonly');
    const req = tx.objectStore(STORE_BLOBS).get(lectureId);
    req.onsuccess = () => resolve(req.result as Blob | undefined);
    req.onerror = () => reject(req.error);
  });

  if (!encryptedBlob) return null;

  const playableBlob = await decryptBlobHeader4KBToPlayableBlob(
    lectureId,
    encryptedBlob,
    record.mimeType || 'video/mp4',
  );
  const objectUrl = URL.createObjectURL(playableBlob);
  return { objectUrl, record };
}

export async function deleteOfflineLecture(lectureId: string): Promise<void> {
  const db = await openVaultDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction([STORE_META, STORE_BLOBS], 'readwrite');
    tx.objectStore(STORE_META).delete(lectureId);
    tx.objectStore(STORE_BLOBS).delete(lectureId);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function clearAllOfflineLectures(userId?: string): Promise<void> {
  const items = await listOfflineDownloads(userId);
  if (items.length === 0) return;
  const db = await openVaultDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction([STORE_META, STORE_BLOBS], 'readwrite');
    const metaStore = tx.objectStore(STORE_META);
    const blobStore = tx.objectStore(STORE_BLOBS);
    for (const item of items) {
      metaStore.delete(item.lectureId);
      blobStore.delete(item.lectureId);
    }
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export function sumVaultBytes(records: OfflineDownloadRecord[]): number {
  return records.reduce((acc, item) => acc + (item.fileSizeBytes || 0), 0);
}
