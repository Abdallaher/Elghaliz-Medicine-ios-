import { create } from 'zustand';
import type { ApprovalStatus, AuthUser, UserRoleMode } from '../data/types';

export type AppTab = 'LIBRARY' | 'EXAMS' | 'DOWNLOADS_ADMINS' | 'SETTINGS';

export type ToastTone = 'success' | 'error' | 'info';
export interface Toast {
  id: number;
  text: string;
  tone: ToastTone;
}

export interface Session {
  user: AuthUser | null;
  role: UserRoleMode;
  approval: ApprovalStatus;
  isCheckingRole: boolean;
  isVerifiedAdmin: boolean;
  isPrimaryAdmin: boolean;
}

interface AppState {
  theme: 'light' | 'dark';
  tab: AppTab;
  banner: string | null;
  toasts: Toast[];
  session: Session | null;

  toggleTheme(): void;
  setTab(t: AppTab): void;
  setBanner(b: string | null): void;
  pushToast(text: string, tone?: ToastTone): void;
  dismissToast(id: number): void;
  setSession(s: Session | null): void;
  patchSession(p: Partial<Session>): void;
}

let toastSeq = 1;

const persistedTheme = (localStorage.getItem('elghaliz.theme') as 'light' | 'dark') || 'light';

export const useAppStore = create<AppState>((set, get) => ({
  theme: persistedTheme,
  tab: 'LIBRARY',
  banner: null,
  toasts: [],
  session: null,

  toggleTheme() {
    const next = get().theme === 'light' ? 'dark' : 'light';
    localStorage.setItem('elghaliz.theme', next);
    document.documentElement.setAttribute('data-theme', next);
    set({ theme: next });
  },

  setTab(t) {
    set({ tab: t, banner: null });
  },

  setBanner(b) {
    set({ banner: b });
  },

  pushToast(text, tone = 'info') {
    const id = toastSeq++;
    set((s) => ({ toasts: [...s.toasts, { id, text, tone }] }));
    window.setTimeout(() => {
      get().dismissToast(id);
    }, 4200);
  },

  dismissToast(id) {
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
  },

  setSession(s) {
    set({ session: s });
  },

  patchSession(p) {
    const cur = get().session;
    if (cur) set({ session: { ...cur, ...p } });
  },
}));

// Initialize theme attribute on load.
document.documentElement.setAttribute('data-theme', persistedTheme);
