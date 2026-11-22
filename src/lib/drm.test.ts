import test from 'node:test';
import assert from 'node:assert/strict';
import {
  decryptBlobHeader4KBToPlayableBlob,
  deriveHeaderKeystream4KB,
  encryptBlobHeader4KB,
  hasPlainMp4Header,
  transformHeader4KB,
} from './drm.ts';
import { VAULT_HEADER_LOCK_BYTES } from '../config.ts';

test('4KB Header Lock derives deterministic 4096-byte keystream per lectureId', async () => {
  const k1 = await deriveHeaderKeystream4KB('lec_anatomy_01');
  const k2 = await deriveHeaderKeystream4KB('lec_anatomy_01');
  const kOther = await deriveHeaderKeystream4KB('lec_anatomy_02');

  assert.equal(k1.byteLength, VAULT_HEADER_LOCK_BYTES);
  assert.deepEqual(k1, k2);
  assert.notDeepEqual(k1, kOther);
});

test('4KB Header Lock obfuscates MP4 header, preserves bytes >= 4096, and round-trips identically', async () => {
  const payload = new Uint8Array(VAULT_HEADER_LOCK_BYTES + 2048);
  // Standard MP4 ftyp box header
  payload.set([0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6d, 0x70, 0x34, 0x32], 0);
  for (let i = 12; i < payload.byteLength; i++) {
    payload[i] = i % 251;
  }

  assert.equal(hasPlainMp4Header(payload), true);

  const encrypted = await transformHeader4KB('lec_nutrition_1', payload);
  assert.equal(hasPlainMp4Header(encrypted), false);

  // Bytes past 4096 must be completely untouched (zero CPU/RAM overhead on body)
  assert.deepEqual(
    encrypted.subarray(VAULT_HEADER_LOCK_BYTES),
    payload.subarray(VAULT_HEADER_LOCK_BYTES),
  );

  // Symmetric decryption restores exact original bytes
  const decrypted = await transformHeader4KB('lec_nutrition_1', encrypted);
  assert.deepEqual(decrypted, payload);
  assert.equal(hasPlainMp4Header(decrypted), true);

  // Wrong lectureId must NOT restore MP4 header
  const wrongDecrypted = await transformHeader4KB('lec_wrong_id', encrypted);
  assert.notDeepEqual(wrongDecrypted.subarray(0, 12), payload.subarray(0, 12));
});

test('Blob 4KB encrypt & decrypt round-trips cleanly without corrupting tail', async () => {
  const raw = new Uint8Array(VAULT_HEADER_LOCK_BYTES + 512);
  raw.set([0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d], 0);
  for (let i = 12; i < raw.length; i++) raw[i] = (i * 7) & 0xff;

  const origBlob = new Blob([raw], { type: 'video/mp4' });
  const lockedBlob = await encryptBlobHeader4KB('lec_blob_test', origBlob);
  const lockedBytes = new Uint8Array(await lockedBlob.arrayBuffer());
  assert.equal(hasPlainMp4Header(lockedBytes), false);
  assert.deepEqual(lockedBytes.subarray(VAULT_HEADER_LOCK_BYTES), raw.subarray(VAULT_HEADER_LOCK_BYTES));

  const unlockedBlob = await decryptBlobHeader4KBToPlayableBlob('lec_blob_test', lockedBlob, 'video/mp4');
  assert.equal(unlockedBlob.type, 'video/mp4');
  const unlockedBytes = new Uint8Array(await unlockedBlob.arrayBuffer());
  assert.deepEqual(unlockedBytes, raw);
});
