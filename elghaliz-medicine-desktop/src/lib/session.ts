import { useAppStore } from '../store/appStore';
import { useDataStore } from '../data/dataStore';
import { DEMO_ADMIN, DEMO_STUDENT } from '../data/seed';
import { DEMO_MODE } from '../config';

type LiveMod = typeof import('./liveAuth');
let liveMod: LiveMod | null = null;
export function setLiveModule(m: LiveMod): void {
  liveMod = m;
}

// Demo-mode session helpers (LIVE Firebase auth will plug in here later).

export function signIn(role: 'STUDENT' | 'ADMIN'): void {
  if (!DEMO_MODE) {
    if (liveMod) {
      // Call synchronously so signInWithPopup runs inside the click gesture (popup allowed).
      void liveMod.signInGoogle();
    } else {
      useAppStore.getState().pushToast('جارٍ التحميل… اضغط «تسجيل الدخول» مرة أخرى', 'info');
      void import('./liveAuth').then((m) => {
        setLiveModule(m);
        m.startLiveAuth();
      });
    }
    return;
  }
  const setSession = useAppStore.getState().setSession;
  if (role === 'ADMIN') {
    setSession({
      user: { uid: DEMO_ADMIN.uid, email: DEMO_ADMIN.email, displayName: DEMO_ADMIN.displayName },
      role: 'ADMIN',
      approval: 'approved',
      isCheckingRole: false,
      isVerifiedAdmin: true,
      isPrimaryAdmin: true,
    });
  } else {
    const approval = useDataStore.getState().data.studentApproval[DEMO_STUDENT.uid] ?? 'pending';
    setSession({
      user: { uid: DEMO_STUDENT.uid, email: DEMO_STUDENT.email, displayName: DEMO_STUDENT.displayName },
      role: 'STUDENT',
      approval,
      isCheckingRole: false,
      isVerifiedAdmin: false,
      isPrimaryAdmin: false,
    });
  }
}

export function signOut(): void {
  if (!DEMO_MODE && liveMod) {
    void liveMod.signOutLive();
  }
  useAppStore.getState().setSession(null);
  useAppStore.getState().setTab('LIBRARY');
}

export function switchToStudentView(): void {
  const st = useAppStore.getState();
  if (!st.session) return;
  st.patchSession({ role: 'STUDENT' });
  st.setBanner('تم التبديل إلى واجهة الطالب للتصفح والمشاهدة');
}

export function switchToAdminView(): void {
  const st = useAppStore.getState();
  if (!st.session || !st.session.isVerifiedAdmin) return;
  st.patchSession({ role: 'ADMIN' });
}

export function useAccess() {
  const session = useAppStore((s) => s.session);
  const isVerifiedAdmin = session?.isVerifiedAdmin ?? false;
  const isAdminView = !!session && session.role === 'ADMIN' && isVerifiedAdmin;
  const isApprovedStudent = !!session && session.role === 'STUDENT' && session.approval === 'approved';
  const adminBypass = isVerifiedAdmin;
  const canAccessContent = adminBypass || isApprovedStudent;
  return { session, isAdminView, isApprovedStudent, adminBypass, canAccessContent };
}
