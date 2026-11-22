// Live Firebase auth bootstrap (only used when an appId is configured).
import { onAuthStateChanged, signInWithPopup, signInWithRedirect, signOut as fbSignOut, type User } from 'firebase/auth';
import { doc, getDoc, setDoc, serverTimestamp } from 'firebase/firestore';
import { fbAuth, fbDb, googleProvider } from '../firebase';
import { useAppStore } from '../store/appStore';
import { loadLiveData } from './liveData';
import { FORCED_ADMIN_EMAIL } from '../config';
import type { ApprovalStatus } from '../data/types';

function reportLog(payload: Record<string, unknown>): void {
  try {
    void fetch('/__log', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: JSON.stringify(payload),
    });
  } catch {
    /* ignore (e.g. file:// or non-http origin) */
  }
}

async function resolveSession(user: User): Promise<void> {
  const email = user.email ?? '';
  const uid = user.uid;

  let isVerifiedAdmin = email.toLowerCase() === FORCED_ADMIN_EMAIL;
  if (!isVerifiedAdmin && fbDb) {
    try {
      const m = await getDoc(doc(fbDb, 'admin_members', uid));
      isVerifiedAdmin = m.exists();
    } catch {
      /* ignore */
    }
  }

  let approval: ApprovalStatus = 'pending';
  if (fbDb) {
    try {
      const s = await getDoc(doc(fbDb, 'student_requests', uid));
      if (s.exists()) {
        approval = (s.data().status as ApprovalStatus) ?? 'pending';
      } else {
        await setDoc(doc(fbDb, 'student_requests', uid), {
          userId: uid,
          email: email.toLowerCase(),
          displayName: user.displayName ?? email.split('@')[0] ?? 'طالب طب',
          status: 'pending',
          scopeKey: 'global',
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
        approval = 'pending';
      }
    } catch {
      /* ignore */
    }
  }

  reportLog({ event: 'session_resolved', uid, isVerifiedAdmin, approval });

  useAppStore.getState().setSession({
    user: { uid, email, displayName: user.displayName ?? email.split('@')[0] ?? 'طالب' },
    role: isVerifiedAdmin ? 'ADMIN' : 'STUDENT',
    approval,
    isCheckingRole: false,
    isVerifiedAdmin,
    isPrimaryAdmin: email.toLowerCase() === FORCED_ADMIN_EMAIL,
  });

  try {
    await loadLiveData(uid);
  } catch {
    /* ignore */
  }
}

let started = false;
export function startLiveAuth(): void {
  if (started || !fbAuth) return;
  started = true;
  reportLog({ event: 'start_live_auth', origin: location.origin });
  onAuthStateChanged(fbAuth, (u) => {
    if (u) void resolveSession(u);
    else useAppStore.getState().setSession(null);
  });
}

export async function signInGoogle(): Promise<void> {
  const store = useAppStore.getState();
  reportLog({ event: 'signin_start', origin: location.origin, hasAuth: !!fbAuth, hasProvider: !!googleProvider });
  if (!fbAuth || !googleProvider) {
    store.pushToast('Firebase غير مهيّأ — أضف appId في src/config.ts', 'error');
    return;
  }
  try {
    const cred = await signInWithPopup(fbAuth, googleProvider);
    const oauthToken = (cred.credential as { accessToken?: string } | null)?.accessToken;
    if (oauthToken) {
      const { setDriveToken } = await import('./drive');
      setDriveToken(oauthToken);
    }
    reportLog({ event: 'signin_popup_resolved', hasDriveToken: !!oauthToken, origin: location.origin });
  } catch (e) {
    const err = e as { code?: string; message?: string };
    const code = err?.code ?? '';
    reportLog({ event: 'signin_error', code, message: err?.message, origin: location.origin });
    if (
      code === 'auth/popup-blocked' ||
      code === 'auth/popup-closed-by-user' ||
      code === 'auth/cancelled-popup-request' ||
      code === 'auth/operation-not-supported-in-this-environment'
    ) {
      try {
        await signInWithRedirect(fbAuth, googleProvider);
        return;
      } catch {
        /* fall through */
      }
    }
    store.pushToast('تعذّر الدخول (' + (code || err?.message || 'خطأ') + ')', 'error');
  }
}

export async function signOutLive(): Promise<void> {
  if (fbAuth) {
    try {
      await fbSignOut(fbAuth);
    } catch {
      /* ignore */
    }
  }
}
