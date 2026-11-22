import { useEffect, useMemo, useState } from 'react';
import {
  BookOpen,
  CheckCircle2,
  Download,
  Hourglass,
  Lock,
  Play,
  PlayCircle,
  Plus,
  Search,
  Trash2,
  Upload,
  Video,
  X,
} from 'lucide-react';
import type { Category, Lecture } from '../data/types';
import { useDataStore, enrollmentStatus } from '../data/dataStore';
import { useAccess } from '../lib/session';
import { DEMO_VIDEO_URL } from '../data/seed';
import { DEMO_MODE } from '../config';
import { uploadVideoToDrive } from '../lib/drive';
import { formatHms, formatSizeMb, formatMmSs } from '../lib/format';
import { ConfirmDialog, EmptyState, Modal, ProgressBar } from '../components/ui';
import { VideoPlayer } from '../components/VideoPlayer';

export function LibraryScreen() {
  const { session, isAdminView, adminBypass } = useAccess();
  const uid = session?.user?.uid ?? 'guest';
  const data = useDataStore((s) => s.data);
  const downloadTasks = useDataStore((s) => s.downloadTasks);
  const addCategory = useDataStore((s) => s.addCategory);
  const deleteCategory = useDataStore((s) => s.deleteCategory);
  const addLecture = useDataStore((s) => s.addLecture);
  const deleteLecture = useDataStore((s) => s.deleteLecture);
  const requestEnrollment = useDataStore((s) => s.requestEnrollment);
  const startDownload = useDataStore((s) => s.startDownload);
  const pauseDownload = useDataStore((s) => s.pauseDownload);
  const resumeDownload = useDataStore((s) => s.resumeDownload);
  const cancelDownload = useDataStore((s) => s.cancelDownload);

  const [selectedCatId, setSelectedCatId] = useState<string | null>(data.categories[0]?.id ?? null);
  const [search, setSearch] = useState('');
  const [playing, setPlaying] = useState<Lecture | null>(null);
  const [showAddCat, setShowAddCat] = useState(false);
  const [showUpload, setShowUpload] = useState(false);
  const [deleteCatTarget, setDeleteCatTarget] = useState<Category | null>(null);

  // Demo: ?play=<lectureId> opens the player directly (for verification/screenshots).
  useEffect(() => {
    const playId = new URLSearchParams(window.location.search).get('play');
    if (!playId) return;
    const lec = useDataStore.getState().data.lectures.find((l) => l.id === playId);
    if (lec) setPlaying(lec);
  }, []);

  const selectedCat = data.categories.find((c) => c.id === selectedCatId) ?? data.categories[0] ?? null;

  const enrolled = selectedCat ? enrollmentStatus(uid, selectedCat.id) : 'none';
  const canViewLectures = !!selectedCat && (adminBypass || enrolled === 'approved');

  const filteredLectures = useMemo(() => {
    if (!selectedCat) return [];
    const q = search.trim().toLowerCase();
    return data.lectures
      .filter((l) => l.categoryId === selectedCat.id)
      .filter((l) => !q || l.title.toLowerCase().includes(q) || l.description.toLowerCase().includes(q) || l.categoryName.toLowerCase().includes(q));
  }, [data.lectures, selectedCat, search]);

  const continueLecture = useMemo(() => {
    const entries = Object.entries(data.progress)
      .filter(([k, p]) => k.startsWith(`${uid}:`) && p.positionSeconds > 5 && !p.completed)
      .sort((a, b) => b[1].updatedAt - a[1].updatedAt);
    for (const [, p] of entries) {
      const lec = data.lectures.find((l) => l.id === p.lectureId);
      if (lec) return { lec, pos: p.positionSeconds };
    }
    return null;
  }, [data.progress, data.lectures, uid]);

  const catLectureCount = (catId: string) => data.lectures.filter((l) => l.categoryId === catId).length;

  return (
    <div className="library-wrap">
      {/* Admin tools */}
      {isAdminView && (
        <div className="card admin-tools">
          <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800, fontSize: 16 }}>أدوات الدكتور والمسؤول الأكاديمي</div>
          <div className="admin-tools-row">
            <button className="btn btn-outline" onClick={() => setShowAddCat(true)}>
              <Plus size={18} /> إضافة تصنيف / مادة
            </button>
            <button className="btn btn-gold" onClick={() => setShowUpload(true)}>
              <Upload size={18} /> رفع فيديو / محاضرة
            </button>
          </div>
          <div className="stats-strip">
            إحصائيات المحاضرات: المشاهدات: {data.lectures.length * 42 + 18} • الطلاب: {Math.max(12, data.studentRequests.filter((r) => r.status === 'approved').length)} • الإكمال: 88%
          </div>
        </div>
      )}

      {/* Search */}
      <div className="search-box">
        <span className="search-icon">
          <Search size={18} />
        </span>
        <input className="input" placeholder="ابحث عن محاضرة أو موضوع طبي..." value={search} onChange={(e) => setSearch(e.target.value)} />
        {search && (
          <button className="search-clear" onClick={() => setSearch('')} aria-label="مسح البحث">
            <X size={16} />
          </button>
        )}
      </div>

      {/* Continue watching */}
      {continueLecture && canViewLectures && (
        <div className="continue-card" onClick={() => setPlaying(continueLecture.lec)}>
          <div className="continue-play">
            <Play size={20} />
          </div>
          <div>
            <div className="continue-label">أكمل من حيث توقفت 🟡</div>
            <div className="continue-title">{continueLecture.lec.title}</div>
            <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.75)' }}>توقفت عند الدقيقة {formatMmSs(continueLecture.pos)}</div>
          </div>
          <span className="continue-pill">متابعة</span>
        </div>
      )}

      {/* Categories */}
      <div className="row-between">
        <div className="section-title">التصنيفات والكورسات الدراسية ({data.categories.length})</div>
        {isAdminView && selectedCat && (
          <button className="btn btn-sm btn-danger" onClick={() => setDeleteCatTarget(selectedCat)}>
            <Trash2 size={15} /> حذف كورس ({selectedCat.name})
          </button>
        )}
      </div>
      <div className="cat-chips">
        {data.categories.map((c) => {
          const st = enrollmentStatus(uid, c.id);
          const locked = !adminBypass && st !== 'approved';
          return (
            <button key={c.id} className={`cat-chip ${selectedCat?.id === c.id ? 'active' : ''}`} onClick={() => setSelectedCatId(c.id)}>
              {locked && <Lock size={14} />}
              {c.name}
            </button>
          );
        })}
      </div>

      {/* Content */}
      {!selectedCat ? (
        <EmptyState
          icon={<BookOpen size={28} />}
          title="لا توجد كورسات دراسية مضافة بعد"
          description={isAdminView ? 'ابدأ بإضافة أول كورس دراسي.' : 'لم يقم الدكتور بإضافة كورسات دراسية بعد'}
          action={isAdminView ? <button className="btn btn-gold" onClick={() => setShowAddCat(true)}>إضافة أول كورس دراسي</button> : null}
        />
      ) : !canViewLectures ? (
        <EnrollmentGate
          category={selectedCat}
          enrolled={enrolled}
          count={catLectureCount(selectedCat.id)}
          onRequest={() => requestEnrollment(uid, selectedCat.id, selectedCat.name, session?.user?.displayName ?? 'طالب', session?.user?.email ?? '')}
        />
      ) : filteredLectures.length === 0 ? (
        <EmptyState
          icon={<Video size={28} />}
          title="لا توجد محاضرات مرفوعة في هذا التصنيف بعد"
          action={isAdminView ? <button className="btn btn-gold" onClick={() => setShowUpload(true)}>رفع محاضرة من الهاتف</button> : null}
        />
      ) : (
        <div className="grid grid-cards">
          {filteredLectures.map((l) => (
            <LectureCard
              key={l.id}
              lecture={l}
              isAdmin={isAdminView}
              downloaded={data.downloads.some((d) => d.lectureId === l.id)}
              task={downloadTasks[l.id]}
              progress={data.progress[`${uid}:${l.id}`]}
              onPlay={() => setPlaying(l)}
              onDelete={() => deleteLecture(l.id)}
              onDownload={() => startDownload(l, uid)}
              onPause={() => pauseDownload(l.id)}
              onResume={() => resumeDownload(l.id)}
              onCancel={() => cancelDownload(l.id)}
            />
          ))}
        </div>
      )}

      {playing && <VideoPlayer lecture={playing} onClose={() => setPlaying(null)} />}

      <AddCategoryModal
        open={showAddCat}
        onClose={() => setShowAddCat(false)}
        onSubmit={(name, desc) => {
          const c = addCategory(name, desc, session?.user?.displayName ?? 'د. المشرف', uid);
          setSelectedCatId(c.id);
          setShowAddCat(false);
        }}
      />

      <UploadLectureModal
        open={showUpload}
        categories={data.categories}
        defaultCatId={selectedCat?.id}
        onClose={() => setShowUpload(false)}
        onSubmit={(input) => {
          addLecture({ ...input, uploadedByUid: uid, doctorName: session?.user?.displayName ?? 'د. المشرف' });
          setShowUpload(false);
        }}
      />

      <ConfirmDialog
        open={!!deleteCatTarget}
        title={`تأكيد حذف الكورس (${deleteCatTarget?.name ?? ''})`}
        message={`هل أنت متأكد من حذف كورس "${deleteCatTarget?.name ?? ''}" مع جميع محاضراته البالغ عددها (${deleteCatTarget ? catLectureCount(deleteCatTarget.id) : 0}) محاضرة؟`}
        confirmLabel="نعم، حذف الكورس"
        danger
        onConfirm={() => {
          if (deleteCatTarget) {
            deleteCategory(deleteCatTarget.id);
            setSelectedCatId(null);
          }
          setDeleteCatTarget(null);
        }}
        onClose={() => setDeleteCatTarget(null)}
      />
    </div>
  );
}

