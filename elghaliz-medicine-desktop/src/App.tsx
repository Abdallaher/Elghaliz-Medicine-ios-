import { useEffect, useRef } from 'react';
import { useAppStore } from './store/appStore';
import { useAccess, signIn, setLiveModule } from './lib/session';
import { AuthScreen } from './screens/AuthScreen';
import { PendingApproval } from './screens/PendingApproval';
import { LibraryScreen } from './screens/LibraryScreen';
import { ExamsScreen } from './screens/ExamsScreen';
import { DownloadsAdminScreen } from './screens/DownloadsAdminScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { Banner, BottomNav, TopBar } from './components/shell';
import { Toasts } from './components/ui';
import { DEMO_MODE } from './config';

export default function App() {
  const session = useAppStore((s) => s.session);
  const tab = useAppStore((s) => s.tab);
  const setTab = useAppStore((s) => s.setTab);
  const { isApprovedStudent, adminBypass } = useAccess();
  const didAuto = useRef(false);

  // Demo convenience: ?as=student|admin to auto sign in, ?tab=LIBRARY|EXAMS|MEETINGS|DOWNLOADS_ADMINS|SETTINGS
  useEffect(() => {
    if (didAuto.current) return;
    didAuto.current = true;
    if (!DEMO_MODE) {
      void import('./lib/liveAuth').then((m) => {
        setLiveModule(m);
        m.startLiveAuth();
        if (new URLSearchParams(window.location.search).get('login') === '1') {
          void m.signInGoogle();
        }
      });
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const tabParam = params.get('tab');
    if (tabParam && ['LIBRARY', 'EXAMS', 'DOWNLOADS_ADMINS', 'SETTINGS'].includes(tabParam)) {
      setTab(tabParam as 'LIBRARY' | 'EXAMS' | 'DOWNLOADS_ADMINS' | 'SETTINGS');
    }
    if (session) return;
    const as = params.get('as');
    if (as === 'admin') signIn('ADMIN');
    else if (as === 'student') signIn('STUDENT');
  }, [session, setTab]);

  if (!session) {
    return (
      <>
        <AuthScreen />
        <Toasts />
      </>
    );
  }

  // Pending / suspended student gate (admin bypasses).
  if (!adminBypass && !isApprovedStudent) {
    return (
      <>
        <PendingApproval />
        <Toasts />
      </>
    );
  }

  return (
    <div className="workspace">
      <TopBar />
      <Banner />
      <div className="main-area">
        {tab === 'LIBRARY' && <LibraryScreen />}
        {tab === 'EXAMS' && <ExamsScreen />}
        {tab === 'DOWNLOADS_ADMINS' && <DownloadsAdminScreen />}
        {tab === 'SETTINGS' && <SettingsScreen />}
      </div>
      <BottomNav />
      <Toasts />
    </div>
  );
}
