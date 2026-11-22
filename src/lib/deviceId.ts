// Persistent Browser Device ID (`WEB_<uuid>`) stored redundantly in BOTH localStorage and IndexedDB.
// Guarantees Strict Permanent 1-Device Lock stability across iOS Safari & desktop sessions.

const LS_DEVICE_KEY = 'elg_web_permanent_device_id';
const IDB_NAME = 'ElghalizDeviceLockDB';
const IDB_STORE = 'device_meta';
const IDB_KEY = 'permanent_device_id';

let cachedDeviceId: string | null = null;

function generateUuid(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  globalThis.crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function openDeviceDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(IDB_NAME, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(IDB_STORE)) {
        db.createObjectStore(IDB_STORE);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function readFromIdb(): Promise<string | null> {
  try {
    const db = await openDeviceDb();
    return await new Promise((resolve) => {
      const tx = db.transaction(IDB_STORE, 'readonly');
      const req = tx.objectStore(IDB_STORE).get(IDB_KEY);
      req.onsuccess = () => resolve(typeof req.result === 'string' ? req.result : null);
      req.onerror = () => resolve(null);
    });
  } catch {
    return null;
  }
}

async function writeToIdb(deviceId: string): Promise<void> {
  try {
    const db = await openDeviceDb();
    await new Promise<void>((resolve) => {
      const tx = db.transaction(IDB_STORE, 'readwrite');
      tx.objectStore(IDB_STORE).put(deviceId, IDB_KEY);
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
    });
  } catch {
    /* ignore */
  }
}

function readFromLocalStorage(): string | null {
  try {
    return localStorage.getItem(LS_DEVICE_KEY);
  } catch {
    return null;
  }
}

function writeToLocalStorage(deviceId: string): void {
  try {
    localStorage.setItem(LS_DEVICE_KEY, deviceId);
  } catch {
    /* ignore */
  }
}

/**
 * Returns the permanent browser Device ID (`WEB_<uuid>`), healing localStorage or IndexedDB
 * automatically if either storage tier was cleared.
 */
export async function getPermanentWebDeviceId(): Promise<string> {
  if (cachedDeviceId) return cachedDeviceId;

  const lsId = readFromLocalStorage()?.trim() || null;
  const idbId = (await readFromIdb())?.trim() || null;

  let resolved = lsId || idbId;
  if (!resolved || !resolved.startsWith('WEB_')) {
    resolved = `WEB_${generateUuid()}`;
  }

  // Replicate to both localStorage and IndexedDB for persistence
  if (lsId !== resolved) writeToLocalStorage(resolved);
  if (idbId !== resolved) await writeToIdb(resolved);

  cachedDeviceId = resolved;
  return resolved;
}
