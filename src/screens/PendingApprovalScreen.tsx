import React, { useState } from 'react';
import { Clock, LogOut, RefreshCw, ShieldAlert, ShieldCheck } from 'lucide-react';
import { useAppStore } from '../store/appStore';

export const PendingApprovalScreen: React.FC = () => {
  const { user, approvalStatus, currentDeviceId, refreshApprovalStatus, verifyAsAdminNow, signOut } =
    useAppStore();
  const [checking, setChecking] = useState(false);

  const isSuspended = approvalStatus === 'suspended';

  const handleRefresh = async () => {
    setChecking(true);
    try {
      await refreshApprovalStatus();
    } finally {
      setChecking(false);
    }
  };

  return (
    <div className="min-h-dvh flex items-center justify-center bg-[#F7F3EA] dark:bg-[#0A2A20] px-4 py-8">
      <div className="w-full max-w-md rounded-[28px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] shadow-xl p-6 text-center">
        <div
          className={`mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-[22px] ${
            isSuspended
              ? 'bg-[#B33A3A]/15 text-[#B33A3A]'
              : 'bg-[#C9A24D]/20 text-[#9A7624] dark:text-[#C9A24D]'
          }`}
        >
          {isSuspended ? <ShieldAlert className="h-8 w-8" /> : <Clock className="h-8 w-8" />}
        </div>

        <h1 className="text-xl font-black text-[#0F3D2E] dark:text-[#F4EFE4]">
          {isSuspended ? 'تم إيقاف الحساب مؤقتاً' : 'حسابك بانتظار موافقة الدكتور'}
        </h1>

        <p className="mt-2 text-sm text-[#546B61] dark:text-[#A5C0B4] leading-relaxed">
          {isSuspended
            ? 'تم تعليق صلاحية الوصول لهذا الحساب من قِبل إدارة Elghaliz Medicine. يُرجى التواصل مع الدكتور لإعادة التفعيل.'
            : 'تم إرسال طلب تسجيلك بنجاح إلى لوحة الإدارة. فور موافقة الدكتور سيتم فتح الكورسات والمحاضرات تلقائياً.'}
        </p>

        <div className="mt-5 rounded-2xl bg-[#E9F0E8]/70 dark:bg-[#0A2A20] border border-[#EFE8D8] dark:border-[#1E4F3D] p-3.5 text-right space-y-1.5">
          <div className="flex items-center justify-between text-xs">
            <span className="text-[#546B61] dark:text-[#A5C0B4]">الاسم:</span>
            <span className="font-bold text-[#0F241D] dark:text-[#F4EFE4]">
              {user?.displayName || 'طالب'}
            </span>
          </div>
          <div className="flex items-center justify-between text-xs">
            <span className="text-[#546B61] dark:text-[#A5C0B4]">البريد:</span>
            <span className="font-bold text-[#0F241D] dark:text-[#F4EFE4] truncate max-w-[210px]">
              {user?.email}
            </span>
          </div>
          <div className="flex items-center justify-between text-xs">
            <span className="text-[#546B61] dark:text-[#A5C0B4]">معرّف الجهاز المسجّل (1/1):</span>
            <span className="font-mono text-[11px] font-bold text-[#1F6B52] dark:text-[#C9A24D]">
              {currentDeviceId.slice(0, 14)}...
            </span>
          </div>
        </div>

        <div className="mt-6 space-y-2.5">
          <button
            type="button"
            disabled={checking}
            onClick={() => void handleRefresh()}
            className="w-full flex items-center justify-center gap-2 rounded-2xl bg-[#0F3D2E] hover:bg-[#1F6B52] text-white font-extrabold text-sm py-3.5 transition cursor-pointer disabled:opacity-60"
          >
            <RefreshCw className={`h-4 w-4 ${checking ? 'animate-spin' : ''}`} />
            <span>تحديث حالة الموافقة</span>
          </button>

          <button
            type="button"
            onClick={() => void verifyAsAdminNow()}
            className="w-full flex items-center justify-center gap-2 rounded-2xl bg-[#E9F0E8] dark:bg-[#0A2A20] text-[#0F3D2E] dark:text-[#C9A24D] border border-[#0F3D2E]/15 dark:border-[#C9A24D]/30 font-bold text-xs py-3 hover:bg-[#dce8da] transition cursor-pointer"
          >
            <ShieldCheck className="h-4 w-4" />
            <span>التحقق من صلاحية المدير</span>
          </button>

          <button
            type="button"
            onClick={() => void signOut()}
            className="w-full flex items-center justify-center gap-2 rounded-2xl bg-[#B33A3A]/10 text-[#B33A3A] dark:text-red-300 font-bold text-xs py-3 hover:bg-[#B33A3A]/20 transition cursor-pointer"
          >
            <LogOut className="h-4 w-4" />
            <span>تسجيل الخروج</span>
          </button>
        </div>
      </div>
    </div>
  );
};
