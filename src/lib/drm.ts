// 4KB Header Lock Encryption (SHA-256 XOR) for iOS Safari / Web IndexedDB Offline Vault.
// Encrypts the first 4096 bytes using SHA-256 derived keystream seeded with:
//   ElghalizMedicalVault_AES256_Key_v1|{lectureId}
// Symmetric XOR means encrypt === decrypt. Bytes >= 4096 are untouched (0ms CPU overhead).

import { VAULT_HEADER_LOCK_BYTES, VAULT_KEY_PREFIX } from '../config';

const KEYSTREAM_BLOCKS = VAULT_HEADER_LOCK_BYTES / 32; // 128 * 32 = 4096 bytes
const keystreamCache = new Map<string, Uint8Array>();

function normalizeLectureId(lectureId: string): string {
  return lectureId.trim() || 'default_lecture';
}

async function sha256Bytes(data: Uint8Array): Promise<Uint8Array> {
  const copy = new Uint8Array(data.byteLength);
  copy.set(data);
  const digest = await globalThis.crypto.subtle.digest('SHA-256', copy.buffer);
  return new Uint8Array(digest);
}

/**
 * Generates and caches a 4096-byte SHA-256 keystream derived from:
 * `ElghalizMedicalVault_AES256_Key_v1|{lectureId}`
 */
export async function deriveHeaderKeystream4KB(lectureId: string): Promise<Uint8Array> {
  const cleanId = normalizeLectureId(lectureId);
  const cached = keystreamCache.get(cleanId);
  if (cached) return cached;

  const encoder = new TextEncoder();
  const baseSeed = `${VAULT_KEY_PREFIX}|${cleanId}`;
  const keystream = new Uint8Array(VAULT_HEADER_LOCK_BYTES);

  // Derive 128 x 32-byte blocks = 4096 bytes deterministically via SHA-256
  for (let block = 0; block < KEYSTREAM_BLOCKS; block++) {
    const blockSeed = block === 0 ? baseSeed : `${baseSeed}|${block}`;
    const digest = await sha256Bytes(encoder.encode(blockSeed));
    keystream.set(digest, block * 32);
  }

  keystreamCache.set(cleanId, keystream);
  return keystream;
}

/**
 * Transforms (encrypts or decrypts) only the first 4096 bytes of a Uint8Array in place.
 * Returns a new Uint8Array copy without mutating the caller's input.
 */
export async function transformHeader4KB(
  lectureId: string,
  input: Uint8Array,
): Promise<Uint8Array> {
  const keystream = await deriveHeaderKeystream4KB(lectureId);
  const out = input.slice();
  const limit = Math.min(out.byteLength, VAULT_HEADER_LOCK_BYTES);
  for (let i = 0; i < limit; i++) {
    out[i] = out[i] ^ keystream[i];
  }
  return out;
}

/**
 * Encrypts ONLY the first 4096 bytes of a Blob without loading the rest of the file into RAM.
 * Ideal for iPhone Safari memory limits.
 */
export async function encryptBlobHeader4KB(lectureId: string, rawBlob: Blob): Promise<Blob> {
  const headerLen = Math.min(rawBlob.size, VAULT_HEADER_LOCK_BYTES);
  const headerSlice = rawBlob.slice(0, headerLen);
  const headerBuf = new Uint8Array(await headerSlice.arrayBuffer());
  const lockedHeader = await transformHeader4KB(lectureId, headerBuf);
  const restSlice = rawBlob.slice(headerLen);
  return new Blob([lockedHeader.buffer as ArrayBuffer, restSlice], {
    type: 'application/octet-stream',
  });
}

/**
 * Decrypts ONLY the first 4096 bytes in RAM and returns a playable video Blob.
 * The remaining bytes stay backed by IndexedDB Blob storage (zero full-file RAM copy).
 */
export async function decryptBlobHeader4KBToPlayableBlob(
  lectureId: string,
  encryptedBlob: Blob,
  mimeType = 'video/mp4',
): Promise<Blob> {
  const headerLen = Math.min(encryptedBlob.size, VAULT_HEADER_LOCK_BYTES);
  const headerSlice = encryptedBlob.slice(0, headerLen);
  const headerBuf = new Uint8Array(await headerSlice.arrayBuffer());
  const unlockedHeader = await transformHeader4KB(lectureId, headerBuf);
  const restSlice = encryptedBlob.slice(headerLen);
  return new Blob([unlockedHeader.buffer as ArrayBuffer, restSlice], {
    type: mimeType || 'video/mp4',
  });
}

/**
 * Checks if bytes start with a standard MP4 ISO BMFF box header (ftyp, moov, mdat, etc.).
 */
export function hasPlainMp4Header(bytes: Uint8Array): boolean {
  if (bytes.byteLength < 12) return false;
  const tag = String.fromCharCode(bytes[4], bytes[5], bytes[6], bytes[7]).toLowerCase();
  return ['ftyp', 'moov', 'mdat', 'free', 'wide', 'skip', 'pnot'].includes(tag);
}
