// Node verification for the DRM module (src/lib/drm.ts).
// Run: node scripts/verify-drm.mjs  (Node 24 strips TS types automatically)
import { encryptHeader, decryptHeader, getKeyStreamTable, isPlainMp4Header, HEADER_LOCK_BYTES } from '../src/lib/drm.ts';

let failures = 0;
function check(name, cond) {
  console.log(`${cond ? 'PASS' : 'FAIL'}: ${name}`);
  if (!cond) failures++;
}

function concat(a, b) {
  const out = new Uint8Array(a.length + b.length);
  out.set(a, 0);
  out.set(b, a.length);
  return out;
}

// Build a fake MP4: 4-byte size + 'ftyp' + 'isom' tag + 56 pseudo-random bytes => 64 header,
// plus 100 bytes of "payload" that must remain untouched.
const header = new Uint8Array(HEADER_LOCK_BYTES);
header[0] = 0x00; header[1] = 0x00; header[2] = 0x00; header[3] = 0x18;
header[4] = 0x66; header[5] = 0x74; header[6] = 0x79; header[7] = 0x70; // ftyp
header[8] = 0x69; header[9] = 0x73; header[10] = 0x6f; header[11] = 0x6d; // isom
for (let i = 12; i < 64; i++) header[i] = (i * 37 + 11) & 0xff;
const payload = new Uint8Array(100);
for (let i = 0; i < payload.length; i++) payload[i] = (i * 13 + 7) & 0xff;
const original = concat(header, payload);

check('isPlainMp4Header detects ftyp', isPlainMp4Header(header) === true);

const lectureId = 'lec_test_123';
const encrypted = await encryptHeader(lectureId, original);

// Header bytes changed
let headerChanged = false;
for (let i = 0; i < HEADER_LOCK_BYTES; i++) if (encrypted[i] !== original[i]) headerChanged = true;
check('first 64 bytes are transformed', headerChanged);

// Payload bytes unchanged
let payloadSame = true;
for (let i = HEADER_LOCK_BYTES; i < original.length; i++) if (encrypted[i] !== original[i]) payloadSame = false;
check('bytes after offset 64 are untouched', payloadSame);

// Encrypted header is no longer a plain MP4 header
check('locked header is not plain MP4', isPlainMp4Header(encrypted) === false);

// Round-trip (decrypt == encrypt) restores original exactly
const decrypted = await decryptHeader(lectureId, encrypted);
let roundTripOk = decrypted.length === original.length;
for (let i = 0; i < original.length; i++) if (decrypted[i] !== original[i]) roundTripOk = false;
check('decrypt(encrypt(x)) === x (round-trip)', roundTripOk);

// Keystream table is deterministic & correct size
const t1 = await getKeyStreamTable(lectureId);
const t2 = await getKeyStreamTable(lectureId);
check('keystream table size = 4096', t1.length === 4096);
let tablesEqual = t1.length === t2.length;
for (let i = 0; i < t1.length; i++) if (t1[i] !== t2[i]) tablesEqual = false;
check('keystream table is deterministic', tablesEqual);

// Different lecture ids produce different keystreams
const tOther = await getKeyStreamTable('lec_other_999');
let differ = false;
for (let i = 0; i < 4096; i++) if (t1[i] !== tOther[i]) { differ = true; break; }
check('different lectureId => different keystream', differ);

console.log(failures === 0 ? '\nALL DRM TESTS PASSED' : `\n${failures} TEST(S) FAILED`);
process.exit(failures === 0 ? 0 : 1);
