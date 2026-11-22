import React, { useMemo, useState } from 'react';
import {
  BookOpen,
  CheckCircle2,
  ChevronLeft,
  Clock,
  FolderPlus,
  Lock,
  Pause,
  Play,
  Stethoscope,
  Upload,
  X,
} from 'lucide-react';
import type { Category } from '../types/models';
import { useAppStore } from '../store/appStore';
import { formatBytesToMb, formatLectureCountAr } from '../lib/logic';
import { Modal } from '../components/Modal';

export const LecturesLibraryScreen: React.FC = () => {
  const {
    user,
    isAdminUser,
    activeRole,
    categories,
    lectures,
    courseRequests,
    uploadTask,
    openCoursePlayer,
    requestCourseEnrollment,
    createCategory,
    uploadLectureVideo,
    pauseUpload,
    resumeUpload,
    cancelUpload,
  } = useAppStore();

  const isAdminView = isAdminUser && activeRole === 'ADMIN';

  // Admin Modals State
  const [showAddCourse, setShowAddCourse] = useState(false);
  const [newCourseName, setNewCourseName] = useState('');
  const [newCourseDesc, setNewCourseDesc] = useState('');
  const [savingCourse, setSavingCourse] = useState(false);

  const [showUploadModal, setShowUploadModal] = useState(false);
  const [uploadTitle, setUploadTitle] = useState('');
  const [uploadDesc, setUploadDesc] = useState('');
  const [uploadCatId, setUploadCatId] = useState('');
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadDurationSec, setUploadDurationSec] = useState(0);

  const lectureCountsByCat = useMemo(() => {
    const map = new Map<string, number>();
    for (const l of lectures) {
      map.set(l.categoryId, (map.get(l.categoryId) ?? 0) + 1);
    }
    return map;
  }, [lectures]);

  const myCourseRequestByCat = useMemo(() => {
    const map = new Map<string, 'pending' | 'approved' | 'suspended'>();
    if (!user) return map;
    for (const r of courseRequests) {
      if (r.userId === user.uid) {
        map.set(r.categoryId, r.status);
      }
    }
    return map;
  }, [courseRequests, user]);

  const handleCreateCourse = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCourseName.trim()) return;
    setSavingCourse(true);
    try {
      await createCategory(newCourseName, newCourseDesc);
      setNewCourseName('');
      setNewCourseDesc('');
      setShowAddCourse(false);
    } finally {
      setSavingCourse(false);
    }
  };

  const handleFileChange = (file: File | null) => {
    setUploadFile(file);
    if (!file) {
      setUploadDurationSec(0);
      return;
    }
    // Extract video duration in browser metadata if possible
    const url = URL.createObjectURL(file);
    const v = document.createElement('video');
    v.preload = 'metadata';
    v.onloadedmetadata = () => {
      if (Number.isFinite(v.duration)) {
        setUploadDurationSec(Math.round(v.duration));
      }
      URL.revokeObjectURL(url);
    };
    v.onerror = () => URL.revokeObjectURL(url);
    v.src = url;
  };

  const handleUploadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const selectedCategory =
      categories.find((c) => c.id === uploadCatId) ?? categories[0] ?? null;
    if (!uploadFile || !uploadTitle.trim() || !selectedCategory) return;

    setShowUploadModal(false);
    await uploadLectureVideo({
      file: uploadFile,
      title: uploadTitle.trim(),
      description: uploadDesc.trim(),
      category: selectedCategory,
      durationSeconds: uploadDurationSec,
    });
    setUploadTitle('');
    setUploadDesc('');
    setUploadFile(null);
    setUploadDurationSec(0);
  };

  const handleCourseClick = (cat: Category) => {
    const status = myCourseRequestByCat.get(cat.id);
    if (isAdminView || status === 'approved') {
      openCoursePlayer(cat.id);
    } else if (!status) {
      void requestCourseEnrollment(cat);
    }
  };

  return (
    <div className="space-y-4">
      {/* Admin Quick Tools Card (matching Android LecturesLibraryScreen) */}
      {isAdminView && (
        <section className="rounded-[24px] bg-[#0F3D2E] text-white p-4 sm:p-5 shadow-md border border-[#C9A24D]/30">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <h2 className="text-base font-extrabold text-[#FFFDF8]">أدوات الدكتور وإدارة الكورسات</h2>
              <p className="text-xs text-[#E9F0E8]/80 mt-0.5">
                رفع المحاضرات بدفعات 2MB إلى مخزن الـ 5TB وإضافة الكورسات الطبية
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => {
                  setUploadCatId(categories[0]?.id ?? '');
                  setShowUploadModal(true);
                }}
                className="inline-flex items-center gap-2 rounded-2xl bg-[#C9A24D] hover:bg-[#d8b464] text-[#0F241D] font-extrabold text-xs px-4 py-2.5 shadow cursor-pointer transition"
              >
                <Upload className="h-4 w-4" />
                <span>رفع محاضرة جديدة</span>
              </button>

              <button
                type="button"
                onClick={() => setShowAddCourse(true)}
                className="inline-flex items-center gap-2 rounded-2xl bg-[#1F6B52] hover:bg-[#278062] text-white font-bold text-xs px-4 py-2.5 border border-[#C9A24D]/40 cursor-pointer transition"
              >
                <FolderPlus className="h-4 w-4 text-[#C9A24D]" />
                <span>إضافة كورس طبي</span>
              </button>
            </div>
          </div>

          {/* Live 2MB Resumable Upload Progress Card */}
          {uploadTask && (
            <div className="mt-4 rounded-2xl bg-[#0A2A20] border border-[#C9A24D]/40 p-3.5 space-y-2.5">
              <div className="flex items-center justify-between gap-2 text-xs">
                <span className="font-extrabold text-[#FFFDF8] truncate">
                  {uploadTask.lectureTitle} ({uploadTask.categoryName})
                </span>
                <span className="font-mono font-bold text-[#C9A24D] shrink-0">
                  {uploadTask.progressPercent}%
                </span>
              </div>

              <div className="h-2.5 w-full overflow-hidden rounded-full bg-white/10">
                <div
                  className="h-full rounded-full bg-gradient-to-l from-[#C9A24D] to-[#2FA27A] transition-all duration-200"
                  style={{ width: `${uploadTask.progressPercent}%` }}
                />
              </div>

              <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] text-[#E9F0E8]/85">
                <span>
                  {uploadTask.statusLabel} • {formatBytesToMb(uploadTask.uploadedBytes)} /{' '}
                  {formatBytesToMb(uploadTask.totalBytes)} MB
                </span>
                <div className="flex items-center gap-1.5">
                  {uploadTask.status === 'UPLOADING' && (
                    <button
                      type="button"
                      onClick={pauseUpload}
                      className="inline-flex items-center gap-1 rounded-xl bg-white/15 px-2.5 py-1 font-bold hover:bg-white/25 cursor-pointer"
                    >
                      <Pause className="h-3 w-3" />
                      <span>إيقاف مؤقت</span>
                    </button>
                  )}
                  {uploadTask.status === 'PAUSED' && (
                    <button
                      type="button"
                      onClick={resumeUpload}
                      className="inline-flex items-center gap-1 rounded-xl bg-[#C9A24D] text-[#0F241D] px-2.5 py-1 font-bold cursor-pointer"
                    >
                      <Play className="h-3 w-3" />
                      <span>استئناف</span>
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={cancelUpload}
                    className="inline-flex items-center gap-1 rounded-xl bg-[#B33A3A] text-white px-2.5 py-1 font-bold cursor-pointer"
                  >
                    <X className="h-3 w-3" />
                    <span>إلغاء</span>
                  </button>
                </div>
              </div>
            </div>
          )}
        </section>
      )}

      {/* Section Header — Medical Courses ONLY (no flat lecture list below) */}
      <div className="flex items-center justify-between px-1">
        <div>
          <h2 className="text-lg font-black text-[#0F3D2E] dark:text-[#F4EFE4]">
            الكورسات الطبية
          </h2>
          <p className="text-xs text-[#546B61] dark:text-[#A5C0B4]">
            {isAdminView
              ? 'اضغط على أي كورس لفتح صفحة الكورس والمشغل وإدارة محاضراته'
              : 'اضغط على أي كورس مفعّل لفتح المحاضرات ومشغل الفيديو وملاحظاتك'}
          </p>
        </div>
        <span className="rounded-xl bg-[#E9F0E8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] px-3 py-1.5 text-xs font-extrabold text-[#0F3D2E] dark:text-[#C9A24D]">
          {categories.length} كورس
        </span>
      </div>

      {/* Medical Courses Grid/List ONLY */}
      {categories.length === 0 ? (
        <div className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-10 text-center">
          <BookOpen className="h-10 w-10 mx-auto text-[#C9A24D] mb-3" />
          <p className="font-extrabold text-sm text-[#0F3D2E] dark:text-[#F4EFE4]">
            لا توجد كورسات طبية حالياً
          </p>
          <p className="text-xs text-[#546B61] dark:text-[#A5C0B4] mt-1">
            سيظهر هنا كل كورس يضيفه الدكتور فور نشره.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
          {categories.map((cat) => {
            const count = lectureCountsByCat.get(cat.id) ?? 0;
            const reqStatus = myCourseRequestByCat.get(cat.id);
            const canOpen = isAdminView || reqStatus === 'approved';

            return (
              <article
                key={cat.id}
                className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 shadow-sm hover:shadow-md transition flex flex-col justify-between gap-4"
              >
                <div className="flex items-start gap-3.5">
                  <div
                    className={`flex h-13 w-13 shrink-0 items-center justify-center rounded-2xl border ${
                      canOpen
                        ? 'bg-[#0F3D2E] text-[#C9A24D] border-[#C9A24D]/40'
                        : 'bg-[#E9F0E8] dark:bg-[#0A2A20] text-[#1F6B52] dark:text-[#C9A24D] border-[#EFE8D8] dark:border-[#1E4F3D]'
                    }`}
                  >
                    <Stethoscope className="h-6 w-6" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <h3 className="text-base font-black text-[#0F3D2E] dark:text-[#F4EFE4] truncate">
                        {cat.name}
                      </h3>
                      <span className="shrink-0 text-[11px] font-bold text-[#1F6B52] dark:text-[#C9A24D]">
                        {formatLectureCountAr(count)}
                      </span>
                    </div>
                    {cat.description && (
                      <p className="mt-1 text-xs text-[#546B61] dark:text-[#A5C0B4] line-clamp-2 leading-relaxed">
                        {cat.description}
                      </p>
                    )}
                  </div>
                </div>

                {/* Course Status & Action Footer */}
                <div className="flex items-center justify-between gap-2 pt-2 border-t border-[#EFE8D8] dark:border-[#1E4F3D]">
                  {isAdminView ? (
                    <span className="inline-flex items-center gap-1.5 text-xs font-extrabold text-[#1F6B52] dark:text-[#C9A24D]">
                      <CheckCircle2 className="h-4 w-4" />
                      <span>متاح للإدارة</span>
                    </span>
                  ) : reqStatus === 'approved' ? (
                    <span className="inline-flex items-center gap-1.5 text-xs font-extrabold text-[#1F6B52] dark:text-[#4AD1A3]">
                      <CheckCircle2 className="h-4 w-4" />
                      <span>مفعّل ✓</span>
                    </span>
                  ) : reqStatus === 'pending' ? (
                    <span className="inline-flex items-center gap-1.5 text-xs font-extrabold text-[#9A7624] dark:text-[#C9A24D]">
                      <Clock className="h-4 w-4" />
                      <span>بانتظار الموافقة</span>
                    </span>
                  ) : reqStatus === 'suspended' ? (
                    <span className="inline-flex items-center gap-1.5 text-xs font-extrabold text-[#B33A3A] dark:text-red-300">
                      <Lock className="h-4 w-4" />
                      <span>موقوف</span>
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 text-xs font-bold text-[#546B61] dark:text-[#A5C0B4]">
                      <Lock className="h-3.5 w-3.5" />
                      <span>غير مشترك</span>
                    </span>
                  )}

                  {canOpen ? (
                    <button
                      type="button"
                      onClick={() => handleCourseClick(cat)}
                      className="inline-flex items-center gap-1.5 rounded-2xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white text-xs font-extrabold px-4 py-2.5 transition cursor-pointer"
                    >
                      <span>{isAdminView ? 'فتح الكورس' : 'دخول الكورس'}</span>
                      <ChevronLeft className="h-4 w-4" />
                    </button>
                  ) : reqStatus === 'pending' ? (
                    <span className="rounded-2xl bg-[#C9A24D]/15 text-[#9A7624] dark:text-[#C9A24D] text-xs font-bold px-3.5 py-2">
                      تم إرسال الطلب
                    </span>
                  ) : reqStatus === 'suspended' ? (
                    <span className="rounded-2xl bg-[#B33A3A]/10 text-[#B33A3A] dark:text-red-300 text-xs font-bold px-3.5 py-2">
                      تواصل مع الدكتور
                    </span>
                  ) : (
                    <button
                      type="button"
                      onClick={() => handleCourseClick(cat)}
                      className="inline-flex items-center gap-1.5 rounded-2xl bg-[#C9A24D] hover:bg-[#d8b464] text-[#0F241D] text-xs font-extrabold px-4 py-2.5 transition cursor-pointer"
                    >
                      <span>طلب انضمام</span>
                    </button>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}

      {/* Modal: Add Medical Course */}
      <Modal
        open={showAddCourse}
        onClose={() => setShowAddCourse(false)}
        title="إضافة كورس طبي جديد"
        subtitle="سيظهر الكورس فوراً في القائمة الرئيسية لجميع الطلاب"
      >
        <form onSubmit={handleCreateCourse} className="space-y-4">
          <div>
            <label htmlFor="course-name" className="block text-xs font-extrabold mb-1.5">
              اسم الكورس الطبي
            </label>
            <input
              id="course-name"
              type="text"
              required
              value={newCourseName}
              onChange={(e) => setNewCourseName(e.target.value)}
              placeholder="مثال: Internal Medicine أو Anatomy"
              className="w-full rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] px-4 py-3 text-sm focus:outline-none focus:border-[#0F3D2E] dark:focus:border-[#C9A24D]"
            />
          </div>
          <div>
            <label htmlFor="course-desc" className="block text-xs font-extrabold mb-1.5">
              وصف الكورس (اختياري)
            </label>
            <textarea
              id="course-desc"
              rows={3}
              value={newCourseDesc}
              onChange={(e) => setNewCourseDesc(e.target.value)}
              placeholder="نبذة مختصرة عن محتوى الكورس والمحاضرات..."
              className="w-full rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] px-4 py-3 text-sm focus:outline-none focus:border-[#0F3D2E] dark:focus:border-[#C9A24D]"
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setShowAddCourse(false)}
              className="rounded-2xl bg-[#E9F0E8] dark:bg-[#0A2A20] px-4 py-2.5 text-xs font-bold cursor-pointer"
            >
              إلغاء
            </button>
            <button
              type="submit"
              disabled={savingCourse}
              className="rounded-2xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white px-5 py-2.5 text-xs font-extrabold cursor-pointer disabled:opacity-60"
            >
              {savingCourse ? 'جاري الحفظ...' : 'حفظ الكورس'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Modal: Upload Lecture via 5TB Google Drive 2MB Resumable Upload */}
      <Modal
        open={showUploadModal}
        onClose={() => setShowUploadModal(false)}
        title="رفع محاضرة جديدة إلى مخزن الـ 5TB"
        subtitle="يتم الرفع بنظام Resumable Upload بدفعات 2MB مع تفعيل البث المباشر تلقائياً"
      >
        <form onSubmit={handleUploadSubmit} className="space-y-4">
          <div>
            <label htmlFor="upload-course" className="block text-xs font-extrabold mb-1.5">
              الكورس الطبي
            </label>
            <select
              id="upload-course"
              value={uploadCatId}
              onChange={(e) => setUploadCatId(e.target.value)}
              className="w-full rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] px-4 py-3 text-sm font-bold focus:outline-none focus:border-[#0F3D2E]"
            >
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label htmlFor="upload-title" className="block text-xs font-extrabold mb-1.5">
              عنوان المحاضرة
            </label>
            <input
              id="upload-title"
              type="text"
              required
              value={uploadTitle}
              onChange={(e) => setUploadTitle(e.target.value)}
              placeholder="مثال: المحاضرة 4 - المضادات الحيوية"
              className="w-full rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] px-4 py-3 text-sm focus:outline-none focus:border-[#0F3D2E]"
            />
          </div>

          <div>
            <label htmlFor="upload-desc" className="block text-xs font-extrabold mb-1.5">
              وصف مختصر (اختياري)
            </label>
            <textarea
              id="upload-desc"
              rows={2}
              value={uploadDesc}
              onChange={(e) => setUploadDesc(e.target.value)}
              placeholder="أهم النقاط الطبية التي تغطيها المحاضرة..."
              className="w-full rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] px-4 py-3 text-sm focus:outline-none focus:border-[#0F3D2E]"
            />
          </div>

          <div>
            <label htmlFor="upload-video-file" className="block text-xs font-extrabold mb-1.5">
              ملف الفيديو (MP4 / MOV)
            </label>
            <input
              id="upload-video-file"
              type="file"
              accept="video/*"
              required
              onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
              className="w-full rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-dashed border-[#C9A24D] px-4 py-3 text-xs file:ml-3 file:rounded-xl file:border-0 file:bg-[#0F3D2E] file:px-3 file:py-1.5 file:text-xs file:font-bold file:text-white cursor-pointer"
            />
            {uploadFile && (
              <p className="mt-1.5 text-[11px] font-bold text-[#1F6B52] dark:text-[#C9A24D]">
                الملف المختار: {uploadFile.name} ({formatBytesToMb(uploadFile.size)} MB)
              </p>
            )}
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setShowUploadModal(false)}
              className="rounded-2xl bg-[#E9F0E8] dark:bg-[#0A2A20] px-4 py-2.5 text-xs font-bold cursor-pointer"
            >
              إلغاء
            </button>
            <button
              type="submit"
              disabled={!uploadFile || !uploadTitle.trim()}
              className="rounded-2xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white px-5 py-2.5 text-xs font-extrabold cursor-pointer disabled:opacity-50"
            >
              بدء الرفع (2MB/دفعة)
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