function LectureCard({
  lecture,
  isAdmin,
  downloaded,
  task,
  progress,
  onPlay,
  onDelete,
  onDownload,
  onPause,
  onResume,
  onCancel,
}: {
  lecture: Lecture;
  isAdmin: boolean;
  downloaded: boolean;
  task?: { status: string; progressPercent: number };
  progress?: { positionSeconds: number; completed: boolean };
  onPlay: () => void;
  onDelete: () => void;
  onDownload: () => void;
  onPause: () => void;
  onResume: () => void;
  onCancel: () => void;
}) {
  const watchBadge = progress?.completed ? (
    <span className="watch-badge wb-done">🟢 مكتملة</span>
  ) : progress && progress.positionSeconds > 5 ? (
    <span className="watch-badge wb-progress">🟡 قيد المشاهدة ({formatMmSs(progress.positionSeconds)})</span>
  ) : (
    <span className="watch-badge wb-new">⚪ لم تبدأ</span>
  );

  const downloading = task && (task.status === 'DOWNLOADING' || task.status === 'PAUSED');

  return (
    <div className="card card-hover lecture-card">
      <div className="lecture-media" onClick={onPlay}>
        <div className="lecture-media-top">
          <span className="cat-pill">{lecture.categoryName}</span>
          <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            {downloaded && <span className="watch-badge" style={{ background: 'rgba(201,162,77,0.22)', color: '#7a5900' }}>محفوظ بالخزنة</span>}
            {watchBadge}
          </div>
        </div>
        <div className="play-circle">
          <Play size={26} />
        </div>
        <div className="lecture-media-bottom">
          <span>
            د. {lecture.doctorName || 'Abdallah.surour'} · {formatHms(lecture.durationSeconds)}
          </span>
          <span>{formatSizeMb(lecture.fileSizeMb)}</span>
        </div>
      </div>
      <div className="lecture-body">
        <div className="lecture-title">{lecture.title}</div>
        {lecture.description && lecture.description !== 'محاضرة مرئية' && <div className="lecture-desc clamp-2">{lecture.description}</div>}
        <div className="lecture-actions">
          <button className="btn btn-primary grow" onClick={onPlay}>
            <PlayCircle size={18} color="var(--gold-accent)" /> {progress && progress.positionSeconds > 5 && !progress.completed ? 'أكمل المشاهدة' : 'مشاهدة'}
          </button>
          <button className="btn btn-outline" onClick={onDownload} disabled={!!downloading || downloaded} style={{ height: 46 }}>
            <Download size={16} /> {downloaded ? 'تم التنزيل ✓' : downloading ? `جاري التنزيل (${task?.progressPercent ?? 0}%)` : 'تنزيل داخل التطبيق'}
          </button>
          {isAdmin && (
            <button className="btn btn-danger btn-sm" onClick={onDelete} aria-label="حذف المحاضرة" style={{ padding: '0 12px' }}>
              <Trash2 size={16} />
            </button>
          )}
        </div>
        {downloading && (
          <div style={{ background: 'rgba(233,240,232,0.7)', borderRadius: 16, padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
              <span>{task?.status === 'PAUSED' ? 'متوقف مؤقتاً' : 'جاري التنزيل'} ({task?.progressPercent}%)</span>
              <span style={{ fontWeight: 700 }}>{task?.progressPercent}%</span>
            </div>
            <ProgressBar percent={task?.progressPercent ?? 0} height={7} />
            <div style={{ display: 'flex', gap: 8 }}>
              {task?.status === 'PAUSED' ? (
                <button className="btn btn-sm btn-primary" onClick={onResume}>استمرار</button>
              ) : (
                <button className="btn btn-sm btn-outline" onClick={onPause}>إيقاف مؤقت</button>
              )}
              <button className="btn btn-sm btn-danger" onClick={onCancel}>إلغاء</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function EnrollmentGate({ category, enrolled, count, onRequest }: { category: Category; enrolled: string; count: number; onRequest: () => void }) {
  const pending = enrolled === 'pending';
  const suspended = enrolled === 'suspended';
  return (
    <div className="card gate-card">
      <div className="gate-icon">
        {pending ? <Hourglass size={30} /> : <Lock size={30} />}
      </div>
      <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800, fontSize: 18 }}>كورس: {category.name}</div>
      <div className="helper" style={{ maxWidth: 420 }}>{category.description}</div>
      <div className="chip">عدد المحاضرات داخل هذا الكورس: {count} محاضرة</div>
      {pending ? (
        <>
          <div className="gate-note">طلب انضمامك لهذا الكورس قيد المراجعة الآن</div>
          <div className="helper">بمجرد موافقة الدكتور أو المسؤول على طلبك، ستفتح لك جميع محاضرات كورس ({category.name}) فوراً.</div>
          <button className="btn btn-outline" disabled>تحديث حالة الموافقة على الكورس</button>
        </>
      ) : suspended ? (
        <>
          <div className="gate-note gate-note-danger">تم إيقاف أو رفض طلب انضمامك السابق لهذا الكورس. يمكنك إرسال طلب انضمام جديد للمراجعة.</div>
          <button className="btn btn-primary" onClick={onRequest}>إعادة إرسال طلب الانضمام للكورس</button>
        </>
      ) : (
        <>
          <div className="helper">لمشاهدة وتنزيل محاضرات كورس ({category.name})، يرجى الضغط على الزر بالأسفل لإرسال طلب انضمام للدكتور.</div>
          <button className="btn btn-primary" onClick={onRequest}>طلب الانضمام إلى كورس {category.name}</button>
        </>
      )}
    </div>
  );
}

function AddCategoryModal({ open, onClose, onSubmit }: { open: boolean; onClose: () => void; onSubmit: (name: string, desc: string) => void }) {
  const [name, setName] = useState('');
  const [desc, setDesc] = useState('');
  return (
    <Modal
      open={open}
      title="إضافة تصنيف / كورس دراسي جديد"
      onClose={onClose}
      actions={
        <>
          <button className="btn btn-gold" disabled={!name.trim()} onClick={() => { onSubmit(name, desc); setName(''); setDesc(''); }}>
            حفظ التصنيف
          </button>
          <button className="btn btn-outline" onClick={onClose}>إلغاء</button>
        </>
      }
    >
      <p className="helper">اكتب اسم الكورس أو المادة الطبية. سيحتاج كل طالب لطلب الانضمام لهذا الكورس وموافقة المسؤول لفتح محاضراته:</p>
      <div className="field">
        <label className="label">اسم الكورس / التصنيف الدراسي</label>
        <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="مثال: Pharmacology, Genetics, Pathology..." />
      </div>
      <div className="field">
        <label className="label">وصف مختصر للكورس أو الفصل (اختياري)</label>
        <input className="input" value={desc} onChange={(e) => setDesc(e.target.value)} />
      </div>
    </Modal>
  );
}

function UploadLectureModal({
  open,
  categories,
  defaultCatId,
  onClose,
  onSubmit,
}: {
  open: boolean;
  categories: Category[];
  defaultCatId?: string;
  onClose: () => void;
  onSubmit: (input: {
    title: string;
    description: string;
    categoryId: string;
    categoryName: string;
    durationSeconds: number;
    fileSizeMb: number;
    videoSourceUri: string;
  }) => void;
}) {
  const [catId, setCatId] = useState(defaultCatId ?? categories[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [chapter, setChapter] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState('');
  const cat = categories.find((c) => c.id === catId);
  const valid = !!title.trim() && !!catId && (DEMO_MODE || !!file) && !uploading;

  const doUpload = async () => {
    if (!cat) return;
    setError('');
    const description = chapter ? `الفصل: ${chapter}` : 'محاضرة مرئية';
    if (DEMO_MODE) {
      onSubmit({ title, description, categoryId: cat.id, categoryName: cat.name, durationSeconds: 42 * 60, fileSizeMb: 320, videoSourceUri: DEMO_VIDEO_URL });
      setTitle(''); setChapter(''); setFile(null);
      return;
    }
    if (!file) { setError('اختر ملف الفيديو أولاً'); return; }
    setUploading(true); setProgress(0);
    try {
      const durationSeconds = await new Promise<number>((resolve) => {
        try {
          const v = document.createElement('video');
          v.preload = 'metadata';
          v.onloadedmetadata = () => { resolve(Number.isFinite(v.duration) ? Math.round(v.duration) : 0); URL.revokeObjectURL(v.src); };
          v.onerror = () => resolve(0);
          v.src = URL.createObjectURL(file);
        } catch { resolve(0); }
      });
      const res = await uploadVideoToDrive(file, setProgress);
      onSubmit({ title, description, categoryId: cat.id, categoryName: cat.name, durationSeconds: durationSeconds || 60, fileSizeMb: res.sizeMb, videoSourceUri: res.url });
      setTitle(''); setChapter(''); setFile(null); setProgress(0);
    } catch (e) {
      const msg = (e as Error)?.message || '';
      if (msg === 'drive_token_missing') setError('انتهت صلاحية إذن Google — سجّل الخروج ثم الدخول مجدداً لمنح صلاحية Drive.');
      else setError('تعذّر رفع الفيديو إلى Drive (' + msg + '). تأكد من منح صلاحية Google Drive عند تسجيل الدخول.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <Modal
      open={open}
      title="رفع فيديو / محاضرة جديدة"
      onClose={onClose}
      actions={
        <>
          <button className="btn btn-gold" disabled={!valid} onClick={doUpload}>
            {uploading ? `جاري الرفع (${progress}%)` : 'رفع المحاضرة'}
          </button>
          <button className="btn btn-outline" onClick={onClose} disabled={uploading}>إلغاء</button>
        </>
      }
    >
      <div className="field">
        <label className="label">1. اختيار الكورس:</label>
        <div className="cat-chips">
          {categories.map((c) => (
            <button key={c.id} className={`cat-chip ${catId === c.id ? 'active' : ''}`} onClick={() => setCatId(c.id)}>
              {c.name}
            </button>
          ))}
        </div>
      </div>
      <div className="field">
        <label className="label">2. عنوان المحاضرة والفصل (Chapter):</label>
        <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="عنوان المحاضرة" />
        <input className="input" value={chapter} onChange={(e) => setChapter(e.target.value)} placeholder="الفصل أو المجلد الفرعي (اختياري)" />
      </div>
      <div className="field">
        <label className="label">3. ملف الفيديو (يُرفع إلى Google Drive):</label>
        <input type="file" accept="video/*" onChange={(e) => setFile(e.target.files?.[0] ?? null)} disabled={uploading} />
        {file && <div className="helper">تم اختيار: {file.name} ({Math.round(file.size / 1048576)} MB)</div>}
        {DEMO_MODE && (
          <div className="gate-note" style={{ background: 'var(--light-mint)', color: 'var(--emerald-primary)' }}>
            الوضع التجريبي — سيُستخدم فيديو تجريبي بدل الرفع إلى Drive.
          </div>
        )}
        {uploading && <ProgressBar percent={progress} height={7} />}
        {error && <div className="field-error">{error}</div>}
      </div>
    </Modal>
  );
}
