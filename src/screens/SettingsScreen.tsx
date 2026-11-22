import React from 'react';
import {
  CheckCircle2,
  Copy,
  HardDrive,
  Lock,
  LogOut,
  Moon,
  ShieldCheck,
  Smartphone,
  Sun,
  Trash2,
  UserCog,
  Wifi,
} from 'lucide-react';
import { APP_VERSION } from '../config';
import { useAppStore } from '../store/appStore';
import { formatBytesToMb, formatUniqueUserId } from '../lib/logic';
import { sumVaultBytes } from '../lib/offlineVault';
import { PWAInstallButton } from '../components/PWAInstallControls';

export const SettingsScreen: React.FC = () => {
  const {
    user,
    isAdminUser,
    activeRole,
    currentDeviceId,
    registeredDeviceIds,
    categories,
    courseRequests,
    offlineDownloads,
    storageStatus,
    isDarkMode,
    fontScale,
    language,
    wifiOnlyDownload,
    autoDeleteWatched,
    toggleDarkMode,
    setFontScale,
    setLanguage,
    setWifiOnlyDownload,
    setAutoDeleteWatched,
    setActiveRole,
    clearAllDownloads,
    signOut,
    pushToast,
  } = useAppStore();

  const uniqueId = formatUniqueUserId(user?.uid || '');
  const totalUsedBytes = sumVaultBytes(offlineDownloads);

  const enrolledCourses = categories.filter((cat) => {
    if (isAdminUser && activeRole === 'ADMIN') return true;
    return courseRequests.some(
      (r) => r.userId === user?.uid && r.categoryId === cat.id && r.status === 'approved',
    );
  });

  const handleCopyUserId = async () => {
    try {
      await navigator.clipboard.writeText(uniqueId);
      pushToast('تم نسخ المعرّف الخاص بك ✓', 'success');
    } catch {
      pushToast(`المعرّف الخاص بك: ${uniqueId}`, 'info');
    }
  };

  return (
    <div className="space-y-4">
      {/* Deep Emerald Profile Card */}
      <section className="rounded-[26px] bg-[#0F3D2E] text-white p-5 shadow-md border border-[#C9A24D]/35">
        <div className="flex items-center gap-3.5">
          <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-[#1F6B52] border-2 border-[#C9A24D] text-xl font-black text-[#C9A24D]">
            {(user?.displayName || 'E').slice(0, 1).toUpperCase()}
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="text-base sm:text-lg font-black truncate">
              {user?.displayName || 'طالب Elghaliz'}
            </h2>
            <p className="text-xs text-[#E9F0E8]/80 truncate">{user?.email}</p>
            <div className="mt-1.5 inline-flex items-center gap-1.5 rounded-lg bg-[#C9A24D]/20 border border-[#C9A24D]/40 px-2.5 py-0.5 text-[11px] font-extrabold text-[#C9A24D]">
              <ShieldCheck className="h-3.5 w-3.5" />
              <span>{isAdminUser && activeRole === 'ADMIN' ? 'مسؤول / دكتور' : 'طالب مفعّل'}</span>
            </div>
          </div>
        </div>
      </section>

      {/* Unique User ID Card */}
      <section className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 flex items-center justify-between gap-3">
        <div>
          <p className="text-xs font-bold text-[#546B61] dark:text-[#A5C0B4]">
            المعرّف الأكاديمي (User ID)
          </p>
          <p className="font-mono text-sm font-black text-[#0F3D2E] dark:text-[#C9A24D] mt-0.5">
            {uniqueId}
          </p>
        </div>
        <button
          type="button"
          onClick={() => void handleCopyUserId()}
          className="inline-flex items-center gap-1.5 rounded-xl bg-[#E9F0E8] dark:bg-[#0A2A20] text-[#0F3D2E] dark:text-[#C9A24D] px-3.5 py-2 text-xs font-extrabold cursor-pointer"
        >
          <Copy className="h-3.5 w-3.5" />
          <span>نسخ</span>
        </button>
      </section>

      {/* Enrolled Medical Courses Card */}
      <section className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 space-y-2.5">
        <h3 className="text-sm font-black text-[#0F3D2E] dark:text-[#F4EFE4]">
          الكورسات الطبية المفعّلة ({enrolledCourses.length})
        </h3>
        {enrolledCourses.length === 0 ? (
          <p className="text-xs text-[#546B61] dark:text-[#A5C0B4]">
            لم يتم تفعيل أي كورس بعد. اطلب الانضمام من تبويب المحاضرات.
          </p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {enrolledCourses.map((c) => (
              <span
                key={c.id}
                className="inline-flex items-center gap-1 rounded-xl bg-[#E9F0E8] dark:bg-[#0A2A20] px-3 py-1.5 text-xs font-extrabold text-[#0F3D2E] dark:text-[#4AD1A3]"
              >
                <CheckCircle2 className="h-3.5 w-3.5" />
                <span>{c.name}</span>
              </span>
            ))}
          </div>
        )}
      </section>

      {/* Offline Vault Storage & Strict Permanent 1-Device Lock Card */}
      <section className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 space-y-4">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <HardDrive className="h-5 w-5 text-[#C9A24D]" />
            <h3 className="text-sm font-black text-[#0F3D2E] dark:text-[#F4EFE4]">
              مساحة التنزيلات المشفّرة والأمان
            </h3>
          </div>
          <span className="font-mono text-xs font-black text-[#1F6B52] dark:text-[#C9A24D]">
            {formatBytesToMb(totalUsedBytes)} MB
          </span>
        </div>

        <div className="rounded-2xl bg-[#E9F0E8]/70 dark:bg-[#0A2A20] p-3.5 space-y-2 text-xs">
          <div className="flex items-center justify-between">
            <span className="text-[#546B61] dark:text-[#A5C0B4]">حالة التخزين الدائم:</span>
            <span className="font-extrabold text-[#0F3D2E] dark:text-[#4AD1A3]">
              {storageStatus.persisted ? 'دائم ومحمي (Persisted) ✓' : 'تخزين محلي IndexedDB'}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-[#546B61] dark:text-[#A5C0B4]">عدد المحاضرات المنزّلة:</span>
            <span className="font-extrabold">{offlineDownloads.length} محاضرة</span>
          </div>
        </div>

        {/* Wi-Fi Only Toggle */}
        <label className="flex items-center justify-between gap-3 cursor-pointer">
          <span className="flex items-center gap-2 text-xs sm:text-sm font-bold">
            <Wifi className="h-4 w-4 text-[#1F6B52] dark:text-[#C9A24D]" />
            <span>التنزيل عبر Wi-Fi فقط</span>
          </span>
          <input
            type="checkbox"
            checked={wifiOnlyDownload}
            onChange={(e) => setWifiOnlyDownload(e.target.checked)}
            className="h-5 w-5 accent-[#0F3D2E] dark:accent-[#C9A24D]"
          />
        </label>

        {/* Auto-Delete After Watching Toggle (iPhone Safari Quota Protection) */}
        <label className="flex items-center justify-between gap-3 cursor-pointer">
          <span className="flex flex-col">
            <span className="text-xs sm:text-sm font-bold">حذف المحاضرات المشاهَدة تلقائياً</span>
            <span className="text-[11px] text-[#546B61] dark:text-[#A5C0B4]">
              يحافظ على مساحة الآيفون تحت حدود Safari بحذف النسخة المحلية بعد إتمام المشاهدة
            </span>
          </span>
          <input
            type="checkbox"
            checked={autoDeleteWatched}
            onChange={(e) => setAutoDeleteWatched(e.target.checked)}
            className="h-5 w-5 accent-[#0F3D2E] dark:accent-[#C9A24D]"
          />
        </label>

        {offlineDownloads.length > 0 && (
          <button
            type="button"
            onClick={() => void clearAllDownloads()}
            className="w-full inline-flex items-center justify-center gap-2 rounded-2xl bg-[#B33A3A]/10 hover:bg-[#B33A3A]/20 text-[#B33A3A] dark:text-red-300 text-xs font-extrabold py-2.5 cursor-pointer"
          >
            <Trash2 className="h-4 w-4" />
            <span>مسح جميع المحاضرات المنزّلة ({formatBytesToMb(totalUsedBytes)} MB)</span>
          </button>
        )}

        {/* Strict Permanent 1-Device Lock Read-Only Info — NO Unlink Button */}
        <div className="rounded-2xl border border-[#C9A24D]/40 bg-[#C9A24D]/10 p-3.5 space-y-1.5 text-xs">
          <div className="flex items-center justify-between">
            <span className="inline-flex items-center gap-1.5 font-extrabold text-[#0F3D2E] dark:text-[#C9A24D]">
              <Lock className="h-3.5 w-3.5" />
              <span>قفل الجهاز الواحد الدائم (Strict 1-Device Lock)</span>
            </span>
            <span className="font-mono font-black text-[#0F3D2E] dark:text-[#C9A24D]">
              {Math.min(1, Math.max(registeredDeviceIds.length, 1))}/1
            </span>
          </div>
          <p className="text-[11px] text-[#546B61] dark:text-[#A5C0B4] leading-relaxed">
            هذا الحساب مرتبط بشكل دائم بمعرّف جهاز واحد فقط (محفوظ في localStorage و IndexedDB معاً) ولا يمكن فك الارتباط.
          </p>
          <p className="inline-flex items-center gap-1.5 font-mono text-[11px] font-bold text-[#1F6B52] dark:text-[#E9F0E8]">
            <Smartphone className="h-3.5 w-3.5" />
            <span>{currentDeviceId}</span>
          </p>
        </div>
      </section>

      {/* Appearance, Font Size, Language, Role & PWA Install */}
      <section className="rounded-[24px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 space-y-4">
        <h3 className="text-sm font-black text-[#0F3D2E] dark:text-[#F4EFE4]">
          المظهر وتخصيص التطبيق
        </h3>

        {/* Dark Mode */}
        <div className="flex items-center justify-between gap-3">
          <span className="flex items-center gap-2 text-xs sm:text-sm font-bold">
            {isDarkMode ? (
              <Moon className="h-4 w-4 text-[#C9A24D]" />
            ) : (
              <Sun className="h-4 w-4 text-[#C9A24D]" />
            )}
            <span>الوضع الليلي (#0A2A20)</span>
          </span>
          <button
            type="button"
            onClick={toggleDarkMode}
            className="rounded-xl bg-[#0F3D2E] text-[#C9A24D] px-3.5 py-2 text-xs font-extrabold cursor-pointer"
          >
            {isDarkMode ? 'تفعيل الفاتح' : 'تفعيل الليلي'}
          </button>
        </div>

        {/* Font Scale */}
        <div className="flex items-center justify-between gap-3">
          <span className="text-xs sm:text-sm font-bold">حجم الخط</span>
          <div className="flex items-center gap-1.5">
            {[
              { label: 'عادي', value: 1 },
              { label: 'متوسط', value: 1.1 },
              { label: 'كبير', value: 1.2 },
            ].map((opt) => (
              <button
                key={opt.value}
                type="button"
                onClick={() => setFontScale(opt.value)}
                className={`rounded-xl px-3 py-1.5 text-xs font-extrabold cursor-pointer ${
                  fontScale === opt.value
                    ? 'bg-[#0F3D2E] text-[#C9A24D]'
                    : 'bg-[#E9F0E8] dark:bg-[#0A2A20] text-[#546B61] dark:text-[#A5C0B4]'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        {/* Language */}
        <div className="flex items-center justify-between gap-3">
          <span className="text-xs sm:text-sm font-bold">لغة الواجهة</span>
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setLanguage('ar')}
              className={`rounded-xl px-3 py-1.5 text-xs font-extrabold cursor-pointer ${
                language === 'ar'
                  ? 'bg-[#0F3D2E] text-[#C9A24D]'
                  : 'bg-[#E9F0E8] dark:bg-[#0A2A20]'
              }`}
            >
              العربية (RTL)
            </button>
            <button
              type="button"
              onClick={() => setLanguage('en')}
              className={`rounded-xl px-3 py-1.5 text-xs font-extrabold cursor-pointer ${
                language === 'en'
                  ? 'bg-[#0F3D2E] text-[#C9A24D]'
                  : 'bg-[#E9F0E8] dark:bg-[#0A2A20]'
              }`}
            >
              English
            </button>
          </div>
        </div>

        {/* Admin Role Preview Switch */}
        {isAdminUser && (
          <div className="flex items-center justify-between gap-3 pt-2 border-t border-[#EFE8D8] dark:border-[#1E4F3D]">
            <span className="flex items-center gap-2 text-xs sm:text-sm font-bold">
              <UserCog className="h-4 w-4 text-[#C9A24D]" />
              <span>تبديل وضع العرض</span>
            </span>
            <button
              type="button"
              onClick={() => setActiveRole(activeRole === 'ADMIN' ? 'STUDENT' : 'ADMIN')}
              className="rounded-xl bg-[#1F6B52] text-white px-3.5 py-2 text-xs font-extrabold cursor-pointer"
            >
              {activeRole === 'ADMIN' ? 'معاينة كطالب' : 'العودة لوضع المدير'}
            </button>
          </div>
        )}

        <div className="pt-1 flex items-center justify-between gap-3">
          <PWAInstallButton />
          <span className="text-[11px] font-mono text-[#546B61] dark:text-[#A5C0B4]">
            Elghaliz Medicine v{APP_VERSION}
          </span>
        </div>
      </section>

      {/* Sign Out Button */}
      <button
        type="button"
        onClick={() => void signOut()}
        className="w-full flex items-center justify-center gap-2 rounded-[22px] bg-[#B33A3A]/12 hover:bg-[#B33A3A] text-[#B33A3A] hover:text-white border border-[#B33A3A]/30 font-extrabold text-sm py-3.5 transition cursor-pointer"
      >
        <LogOut className="h-4 w-4" />
        <span>تسجيل الخروج من الحساب</span>
      </button>
    </div>
  );
};
