import { useEffect } from 'react';
import {
  ArrowLeftRight,
  ClipboardList,
  Download,
  LogOut,
  Moon,
  Plus,
  Settings as SettingsIcon,
  ShieldCheck,
  Sun,
  Video,
  X,
} from 'lucide-react';
import { useAppStore, type AppTab } from '../store/appStore';
import { useDataStore } from '../data/dataStore';
import { useAccess, signOut, switchToAdminView, switchToStudentView } from '../lib/session';

export function Banner() {
  const banner = useAppStore((s) => s.banner);
  const setBanner = useAppStore((s) => s.setBanner);
  useEffect(() => {
    if (!banner) return;
    const t = window.setTimeout(() => setBanner(null), 3000);
    return () => window.clearTimeout(t);
  }, [banner, setBanner]);
  if (!banner) return null;
  return (
    <div className="app-banner">
      <div className="app-banner-inner">
        <span>{banner}</span>
        <button onClick={() => setBanner(null)} aria-label="إغلاق">
          <X size={16} color="var(--gold-accent)" />
        </button>
      </div>
    </div>
  );
}

export function TopBar() {
  const theme = useAppStore((s) => s.theme);
  const toggleTheme = useAppStore((s) => s.toggleTheme);
  const { session, isAdminView } = useAccess();
  const role = session?.role ?? 'STUDENT';
  const verified = session?.isVerifiedAdmin ?? false;

  const onToggleRole = () => {
    if (!verified) return;
    if (role === 'ADMIN') switchToStudentView();
    else switchToAdminView();
  };

  return (
    <div className="topbar">
      <div className="container topbar-inner">
        <div className="brand">
          <div className="brand-logo">
            <Plus size={22} />
          </div>
          <div>
            <div className="brand-title">Elghaliz Medicine</div>
            <div className="brand-sub">{session?.user?.email}</div>
          </div>
        </div>
        <div className="topbar-actions">
          <button className="icon-btn" onClick={toggleTheme} title="تبديل الوضع الليلي" aria-label="تبديل الوضع الليلي">
            {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          </button>
          <button
            onClick={onToggleRole}
            disabled={!verified}
            title={verified ? (role === 'ADMIN' ? 'التبديل إلى معاينة واجهة الطالب' : 'العودة إلى لوحة الدكتور / المسؤول') : 'طالب'}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              borderRadius: 999,
              padding: '6px 12px',
              background: 'rgba(201,162,77,0.2)',
              border: '1px solid rgba(201,162,77,0.65)',
              color: 'var(--gold-accent)',
              fontFamily: 'var(--font-head)',
              fontWeight: 800,
              fontSize: 12,
              cursor: verified ? 'pointer' : 'default',
            }}
          >
            {verified && <ArrowLeftRight size={14} />}
            {isAdminView ? 'مسؤول / دكتور' : 'طالب'}
          </button>
          <button className="icon-btn" onClick={signOut} title="تسجيل الخروج" aria-label="تسجيل الخروج">
            <LogOut size={18} />
          </button>
        </div>
      </div>
    </div>
  );
}

export function BottomNav() {
  const tab = useAppStore((s) => s.tab);
  const setTab = useAppStore((s) => s.setTab);
  const { isAdminView } = useAccess();
  const data = useDataStore((s) => s.data);

  const pendingStudents = data.studentRequests.filter((r) => r.status === 'pending').length;
  const pendingCourses = data.courseRequests.filter((r) => r.status === 'pending').length;
  const adminBadge = pendingStudents + pendingCourses;

  const items: { key: AppTab; label: string; icon: React.ReactNode; badge?: number }[] = [
    { key: 'LIBRARY', label: 'المحاضرات', icon: <Video size={22} /> },
    { key: 'EXAMS', label: 'الاختبارات', icon: <ClipboardList size={22} /> },
    isAdminView
      ? { key: 'DOWNLOADS_ADMINS', label: 'المدراء والطلبات', icon: <ShieldCheck size={22} />, badge: adminBadge }
      : { key: 'DOWNLOADS_ADMINS', label: 'التنزيلات', icon: <Download size={22} /> },
    { key: 'SETTINGS', label: 'الإعدادات', icon: <SettingsIcon size={22} /> },
  ];

  return (
    <div className="tabbar">
      {items.map((it) => (
        <button key={it.key} className={`tab ${tab === it.key ? 'active' : ''}`} onClick={() => setTab(it.key)}>
          {it.icon}
          <span>{it.label}</span>
          {it.badge ? <span className="tab-badge">{it.badge}</span> : null}
        </button>
      ))}
    </div>
  );
}
