import React, { useEffect } from 'react';
import { X } from 'lucide-react';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  maxWidthClass?: string;
}

export const Modal: React.FC<ModalProps> = ({
  open,
  onClose,
  title,
  subtitle,
  children,
  maxWidthClass = 'max-w-lg',
}) => {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/60 backdrop-blur-[2px] p-0 sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className={`w-full ${maxWidthClass} max-h-[90vh] overflow-y-auto rounded-t-[28px] sm:rounded-[28px] bg-[#FFFDF8] dark:bg-[#123528] border border-[#EFE8D8] dark:border-[#1E4F3D] shadow-2xl p-5 sm:p-6 text-[#0F241D] dark:text-[#F4EFE4]`}
      >
        <div className="mx-auto mb-3 h-1.5 w-11 rounded-full bg-[#C9A24D]/40 sm:hidden" />
        <div className="flex items-start justify-between gap-3 mb-4">
          <div className="min-w-0">
            <h3 className="text-lg font-extrabold text-[#0F3D2E] dark:text-[#F4EFE4] leading-snug">
              {title}
            </h3>
            {subtitle && (
              <p className="text-xs text-[#546B61] dark:text-[#A5C0B4] mt-0.5 leading-relaxed">
                {subtitle}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="إغلاق"
            className="shrink-0 flex h-9 w-9 items-center justify-center rounded-full bg-[#E9F0E8] dark:bg-[#0A2A20] text-[#0F3D2E] dark:text-[#E9F0E8] hover:bg-[#dce8da] transition cursor-pointer"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
};
