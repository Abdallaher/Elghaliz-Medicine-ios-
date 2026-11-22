import React, { useEffect, useMemo, useState } from 'react';
import {
  CheckCircle2,
  ClipboardCheck,
  Clock,
  Download,
  Eye,
  FileText,
  Lock,
  Plus,
  Trash2,
  Upload,
} from 'lucide-react';
import type { ExamItem } from '../types/models';
import { useAppStore } from '../store/appStore';
import {
  filterVisibleExams,
  formatCountdownSeconds,
  formatDateTime12HrArabic,
  getExamTimingStatus,
} from '../lib/logic';
import { Modal } from '../components/Modal';

function buildEpochFrom12Hr(
  dateStr: string,
  hour12: number,
  minute: number,
  period: 'AM' | 'PM',
): number {
  const [y, m, d] = dateStr.split('-').map(Number);
  let h24 = hour12 % 12;
  if (period === 'PM') h24 += 12;
  return new Date(y, (m || 1) - 1, d || 1, h24, minute, 0, 0).getTime();
}

function todayDateInput(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export const ExamsScreen: React.FC = () => {
  const {
    user,
    isAdminUser,
    activeRole,
    categories,
    exams,
    courseRequests,
    requestCourseEnrollment,
    uploadTimedPdfExam,
    fetchExamPdfBlob,
    deleteExam,
    pushToast,
  } = useAppStore();

  const isAdminView = isAdminUser && activeRole === 'ADMIN';
  const [selectedCatId, setSelectedCatId] = useState<string | null>(categories[0]?.id ?? null);
  const [nowMs, setNowMs] = useState<number>(() => Date.now());

  // Live 1-second ticker for countdown & automatic hide on expiry
  useEffect(() => {
    const id = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    if (!selectedCatId && categories.length > 0) {
      setSelectedCatId(categories[0].id);
    }
  }, [categories, selectedCatId]);

  const myCourseStatusByCat = useMemo(() => {
    const map = new Map<string, 'pending' | 'approved' | 'suspended'>();
    if (!user) return map;
    for (const r of courseRequests) {
      if (r.userId === user.uid) map.set(r.categoryId, r.status);
    }
    return map;
  }, [courseRequests, user]);

  const selectedCategory = useMemo(
    () => categories.find((c) => c.id === selectedCatId) ?? null,
    [categories, selectedCatId],
  );

  const selectedCourseStatus = selectedCatId ? myCourseStatusByCat.get(selectedCatId) : undefined;
  const isCourseUnlocked = isAdminView || !selectedCatId || selectedCourseStatus === 'approved';

  const visibleExams = useMemo(
    () => filterVisibleExams(exams, selectedCatId, isAdminView, nowMs),
    [exams, selectedCatId, isAdminView, nowMs],
  );

  // PDF Preview Modal
  const [previewExam, setPreviewExam] = useState<ExamItem | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [loadingPdfId, setLoadingPdfId] = useState<string | null>(null);

  // Upload Exam Modal (Admin)
  const [showUploadExam, setShowUploadExam] = useState(false);
  const [examTitle, setExamTitle] = useState('');
  const [examDesc, setExamDesc] = useState('');
  const [examCatId, setExamCatId] = useState('');
  const [examPdfFile, setExamPdfFile] = useState<File | null>(null);
  const [startDate, setStartDate] = useState(todayDateInput);
  const [startHour, setStartHour] = useState(9);
  const [startMinute, setStartMinute] = useState(0);
  const [startPeriod, setStartPeriod] = useState<'AM' | 'PM'>('AM');
  const [endDate, setEndDate] = useState(todayDateInput);
  const [endHour, setEndHour] = useState(11);
  const [endMinute, setEndMinute] = useState(0);
  const [endPeriod, setEndPeriod] = useState<'AM' | 'PM'>('PM');
  const [uploadingExam, setUploadingExam] = useState(false);

  const closePreview = () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPreviewUrl(null);
    setPreviewExam(null);
  };

  const handleOpenPreview = async (exam: ExamItem) => {
    setLoadingPdfId(exam.id);
    try {
      const blob = await fetchExamPdfBlob(exam);
      const url = URL.createObjectURL(blob);
      setPreviewExam(exam);
      setPreviewUrl(url);
    } catch (err) {
      pushToast(err instanceof Error ? err.message : 'تعذّر فتح ملف الاختبار', 'error');
    } finally {
      setLoadingPdfId(null);
    }
  };

  const handleDownloadPdf = async (exam: ExamItem) => {
    setLoadingPdfId(exam.id);
    try {
      const blob = await fetchExamPdfBlob(exam);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = exam.pdfFileName || `${exam.title}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 4000);
      pushToast('تم تنزيل ملف PDF بنجاح ✓', 'success');
    } catch (err) {
      pushToast(err instanceof Error ? err.message : 'تعذّر تنزيل ملف الاختبار', 'error');
    } finally {
      setLoadingPdfId(null);
    }
  };

  const handleUploadExamSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const cat = categories.find((c) => c.id === examCatId) ?? categories[0];
    if (!cat || !examPdfFile || !examTitle.trim()) return;

    const startMs = buildEpochFrom12Hr(startDate, startHour, startMinute, startPeriod);
    const endMs = buildEpochFrom12Hr(endDate, endHour, endMinute, endPeriod);
    if (endMs <= startMs) {
      pushToast('يجب أن يكون وقت نهاية الاختبار بعد وقت البداية', 'warning');
      return;
    }

    setUploadingExam(true);
    try {
      await uploadTimedPdfExam({
        title: examTitle.trim(),
        description: examDesc.trim(),
        category: cat,
        pdfFile: examPdfFile,
        startTimeMillis: startMs,
        endTimeMillis: endMs,
        scheduleLabel: `من ${formatDateTime12HrArabic(startMs)} إلى ${formatDateTime12HrArabic(endMs)}`,
      });
      setExamTitle('');
      setExamDesc('');
      setExamPdfFile(null);
      setShowUploadExam(false);
    } finally {
      setUploadingExam(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* Header & Admin Upload Action */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-black text-[#0F3D2E] dark:text-[#F4EFE4]">
            الاختبارات الطبية الموقوتة (PDF)
          </h2>
          <p className="text-xs text-[#546B61] dark:text-[#A5C0B4]">
            تُعرض الاختبارات بنظام 12 ساعة (ص/م) وتُخفى تلقائياً فور انتهاء الموعد
          </p>
        </div>

        {isAdminView && (
          <button
            type="button"
            onClick={() => {
              setExamCatId(selectedCatId || categories[0]?.id || '');
              setShowUploadExam(true);
            }}
            className="inline-flex items-center gap-2 rounded-2xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white text-xs font-extrabold px-4 py-2.5 shadow cursor-pointer transition"
          >
            <Plus className="h-4 w-4 text-[#C9A24D]" />
            <span>رفع اختبار PDF جديد</span>
          </button>
        )}
      </div>

      {/* Course Filter Selector */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1">
        {categories.map((cat) => {
          const active = selectedCatId === cat.id;
          const unlocked = isAdminView || myCourseStatusByCat.get(cat.id) === 'approved';
          return (
            <button
              key={cat.id}
              type="button"
              onClick={() => setSelectedCatId(cat.id)}
              className={`inline-flex items-center gap-1.5 shrink-0 rounded-2xl px-3.5 py-2 text-xs font-extrabold border transition cursor-pointer ${
                active
                  ? 'bg-[#0F3D2E] text-[#C9A24D] border-[#0F3D2E]'
                  : 'bg-[#FFFDF8] dark:bg-[#123528] text-[#0F241D] dark:text-[#F4EFE4] border-[#EFE8D8] dark:border-[#1E4F3D]'
              }`}
            >
              {!unlocked && <Lock className="h-3.5 w-3.5 opacity-70" />}
              <span>{cat.name}</span>
            </button>
          );
        })}
      </div>

      {/* Course Enrollment Gate for Student */}
      {!isCourseUnlocked && selectedCategory ? (
        <div className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-7 text-center space-y-3">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-[#C9A24D]/15 text-[#9A7624] dark:text-[#C9A24D]">
            <Lock className="h-7 w-7" />
          </div>
          <h3 className="text-base font-black text-[#0F3D2E] dark:text-[#F4EFE4]">
            اختبارات كورس {selectedCategory.name} مخصصة للمشتركين
          </h3>
          <p className="text-xs text-[#546B61] dark:text-[#A5C0B4] max-w-md mx-auto leading-relaxed">
            {selectedCourseStatus === 'pending'
              ? 'طلب انضمامك لهذا الكورس قيد المراجعة حالياً من قِبل الدكتور.'
              : 'أرسل طلب انضمام للكورس ليتم تفعيل محاضراته واختباراته الموقوتة.'}
          </p>
          {!selectedCourseStatus && (
            <button
              type="button"
              onClick={() => void requestCourseEnrollment(selectedCategory)}
              className="inline-flex items-center gap-2 rounded-2xl bg-[#C9A24D] hover:bg-[#d8b464] text-[#0F241D] text-xs font-extrabold px-5 py-2.5 cursor-pointer"
            >
              <span>طلب انضمام لكورس {selectedCategory.name}</span>
            </button>
          )}
        </div>
      ) : visibleExams.length === 0 ? (
        <div className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-10 text-center">
          <ClipboardCheck className="h-10 w-10 mx-auto text-[#C9A24D] mb-2.5" />
          <p className="text-sm font-extrabold text-[#0F3D2E] dark:text-[#F4EFE4]">
            لا توجد اختبارات متاحة حالياً في هذا الكورس
          </p>
          <p className="text-xs text-[#546B61] dark:text-[#A5C0B4] mt-1">
            تظهر الاختبارات هنا تلقائياً عند جدولتها وتختفي فور انتهاء وقتها.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {visibleExams.map((exam) => {
            const status = getExamTimingStatus(exam, nowMs);
            const remainingSec =
              status === 'upcoming'
                ? Math.max(0, Math.floor((exam.startTimeMillis - nowMs) / 1000))
                : status === 'open'
                  ? Math.max(0, Math.floor((exam.endTimeMillis - nowMs) / 1000))
                  : 0;
            const canOpenPdf = isAdminView || status === 'open';

            return (
              <article
                key={exam.id}
                className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 sm:p-5 shadow-sm space-y-3.5"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-start gap-3 min-w-0">
                    <div
                      className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl ${
                        status === 'open'
                          ? 'bg-[#0F3D2E] text-[#C9A24D]'
                          : status === 'upcoming'
                            ? 'bg-[#C9A24D]/20 text-[#9A7624] dark:text-[#C9A24D]'
                            : 'bg-[#B33A3A]/15 text-[#B33A3A]'
                      }`}
                    >
                      <FileText className="h-6 w-6" />
                    </div>
                    <div className="min-w-0">
                      <h3 className="text-sm sm:text-base font-black text-[#0F3D2E] dark:text-[#F4EFE4]">
                        {exam.title}
                      </h3>
                      {exam.description && (
                        <p className="text-xs text-[#546B61] dark:text-[#A5C0B4] mt-0.5 leading-relaxed">
                          {exam.description}
                        </p>
                      )}
                    </div>
                  </div>

                  {/* Status Label */}
                  <span
                    className={`shrink-0 rounded-xl px-3 py-1 text-[11px] font-extrabold ${
                      status === 'open'
                        ? 'bg-[#1F6B52]/15 text-[#1F6B52] dark:text-[#4AD1A3]'
                        : status === 'upcoming'
                          ? 'bg-[#C9A24D]/20 text-[#9A7624] dark:text-[#C9A24D]'
                          : 'bg-[#B33A3A]/15 text-[#B33A3A] dark:text-red-300'
                    }`}
                  >
                    {status === 'open'
                      ? 'متاح الآن ✓'
                      : status === 'upcoming'
                        ? 'قريباً'
                        : 'منتهي (مخفي عن الطلاب)'}
                  </span>
                </div>

                {/* 12-Hour Schedule & Live Countdown */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 rounded-2xl bg-[#E9F0E8]/70 dark:bg-[#0A2A20] p-3 text-xs">
                  <div className="space-y-1">
                    <p className="text-[#546B61] dark:text-[#A5C0B4]">
                      <strong className="text-[#0F3D2E] dark:text-[#F4EFE4]">بداية الاختبار:</strong>{' '}
                      {formatDateTime12HrArabic(exam.startTimeMillis)}
                    </p>
                    <p className="text-[#546B61] dark:text-[#A5C0B4]">
                      <strong className="text-[#0F3D2E] dark:text-[#F4EFE4]">نهاية الاختبار:</strong>{' '}
                      {formatDateTime12HrArabic(exam.endTimeMillis)}
                    </p>
                  </div>

                  <div className="flex sm:flex-col sm:items-end justify-between sm:justify-center gap-1">
                    <span className="inline-flex items-center gap-1 text-[11px] font-bold text-[#546B61] dark:text-[#A5C0B4]">
                      <Clock className="h-3.5 w-3.5 text-[#C9A24D]" />
                      <span>
                        {status === 'upcoming'
                          ? 'يفتح خلال:'
                          : status === 'open'
                            ? 'الوقت المتبقي:'
                            : 'انتهى الموعد'}
                      </span>
                    </span>
                    {status !== 'expired' && (
                      <span className="font-mono text-base font-black text-[#0F3D2E] dark:text-[#C9A24D] tracking-wider">
                        {formatCountdownSeconds(remainingSec)}
                      </span>
                    )}
                  </div>
                </div>

                {/* Actions */}
                <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
                  <span className="text-[11px] font-bold text-[#546B61] dark:text-[#A5C0B4]">
                    {exam.pdfFileName} • {exam.fileSizeKb} KB
                  </span>

                  <div className="flex flex-wrap items-center gap-2">
                    {canOpenPdf ? (
                      <>
                        <button
                          type="button"
                          disabled={loadingPdfId === exam.id}
                          onClick={() => void handleOpenPreview(exam)}
                          className="inline-flex items-center gap-1.5 rounded-xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white text-xs font-extrabold px-3.5 py-2 cursor-pointer transition disabled:opacity-60"
                        >
                          <Eye className="h-3.5 w-3.5 text-[#C9A24D]" />
                          <span>عرض الاختبار (PDF)</span>
                        </button>

                        <button
                          type="button"
                          disabled={loadingPdfId === exam.id}
                          onClick={() => void handleDownloadPdf(exam)}
                          className="inline-flex items-center gap-1.5 rounded-xl bg-[#E9F0E8] dark:bg-[#0A2A20] text-[#0F3D2E] dark:text-[#C9A24D] border border-[#0F3D2E]/15 dark:border-[#C9A24D]/30 text-xs font-bold px-3.5 py-2 cursor-pointer transition"
                        >
                          <Download className="h-3.5 w-3.5" />
                          <span>تنزيل PDF</span>
                        </button>
                      </>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 rounded-xl bg-[#C9A24D]/15 text-[#9A7624] dark:text-[#C9A24D] text-xs font-bold px-3.5 py-2">
                        <Lock className="h-3.5 w-3.5" />
                        <span>سيُفتح تلقائياً عند حلول الموعد</span>
                      </span>
                    )}

                    {isAdminView && (
                      <button
                        type="button"
                        onClick={() => void deleteExam(exam.id)}
                        aria-label="حذف الاختبار"
                        className="inline-flex items-center gap-1 rounded-xl bg-[#B33A3A]/10 text-[#B33A3A] dark:text-red-300 text-xs font-bold px-3 py-2 hover:bg-[#B33A3A]/20 cursor-pointer"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                        <span>حذف</span>
                      </button>
                    )}
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {/* In-App PDF Preview Modal */}
      <Modal
        open={Boolean(previewExam && previewUrl)}
        onClose={closePreview}
        title={previewExam?.title || 'معاينة الاختبار'}
        subtitle={previewExam?.pdfFileName}
        maxWidthClass="max-w-3xl"
      >
        {previewUrl && (
          <div className="space-y-3">
            <div className="h-[65vh] w-full overflow-hidden rounded-2xl border border-[#EFE8D8] dark:border-[#1E4F3D] bg-white">
              <iframe
                title={previewExam?.title || 'Exam PDF'}
                src={previewUrl}
                className="h-full w-full"
              />
            </div>
            <div className="flex justify-end gap-2">
              {previewExam && (
                <button
                  type="button"
                  onClick={() => void handleDownloadPdf(previewExam)}
                  className="inline-flex items-center gap-1.5 rounded-2xl bg-[#0F3D2E] text-white text-xs font-extrabold px-4 py-2.5 cursor-pointer"
                >
                  <Download className="h-4 w-4 text-[#C9A24D]" />
                  <span>حفظ ملف PDF</span>
                </button>
              )}
              <button
                type="button"
                onClick={closePreview}
                className="rounded-2xl bg-[#E9F0E8] dark:bg-[#0A2A20] text-xs font-bold px-4 py-2.5 cursor-pointer"
              >
                إغلاق
              </button>
            </div>
          </div>
        )}
      </Modal>

      {/* Admin Upload Timed PDF Exam Modal (12-Hour ص / م) */}
      <Modal
        open={showUploadExam}
        onClose={() => setShowUploadExam(false)}
        title="رفع اختبار PDF جديد وتحديد موعده"
        subtitle="النظام بنظام 12 ساعة (ص/م) — يُخفى تلقائياً عن الطلاب بعد انتهاء الموعد"
      >
        <form onSubmit={handleUploadExamSubmit} className="space-y-3.5">
          <div>
            <label htmlFor="exam-course" className="block text-xs font-extrabold mb-1">
              الكورس الطبي
            </label>
            <select
              id="exam-course"
              value={examCatId}
              onChange={(e) => setExamCatId(e.target.value)}
              className="w-full rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] px-3.5 py-2.5 text-sm font-bold"
            >
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="exam-title" className="block text-xs font-extrabold mb-1">
              عنوان الاختبار
            </label>
            <input
              id="exam-title"
              type="text"
              required
              value={examTitle}
              onChange={(e) => setExamTitle(e.target.value)}
              placeholder="مثال: اختبار منتصف الترم - Pharmacology"
              className="w-full rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] px-3.5 py-2.5 text-sm"
            />
          </div>

          <div>
            <label htmlFor="exam-desc" className="block text-xs font-extrabold mb-1">
              ملاحظات للطلاب (اختياري)
            </label>
            <input
              id="exam-desc"
              type="text"
              value={examDesc}
              onChange={(e) => setExamDesc(e.target.value)}
              placeholder="المحاضرات المشمولة في الاختبار..."
              className="w-full rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] px-3.5 py-2.5 text-sm"
            />
          </div>

          {/* Start Time 12h ص/م */}
          <div className="rounded-2xl bg-[#E9F0E8]/60 dark:bg-[#0A2A20] p-3 space-y-2">
            <span className="text-xs font-extrabold text-[#0F3D2E] dark:text-[#C9A24D]">
              موعد فتح الاختبار (12 ساعة ص/م)
            </span>
            <div className="grid grid-cols-4 gap-2">
              <input
                type="date"
                aria-label="تاريخ بداية الاختبار"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="col-span-2 rounded-xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] px-2.5 py-2 text-xs font-bold"
              />
              <select
                aria-label="ساعة البداية"
                value={startHour}
                onChange={(e) => setStartHour(Number(e.target.value))}
                className="rounded-xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] px-2 py-2 text-xs font-bold"
              >
                {[12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11].map((h) => (
                  <option key={h} value={h}>
                    {String(h).padStart(2, '0')}:00
                  </option>
                ))}
              </select>
              <select
                aria-label="فترة البداية"
                value={startPeriod}
                onChange={(e) => setStartPeriod(e.target.value as 'AM' | 'PM')}
                className="rounded-xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] px-2 py-2 text-xs font-extrabold"
              >
                <option value="AM">ص</option>
                <option value="PM">م</option>
              </select>
            </div>
            <div className="flex items-center gap-2 text-[11px]">
              <label htmlFor="start-min">الدقائق:</label>
              <input
                id="start-min"
                type="number"
                min={0}
                max={59}
                value={startMinute}
                onChange={(e) => setStartMinute(Math.max(0, Math.min(59, Number(e.target.value))))}
                className="w-16 rounded-lg bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] px-2 py-1 text-xs font-bold"
              />
            </div>
          </div>

          {/* End Time 12h ص/م */}
          <div className="rounded-2xl bg-[#E9F0E8]/60 dark:bg-[#0A2A20] p-3 space-y-2">
            <span className="text-xs font-extrabold text-[#0F3D2E] dark:text-[#C9A24D]">
              موعد إغلاق الاختبار وإخفائه (12 ساعة ص/م)
            </span>
            <div className="grid grid-cols-4 gap-2">
              <input
                type="date"
                aria-label="تاريخ نهاية الاختبار"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="col-span-2 rounded-xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] px-2.5 py-2 text-xs font-bold"
              />
              <select
                aria-label="ساعة النهاية"
                value={endHour}
                onChange={(e) => setEndHour(Number(e.target.value))}
                className="rounded-xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] px-2 py-2 text-xs font-bold"
              >
                {[12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11].map((h) => (
                  <option key={h} value={h}>
                    {String(h).padStart(2, '0')}:00
                  </option>
                ))}
              </select>
              <select
                aria-label="فترة النهاية"
                value={endPeriod}
                onChange={(e) => setEndPeriod(e.target.value as 'AM' | 'PM')}
                className="rounded-xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] px-2 py-2 text-xs font-extrabold"
              >
                <option value="AM">ص</option>
                <option value="PM">م</option>
              </select>
            </div>
            <div className="flex items-center gap-2 text-[11px]">
              <label htmlFor="end-min">الدقائق:</label>
              <input
                id="end-min"
                type="number"
                min={0}
                max={59}
                value={endMinute}
                onChange={(e) => setEndMinute(Math.max(0, Math.min(59, Number(e.target.value))))}
                className="w-16 rounded-lg bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] px-2 py-1 text-xs font-bold"
              />
            </div>
          </div>

          <div>
            <label htmlFor="exam-pdf" className="block text-xs font-extrabold mb-1">
              ملف الاختبار (PDF)
            </label>
            <input
              id="exam-pdf"
              type="file"
              accept="application/pdf,.pdf"
              required
              onChange={(e) => setExamPdfFile(e.target.files?.[0] ?? null)}
              className="w-full rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-dashed border-[#C9A24D] px-3 py-2.5 text-xs file:ml-3 file:rounded-xl file:border-0 file:bg-[#0F3D2E] file:px-3 file:py-1.5 file:text-xs file:font-bold file:text-white cursor-pointer"
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setShowUploadExam(false)}
              className="rounded-2xl bg-[#E9F0E8] dark:bg-[#0A2A20] px-4 py-2.5 text-xs font-bold cursor-pointer"
            >
              إلغاء
            </button>
            <button
              type="submit"
              disabled={uploadingExam || !examPdfFile || !examTitle.trim()}
              className="inline-flex items-center gap-1.5 rounded-2xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white px-5 py-2.5 text-xs font-extrabold cursor-pointer disabled:opacity-50"
            >
              {uploadingExam ? (
                <span>جاري الرفع...</span>
              ) : (
                <>
                  <Upload className="h-3.5 w-3.5 text-[#C9A24D]" />
                  <span>نشر الاختبار</span>
                </>
              )}
            </button>
          </div>
        </form>
      </Modal>

      {/* Informational footnote */}
      <div className="flex items-center gap-2 text-[11px] text-[#546B61] dark:text-[#A5C0B4] px-1">
        <CheckCircle2 className="h-3.5 w-3.5 text-[#1F6B52] dark:text-[#C9A24D] shrink-0" />
        <span>يتم تحديث العداد التنازلي لحظياً كل ثانية وإخفاء الاختبارات المنتهية تلقائياً للطلاب.</span>
      </div>
    </div>
  );
};
