import React from 'react';
import { Lock, LogOut, Smartphone } from 'lucide-react';
import { useAppStore } from '../store/appStore';

/**
 * Strict Permanent 1-Device Lock Screen.
 * Enforces max 1 registered device per email with NO unlink / reset device option for student or doctor.
 */
export const DeviceLockedScreen: React.FC = () => {
  const { user, currentDeviceId, lockedDeviceId, signOut } = useAppStore();

  return (
    <div className="min-h-dvh flex items-center justify-center bg-[#F7F3EA] dark:bg-[#0A2A20] px-4 py-8">
      <div className="w-full max-w-md rounded-[28px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#B33A3A]/30 shadow-xl p-6 text-center">
        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-[22px] bg-[#B33A3A]/15 text-[#B33A3A]">
          <Lock className="h-8 w-8" />
        </div>

        <h1 className="text-xl font-black text-[#B33A3A] dark:text-red-300">
          الحساب مقفل على جهاز آخر بشكل دائم
        </h1>

        <p className="mt-2.5 text-sm text-[#546B61] dark:text-[#A5C0B4] leading-relaxed">
          تطبق منصة Elghaliz Medicine سياسة <strong>قفل الجهاز الواحد الدائم (1-Device Lock)</strong>.
          هذا الحساب ({user?.email}) مرتبط مسبقاً بجهاز واحد فقط ولا يمكن فتحه من جهاز أو متصفح ثانٍ.
        </p>

        <div className="mt-5 rounded-2xl bg-[#E9F0E8]/70 dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] p-4 text-right space-y-2 text-xs">
          <div className="flex items-center justify-between gap-2">
            <span className="text-[#546B61] dark:text-[#A5C0B4] flex items-center gap-1.5">
              <Smartphone className="h-3.5 w-3.5" />
              <span>الجهاز المعتمد (1/1):</span>
            </span>
            <span className="font-mono font-bold text-[#0F3D2E] dark:text-[#C9A24D]">
              {lockedDeviceId ? `${lockedDeviceId.slice(0, 16)}...` : 'مسجّل'}
            </span>
          </div>
          <div className="flex items-center justify-between gap-2">
            <span className="text-[#546B61] dark:text-[#A5C0B4]">معرّف هذا المتصفح:</span>
            <span className="font-mono font-bold text-[#B33A3A] dark:text-red-300">
              {currentDeviceId.slice(0, 16)}...
            </span>
          </div>
        </div>

        <p className="mt-4 text-[11px] text-[#546B61] dark:text-[#A5C0B4]">
          يُرجى تسجيل الدخول من جهازك الأصلي المسجّل، أو الخروج والدخول بحساب آخر.
        </p>

        {/* Strict 1-Device Lock: Sign Out ONLY — zero unlink or device reset button */}
        <button
          type="button"
          onClick={() => void signOut()}
          className="mt-6 w-full flex items-center justify-center gap-2 rounded-2xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white font-extrabold text-sm py-3.5 transition cursor-pointer"
        >
          <LogOut className="h-4 w-4" />
          <span>تسجيل الخروج</span>
        </button>
      </div>
    </div>
  );
};
