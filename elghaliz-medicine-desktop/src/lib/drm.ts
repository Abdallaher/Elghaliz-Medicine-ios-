// Faithful port of EncryptedVideoVault (Android/Kotlin) — the offline "vault" DRM.
//
// Scheme (identical to the original): the first 64 bytes (MP4 ftyp/moov header) are
// XOR-locked with a per-lecture 256-bit keystream derived from SHA-256, making the file
// unplayable outside the app. Encoding == decoding (XOR is symmetric). Only the first
// HEADER_LOCK_BYTES bytes are touched; the rest of the (potentially huge) file is never copied.

export const VAULT_SECRET_SALT = 'Elghaliz_Medicine_DRM_AES256_Vault_2026_Secret';
const KEYSTREAM_TABLE_BLOCKS = 128; // 128 * 32 bytes = 4096 bytes
const KEYSTREAM_TABLE_SIZE = KEYSTREAM_TABLE_BLOCKS * 32;
export const HEADER_LOCK_BYTES = 64;

const tableCache = new Map<string, Uint8Array>();

function normalizeId(lectureId: string): string {
  return lectureId.trim() || 'default_lecture';
}

async function sha256(bytes: Uint8Array): Promise<Uint8Array> {
  const digest = await crypto.subtle.digest('SHA-256', bytes as unknown as ArrayBuffer);
  return new Uint8Array(digest);
}

// Builds (and caches) the 4096-byte keystream table for a lecture id.
export async function getKeyStreamTable(lectureId: string): Promise<Uint8Array> {
  const clean = normalizeId(lectureId);
  const cached = tableCache.get(clean);
  if (cached) return cached;
  const table = new Uint8Array(KEYSTREAM_TABLE_SIZE);
  const enc = new TextEncoder();
  for (let block = 0; block < KEYSTREAM_TABLE_BLOCKS; block++) {
    const seed = enc.encode(`${VAULT_SECRET_SALT}|${clean}|KEYSTREAM_BLOCK_${block}`);
    const digest = await sha256(seed);
    table.set(digest, block * 32);
  }
  tableCache.set(clean, table);
  return table;
}

// Pure transform, mirrors Kotlin transformBytesAtOffset. Only bytes with absolute
// index < HEADER_LOCK_BYTES are modified.
export function transformBytesAtOffset(
  table: Uint8Array,
  buffer: Uint8Array,
  bufferOffset: number,
  length: number,
  startByteIndex: number,
): void {
  if (length <= 0 || startByteIndex >= HEADER_LOCK_BYTES) return;
  const maxTransform = Math.min(length, HEADER_LOCK_BYTES - startByteIndex);
  for (let i = 0; i < maxTransform; i++) {
    const byteIndex = startByteIndex + i;
    const tableIdx = byteIndex & 0xfff;
    const tableByte = table[tableIdx];
    // positionSalt = (((byteIndex >>> 12) * 73) xor (byteIndex * 31) xor 0x5A) as Int32
    const positionSalt = ((Math.floor(byteIndex / 4096) * 73) ^ (byteIndex * 31) ^ 0x5a) >>> 0;
    const keyStreamByte = (tableByte ^ positionSalt) & 0xff;
    buffer[bufferOffset + i] = buffer[bufferOffset + i] ^ keyStreamByte;
  }
}

// Encrypt (lock) the header bytes. Returns a transformed copy.
export async function encryptHeader(lectureId: string, bytes: Uint8Array): Promise<Uint8Array> {
  const table = await getKeyStreamTable(lectureId);
  const copy = bytes.slice();
  transformBytesAtOffset(table, copy, 0, copy.length, 0);
  return copy;
}

// Decrypt (unlock) — identical to encrypt because XOR is symmetric.
export const decryptHeader = encryptHeader;

// Convenience: transform an already-built table over a buffer in place.
export function transformWithCachedTable(lectureId: string, table: Uint8Array, buffer: Uint8Array, startByteIndex = 0): void {
  void lectureId;
  transformBytesAtOffset(table, buffer, 0, buffer.length, startByteIndex);
}

// Detects whether the first bytes are an already-plain MP4 box header.
export function isPlainMp4Header(bytes: Uint8Array): boolean {
  if (bytes.length < 12) return false;
  const tag = String.fromCharCode(bytes[4], bytes[5], bytes[6], bytes[7]).toLowerCase();
  return ['ftyp', 'moov', 'mdat', 'free', 'wide', 'skip', 'pnot'].includes(tag);
}
