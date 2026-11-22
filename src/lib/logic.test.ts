import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildDriveStreamUrl,
  computeResumableUploadRanges,
  emailToSafeAdminDocId,
  evaluateStrictDeviceLock,
  extractDriveFileId,
  filterVisibleExams,
  formatCountdownSeconds,
  formatDateTime12HrArabic,
  getExamTimingStatus,
} from './logic.ts';
import type { ExamItem } from '../types/models.ts';

test('Strict Permanent 1-Device Lock enforces max 1 device and blocks any second browser', () => {
  const first = evaluateStrictDeviceLock([], 'WEB_device-aaa');
  assert.equal(first.allowed, true);
  assert.equal(first.shouldRegisterFirstDevice, true);
  assert.deepEqual(first.registeredDevices, ['WEB_device-aaa']);

  const returningSame = evaluateStrictDeviceLock(['WEB_device-aaa'], 'WEB_device-aaa');
  assert.equal(returningSame.allowed, true);
  assert.equal(returningSame.shouldRegisterFirstDevice, false);

  const secondDevice = evaluateStrictDeviceLock(['WEB_device-aaa'], 'WEB_device-bbb');
  assert.equal(secondDevice.allowed, false);
  assert.equal(secondDevice.shouldRegisterFirstDevice, false);
  assert.equal(secondDevice.lockedDeviceId, 'WEB_device-aaa');
});

test('Google Drive File ID extraction and streaming URL builder', () => {
  assert.equal(extractDriveFileId('gdrive://1AbCdEfGhIjKlMnOpQr'), '1AbCdEfGhIjKlMnOpQr');
  assert.equal(
    extractDriveFileId('https://drive.google.com/file/d/1XyZ9876543210AbCd/view?usp=sharing'),
    '1XyZ9876543210AbCd',
  );
  assert.equal(
    extractDriveFileId('https://www.googleapis.com/drive/v3/files/1FileId123456789?alt=media'),
    '1FileId123456789',
  );

  const withToken = buildDriveStreamUrl('1FileId123456789', 'ya29.token123', 'AIzaKey');
  assert.ok(withToken.includes('alt=media&access_token=ya29.token123'));

  const withKeyFallback = buildDriveStreamUrl('1FileId123456789', null, 'AIzaKey');
  assert.ok(withKeyFallback.includes('alt=media&key=AIzaKey'));
});

test('2MB Resumable Upload chunk ranges match exact byte boundaries', () => {
  const chunk2MB = 2 * 1024 * 1024;
  const total = 5 * 1024 * 1024 + 500;
  const ranges = computeResumableUploadRanges(total, chunk2MB);

  assert.equal(ranges.length, 3);
  assert.equal(ranges[0].contentRangeHeader, `bytes 0-${chunk2MB - 1}/${total}`);
  assert.equal(ranges[1].contentRangeHeader, `bytes ${chunk2MB}-${2 * chunk2MB - 1}/${total}`);
  assert.equal(ranges[2].contentRangeHeader, `bytes ${2 * chunk2MB}-${total - 1}/${total}`);
});

test('Timed Exams: 12-hour Arabic formatting (ص/م) & auto-hide expired exams for students', () => {
  const morning = new Date(2026, 4, 10, 9, 30, 0).getTime();
  const evening = new Date(2026, 4, 10, 21, 5, 0).getTime();
  const midnight = new Date(2026, 4, 10, 0, 15, 0).getTime();

  assert.equal(formatDateTime12HrArabic(morning), '2026/05/10 - 09:30 ص');
  assert.equal(formatDateTime12HrArabic(evening), '2026/05/10 - 09:05 م');
  assert.equal(formatDateTime12HrArabic(midnight), '2026/05/10 - 12:15 ص');
  assert.equal(formatCountdownSeconds(3665), '01:01:05');

  const now = 1_700_000_000_000;
  const makeExam = (id: string, startOffset: number, endOffset: number): ExamItem => ({
    id,
    title: id,
    description: '',
    categoryId: 'cat_nutrition',
    categoryName: 'Nutrition',
    uploadedByUid: 'admin',
    doctorName: 'Dr',
    pdfFileName: 'e.pdf',
    fileSizeKb: 100,
    chunkCount: 1,
    startTimeMillis: now + startOffset,
    endTimeMillis: now + endOffset,
    scheduleLabel: '',
    visibility: 'published',
  });

  const exams = [
    makeExam('open', -10_000, 60_000),
    makeExam('upcoming', 60_000, 120_000),
    makeExam('expired', -120_000, -10_000),
  ];

  assert.equal(getExamTimingStatus(exams[0], now), 'open');
  assert.equal(getExamTimingStatus(exams[1], now), 'upcoming');
  assert.equal(getExamTimingStatus(exams[2], now), 'expired');

  const studentVisible = filterVisibleExams(exams, 'cat_nutrition', false, now);
  assert.deepEqual(
    studentVisible.map((e) => e.id),
    ['open', 'upcoming'],
  );

  const adminVisible = filterVisibleExams(exams, 'cat_nutrition', true, now);
  assert.equal(adminVisible.length, 3);
});

test('Admin safe doc ID hashing is deterministic and case-insensitive', async () => {
  const id1 = await emailToSafeAdminDocId('AbdallahXH909@gmail.com');
  const id2 = await emailToSafeAdminDocId('  abdallahxh909@gmail.com  ');
  assert.equal(id1, id2);
  assert.ok(id1.startsWith('adm_'));
  assert.equal(id1.length, 32);
});
