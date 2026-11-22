import { useMemo, useState } from 'react';
import {
  CheckCircle2,
  ChevronDown,
  CircleSlash,
  FolderOpen,
  FolderDown,
  Search,
  Trash2,
  UserCheck,
  UserX,
  Users,
} from 'lucide-react';
import { useDataStore } from '../data/dataStore';
import { useAccess } from '../lib/session';
import { formatMmSs } from '../lib/format';
import { EmptyState, ProgressBar } from '../components/ui';
import { useAppStore } from '../store/appStore';

export function DownloadsAdminScreen() {
  const { isAdminView } = useAccess();
  return isAdminView ? <AdminPanel /> : <StudentDownloads />;
}

function StudentDownloads() {
  const data = useDataStore((s) => s.data);
  const downloadTasks = useDataStore((s) => s.downloadTasks);
  const pauseDownload = useDataStore((s) => s.pauseDownload);
  const resumeDownload = useDataStore((s) => s.resumeDownload);
  const cancelDownload = useDataStore((s) => s.cancelDownload);
  const removeDownload = useDataStore((s) => s.removeDownload);
  const pushToast = useAppStore((s) => s.pushToast);

  const activeTasks = Object.values(downloadTasks).filter((t) => t.status === 'DOWNLOADING' || t.status === 'PAUSED');

  const folders = useMemo(() => {
    const map = new Map<string, typeof data.downloads>();
    for (const d of data.downloads) {
      const arr = map.get(d.categoryName) ?? [];
      arr.push(d);
      map.set(d.categoryName, arr);
    }
    return Array.from(map.entries());
  }, [data.downloads]);

  const [openFolders, setOpenFolders] = useState<Record<string, boolean>>({});

  const totalMb = data.downloads.reduce((a, d) => a + d.fileSizeMb, 0);

  return (
    <div className="stack">
      <div className="card card-pad" style={{ background: 'rgba(233,240,232,0.65)' }}>
        <div className="row">
          <div className="gate-icon" style={{ width: 46, height: 46, background: 'var(--emerald-primary)', color: '#fff' }}>
            <FolderDown size={22} />
          </div>
          <div>
            <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800, fontSize: 16 }}>مجلدات التنزيلات حسب الكورس</div>
            <div className="helper">مرتبة في مجلدات حسب الكورسات المشترك بها للمشاهدة بدون إنترنت.</div>
          </div>
        </div>
        <div className="helper" style={{ marginTop: 8 }}>
          المساحة المستخدمة: {Math.round(totalMb)} MB • ترتبط صلاحية التنزيلات بفترة الاشتراك
        </div>
      </div>

      {activeTasks.length > 0 && (
        <div className="card panel-card">
          <div className="panel-title">التنزيلات الجارية والمتوقفة مؤقتاً ({activeTasks.length}):</div>
          {activeTasks.map((t) => (
            <div key={t.lectureId} style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingBottom: 10, borderBottom: '1px solid var(--color-outline)' }}>
              <div style={{ fontWeight: 700 }}>{t.lectureTitle}</div>
              <div className="helper">مجلد الكورس: {t.categoryName} • {t.status === 'PAUSED' ? 'متوقف مؤقتاً' : 'جاري التنزيل'} ({t.progressPercent}%)</div>
              <ProgressBar percent={t.progressPercent} height={8} />
              <div className="row-actions" style={{ marginInlineStart: 0 }}>
                {t.status === 'PAUSED' ? (
                  <button className="btn btn-sm btn-primary" onClick={() => resumeDownload(t.lectureId)}>▶️ استمرار (من {t.progressPercent}%)</button>
                ) : (
                  <button className="btn btn-sm btn-outline" onClick={() => pauseDownload(t.lectureId)}>|| إيقاف مؤقت</button>
                )}
                <button className="btn btn-sm btn-danger" onClick={() => cancelDownload(t.lectureId)}>إلغاء التنزيل</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {folders.length === 0 ? (
        <EmptyState
          icon={<FolderOpen size={32} />}
          title="لا توجد محاضرات محملة داخل التطبيق حالياً"
          description='اضغط على زر "تنزيل داخل التطبيق" أسفل أي محاضرة في كورساتك المفعلة لحفظها داخل مجلد الكورس الخاص بها.'
        />
      ) : (
        folders.map(([catName, items]) => {
          const open = openFolders[catName] ?? true;
          return (
            <div key={catName} className="card folder-card">
              <div className="folder-head" onClick={() => setOpenFolders((o) => ({ ...o, [catName]: !open }))}>
                <div className="avatar-circle" style={{ borderRadius: 12 }}>
                  {open ? <FolderOpen size={20} /> : <FolderDown size={20} />}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontFamily: 'var(--font-head)', fontWeight: 700 }}>مجلد كورس: {catName}</div>
                  <div className="helper">{items.length} محاضرة محملة</div>
                </div>
                <ChevronDown size={18} style={{ transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s' }} />
              </div>
              {open &&
                items.map((d) => (
                  <div key={d.id} className="dl-item">
                    <div style={{ flex: 1 }}>
                      <div style={{ fontFamily: 'var(--font-head)', fontWeight: 700, fontSize: 15 }}>{d.title}</div>
                      <div className="helper">
                        المدة: {formatMmSs(d.durationSeconds)} • الحجم: {Math.round(d.fileSizeMb)} MB • د. {d.doctorName}
                      </div>
                    </div>
                    <button className="btn btn-sm btn-soft" onClick={() => pushToast('تشغيل المحاضرة بدون إنترنت', 'success')}>تشغيل بدون إنترنت</button>
                    <button className="btn btn-sm btn-danger" onClick={() => removeDownload(d.lectureId)} aria-label="حذف">
                      <Trash2 size={15} />
                    </button>
                  </div>
                ))}
            </div>
          );
        })
      )}
    </div>
  );
}

function AdminPanel() {
  const [tab, setTab] = useState(0);
  const data = useDataStore((s) => s.data);
  const pendingStudents = data.studentRequests.filter((r) => r.status === 'pending').length;
  const pendingCourses = data.courseRequests.filter((r) => r.status === 'pending').length;

  return (
    <div className="stack">
      <div className="admin-tabs">
        <button className={`admin-tab ${tab === 0 ? 'active' : ''}`} onClick={() => setTab(0)}>
          الطلبات الجديدة {pendingStudents > 0 && <span className="count-pill">{pendingStudents}</span>}
        </button>
        <button className={`admin-tab ${tab === 1 ? 'active' : ''}`} onClick={() => setTab(1)}>
          سجل الطلاب
        </button>
        <button className={`admin-tab ${tab === 2 ? 'active' : ''}`} onClick={() => setTab(2)}>
          إدارة المدراء
        </button>
      </div>
      {tab === 0 && <RequestsTab pendingCourses={pendingCourses} />}
      {tab === 1 && <LogTab />}
      {tab === 2 && <AdminsTab />}
    </div>
  );
}

function RequestsTab({ pendingCourses }: { pendingCourses: number }) {
  const data = useDataStore((s) => s.data);
  const approveStudent = useDataStore((s) => s.approveStudent);
  const suspendStudent = useDataStore((s) => s.suspendStudent);
  const approveAllPending = useDataStore((s) => s.approveAllPending);
  const suspendAllPending = useDataStore((s) => s.suspendAllPending);
  const approveCourseRequest = useDataStore((s) => s.approveCourseRequest);
  const rejectCourseRequest = useDataStore((s) => s.rejectCourseRequest);
  const pushToast = useAppStore((s) => s.pushToast);
  const [q, setQ] = useState('');
  const [courseFilter, setCourseFilter] = useState('all');

  const pending = data.studentRequests.filter((r) => r.status === 'pending');
  const filtered = pending.filter((r) => !q.trim() || r.email.toLowerCase().includes(q.toLowerCase()) || r.displayName.toLowerCase().includes(q.toLowerCase()));

  const pendingCourseReqs = data.courseRequests.filter((r) => r.status === 'pending');
  const courseCats = Array.from(new Set(pendingCourseReqs.map((r) => r.categoryName)));
  const filteredCourseReqs = pendingCourseReqs.filter((r) => courseFilter === 'all' || r.categoryName === courseFilter);

  return (
    <div className="stack">
      <div className="card panel-card">
        <div className="panel-head">
          <div className="panel-title">طلبات انضمام الطلاب للتطبيق ({pending.length})</div>
          {pending.length > 0 && (
            <div style={{ display: 'flex', gap: 6 }}>
              <button className="btn btn-sm btn-primary" onClick={() => { const n = approveAllPending(); pushToast(`تم قبول وتفعيل جميع الطلبات المعلقة (${n} طالب) ✓`, 'success'); }}>قبول الكل ({pending.length})</button>
              <button className="btn btn-sm btn-danger" onClick={() => { const n = suspendAllPending(); pushToast(`تم رفض / إيقاف الطلبات المحددة (${n} طالب)`, 'info'); }}>رفض الكل</button>
            </div>
          )}
        </div>
        <div className="search-box">
          <span className="search-icon"><Search size={16} /></span>
          <input className="input" placeholder="ابحث بالبريد الإلكتروني أو اسم الطالب..." value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        {filtered.length === 0 ? (
          <div className="helper" style={{ textAlign: 'center', padding: 12 }}>
            {pending.length === 0 ? 'لا توجد طلبات انضمام طلاب معلقة حالياً (جميع الطلبات تمت مراجعتها ✓).' : `لا توجد طلبات معلقة مطابقة للبحث "${q}".`}
          </div>
        ) : (
          filtered.map((r) => (
            <div key={r.id} className="request-row">
              <div className="avatar-circle">{(r.displayName || 'ط').slice(0, 1)}</div>
              <div>
                <div style={{ fontWeight: 700 }}>{r.displayName || 'طالب طب'}</div>
                <div className="helper">{r.email}</div>
              </div>
              <span className="badge" style={{ background: '#fdf0d9', color: '#d97706' }}>قيد المراجعة (Pending)</span>
              <div className="row-actions">
                <button className="btn btn-sm btn-primary" onClick={() => { approveStudent(r.id); pushToast(`تم قبول وتفعيل الطالب (${r.displayName}) بنجاح ✓`, 'success'); }}><UserCheck size={15} /> قبول وتفعيل</button>
                <button className="btn btn-sm btn-danger" onClick={() => { suspendStudent(r.id); pushToast(`تم رفض/إيقاف الطالب (${r.displayName})`, 'info'); }}><UserX size={15} /> رفض / إيقاف</button>
              </div>
            </div>
          ))
        )}
      </div>

      <div className="card panel-card">
        <div className="panel-title">طلبات الانضمام للكورسات ({pendingCourseReqs.length})</div>
        <div className="helper">طلبات الطلاب المعلقة للاشتراك في كورسات محددة.</div>
        <div className="cat-chips">
          <button className={`cat-chip ${courseFilter === 'all' ? 'active' : ''}`} onClick={() => setCourseFilter('all')}>كل الكورسات ({pendingCourseReqs.length})</button>
          {courseCats.map((c) => (
            <button key={c} className={`cat-chip ${courseFilter === c ? 'active' : ''}`} onClick={() => setCourseFilter(c)}>
              {c} ({pendingCourseReqs.filter((r) => r.categoryName === c).length})
            </button>
          ))}
        </div>
        {filteredCourseReqs.length === 0 ? (
          <div className="helper" style={{ textAlign: 'center', padding: 12 }}>لا توجد طلبات انضمام لكورسات معلقة حالياً ✓.</div>
        ) : (
          filteredCourseReqs.map((r) => (
            <div key={r.id} className="request-row">
              <div className="avatar-circle">{(r.studentName || 'ط').slice(0, 1)}</div>
              <div>
                <div style={{ fontWeight: 700 }}>{r.studentName}</div>
                <div className="helper">{r.studentEmail} • كورس: {r.categoryName}</div>
              </div>
              <div className="row-actions">
                <button className="btn btn-sm btn-primary" onClick={() => { approveCourseRequest(r.id); pushToast(`تم قبول طلب كورس ${r.categoryName}`, 'success'); }}>قبول الكورس</button>
                <button className="btn btn-sm btn-danger" onClick={() => { rejectCourseRequest(r.id); pushToast(`تم رفض طلب كورس ${r.categoryName}`, 'info'); }}>رفض الكورس</button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function LogTab() {
  const data = useDataStore((s) => s.data);
  const approveStudent = useDataStore((s) => s.approveStudent);
  const suspendStudent = useDataStore((s) => s.suspendStudent);
  const setEnrollment = useDataStore((s) => s.setEnrollment);
  const pushToast = useAppStore((s) => s.pushToast);
  const [filter, setFilter] = useState<'all' | 'approved' | 'suspended'>('all');

  const log = data.studentRequests.filter((r) => r.status !== 'pending');
  const filtered = log.filter((r) => filter === 'all' || r.status === filter);
  const counts = {
    all: log.length,
    approved: log.filter((r) => r.status === 'approved').length,
    suspended: log.filter((r) => r.status === 'suspended').length,
  };

  return (
    <div className="stack">
      <div className="cat-chips">
        {(['all', 'approved', 'suspended'] as const).map((f) => (
          <button key={f} className={`cat-chip ${filter === f ? 'active' : ''}`} onClick={() => setFilter(f)}>
            {f === 'all' ? 'الكل' : f === 'approved' ? 'المعتمدون' : 'الموقوفون'} ({counts[f]})
          </button>
        ))}
      </div>
      {filtered.length === 0 ? (
        <EmptyState icon={<Users size={28} />} title="سجل الطلاب فارغ حالياً" description="سيظهر هنا الطلاب الذين تم قبولهم أو إيقافهم." />
      ) : (
        filtered.map((r) => (
          <div key={r.id} className="card panel-card">
            <div className="row">
              <div className="avatar-circle">{(r.displayName || 'ط').slice(0, 1)}</div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 700 }}>{r.displayName}</div>
                <div className="helper">{r.email}</div>
              </div>
              <span className={`badge ${r.status === 'approved' ? 'badge-green' : 'badge-red'}`}>
                {r.status === 'approved' ? 'مفعّل بالتطبيق (Approved)' : 'موقوف / مرفوض (Suspended)'}
              </span>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn btn-sm btn-primary" disabled={r.status === 'approved'} onClick={() => { approveStudent(r.id); pushToast(`تم تفعيل ${r.displayName}`, 'success'); }}>تفعيل</button>
              <button className="btn btn-sm btn-danger" disabled={r.status === 'suspended'} onClick={() => { suspendStudent(r.id); pushToast(`تم إيقاف ${r.displayName}`, 'info'); }}>إيقاف</button>
            </div>
            <div className="helper">الكورسات المسموح لهذا الطالب بدخولها (اضغط لتفعيل أو إلغاء أي كورس):</div>
            <div className="cat-chips">
              {data.categories.map((c) => {
                const subscribed = data.enrollments[r.id]?.[c.id] === 'approved';
                return (
                  <button key={c.id} className={`cat-chip ${subscribed ? 'active' : ''}`} onClick={() => setEnrollment(r.id, c.id, subscribed ? 'suspended' : 'approved')}>
                    {c.name} {subscribed ? '(مشترك ✓)' : '(غير مشترك)'}
                  </button>
                );
              })}
            </div>
          </div>
        ))
      )}
    </div>
  );
}

function AdminsTab() {
  const data = useDataStore((s) => s.data);
  const addAdmin = useDataStore((s) => s.addAdmin);
  const removeAdmin = useDataStore((s) => s.removeAdmin);
  const deleteCategory = useDataStore((s) => s.deleteCategory);
  const pushToast = useAppStore((s) => s.pushToast);
  const session = useAppStore((s) => s.session);
  const [email, setEmail] = useState('');
  const [roleTitle, setRoleTitle] = useState('');

  return (
    <div className="stack">
      <div className="card panel-card">
        <div className="panel-title">لوحة إدارة المدراء (Admins Management)</div>
        <div className="helper">أنت المدير الرئيسي: يمكنك إضافة إيميلات دكاترة ومشرفين آخرين أو حذفهم</div>
        <div className="field">
          <label className="label">البريد الإلكتروني للدكتور / المشرف الجديد</label>
          <input className="input" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="doctor@gmail.com" />
        </div>
        <div className="field">
          <label className="label">التخصص أو المسمى الأكاديمي (اختياري)</label>
          <input className="input" value={roleTitle} onChange={(e) => setRoleTitle(e.target.value)} placeholder="أستاذ الجراحة العامة" />
        </div>
        <button
          className="btn btn-gold"
          disabled={!email.includes('@')}
          onClick={() => {
            addAdmin(email, roleTitle, session?.user?.uid ?? 'admin');
            pushToast(`تمت إضافة ${email} كمسؤول معتمد`, 'success');
            setEmail(''); setRoleTitle('');
          }}
        >
          إضافة كمسؤول / دكتور معتمد
        </button>
        <div className="panel-title" style={{ fontSize: 14 }}>قائمة المدراء والأطباء المعتمدين ({data.admins.length}):</div>
        {data.admins.map((a) => (
          <div key={a.id} className="request-row">
            <div className="avatar-circle"><UserCheck size={18} /></div>
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 700 }}>{a.email}{a.isPrimary ? ' (المدير الأساسي)' : ''}</div>
              <div className="helper">{a.roleTitle}</div>
            </div>
            {!a.isPrimary && (
              <button className="btn btn-sm btn-danger" onClick={() => { removeAdmin(a.id); pushToast('تم حذف المسؤول', 'info'); }} aria-label="حذف">
                <Trash2 size={15} />
              </button>
            )}
          </div>
        ))}
      </div>

      <div className="card panel-card">
        <div className="panel-title" style={{ fontSize: 14 }}>إدارة التصنيفات والكورسات المضافة ({data.categories.length})</div>
        {data.categories.map((c) => (
          <div key={c.id} className="request-row">
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 700 }}>{c.name}</div>
              <div className="helper">{c.description}</div>
            </div>
            <button className="btn btn-sm btn-danger" onClick={() => { deleteCategory(c.id); pushToast(`تم حذف الكورس ${c.name}`, 'info'); }} aria-label="حذف">
              <Trash2 size={15} />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
