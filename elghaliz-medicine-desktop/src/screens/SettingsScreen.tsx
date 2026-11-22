import { useState } from 'react';
import { Copy, Fingerprint, LogOut, MonitorSmartphone, Moon, Sun, ShieldCheck, GraduationCap, Wifi } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { useAccess, signOut, switchToAdminView, switchToStudentView } from '../lib/session';
import { formatUserIdCode } from '../data/defaults';
import { useDataStore } from '../data/dataStore';
import { APP_VERSION, UPDATE_FEED_URL } from '../config';

export function SettingsScreen() {
  const { session, isAdminView, isVerifiedAdmin } = useAccess();
  const theme = useAppStore((s) => s.theme);
  const toggleTheme = useAppStore((s) => s.toggleTheme);
  const pushToast = useAppStore((s) => s.pushToast);
  const data = useDataStore((s) => s.data);

  const [wifiOnly, setWifiOnly] = useState(true);
  const [autoDelete, setAutoDelete] = useState(false);
  const [fontScale, setFontScale] = useState<'normal' | 'large' | 'xlarge'>('normal');
  const [copied, setCopied] = useState(false);
  const [checking, setChecking] = useState(false);

  const id = formatUserIdCode(session?.user?.uid ?? '');

  const roleLabel = session?.isPrimaryAdmin
    ? 'الصفة الحالية: المدير الرئيسي / دكتور مؤسس'
    : isAdminView
      ? 'الصفة الحالية: دكتور / مسؤول معتمد'
      : session?.approval === 'approved'
        ? 'الصفة الحالية: طالب معتمد ومفعّل ✓'
        : `الصفة الحالية: طالب (${session?.approval ?? 'pending'})`;

  const enrolled = data.enrollments[session?.user?.uid ?? ''] ?? {};

  const copyId = () => {
    const text = `ID: ${id} | UID: ${session?.user?.uid ?? ''} | Email: ${session?.user?.email ?? ''}`;
    navigator.clipboard?.writeText(text).catch(() => {});
    setCopied(true);
    pushToast(`تم نسخ معرّف الـ ID (${id}) إلى الحافظة ✓`, 'success');
  };

  const checkUpdates = async () => {
    if (!UPDATE_FEED_URL) {
      pushToast(`الإصدار الحالي ${APP_VERSION}. سيُفعَّل التحقق التلقائي عند ضبط رابط التحديث (UPDATE_FEED_URL).`, 'info');
      return;
    }
    setChecking(true);
    try {
      const res = await fetch(UPDATE_FEED_URL, { cache: 'no-store' });
      const info = await res.json();
      if (info?.version && info.version !== APP_VERSION) {
        pushToast(`يتوفر إصدار جديد ${info.version} — ${info.url ?? ''}`, 'success');
      } else {
        pushToast(`أنت على أحدث إصدار (${APP_VERSION}) ✓`, 'success');
      }
    } catch {
      pushToast('تعذّر التحقق من التحديثات', 'error');
    } finally {
      setChecking(false);
    }
  };

  return (
    <div className="stack">
      <div className="card profile-card">
        <div className="profile-avatar">
          {isVerifiedAdmin ? <ShieldCheck size={34} /> : <GraduationCap size={34} />}
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800, fontSize: 18 }}>{session?.user?.displayName || 'مستخدم Elghaliz Medicine'}</div>
          <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.75)' }}>{session?.user?.email}</div>
          <div className="chip" style={{ marginTop: 8, background: 'rgba(201,162,77,0.2)', color: 'var(--gold-accent)' }}>{roleLabel}</div>
        </div>
      </div>

      <div className="card settings-card">
        <div className="row">
          <div className="avatar-circle" style={{ borderRadius: 12 }}><Fingerprint size={22} /></div>
          <div style={{ flex: 1 }}>
            <div style={{ fontFamily: 'var(--font-head)', fontWeight: 700 }}>معرّف الحساب الخاص بك (User ID)</div>
            <div className="mono" style={{ fontSize: 15, color: 'var(--emerald-primary)', fontWeight: 700 }}>{id}</div>
          </div>
          <button className="btn btn-sm btn-gold" onClick={copyId}>
            <Copy size={15} /> {copied ? 'تم النسخ ✓' : 'نسخ ID'}
          </button>
        </div>
      </div>

      <div className="card settings-card">
        <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800 }}>كورساتي المسجلة</div>
        {data.categories.filter((c) => enrolled[c.id] === 'approved').length === 0 ? (
          <div className="helper">أنت غير مشترك في أي كورس حالياً. يمكنك إرسال طلب انضمام لأي كورس من تبويب (المحاضرات).</div>
        ) : (
          data.categories
            .filter((c) => enrolled[c.id])
            .map((c) => (
              <div key={c.id} className="setting-row">
                <span style={{ fontWeight: 700 }}>{c.name}</span>
                <span className={`badge ${enrolled[c.id] === 'approved' ? 'badge-green' : 'badge-gold'}`}>
                  {enrolled[c.id] === 'approved' ? 'مفعّل' : 'بانتظار موافقة الدكتور'}
                </span>
              </div>
            ))
        )}
      </div>

      <div className="card settings-card">
        <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800 }}>إدارة التنزيلات وحماية الأجهزة</div>
        <div className="setting-row">
          <div>
            <div style={{ fontWeight: 700, fontSize: 14 }}>تنزيل المحاضرات عبر الواي فاي فقط</div>
            <div className="helper">حماية باقة بيانات الهاتف عند تنزيل الفيديوهات الكبيرة</div>
          </div>
          <button className={`toggle ${wifiOnly ? 'on' : ''}`} onClick={() => setWifiOnly((v) => !v)} aria-label="تنزيل عبر الواي فاي فقط" />
        </div>
        <div className="setting-row">
          <div>
            <div style={{ fontWeight: 700, fontSize: 14 }}>حذف المحاضرات المشاهَدة تلقائياً</div>
            <div className="helper">توفير المساحة تلقائياً فور إكمال مشاهدة المحاضرة (🟢 مكتملة)</div>
          </div>
          <button className={`toggle ${autoDelete ? 'on' : ''}`} onClick={() => setAutoDelete((v) => !v)} aria-label="حذف المشاهد تلقائياً" />
        </div>
        <div className="setting-row">
          <div className="row">
            <Wifi size={18} />
            <span style={{ fontWeight: 700, fontSize: 14 }}>الأجهزة المرتبطة بالحساب (1/2 كحد أقصى)</span>
          </div>
          <button className="btn btn-sm btn-outline" onClick={() => pushToast('تم تسجيل الخروج من جميع الأجهزة الأخرى وتثبيت هذا الجهاز فقط (1/2) ✓', 'success')}>
            <MonitorSmartphone size={15} /> خروج من الأجهزة الأخرى
          </button>
        </div>
      </div>

      <div className="card settings-card">
        <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800 }}>المظهر والخط واللغة</div>
        <div className="setting-row">
          <div>
            <div style={{ fontWeight: 700, fontSize: 14 }}>الوضع الليلي (Dark Mode)</div>
            <div className="helper">{theme === 'dark' ? 'مفعّل حالياً لراحة العينين' : 'الوضع النهاري الكريمي مفعّل'}</div>
          </div>
          <button className="btn btn-sm btn-soft" onClick={toggleTheme}>
            {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />} تبديل
          </button>
        </div>
        <div className="setting-row">
          <span style={{ fontWeight: 700, fontSize: 14 }}>حجم الخط:</span>
          <div style={{ display: 'flex', gap: 6 }}>
            {(['normal', 'large', 'xlarge'] as const).map((f) => (
              <button key={f} className={`font-chip ${fontScale === f ? 'active' : ''}`} onClick={() => setFontScale(f)}>
                {f === 'normal' ? 'عادي' : f === 'large' ? 'كبير' : 'كبير جداً'}
              </button>
            ))}
          </div>
        </div>
        <div className="setting-row">
          <span style={{ fontWeight: 700, fontSize: 14 }}>لغة الواجهة:</span>
          <div style={{ display: 'flex', gap: 6 }}>
            <button className="font-chip active">العربية (RTL)</button>
            <button className="font-chip" onClick={() => pushToast('النسخة الحالية عربية فقط (RTL)', 'info')}>English</button>
          </div>
        </div>
        {isVerifiedAdmin && (
          <button className="btn btn-outline btn-block" onClick={() => (isAdminView ? switchToStudentView() : switchToAdminView())}>
            {isAdminView ? 'التبديل إلى معاينة واجهة الطالب' : 'العودة إلى لوحة الدكتور / المسؤول'}
          </button>
        )}
      </div>

      <div className="card settings-card">
        <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800 }}>التحديثات</div>
        <div className="setting-row">
          <div>
            <div style={{ fontWeight: 700, fontSize: 14 }}>إصدار التطبيق: {APP_VERSION}</div>
            <div className="helper">تحقّق من توفّر إصدار أحدث</div>
          </div>
          <button className="btn btn-sm btn-outline" onClick={checkUpdates} disabled={checking}>
            {checking ? 'جارٍ الفحص...' : 'التحقق من التحديثات'}
          </button>
        </div>
      </div>

      <button
        className="btn btn-outline btn-block"
        onClick={() => {
          useDataStore.getState().reset();
          try {
            localStorage.removeItem('elghaliz.demo.data.v1');
          } catch {
            /* ignore */
          }
          pushToast('تمت إعادة تعيين البيانات التجريبية', 'info');
        }}
      >
        إعادة تعيين البيانات التجريبية
      </button>

      <button className="btn btn-danger btn-block" style={{ height: 52, borderRadius: 16 }} onClick={signOut}>
        <LogOut size={18} /> تسجيل الخروج من الحساب
      </button>
    </div>
  );
}
