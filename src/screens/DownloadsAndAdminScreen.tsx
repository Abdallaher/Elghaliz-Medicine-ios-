import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Download,
  Folder,
  HardDrive,
  Lock,
  Play,
  Plus,
  Search,
  ShieldCheck,
  Smartphone,
  Trash2,
  UserCheck,
  UserPlus,
  Users,
  X,
} from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { formatBytesToMb, formatDurationHms } from '../lib/logic';
import { sumVaultBytes } from '../lib/offlineVault';

export const DownloadsAndAdminScreen: React.FC = () => {
  const { isAdminUser, activeRole } = useAppStore();
  const isAdminView = isAdminUser && activeRole === 'ADMIN';
  return isAdminView ? <AdminManagementPanel /> : <StudentDownloadsVault />;
};

const StudentDownloadsVault: React.FC = () => {
  const {
    offlineDownloads,
    downloadTasks,
    storageStatus,
    autoDeleteWatched,
    setAutoDeleteWatched,
    openCoursePlayer,
    removeOfflineDownload,
    clearAllDownloads,
    pauseOfflineDownload,
    resumeOfflineDownload,
    cancelOfflineDownload,
  } = useAppStore();

  const [expandedCourses, setExpandedCourses] = useState<Record<string, boolean>>({});

  const totalUsedBytes = useMemo(() => sumVaultBytes(offlineDownloads), [offlineDownloads]);
  const activeTasks = Object.values(downloadTasks);

  const groupedByCourse = useMemo(() => {
    const groups = new Map<string, { courseName: string; items: typeof offlineDownloads }>();
    for (const item of offlineDownloads) {
      const existing = groups.get(item.categoryId);
      if (existing) {
        existing.items.push(item);
      } else {
        groups.set(item.categoryId, { courseName: item.categoryName || 'كورس طبي', items: [item] });
      }
    }
    return Array.from(groups.entries());
  }, [offlineDownloads]);

  const toggleCourse = (catId: string) => {
    setExpandedCourses((prev) => ({ ...prev, [catId]: prev[catId] === false ? true : false }));
  };

  return (
    <div className="space-y-4">
      {/* Storage Summary Card for iPhone Safari Quota Management */}
      <section className="rounded-[24px] bg-[#0F3D2E] text-white p-5 shadow-md border border-[#C9A24D]/35 space-y-3.5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#1F6B52] text-[#C9A24D] border border-[#C9A24D]/40">
              <HardDrive className="h-6 w-6" />
            </div>
            <div>
              <h2 className="text-base font-black text-[#FFFDF8]">
                مخزن التنزيلات المشفّر (4KB Header Lock)
              </h2>
              <p className="text-xs text-[#E9F0E8]/80 mt-0.5">
                المساحة المستخدمة داخل التطبيق:{' '}
                <strong className="text-[#C9A24D]">{formatBytesToMb(totalUsedBytes)} MB</strong> •{' '}
                {offlineDownloads.length} محاضرة
              </p>
            </div>
          </div>

          {offlineDownloads.length > 0 && (
            <button
              type="button"
              onClick={() => void clearAllDownloads()}
              className="shrink-0 rounded-xl bg-[#B33A3A] hover:bg-red-700 text-white text-xs font-extrabold px-3 py-2 cursor-pointer transition"
            >
              تفريغ الكل
            </button>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 rounded-2xl bg-[#0A2A20] px-3.5 py-2.5 text-xs">
          <span className="inline-flex items-center gap-1.5 font-bold text-[#E9F0E8]">
            <ShieldCheck className="h-4 w-4 text-[#C9A24D]" />
            <span>
              حالة التخزين الدائم (Persistent Storage):{' '}
              <strong className="text-[#4AD1A3]">
                {storageStatus.persisted ? 'مفعّل ومحمي ✓' : 'تخزين IndexedDB قياسي'}
              </strong>
            </span>
          </span>

          <label className="inline-flex items-center gap-2 cursor-pointer font-bold text-[#C9A24D]">
            <input
              type="checkbox"
              checked={autoDeleteWatched}
              onChange={(e) => setAutoDeleteWatched(e.target.checked)}
              className="h-4 w-4 accent-[#C9A24D] rounded"
            />
            <span>حذف تلقائي بعد المشاهدة</span>
          </label>
        </div>

        <div className="flex items-start gap-2 text-[11px] text-[#E9F0E8]/85 leading-relaxed">
          <AlertTriangle className="h-4 w-4 text-[#C9A24D] shrink-0 mt-0.5" />
          <p>
            لحماية مساحة الآيفون تحت حدود Safari، احذف المحاضرات التي أنهيتها أو فعّل خيار «الحذف التلقائي بعد المشاهدة». وأبقِ الشاشة مفتوحة أثناء التنزيل.
          </p>
        </div>
      </section>

      {/* Active Streaming Downloads */}
      {activeTasks.length > 0 && (
        <section className="space-y-2.5">
          <h3 className="text-sm font-black text-[#0F3D2E] dark:text-[#F4EFE4] px-1">
            التنزيلات الجارية (أبقِ الشاشة مفتوحة)
          </h3>
          {activeTasks.map((task) => (
            <div
              key={task.lectureId}
              className="rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#C9A24D]/50 p-4 space-y-2"
            >
              <div className="flex items-center justify-between gap-2 text-xs font-extrabold">
                <span className="truncate">{task.lectureTitle}</span>
                <span className="font-mono text-[#0F3D2E] dark:text-[#C9A24D]">
                  {task.progressPercent}%
                </span>
              </div>
              <div className="h-2.5 w-full overflow-hidden rounded-full bg-[#E9F0E8] dark:bg-[#0A2A20]">
                <div
                  className="h-full rounded-full bg-[#0F3D2E] dark:bg-[#C9A24D] transition-all"
                  style={{ width: `${task.progressPercent}%` }}
                />
              </div>
              <div className="flex items-center justify-between text-[11px] text-[#546B61] dark:text-[#A5C0B4]">
                <span>{task.statusLabel}</span>
                <div className="flex items-center gap-2">
                  {task.status === 'DOWNLOADING' ? (
                    <button
                      type="button"
                      onClick={() => pauseOfflineDownload(task.lectureId)}
                      className="font-bold text-[#0F3D2E] dark:text-[#C9A24D] cursor-pointer"
                    >
                      إيقاف مؤقت
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => resumeOfflineDownload(task.lectureId)}
                      className="font-bold text-[#0F3D2E] dark:text-[#C9A24D] cursor-pointer"
                    >
                      استئناف
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => cancelOfflineDownload(task.lectureId)}
                    className="font-bold text-[#B33A3A] cursor-pointer"
                  >
                    إلغاء
                  </button>
                </div>
              </div>
            </div>
          ))}
        </section>
      )}

      {/* Downloaded Lectures Grouped by Expandable Course Folders */}
      {groupedByCourse.length === 0 ? (
        <div className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-10 text-center">
          <Download className="h-10 w-10 mx-auto text-[#C9A24D] mb-2.5" />
          <p className="text-sm font-extrabold text-[#0F3D2E] dark:text-[#F4EFE4]">
            لا توجد محاضرات منزّلة أوفلاين بعد
          </p>
          <p className="text-xs text-[#546B61] dark:text-[#A5C0B4] mt-1">
            افتح أي كورس مفعّل واضغط «تنزيل داخل التطبيق» لحفظ المحاضرة مشفّرة ومشاهدتها بدون إنترنت.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {groupedByCourse.map(([catId, group]) => {
            const isOpen = expandedCourses[catId] !== false;
            return (
              <div
                key={catId}
                className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] overflow-hidden"
              >
                <button
                  type="button"
                  onClick={() => toggleCourse(catId)}
                  className="w-full flex items-center justify-between gap-3 px-4 py-3.5 text-right hover:bg-[#E9F0E8]/40 dark:hover:bg-white/5 cursor-pointer"
                >
                  <div className="flex items-center gap-2.5">
                    <Folder className="h-5 w-5 text-[#C9A24D]" />
                    <span className="font-black text-sm text-[#0F3D2E] dark:text-[#F4EFE4]">
                      {group.courseName}
                    </span>
                    <span className="rounded-lg bg-[#E9F0E8] dark:bg-[#0A2A20] px-2 py-0.5 text-[11px] font-bold text-[#1F6B52] dark:text-[#C9A24D]">
                      {group.items.length}
                    </span>
                  </div>
                  {isOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                </button>

                {isOpen && (
                  <div className="border-t border-[#EFE8D8] dark:border-[#1E4F3D] divide-y divide-[#EFE8D8] dark:divide-[#1E4F3D]">
                    {group.items.map((item) => (
                      <div
                        key={item.lectureId}
                        className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                      >
                        <div className="min-w-0">
                          <h4 className="text-sm font-extrabold text-[#0F3D2E] dark:text-[#F4EFE4]">
                            {item.title}
                          </h4>
                          <p className="text-[11px] text-[#546B61] dark:text-[#A5C0B4] mt-0.5">
                            {formatBytesToMb(item.fileSizeBytes)} MB •{' '}
                            {formatDurationHms(item.durationSeconds)} • مشفّر (4KB SHA-256 XOR)
                          </p>
                        </div>

                        <div className="flex items-center gap-2 shrink-0">
                          <button
                            type="button"
                            onClick={() => openCoursePlayer(item.categoryId, item.lectureId)}
                            className="inline-flex items-center gap-1.5 rounded-xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white text-xs font-extrabold px-3.5 py-2 cursor-pointer"
                          >
                            <Play className="h-3.5 w-3.5 text-[#C9A24D]" />
                            <span>تشغيل أوفلاين</span>
                          </button>

                          <button
                            type="button"
                            onClick={() => void removeOfflineDownload(item.lectureId)}
                            aria-label={`حذف ${item.title}`}
                            className="inline-flex items-center gap-1 rounded-xl bg-[#B33A3A]/10 text-[#B33A3A] dark:text-red-300 text-xs font-bold px-3 py-2 hover:bg-[#B33A3A]/20 cursor-pointer"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                            <span>حذف</span>
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

const AdminManagementPanel: React.FC = () => {
  const {
    categories,
    studentRequests,
    courseRequests,
    admins,
    updateStudentStatus,
    bulkUpdatePendingStudents,
    updateCourseRequestStatus,
    setStudentCourseAccess,
    addAdmin,
    removeAdmin,
    deleteCategory,
  } = useAppStore();

  const [subTab, setSubTab] = useState<'REQUESTS' | 'STUDENTS' | 'ADMINS'>('REQUESTS');
  const [searchReq, setSearchReq] = useState('');
  const [searchStudents, setSearchStudents] = useState('');
  const [studentFilter, setStudentFilter] = useState<'ALL' | 'approved' | 'suspended'>('ALL');
  const [expandedStudentId, setExpandedStudentId] = useState<string | null>(null);

  // Add Admin form
  const [newAdminEmail, setNewAdminEmail] = useState('');
  const [newAdminRole, setNewAdminRole] = useState('دكتور / مشرف');

  const pendingStudents = useMemo(() => {
    const q = searchReq.trim().toLowerCase();
    return studentRequests.filter(
      (s) =>
        s.status === 'pending' &&
        (!q || s.displayName.toLowerCase().includes(q) || s.email.toLowerCase().includes(q)),
    );
  }, [studentRequests, searchReq]);

  const pendingCourseReqs = useMemo(() => {
    const q = searchReq.trim().toLowerCase();
    return courseRequests.filter(
      (r) =>
        r.status === 'pending' &&
        (!q ||
          r.studentName.toLowerCase().includes(q) ||
          r.studentEmail.toLowerCase().includes(q) ||
          r.categoryName.toLowerCase().includes(q)),
    );
  }, [courseRequests, searchReq]);

  const filteredStudents = useMemo(() => {
    const q = searchStudents.trim().toLowerCase();
    return studentRequests.filter((s) => {
      const matchFilter = studentFilter === 'ALL' || s.status === studentFilter;
      const matchQ =
        !q || s.displayName.toLowerCase().includes(q) || s.email.toLowerCase().includes(q);
      return matchFilter && matchQ;
    });
  }, [studentRequests, searchStudents, studentFilter]);

  const handleAddAdmin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newAdminEmail.trim()) return;
    await addAdmin(newAdminEmail, newAdminRole);
    setNewAdminEmail('');
  };

  return (
    <div className="space-y-4">
      {/* 3 Sub-Tabs matching Android DownloadsAndAdminScreen */}
      <div className="grid grid-cols-3 gap-1.5 rounded-[22px] bg-[#E9F0E8] dark:bg-[#123528] p-1.5 border border-[#EFE8D8] dark:border-[#1E4F3D]">
        <button
          type="button"
          onClick={() => setSubTab('REQUESTS')}
          className={`rounded-2xl py-2.5 px-2 text-xs font-extrabold transition cursor-pointer ${
            subTab === 'REQUESTS'
              ? 'bg-[#0F3D2E] text-[#C9A24D] shadow'
              : 'text-[#546B61] dark:text-[#A5C0B4]'
          }`}
        >
          الطلبات الجديدة ({pendingStudents.length + pendingCourseReqs.length})
        </button>
        <button
          type="button"
          onClick={() => setSubTab('STUDENTS')}
          className={`rounded-2xl py-2.5 px-2 text-xs font-extrabold transition cursor-pointer ${
            subTab === 'STUDENTS'
              ? 'bg-[#0F3D2E] text-[#C9A24D] shadow'
              : 'text-[#546B61] dark:text-[#A5C0B4]'
          }`}
        >
          سجل الطلاب ({studentRequests.length})
        </button>
        <button
          type="button"
          onClick={() => setSubTab('ADMINS')}
          className={`rounded-2xl py-2.5 px-2 text-xs font-extrabold transition cursor-pointer ${
            subTab === 'ADMINS'
              ? 'bg-[#0F3D2E] text-[#C9A24D] shadow'
              : 'text-[#546B61] dark:text-[#A5C0B4]'
          }`}
        >
          إدارة المدراء
        </button>
      </div>

      {/* SUB-TAB 1: Pending Student & Course Requests */}
      {subTab === 'REQUESTS' && (
        <div className="space-y-4">
          <div className="relative">
            <Search className="pointer-events-none absolute right-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-[#546B61]" />
            <input
              type="search"
              value={searchReq}
              onChange={(e) => setSearchReq(e.target.value)}
              placeholder="ابحث بالاسم أو البريد أو الكورس..."
              className="w-full rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] pr-10 pl-4 py-2.5 text-xs sm:text-sm"
            />
          </div>

          {/* Pending Account Registrations */}
          <section className="space-y-2.5">
            <div className="flex flex-wrap items-center justify-between gap-2 px-1">
              <h3 className="text-sm font-black text-[#0F3D2E] dark:text-[#F4EFE4] flex items-center gap-1.5">
                <UserCheck className="h-4 w-4 text-[#C9A24D]" />
                <span>طلبات تفعيل حسابات الطلاب ({pendingStudents.length})</span>
              </h3>

              {pendingStudents.length > 0 && (
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => void bulkUpdatePendingStudents('approved')}
                    className="rounded-xl bg-[#0F3D2E] text-white text-[11px] font-extrabold px-3 py-1.5 cursor-pointer"
                  >
                    قبول الكل
                  </button>
                  <button
                    type="button"
                    onClick={() => void bulkUpdatePendingStudents('suspended')}
                    className="rounded-xl bg-[#B33A3A]/15 text-[#B33A3A] dark:text-red-300 text-[11px] font-extrabold px-3 py-1.5 cursor-pointer"
                  >
                    رفض الكل
                  </button>
                </div>
              )}
            </div>

            {pendingStudents.length === 0 ? (
              <div className="rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-5 text-center text-xs text-[#546B61] dark:text-[#A5C0B4]">
                لا توجد طلبات تسجيل جديدة بانتظار الموافقة.
              </div>
            ) : (
              pendingStudents.map((st) => (
                <div
                  key={st.userId}
                  className="rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                >
                  <div>
                    <p className="text-sm font-extrabold text-[#0F3D2E] dark:text-[#F4EFE4]">
                      {st.displayName}
                    </p>
                    <p className="text-xs text-[#546B61] dark:text-[#A5C0B4]">{st.email}</p>
                    <p className="text-[11px] text-[#1F6B52] dark:text-[#C9A24D] mt-1 font-bold">
                      جهاز مقفل (1/1): {st.registeredDeviceIds[0]?.slice(0, 14) || 'جديد'}...
                    </p>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={() => void updateStudentStatus(st.userId, 'approved')}
                      className="inline-flex items-center gap-1 rounded-xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white text-xs font-extrabold px-3.5 py-2 cursor-pointer"
                    >
                      <Check className="h-3.5 w-3.5 text-[#C9A24D]" />
                      <span>موافقة</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => void updateStudentStatus(st.userId, 'suspended')}
                      className="inline-flex items-center gap-1 rounded-xl bg-[#B33A3A]/15 text-[#B33A3A] dark:text-red-300 text-xs font-bold px-3.5 py-2 cursor-pointer"
                    >
                      <X className="h-3.5 w-3.5" />
                      <span>رفض</span>
                    </button>
                  </div>
                </div>
              ))
            )}
          </section>

          {/* Pending Per-Course Enrollment Requests */}
          <section className="space-y-2.5">
            <h3 className="text-sm font-black text-[#0F3D2E] dark:text-[#F4EFE4] px-1">
              طلبات الانضمام للكورسات الطبية ({pendingCourseReqs.length})
            </h3>
            {pendingCourseReqs.length === 0 ? (
              <div className="rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-5 text-center text-xs text-[#546B61] dark:text-[#A5C0B4]">
                لا توجد طلبات انضمام للكورسات بانتظار المراجعة.
              </div>
            ) : (
              pendingCourseReqs.map((req) => (
                <div
                  key={req.id}
                  className="rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                >
                  <div>
                    <span className="inline-block rounded-lg bg-[#C9A24D]/20 text-[#9A7624] dark:text-[#C9A24D] px-2 py-0.5 text-[11px] font-extrabold mb-1">
                      كورس: {req.categoryName}
                    </span>
                    <p className="text-sm font-extrabold text-[#0F3D2E] dark:text-[#F4EFE4]">
                      {req.studentName}
                    </p>
                    <p className="text-xs text-[#546B61] dark:text-[#A5C0B4]">{req.studentEmail}</p>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={() => void updateCourseRequestStatus(req.id, 'approved')}
                      className="inline-flex items-center gap-1 rounded-xl bg-[#0F3D2E] text-white text-xs font-extrabold px-3.5 py-2 cursor-pointer"
                    >
                      <Check className="h-3.5 w-3.5 text-[#C9A24D]" />
                      <span>تفعيل الكورس</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => void updateCourseRequestStatus(req.id, 'suspended')}
                      className="inline-flex items-center gap-1 rounded-xl bg-[#B33A3A]/15 text-[#B33A3A] dark:text-red-300 text-xs font-bold px-3.5 py-2 cursor-pointer"
                    >
                      <X className="h-3.5 w-3.5" />
                      <span>رفض</span>
                    </button>
                  </div>
                </div>
              ))
            )}
          </section>
        </div>
      )}

      {/* SUB-TAB 2: Students Directory (Read-Only Strict 1-Device Lock — NO Unlink Button!) */}
      {subTab === 'STUDENTS' && (
        <div className="space-y-3">
          <div className="flex flex-col sm:flex-row gap-2">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute right-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-[#546B61]" />
              <input
                type="search"
                value={searchStudents}
                onChange={(e) => setSearchStudents(e.target.value)}
                placeholder="ابحث عن طالب بالاسم أو البريد..."
                className="w-full rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] pr-10 pl-4 py-2.5 text-xs sm:text-sm"
              />
            </div>

            <div className="flex items-center gap-1.5">
              {(['ALL', 'approved', 'suspended'] as const).map((f) => (
                <button
                  key={f}
                  type="button"
                  onClick={() => setStudentFilter(f)}
                  className={`rounded-xl px-3 py-2 text-xs font-extrabold cursor-pointer ${
                    studentFilter === f
                      ? 'bg-[#0F3D2E] text-[#C9A24D]'
                      : 'bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D]'
                  }`}
                >
                  {f === 'ALL' ? 'الكل' : f === 'approved' ? 'مفعّل' : 'موقوف'}
                </button>
              ))}
            </div>
          </div>

          {filteredStudents.map((st) => {
            const expanded = expandedStudentId === st.userId;
            return (
              <div
                key={st.userId}
                className="rounded-[22px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 space-y-3"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2">
                      <Users className="h-4 w-4 text-[#C9A24D]" />
                      <span className="text-sm font-black text-[#0F3D2E] dark:text-[#F4EFE4]">
                        {st.displayName}
                      </span>
                      <span
                        className={`rounded-lg px-2 py-0.5 text-[10px] font-extrabold ${
                          st.status === 'approved'
                            ? 'bg-[#1F6B52]/15 text-[#1F6B52] dark:text-[#4AD1A3]'
                            : st.status === 'suspended'
                              ? 'bg-[#B33A3A]/15 text-[#B33A3A] dark:text-red-300'
                              : 'bg-[#C9A24D]/20 text-[#9A7624] dark:text-[#C9A24D]'
                        }`}
                      >
                        {st.status === 'approved'
                          ? 'مفعّل'
                          : st.status === 'suspended'
                            ? 'موقوف'
                            : 'قيد الانتظار'}
                      </span>
                    </div>
                    <p className="text-xs text-[#546B61] dark:text-[#A5C0B4] mt-0.5">{st.email}</p>
                    {/* Read-Only Strict 1-Device Lock indicator (NO unlink button anywhere) */}
                    <p className="mt-1 inline-flex items-center gap-1.5 text-[11px] font-bold text-[#1F6B52] dark:text-[#C9A24D]">
                      <Lock className="h-3 w-3" />
                      <Smartphone className="h-3 w-3" />
                      <span>
                        قفل الجهاز الواحد الدائم ({Math.min(1, st.registeredDeviceIds.length)}/1)
                      </span>
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setExpandedStudentId(expanded ? null : st.userId)}
                      className="rounded-xl bg-[#E9F0E8] dark:bg-[#0A2A20] text-[#0F3D2E] dark:text-[#C9A24D] text-xs font-extrabold px-3 py-2 cursor-pointer"
                    >
                      {expanded ? 'إخفاء الكورسات' : 'صلاحيات الكورسات'}
                    </button>

                    {st.status !== 'approved' ? (
                      <button
                        type="button"
                        onClick={() => void updateStudentStatus(st.userId, 'approved')}
                        className="rounded-xl bg-[#0F3D2E] text-white text-xs font-extrabold px-3 py-2 cursor-pointer"
                      >
                        تفعيل الحساب
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={() => void updateStudentStatus(st.userId, 'suspended')}
                        className="rounded-xl bg-[#B33A3A]/15 text-[#B33A3A] dark:text-red-300 text-xs font-bold px-3 py-2 cursor-pointer"
                      >
                        إيقاف مؤقت
                      </button>
                    )}
                  </div>
                </div>

                {/* Per-Student Course Access Matrix */}
                {expanded && (
                  <div className="pt-3 border-t border-[#EFE8D8] dark:border-[#1E4F3D] grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {categories.map((cat) => {
                      const req = courseRequests.find(
                        (r) => r.userId === st.userId && r.categoryId === cat.id,
                      );
                      const isEnrolled = req?.status === 'approved';
                      return (
                        <div
                          key={cat.id}
                          className="flex items-center justify-between gap-2 rounded-xl bg-[#F7F3EA] dark:bg-[#0A2A20] px-3 py-2 text-xs"
                        >
                          <span className="font-bold">{cat.name}</span>
                          <button
                            type="button"
                            onClick={() =>
                              void setStudentCourseAccess(
                                st,
                                cat,
                                isEnrolled ? 'suspended' : 'approved',
                              )
                            }
                            className={`rounded-lg px-2.5 py-1 text-[11px] font-extrabold cursor-pointer ${
                              isEnrolled
                                ? 'bg-[#1F6B52] text-white'
                                : 'bg-[#E9F0E8] dark:bg-[#123528] text-[#546B61] dark:text-[#A5C0B4]'
                            }`}
                          >
                            {isEnrolled ? 'مفعّل ✓' : 'تفعيل'}
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* SUB-TAB 3: Admins & Courses Management */}
      {subTab === 'ADMINS' && (
        <div className="space-y-4">
          <form
            onSubmit={handleAddAdmin}
            className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 space-y-3"
          >
            <h3 className="text-sm font-black text-[#0F3D2E] dark:text-[#F4EFE4] flex items-center gap-2">
              <UserPlus className="h-4 w-4 text-[#C9A24D]" />
              <span>إضافة دكتور أو مشرف جديد</span>
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
              <input
                type="email"
                required
                value={newAdminEmail}
                onChange={(e) => setNewAdminEmail(e.target.value)}
                placeholder="بريد Google للمشرف..."
                className="sm:col-span-2 rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] px-3.5 py-2.5 text-xs sm:text-sm"
              />
              <input
                type="text"
                value={newAdminRole}
                onChange={(e) => setNewAdminRole(e.target.value)}
                placeholder="الصفة (دكتور / مساعد)"
                className="rounded-2xl bg-[#F7F3EA] dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] px-3.5 py-2.5 text-xs sm:text-sm"
              />
            </div>
            <button
              type="submit"
              className="inline-flex items-center gap-1.5 rounded-2xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white text-xs font-extrabold px-4 py-2.5 cursor-pointer"
            >
              <Plus className="h-4 w-4 text-[#C9A24D]" />
              <span>إضافة إلى قائمة المدراء</span>
            </button>
          </form>

          {/* Admins List */}
          <section className="space-y-2">
            <h3 className="text-sm font-black text-[#0F3D2E] dark:text-[#F4EFE4] px-1">
              المدراء الحاليون ({admins.length})
            </h3>
            {admins.map((adm) => (
              <div
                key={adm.id}
                className="rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-3.5 flex items-center justify-between gap-3"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <ShieldCheck className="h-4 w-4 text-[#C9A24D] shrink-0" />
                    <span className="text-xs sm:text-sm font-extrabold truncate">{adm.email}</span>
                  </div>
                  <p className="text-[11px] text-[#546B61] dark:text-[#A5C0B4] mt-0.5">
                    {adm.roleTitle} {adm.isPrimary ? '• المدير الرئيسي الحصري' : ''}
                  </p>
                </div>

                {!adm.isPrimary ? (
                  <button
                    type="button"
                    onClick={() => void removeAdmin(adm)}
                    className="rounded-xl bg-[#B33A3A]/10 text-[#B33A3A] dark:text-red-300 text-xs font-bold px-3 py-1.5 cursor-pointer"
                  >
                    إزالة
                  </button>
                ) : (
                  <span className="inline-flex items-center gap-1 rounded-xl bg-[#C9A24D]/20 text-[#9A7624] dark:text-[#C9A24D] text-[11px] font-extrabold px-2.5 py-1">
                    <CheckCircle2 className="h-3.5 w-3.5" />
                    <span>محمي</span>
                  </span>
                )}
              </div>
            ))}
          </section>

          {/* Manage Categories */}
          <section className="space-y-2">
            <h3 className="text-sm font-black text-[#0F3D2E] dark:text-[#F4EFE4] px-1">
              إدارة الكورسات الطبية ({categories.length})
            </h3>
            {categories.map((cat) => (
              <div
                key={cat.id}
                className="rounded-2xl bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-3.5 flex items-center justify-between gap-3"
              >
                <div className="min-w-0">
                  <p className="text-sm font-extrabold text-[#0F3D2E] dark:text-[#F4EFE4]">
                    {cat.name}
                  </p>
                  <p className="text-[11px] text-[#546B61] dark:text-[#A5C0B4] truncate">
                    {cat.description}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void deleteCategory(cat.id)}
                  aria-label={`حذف كورس ${cat.name}`}
                  className="shrink-0 rounded-xl bg-[#B33A3A]/10 text-[#B33A3A] dark:text-red-300 text-xs font-bold px-3 py-1.5 cursor-pointer"
                >
                  حذف
                </button>
              </div>
            ))}
          </section>
        </div>
      )}
    </div>
  );
};
