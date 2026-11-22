import { useEffect, useMemo, useState } from 'react';
import { Clock, ClipboardList, Eye, FolderDown, Lock, PictureInPicture2, Plus, Trash2, Upload } from 'lucide-react';
import type { Category, ExamItem } from '../data/types';
import { examStatus } from '../data/types';
import { useDataStore, enrollmentStatus } from '../data/dataStore';
import { useAccess } from '../lib/session';
import { useAppStore } from '../store/appStore';
import { formatCountdown, formatDateTime } from '../lib/format';
import { ConfirmDialog, EmptyState, Modal } from '../components/ui';

function useTicker(): number {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, []);
  return now;
}

const MCQ = [
  {
    q: '1. Which receptor is primarily responsible for bronchodilation in smooth muscle?',
    options: ['Alpha-1', 'Beta-1', 'Beta-2 adrenergic', 'Muscarinic M3'],
    correct: 2,
  },
  {
    q: '2. First-pass hepatic metabolism primarily affects drugs administered via which route?',
    options: ['IV', 'Sublingual', 'Oral (PO)', 'Inhalation'],
    correct: 2,
  },
  {
    q: '3. What is the specific antidote for Acetaminophen (Paracetamol) toxicity?',
    options: ['Naloxone', 'N-acetylcysteine (NAC)', 'Flumazenil', 'Atropine'],
    correct: 1,
  },
];

