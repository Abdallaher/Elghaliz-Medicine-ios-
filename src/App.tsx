import React, { useEffect, useState } from 'react';
import { Activity, Loader2 } from 'lucide-react';
import { useAppStore } from './store/appStore';
import { AppShell } from './components/AppShell';
import { AuthScreen } from './screens/AuthScreen';
import { PendingApprovalScreen } from './screens/PendingApprovalScreen';
import { DeviceLockedScreen } from './screens/DeviceLockedScreen';
import { LecturesLibraryScreen } from './screens/LecturesLibraryScreen';
import { CoursePlayerScreen } from './screens/CoursePlayerScreen';
import { ExamsScreen } from './screens/ExamsScreen';
import { DownloadsAndAdminScreen } from './screens/DownloadsAndAdminScreen';
import { SettingsScreen } from './screens/SettingsScreen';

function shouldSkipSplash(): boolean {
  if (typeof window === 'undefined') return true;
  const params = new URLSearchParams(window.location.search);
  return params.has('demo') || params.get('autostart') === '1';
}

export const App: React.FC = () => {
  const {
    initialized,
    user,
    isAdminUser,
    approvalStatus,
    isDeviceLocked,
    activeTab,
    openedCourseId,
    bootstrapApp,
  } = useAppStore();

  const [showSplash, setShowSplash] = useState<boolean>(() => !shouldSkipSplash());

  useEffect(() => {
    void bootstrapApp();
  }, [bootstrapApp]);

  useEffect(() => {
    if (!showSplash) return;
    const t = window.setTimeout(() => setShowSplash(false), 900);
    return () => window.clearTimeout(t);
  }, [showSplash]);

  if (showSplash || !initialized) {
    return (
      <div className="min-h-dvh flex flex-col items-center justify-center bg-[#0F3D2E] text-white px-6 text-center">
        <div className="flex h-24 w-24 items-center justify-center rounded-[30px] bg-[#1F6B52] border-2 border-[#C9A24D] shadow-2xl mb-5 animate-pulse">
          <Activity className="h-12 w-12 text-[#C9A24D]" />
        </div>
        <h1 className="text-2xl font-black tracking-tight text-[#FFFDF8]">Elghaliz Medicine</h1>
        <p className="mt-1.5 text-sm font-bold text-[#C9A24D]">
          أهلاً وسهلاً بك في التطبيق الغليظ ~
        </p>
        <div className="mt-6 flex items-center gap-2 text-xs text-[#E9F0E8]/75">
          <Loader2 className="h-4 w-4 animate-spin text-[#C9A24D]" />
          <span>جاري تجهيز المنصة الطبية...</span>
        </div>
      </div>
    );
  }

  // 1. Unauthenticated -> Auth Screen
  if (!user) {
    return <AuthScreen />;
  }

  // 2. Strict Permanent 1-Device Lock Violation -> Device Locked Screen
  if (isDeviceLocked && !isAdminUser) {
    return <DeviceLockedScreen />;
  }

  // 3. Loading Approval State
  if (approvalStatus === 'loading' && !isAdminUser) {
    return (
      <div className="min-h-dvh flex flex-col items-center justify-center bg-[#F7F3EA] dark:bg-[#0A2A20] text-[#0F3D2E] dark:text-[#F4EFE4] gap-3">
        <Loader2 className="h-8 w-8 animate-spin text-[#C9A24D]" />
        <p className="text-sm font-extrabold">جاري التحقق من حالة الحساب وقفل الجهاز...</p>
      </div>
    );
  }

  // 4. Pending / Suspended Student -> Pending Approval Screen
  if (!isAdminUser && approvalStatus !== 'approved') {
    return <PendingApprovalScreen />;
  }

  // 5. Unified Course & Video Player Screen (when a medical course is opened)
  if (openedCourseId) {
    return <CoursePlayerScreen />;
  }

  // 6. Main 4-Tab Application Shell
  return (
    <AppShell>
      {activeTab === 'LIBRARY' && <LecturesLibraryScreen />}
      {activeTab === 'EXAMS' && <ExamsScreen />}
      {activeTab === 'DOWNLOADS_ADMINS' && <DownloadsAndAdminScreen />}
      {activeTab === 'SETTINGS' && <SettingsScreen />}
    </AppShell>
  );
};

export default App;
