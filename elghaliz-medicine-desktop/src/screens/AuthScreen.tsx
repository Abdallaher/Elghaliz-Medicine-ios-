import { useState } from 'react';
import { GraduationCap, HeartPulse, LogIn, Moon, ShieldCheck, Smartphone, Sun } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { signIn } from '../lib/session';
import { Modal } from '../components/ui';

const IOS_STEPS = [
  '1. افتح متصفح Safari على جهاز الآيفون.',
  '2. سجل الدخول بنفس بريدك الإلكتروني في Google واختر صفة (طالب).',
  '3. اضغط على زر المشاركة (Share) في Safari ثم اختر (إضافة إلى الشاشة الرئيسية - Add to Home Screen) لتثبيت أيقونة Elghaliz Medicine على شاشة الآيفون.',
  '4. أرسل بريدك الإلكتروني أو معرّف الـ ID الخاص بك للدكتور لتفعيل حسابك والكورسات المشترك بها.',
];

export function AuthScreen() {
  const theme = useAppStore((s) => s.theme);
  const toggleTheme = useAppStore((s) => s.toggleTheme);
  const [role, setRole] = useState<'STUDENT' | 'ADMIN'>('STUDENT');
  const [busy, setBusy] = useState(false);
  const [showIos, setShowIos] = useState(false);
  const [copied, setCopied] = useState(false);

  const doSignIn = () => {
    setBusy(true);
    // Call synchronously (no timer) to preserve the click gesture so the Google popup isn't blocked.
    signIn(role);
  };

  const copyIos = () => {
    const text =
      'منصة Elghaliz Medicine متوافقة مع أجهزة iPhone و iPad عبر متصفح Safari (تطبيق الويب التقدمي PWA) مع تسجيل الدخول بنفس البريد الإلكتروني المعتمد.';
    navigator.clipboard?.writeText(text).catch(() => {});
    setCopied(true);
  };

  return (
    <div className="auth-screen">
      <div className="auth-hero">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 520 }}>
          <div className="auth-hero-logo">
            <HeartPulse size={44} />
          </div>
          <div className="auth-hero-title">Elghaliz Medicine</div>
          <div className="auth-hero-welcome">أهلاً وسهلاً بك في التطبيق الغليظ ~</div>
          <p className="auth-tagline">أكاديمية الطب التعليمي والمحاضرات التفاعلية</p>
        </div>
      </div>

      <div className="auth-panel">
        <div className="auth-sheet">
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button className="icon-btn" style={{ borderColor: 'var(--card-border)', background: 'var(--color-surface)', color: 'var(--emerald-primary)' }} onClick={toggleTheme} aria-label="تبديل الوضع الليلي">
              {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
            </button>
          </div>

          <div>
            <div style={{ fontFamily: 'var(--font-head)', fontWeight: 800, fontSize: 20 }}>اختر صفة الدخول إلى المنصة</div>
            <div className="helper">يتم حفظ إيميلك وصفتك تلقائياً بعد أول تسجيل دخول</div>
          </div>

          <div className="role-choice">
            <button className={`role-mini ${role === 'ADMIN' ? 'selected' : ''}`} onClick={() => setRole('ADMIN')}>
              <div className="role-icon" style={{ width: 42, height: 42, borderRadius: 14 }}>
                <ShieldCheck size={22} />
              </div>
              <div className="role-mini-title">مسؤول / دكتور</div>
              <div className="role-mini-desc">رفع المحاضرات والاختبارات وإدارة الطلاب</div>
            </button>
            <button className={`role-mini ${role === 'STUDENT' ? 'selected' : ''}`} onClick={() => setRole('STUDENT')}>
              <div className="role-icon" style={{ width: 42, height: 42, borderRadius: 14 }}>
                <GraduationCap size={22} />
              </div>
              <div className="role-mini-title">طالب</div>
              <div className="role-mini-desc">مشاهدة المحاضرات وتنزيلها وحل الاختبارات</div>
            </button>
          </div>

          <button className="btn btn-primary btn-block btn-lg" onClick={doSignIn} disabled={busy} style={{ height: 52, borderRadius: 16 }}>
            {busy ? (
              <>
                <span className="spinner" style={{ borderTopColor: 'var(--gold-accent)' }} /> جاري التحقق من الحساب...
              </>
            ) : (
              <>
                <LogIn size={20} color="var(--gold-accent)" />
                {role === 'ADMIN' ? 'تسجيل الدخول كدكتور / مسؤول (Sign in with Google)' : 'تسجيل الدخول كطالب (Sign in with Google)'}
              </>
            )}
          </button>

          <button className="btn btn-soft btn-block" onClick={() => setShowIos(true)} style={{ height: 48, borderRadius: 16 }}>
            <Smartphone size={20} /> طريقة تسجيل الدخول لمستخدمي الآيفون (iPhone / iOS)
          </button>

          <div className="helper" style={{ textAlign: 'center' }}>
            نسخة سطح المكتب لنظام Windows — تعمل بالكامل بدون إنترنت (وضع تجريبي).
          </div>
        </div>
      </div>

      <Modal
        open={showIos}
        title={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <Smartphone size={20} /> تشغيل المنصة على الآيفون والآيباد (iOS)
          </span>
        }
        onClose={() => {
          setShowIos(false);
          setCopied(false);
        }}
        actions={
          <>
            <button className="btn btn-primary" onClick={copyIos}>
              نسخ إرشادات الآيفون
            </button>
            <button className="btn btn-outline" onClick={() => setShowIos(false)}>
              إغلاق
            </button>
          </>
        }
      >
        <p style={{ fontSize: 14, color: 'var(--color-text)' }}>
          يمكن لطلاب الدفعة ومستخدمي أجهزة iPhone / iPad الدخول إلى نفس حساباتهم ومشاهدة جميع المحاضرات والاختبارات باتباع الخطوات التالية:
        </p>
        <div style={{ background: 'var(--light-mint)', borderRadius: 14, padding: 12, display: 'flex', flexDirection: 'column', gap: 6 }}>
          {IOS_STEPS.map((s) => (
            <div key={s} style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 13 }}>
              {s}
            </div>
          ))}
        </div>
        {copied && (
          <div style={{ color: 'var(--emerald-primary)', fontWeight: 700, fontSize: 13 }}>
            تم نسخ إرشادات الدخول لمستخدمي الآيفون لمشاركتها مع الطلاب ✓
          </div>
        )}
      </Modal>
    </div>
  );
}