export function ExamsScreen() {
  const { session, isAdminView, adminBypass } = useAccess();
  const uid = session?.user?.uid ?? 'guest';
  const data = useDataStore((s) => s.data);
  const deleteExam = useDataStore((s) => s.deleteExam);
  const addExam = useDataStore((s) => s.addExam);
  const requestEnrollment = useDataStore((s) => s.requestEnrollment);
  const pushToast = useAppStore((s) => s.pushToast);
  const now = useTicker();

  const [selectedCatId, setSelectedCatId] = useState<string | null>(data.categories[0]?.id ?? null);
  const [showUpload, setShowUpload] = useState(false);
  const [showMcq, setShowMcq] = useState(false);
  const [preview, setPreview] = useState<ExamItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ExamItem | null>(null);

  const selectedCat = data.categories.find((c) => c.id === selectedCatId) ?? data.categories[0] ?? null;
  const enrolled = selectedCat ? enrollmentStatus(uid, selectedCat.id) : 'none';
  const canView = !!selectedCat && (adminBypass || enrolled === 'approved');

  const exams = useMemo(() => {
    return data.exams
      .filter((e) => (selectedCat ? e.categoryId === selectedCat.id : true))
      .filter((e) => isAdminView || examStatus(e, now) !== 'expired')
      .sort((a, b) => a.startTimeMillis - b.startTimeMillis);
  }, [data.exams, selectedCat, isAdminView, now]);

  return (
    <div className="stack">
      <div className="screen-head">
        <div className="screen-title">الاختبارات (ملفات PDF بوقت محدد)</div>
        <button className="btn btn-outline btn-sm" onClick={() => setShowMcq(true)} style={{ borderColor: 'var(--gold-accent)', background: 'rgba(201,162,77,0.14)' }}>
          <ClipboardList size={16} /> اختبار MCQ تفاعلي
        </button>
      </div>

      {isAdminView && (
        <button className="btn btn-gold btn-block" style={{ height: 50 }} onClick={() => setShowUpload(true)}>
          <Upload size={18} /> رفع اختبار PDF جديد وتحديد موعده
        </button>
      )}

      {/* Category chips */}
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

      {!selectedCat ? (
        <EmptyState icon={<ClipboardList size={28} />} title="لا توجد اختبارات PDF مرفوعة حالياً" />
      ) : !canView ? (
        <div className="card gate-card">
          <div className="gate-icon">{enrolled === 'pending' ? <Clock size={30} /> : <Lock size={30} />}</div>
          <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800, fontSize: 18 }}>اختبارات كورس: {selectedCat.name}</div>
          {enrolled === 'pending' ? (
            <>
              <div className="gate-note">طلب انضمامك لكورس ({selectedCat.name}) قيد المراجعة حالياً. ستفتح لك اختبارات هذا الكورس فور موافقة الدكتور.</div>
              <button className="btn btn-outline" disabled>تحديث حالة القبول</button>
            </>
          ) : (
            <>
              <div className="helper">لدخول وتحميل اختبارات كورس ({selectedCat.name})، يجب أولاً الانضمام للكورس وموافقة المسؤول.</div>
              <button className="btn btn-primary" onClick={() => requestEnrollment(uid, selectedCat.id, selectedCat.name, session?.user?.displayName ?? 'طالب', session?.user?.email ?? '')}>
                طلب الانضمام لكورس {selectedCat.name}
              </button>
            </>
          )}
        </div>
      ) : exams.length === 0 ? (
        <EmptyState icon={<PictureInPicture2 size={28} />} title="لا توجد اختبارات PDF مرفوعة حالياً" />
      ) : (
        <div className="stack">
          {exams.map((e) => (
            <ExamCard
              key={e.id}
              exam={e}
              now={now}
              isAdmin={isAdminView}
              onPreview={() => setPreview(e)}
              onDownload={() => pushToast(`تم تجهيز ملف الاختبار PDF (${e.pdfFileName}) بنجاح`, 'success')}
              onDelete={() => setDeleteTarget(e)}
            />
          ))}
        </div>
      )}

      <UploadExamModal
        open={showUpload}
        categories={data.categories}
        defaultCatId={selectedCat?.id}
        onClose={() => setShowUpload(false)}
        onSubmit={(input) => {
          addExam({ ...input, uploadedByUid: uid, doctorName: session?.user?.displayName ?? 'د. المشرف' });
          setShowUpload(false);
        }}
      />

      <McqModal open={showMcq} onClose={() => setShowMcq(false)} courseName={selectedCat?.name ?? 'Pharmacology'} />

      <Modal
        open={!!preview}
        title={preview ? `${preview.categoryName} • معاينة مباشرة داخل التطبيق` : ''}
        onClose={() => setPreview(null)}
        large
        actions={
          <>
            <button className="btn btn-gold" onClick={() => { pushToast('تم تجهيز ملف الاختبار PDF وحفظه في التنزيلات', 'success'); setPreview(null); }}>
              <FolderDown size={18} /> حفظ في التنزيلات
            </button>
            <button className="btn btn-outline" onClick={() => setPreview(null)}>إغلاق</button>
          </>
        }
      >
        <div className="stack" style={{ alignItems: 'center' }}>
          {[1, 2, 3].map((i) => (
            <div key={i} className="card" style={{ width: '100%', padding: 24, textAlign: 'center', background: '#fff', minHeight: 120 }}>
              <div style={{ fontWeight: 700, color: 'var(--color-text-muted)' }}>صفحة {i} من 3</div>
              <div className="helper">{preview?.title}</div>
            </div>
          ))}
        </div>
      </Modal>

      <ConfirmDialog
        open={!!deleteTarget}
        title="تأكيد حذف الاختبار"
        message={`هل أنت متأكد من حذف اختبار "${deleteTarget?.title ?? ''}" نهائياً؟`}
        confirmLabel="نعم، حذف الاختبار"
        danger
        onConfirm={() => { if (deleteTarget) deleteExam(deleteTarget.id); setDeleteTarget(null); }}
        onClose={() => setDeleteTarget(null)}
      />
    </div>
  );
}

