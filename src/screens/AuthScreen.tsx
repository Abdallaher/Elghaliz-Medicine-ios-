import React, { useState } from 'react';
import {
  Activity,
  GraduationCap,
  Moon,
  ShieldCheck,
  Smartphone,
  Sun,
  CheckCircle2,
  Loader2,
} from 'lucide-react';
import type { UserRoleMode } from '../types/models';
import { useAppStore } from '../store/appStore';
import { Modal } from '../components/Modal';
import { IOSInstallBanner, PWAInstallButton } from '../components/PWAInstallControls';

export const AuthScreen: React.FC = () => {
  const { isDarkMode, authBusy, authError, activeRole, toggleDarkMode, signInGoogle } =
    useAppStore();
  const [selectedRole, setSelectedRole] = useState<UserRoleMode>(activeRole);
  const [showIosGuide, setShowIosGuide] = useState(false);

  return (
    <div className="min-h-dvh flex flex-col bg-[#0F3D2E] text-white relative overflow-hidden">
      {/* Decorative faint thin gold concentric rings matching Android AuthScreen */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-24 -left-24 h-80 w-80 rounded-full border border-[#C9A24D]/15"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-12 -left-12 h-56 w-56 rounded-full border border-[#C9A24D]/20"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute top-16 -right-28 h-72 w-72 rounded-full border border-[#C9A24D]/15"
      />

      {/* Top Action Bar */}
      <div className="relative z-10 flex items-center justify-between px-5 pt-[max(1rem,env(safe-area-inset-top))] max-w-2xl w-full mx-auto">
        <button
          type="button"
          onClick={() => setShowIosGuide(true)}
          className="inline-flex items-center gap-1.5 rounded-xl bg-white/10 border border-[#C9A24D]/30 px-3 py-1.5 text-xs font-bold text-[#F7F3EA] hover:bg-white/20 transition cursor-pointer"
        >
          <Smartphone className="h-3.5 w-3.5 text-[#C9A24D]" />
          <span>تثبيت على الآيفون</span>
        </button>

        <div className="flex items-center gap-2">
          <PWAInstallButton compact />
          <button
            type="button"
            onClick={toggleDarkMode}
            aria-label={isDarkMode ? 'الوضع الفاتح' : 'الوضع الليلي'}
            className="flex h-10 w-10 items-center justify-center rounded-2xl bg-white/10 border border-white/10 text-[#C9A24D] hover:bg-white/20 transition cursor-pointer"
          >
            {isDarkMode ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
          </button>
        </div>
      </div>

      {/* Hero Emblem */}
      <div className="relative z-10 flex flex-col items-center justify-center text-center px-6 pt-8 pb-9 flex-1">
        <div className="relative mb-5">
          <div className="flex h-24 w-24 items-center justify-center rounded-[30px] bg-gradient-to-br from-[#1F6B52] to-[#0B2D22] border-2 border-[#C9A24D]/75 shadow-2xl">
            <Activity className="h-12 w-12 text-[#C9A24D] stroke-[2.4]" />
          </div>
          <span className="absolute -bottom-2 left-1/2 -translate-x-1/2 rounded-full bg-[#C9A24D] px-2.5 py-0.5 text-[10px] font-black text-[#0F241D] tracking-wide shadow">
            MEDICAL
          </span>
        </div>

        <h1 className="text-3xl sm:text-4xl font-black tracking-tight text-[#FFFDF8]">
          Elghaliz Medicine
        </h1>
        <p className="mt-2.5 text-base sm:text-lg font-bold text-[#C9A24D]">
          أهلاً وسهلاً بك في التطبيق الغليظ ~
        </p>
        <p className="mt-1.5 text-xs sm:text-sm text-[#E9F0E8]/80 max-w-md leading-relaxed">
          منصة المحاضرات الطبية والاختبارات الموقوتة والتشغيل أوفلاين المشفّر للآيفون
        </p>
      </div>

      {/* Cream Bottom Sheet matching Android rounded-t-[38px] */}
      <section className="relative z-10 w-full max-w-2xl mx-auto rounded-t-[38px] bg-[#F7F3EA] dark:bg-[#0A2A20] text-[#0F241D] dark:text-[#F4EFE4] px-6 pt-4 pb-[max(2rem,env(safe-area-inset-bottom))] shadow-2xl border-t border-[#C9A24D]/30">
        <div className="mx-auto mb-5 h-1.5 w-12 rounded-full bg-[#C9A24D]/60" />

        <div className="mb-4 text-center">
          <h2 className="text-lg font-extrabold text-[#0F3D2E] dark:text-[#F4EFE4]">
            اختر صفة الدخول
          </h2>
          <p className="text-xs text-[#546B61] dark:text-[#A5C0B4] mt-0.5">
            يرتبط حساب الطالب بجهاز واحد فقط بشكل دائم لحماية حقوق المحاضرات
          </p>
        </div>

        {/* Role Selector Cards */}
        <div className="grid grid-cols-2 gap-3 mb-5">
          <button
            type="button"
            onClick={() => setSelectedRole('STUDENT')}
            className={`relative flex flex-col items-center text-center rounded-[22px] p-4 border-2 transition cursor-pointer ${
              selectedRole === 'STUDENT'
                ? 'bg-[#FFFDF8] dark:bg-[#123528] border-[#0F3D2E] dark:border-[#C9A24D] shadow-md'
                : 'bg-[#FFFDF8]/60 dark:bg-[#123528]/50 border-[#EFE8D8] dark:border-[#1E4F3D]'
            }`}
          >
            {selectedRole === 'STUDENT' && (
              <CheckCircle2 className="absolute top-3 left-3 h-4 w-4 text-[#0F3D2E] dark:text-[#C9A24D]" />
            )}
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#E9F0E8] dark:bg-[#0A2A20] text-[#0F3D2E] dark:text-[#C9A24D] mb-2.5">
              <GraduationCap className="h-6 w-6" />
            </div>
            <span className="text-sm font-extrabold">طالب</span>
            <span className="text-[11px] text-[#546B61] dark:text-[#A5C0B4] mt-0.5">
              متابعة الكورسات والاختبارات
            </span>
          </button>

          <button
            type="button"
            onClick={() => setSelectedRole('ADMIN')}
            className={`relative flex flex-col items-center text-center rounded-[22px] p-4 border-2 transition cursor-pointer ${
              selectedRole === 'ADMIN'
                ? 'bg-[#FFFDF8] dark:bg-[#123528] border-[#0F3D2E] dark:border-[#C9A24D] shadow-md'
                : 'bg-[#FFFDF8]/60 dark:bg-[#123528]/50 border-[#EFE8D8] dark:border-[#1E4F3D]'
            }`}
          >
            {selectedRole === 'ADMIN' && (
              <CheckCircle2 className="absolute top-3 left-3 h-4 w-4 text-[#0F3D2E] dark:text-[#C9A24D]" />
            )}
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#C9A24D]/20 text-[#9A7624] dark:text-[#C9A24D] mb-2.5">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <span className="text-sm font-extrabold">مسؤول / دكتور</span>
            <span className="text-[11px] text-[#546B61] dark:text-[#A5C0B4] mt-0.5">
              رفع المحاضرات وإدارة الطلاب
            </span>
          </button>
        </div>

        {authError && (
          <div
            role="alert"
            className="mb-4 rounded-2xl bg-[#B33A3A]/10 border border-[#B33A3A]/30 px-4 py-3 text-xs font-bold text-[#B33A3A] dark:text-red-300"
          >
            {authError}
          </div>
        )}

        {/* Primary Google Sign-In CTA */}
        <button
          type="button"
          disabled={authBusy}
          onClick={() => void signInGoogle(selectedRole)}
          className="w-full flex items-center justify-center gap-3 rounded-[20px] bg-[#0F3D2E] hover:bg-[#16523F] disabled:opacity-60 text-[#FFFDF8] font-extrabold text-sm sm:text-base py-4 px-5 shadow-lg border border-[#C9A24D]/40 transition cursor-pointer"
        >
          {authBusy ? (
            <>
              <Loader2 className="h-5 w-5 animate-spin text-[#C9A24D]" />
              <span>جاري فتح تسجيل الدخول عبر Google...</span>
            </>
          ) : (
            <>
              <span className="flex h-7 w-7 items-center justify-center rounded-full bg-white text-[#0F3D2E] font-black text-sm">
                G
              </span>
              <span>
                {selectedRole === 'ADMIN'
                  ? 'دخول المسؤول بحساب Google'
                  : 'تسجيل الدخول بحساب Google'}
              </span>
            </>
          )}
        </button>
      </section>

      <IOSInstallBanner />

      <Modal
        open={showIosGuide}
        onClose={() => setShowIosGuide(false)}
        title="تثبيت Elghaliz Medicine كتطبيق على الآيفون"
        subtitle="يعمل بملء الشاشة ويدعم تنزيل المحاضرات أوفلاين"
      >
        <ol className="space-y-3 text-sm leading-relaxed text-[#0F241D] dark:text-[#F4EFE4]">
          <li className="flex gap-2.5 rounded-2xl bg-[#E9F0E8]/70 dark:bg-[#0A2A20] p-3">
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#0F3D2E] text-[#C9A24D] text-xs font-black">
              1
            </span>
            <span>افتح الموقع في متصفح Safari على الآيفون.</span>
          </li>
          <li className="flex gap-2.5 rounded-2xl bg-[#E9F0E8]/70 dark:bg-[#0A2A20] p-3">
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#0F3D2E] text-[#C9A24D] text-xs font-black">
              2
            </span>
            <span>اضغط زر المشاركة (المربع والسهم للأعلى) أو الثلاث نقاط أسفل الشاشة.</span>
          </li>
          <li className="flex gap-2.5 rounded-2xl bg-[#E9F0E8]/70 dark:bg-[#0A2A20] p-3">
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[#0F3D2E] text-[#C9A24D] text-xs font-black">
              3
            </span>
            <span>اختر «إضافة إلى الشاشة الرئيسية» (Add to Home Screen) ثم اضغط «إضافة».</span>
          </li>
        </ol>
      </Modal>
    </div>
  );
};
