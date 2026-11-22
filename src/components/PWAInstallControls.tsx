import React from 'react';
import { Download, Share, WifiOff, X } from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';
import { useOnlineStatus } from '../hooks/useOnlineStatus';

export const PWAInstallButton: React.FC<{ compact?: boolean }> = ({ compact = false }) => {
  const { canInstallPrompt, isInstalled, promptInstall } = usePWAInstall();
  if (isInstalled || !canInstallPrompt) return null;

  return (
    <button
      type="button"
      onClick={() => void promptInstall()}
      aria-label="تثبيت التطبيق"
      className={
        compact
          ? 'flex items-center gap-1.5 rounded-xl bg-[#C9A24D] px-2.5 py-1.5 text-xs font-bold text-[#0F241D] hover:bg-[#d8b464] transition cursor-pointer'
          : 'inline-flex items-center justify-center gap-2 rounded-2xl bg-[#C9A24D] px-4 py-2.5 text-sm font-bold text-[#0F241D] hover:bg-[#d8b464] transition cursor-pointer'
      }
    >
      <Download className="h-4 w-4" />
      <span>تثبيت التطبيق</span>
    </button>
  );
};

export const IOSInstallBanner: React.FC = () => {
  const { showIosBanner, dismissIosPrompt } = usePWAInstall();
  if (!showIosBanner) return null;

  return (
    <aside
      role="status"
      aria-label="تثبيت التطبيق على الآيفون"
      className="fixed bottom-24 left-3 right-3 z-40 mx-auto max-w-xl rounded-[22px] border border-[#C9A24D]/60 bg-[#0F3D2E] text-[#FFFDF8] shadow-2xl px-4 py-3.5"
    >
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-[#C9A24D]/20 text-[#C9A24D] border border-[#C9A24D]/40">
          <Share className="h-5 w-5" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-xs font-extrabold text-[#C9A24D] mb-0.5">
            تثبيت التطبيق على الآيفون (iOS)
          </p>
          <p className="text-xs leading-relaxed text-[#F7F3EA]">
            لتثبيت Elghaliz Medicine على الآيفون: اضغط زر المشاركة أو الثلاث نقاط ثم اختر (إضافة إلى الشاشة الرئيسية)
          </p>
        </div>
        <button
          type="button"
          onClick={dismissIosPrompt}
          aria-label="إخفاء تنبيه التثبيت"
          className="shrink-0 flex h-8 w-8 items-center justify-center rounded-full bg-white/10 text-[#F7F3EA] hover:bg-white/20 transition cursor-pointer"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </aside>
  );
};

export const OfflineIndicator: React.FC = () => {
  const isOnline = useOnlineStatus();
  if (isOnline) return null;

  return (
    <div
      role="status"
      className="sticky top-0 z-40 flex items-center justify-center gap-2 bg-[#B33A3A] text-white px-4 py-2 text-xs font-bold shadow"
    >
      <WifiOff className="h-4 w-4 shrink-0" />
      <span>أنت غير متصل بالإنترنت — يمكنك مشاهدة المحاضرات المنزّلة من تبويب التنزيلات</span>
    </div>
  );
};