function ExamCard({ exam, now, isAdmin, onPreview, onDownload, onDelete }: { exam: ExamItem; now: number; isAdmin: boolean; onPreview: () => void; onDownload: () => void; onDelete: () => void }) {
  const status = examStatus(exam, now);
  const statusLabel = status === 'open' ? 'متاح الآن' : status === 'upcoming' ? 'قادم قريباً' : 'منتهي';
  const statusClass = status === 'open' ? 'badge-green' : status === 'upcoming' ? 'badge-gold' : 'badge-red';
  const countdown =
    status === 'open'
      ? `⏳ متبقي على الإغلاق: ${formatCountdown(exam.endTimeMillis - now)}`
      : status === 'upcoming'
        ? `⏳ يبدأ بعد: ${formatCountdown(exam.startTimeMillis - now)}`
        : 'انتهى وقت الاختبار';

  const actionsEnabled = status === 'open' || isAdmin;
  return (
    <div className="card exam-card">
      <div className="exam-top">
        <div className="exam-pdf-ic">
          <ClipboardList size={28} />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 4 }}>
            <span className="badge badge-gold">{exam.categoryName}</span>
            <span className={`badge ${statusClass}`}>{statusLabel}</span>
          </div>
          <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800, fontSize: 15 }}>{exam.title}</div>
        </div>
        {isAdmin && (
          <button className="btn btn-danger btn-sm" onClick={onDelete} aria-label="حذف الاختبار">
            <Trash2 size={15} />
          </button>
        )}
      </div>

      {exam.description && <div className="helper">{exam.description}</div>}

      <div className={`exam-countdown ${status}`}>{countdown}</div>

      <div className="schedule-box">
        <div className="schedule-row">
          <Clock size={15} color="var(--emerald-light-accent)" /> يبدأ في: {formatDateTime(exam.startTimeMillis)}
        </div>
        <div className="schedule-row">
          <Clock size={15} color="var(--coral)" /> ينتهي في: {formatDateTime(exam.endTimeMillis)}
        </div>
        <div className="helper">
          د. {exam.doctorName || 'Abdallah.surour'} • حجم الملف: {exam.fileSizeKb} KB
        </div>
      </div>

      {actionsEnabled ? (
        <div className="stack-sm">
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-outline grow" onClick={onPreview}>
              <Eye size={16} /> معاينة الاختبار
            </button>
          </div>
          <button className="btn btn-gold btn-block" onClick={onDownload}>
            تنزيل ملف الاختبار PDF
          </button>
        </div>
      ) : (
        <button className="btn btn-outline btn-block" disabled>
          <Clock size={16} /> {status === 'upcoming' ? 'يُفتح تلقائياً في الموعد المحدد أعلاه' : 'انتهى وقت الاختبار ولم يعد متاحاً للتحميل'}
        </button>
      )}
    </div>
  );
}

function McqModal({ open, onClose, courseName }: { open: boolean; onClose: () => void; courseName: string }) {
  const [answers, setAnswers] = useState<Record<number, number>>({});
  const [submitted, setSubmitted] = useState(false);

  const reset = () => { setAnswers({}); setSubmitted(false); };
  const score = MCQ.reduce((acc, q, i) => acc + (answers[i] === q.correct ? 1 : 0), 0);
  const pct = Math.round((score / MCQ.length) * 100);

  return (
    <Modal
      open={open}
      title={`اختبار تفاعلي سريع (MCQ) — ${courseName}`}
      onClose={() => { reset(); onClose(); }}
      large
      actions={
        submitted ? (
          <button className="btn btn-primary" onClick={() => { reset(); onClose(); }}>إغلاق</button>
        ) : (
          <>
            <button className="btn btn-gold" disabled={Object.keys(answers).length < MCQ.length} onClick={() => setSubmitted(true)}>
              تصحيح فوري وإظهار النتيجة
            </button>
            <button className="btn btn-outline" onClick={() => { reset(); onClose(); }}>إلغاء</button>
          </>
        )
      }
    >
      {submitted && (
        <div className="gate-note" style={{ background: pct >= 67 ? 'var(--light-mint)' : 'var(--danger-bg)', color: pct >= 67 ? 'var(--emerald-primary)' : 'var(--coral)', fontWeight: 800 }}>
          نتيجتك الفورية: {score} من {MCQ.length} ({pct}%)
        </div>
      )}
      {MCQ.map((q, i) => (
        <div key={i} className="stack-sm">
          <div style={{ fontFamily: 'var(--font-head)', fontWeight: 700, fontSize: 14 }}>{q.q}</div>
          {q.options.map((opt, oi) => {
            let cls = 'mcq-option';
            if (submitted) {
              if (oi === q.correct) cls += ' correct';
              else if (answers[i] === oi) cls += ' wrong';
            }
            return (
              <button key={oi} className={cls} onClick={() => !submitted && setAnswers((a) => ({ ...a, [i]: oi }))} style={answers[i] === oi && !submitted ? { borderColor: 'var(--emerald-primary)', background: 'var(--light-mint)', fontWeight: 700 } : undefined}>
                <span style={{ width: 18, height: 18, borderRadius: '50%', border: '2px solid var(--medical-outline)', display: 'inline-grid', placeItems: 'center' }}>
                  {answers[i] === oi && <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--emerald-primary)' }} />}
                </span>
                {opt}
                {submitted && oi === q.correct && ' ✓'}
              </button>
            );
          })}
        </div>
      ))}
    </Modal>
  );
}

