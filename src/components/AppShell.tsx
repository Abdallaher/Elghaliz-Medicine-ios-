import React from 'react';
import {
  Activity,
  BookOpen,
  ClipboardCheck,
  Download,
  LogOut,
  Moon,
  Settings,
  ShieldCheck,
  Sun,
  UserCog,
} from 'lucide-react';
import type { AppTab } from '../types/models';
import { useAppStore } from '../store/appStore';
import { IOSInstallBanner, OfflineIndicator, PWAInstallButton } from './PWAInstallControls';

interface AppShellProps {
  children: React.ReactNode;
}

export const AppShell: React.FC<AppShellProps> = ({ children }) => {
  const {
    user,
    isAdminUser,
    activeRole,
    activeTab,
    isDarkMode,
    studentRequests,
    courseRequests,
    toasts,
    setActiveTab,
    setActiveRole,
    toggleDarkMode,
    signOut,
    dismissToast,
  } = useAppStore();

  const isAdminView = isAdminUser && activeRole === 'ADMIN';
  const pendingCount =
    studentRequests.filter((s) => s.status === 'pending').length +
    courseRequests.filter((c) => c.status === 'pending').length;

  const navItems: Array<{
    id: AppTab;
    label: string;
    icon: React.ReactNode;
    badge?: number;
  }> = [
    {
      id: 'LIBRARY',
      label: 'المحاضرات',
      icon: <BookOpen className="h-5 w-5" />,
    },
    {
      id: 'EXAMS',
      label: 'الاختبارات',
      icon: <ClipboardCheck className="h-5 w-5" />,
    },
    {
      id: 'DOWNLOADS_ADMINS',
      label: isAdminView ? 'المدراء والطلبات' : 'التنزيلات',
      icon: isAdminView ? <ShieldCheck className="h-5 w-5" /> : <Download className="h-5 w-5" />,
      badge: isAdminView && pendingCount > 0 ? pendingCount : undefined,
    },
    {
      id: 'SETTINGS',
      label: 'الإعدادات',
      icon: <Settings className="h-5 w-5" />,
    },
  ];

  return (
    <div className="min-h-dvh flex flex-col bg-[#F7F3EA] dark:bg-[#0A2A20] text-[#0F241D] dark:text-[#F4EFE4] transition-colors">
      <OfflineIndicator />

      {/* Deep Emerald Curved Top Header matching Android MainActivity */}
      <header className="sticky top-0 z-30 bg-[#0F3D2E] text-white rounded-b-[28px] shadow-md px-4 pt-[max(0.85rem,env(safe-area-inset-top))] pb-3.5">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-[#1F6B52] border border-[#C9A24D]/50 text-[#C9A24D]">
              <Activity className="h-5 w-5" />
            </div>
            <div className="min-w-0">
              <h1 className="text-base sm:text-lg font-extrabold tracking-tight leading-tight truncate">
                Elghaliz Medicine
              </h1>
              <p className="text-[11px] text-[#E9F0E8]/80 truncate">
                {user?.email || 'المنصة الطبية التعليمية'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            <PWAInstallButton compact />

            {isAdminUser && (
              <button
                type="button"
                onClick={() => setActiveRole(isAdminView ? 'STUDENT' : 'ADMIN')}
                aria-label={isAdminView ? 'معاينة كطالب' : 'التبديل لوضع المدير'}
                className="flex items-center gap-1.5 rounded-xl bg-[#1F6B52] border border-[#C9A24D]/40 px-2.5 py-1.5 text-xs font-bold text-[#FFFDF8] hover:bg-[#267d60] transition cursor-pointer"
              >
                <UserCog className="h-3.5 w-3.5 text-[#C9A24D]" />
                <span className="hidden sm:inline">
                  {isAdminView ? 'وضع المدير' : 'وضع الطالب'}
                </span>
              </button>
            )}

            <button
              type="button"
              onClick={toggleDarkMode}
              aria-label={isDarkMode ? 'تفعيل الوضع الفاتح' : 'تفعيل الوضع الليلي'}
              className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/10 text-[#C9A24D] hover:bg-white/20 transition cursor-pointer"
            >
              {isDarkMode ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </button>

            <button
              type="button"
              onClick={() => void signOut()}
              aria-label="تسجيل الخروج"
              className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/10 text-[#F7F3EA] hover:bg-[#B33A3A] transition cursor-pointer"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 w-full max-w-5xl mx-auto px-4 pt-4 pb-28">
        {children}
      </main>

      {/* iOS Safari Add to Home Screen gentle bottom banner */}
      <IOSInstallBanner />

      {/* Floating Deep Emerald Bottom Navigation Bar (100% Android Parity) */}
      <nav
        aria-label="التنقل الرئيسي"
        className="fixed bottom-3 left-3 right-3 z-30 mx-auto max-w-xl safe-bottom"
      >
        <div className="grid grid-cols-4 gap-1 rounded-[28px] bg-[#0F3D2E] p-1.5 shadow-2xl border border-[#1F6B52]/70">
          {navItems.map((item) => {
            const active = activeTab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setActiveTab(item.id)}
                aria-current={active ? 'page' : undefined}
                className={`relative flex flex-col items-center justify-center gap-0.5 rounded-[22px] py-2 px-1 transition cursor-pointer ${
                  active
                    ? 'bg-[#1F6B52] text-[#C9A24D] font-extrabold shadow-inner'
                    : 'text-[#E9F0E8]/75 hover:text-white font-semibold'
                }`}
              >
                <span className="relative">
                  {item.icon}
                  {item.badge !== undefined && (
                    <span className="absolute -top-1.5 -left-2.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#C9A24D] px-1 text-[10px] font-black text-[#0F241D]">
                      {item.badge}
                    </span>
                  )}
                </span>
                <span className="text-[11px] leading-tight truncate max-w-full">
                  {item.label}
                </span>
              </button>
            );
          })}
        </div>
      </nav>

      {/* Toast Stack */}
      {toasts.length > 0 && (
        <div
          aria-live="polite"
          className="fixed top-20 left-1/2 -translate-x-1/2 z-50 flex flex-col gap-2 w-[92%] max-w-md pointer-events-none"
        >
          {toasts.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => dismissToast(t.id)}
              className={`pointer-events-auto rounded-2xl px-4 py-3 text-xs font-bold shadow-xl text-right border cursor-pointer ${
                t.tone === 'success'
                  ? 'bg-[#0F3D2E] text-[#FFFDF8] border-[#C9A24D]'
                  : t.tone === 'error'
                    ? 'bg-[#B33A3A] text-white border-red-300/40'
                    : t.tone === 'warning'
                      ? 'bg-[#C9A24D] text-[#0F241D] border-[#0F3D2E]/20'
                      : 'bg-[#123528] text-[#FFFDF8] border-[#1F6B52]'
              }`}
            >
              {t.text}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