function UploadExamModal({ open, categories, defaultCatId, onClose, onSubmit }: {
  open: boolean;
  categories: Category[];
  defaultCatId?: string;
  onClose: () => void;
  onSubmit: (input: { title: string; description: string; categoryId: string; categoryName: string; pdfFileName: string; fileSizeKb: number; startTimeMillis: number; endTimeMillis: number }) => void;
}) {
  const [catId, setCatId] = useState(defaultCatId ?? categories[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [desc, setDesc] = useState('');
  const [durationH, setDurationH] = useState(2);
  const cat = categories.find((c) => c.id === catId);
  const valid = !!title.trim() && !!catId;

  return (
    <Modal
      open={open}
      title="رفع اختبار PDF جديد وتحديد موعده"
      onClose={onClose}
      actions={
        <>
          <button className="btn btn-gold" disabled={!valid} onClick={() => {
            if (!cat) return;
            const start = Date.now() - 60_000;
            onSubmit({ title, description: desc, categoryId: cat.id, categoryName: cat.name, pdfFileName: `${title.slice(0, 30)}.pdf`, fileSizeKb: 420, startTimeMillis: start, endTimeMillis: start + durationH * 3600_000 });
            setTitle(''); setDesc('');
          }}>حفظ ورفع الاختبار</button>
          <button className="btn btn-outline" onClick={onClose}>إلغاء</button>
        </>
      }
    >
      <div className="field">
        <label className="label">اختر الكورس الدراسي:</label>
        <div className="cat-chips">
          {categories.map((c) => (
            <button key={c.id} className={`cat-chip ${catId === c.id ? 'active' : ''}`} onClick={() => setCatId(c.id)}>{c.name}</button>
          ))}
        </div>
      </div>
      <div className="field">
        <label className="label">عنوان الاختبار</label>
        <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="مثال: اختبار الفارماكولوجي النصفي" />
      </div>
      <div className="field">
        <label className="label">تعليمات الاختبار للطلاب (اختياري)</label>
        <input className="input" value={desc} onChange={(e) => setDesc(e.target.value)} />
      </div>
      <div className="field">
        <label className="label">أو اختر مدة سريعة من وقت البداية:</label>
        <div className="cat-chips">
          {[0.5, 1, 2, 24].map((h) => (
            <button key={h} className={`cat-chip ${durationH === h ? 'active' : ''}`} onClick={() => setDurationH(h)}>
              {h < 1 ? '30 دقيقة' : h === 1 ? 'ساعة واحدة' : h === 2 ? 'ساعتان' : '24 ساعة'}
            </button>
          ))}
        </div>
      </div>
      <div className="gate-note" style={{ background: 'var(--light-mint)', color: 'var(--emerald-primary)' }}>
        <Plus size={14} /> سيتم توليد نموذج PDF تجريبي للاختبار في هذه النسخة.
      </div>
    </Modal>
  );
}
