import type { TimestampedLectureNote, UserRoleMode } from '../types/models';

const KEY_DARK_MODE = 'elg_dark_mode';
const KEY_FONT_SCALE = 'elg_font_scale';
const KEY_LANGUAGE = 'elg_language';
const KEY_WIFI_ONLY = 'elg_wifi_only_download';
const KEY_AUTO_DELETE_WATCHED = 'elg_auto_delete_watched';
const KEY_PREFERRED_ROLE = 'elg_preferred_role';
const KEY_POSITIONS = 'elg_playback_positions';
const KEY_COMPLETED = 'elg_completed_lectures';
const KEY_NOTES = 'elg_timestamped_notes';
const KEY_IOS_BANNER_DISMISSED = 'elg_ios_banner_dismissed';

export interface AppPreferences {
  isDarkMode: boolean;
  fontScale: number; // 1.0 | 1.1 | 1.2
  language: 'ar' | 'en';
  wifiOnlyDownload: boolean;
  autoDeleteWatched: boolean;
  preferredRole: UserRoleMode;
}

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function writeJson(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* ignore quota errors */
  }
}

export function loadPreferences(): AppPreferences {
  try {
    const isDarkMode = localStorage.getItem(KEY_DARK_MODE) === 'true';
    const rawScale = Number(localStorage.getItem(KEY_FONT_SCALE) || '1');
    const fontScale = [1, 1.1, 1.2].includes(rawScale) ? rawScale : 1;
    const rawLang = localStorage.getItem(KEY_LANGUAGE);
    const language: 'ar' | 'en' = rawLang === 'en' ? 'en' : 'ar';
    const wifiOnlyDownload = localStorage.getItem(KEY_WIFI_ONLY) !== 'false';
    const autoDeleteWatched = localStorage.getItem(KEY_AUTO_DELETE_WATCHED) === 'true';
    const rawRole = localStorage.getItem(KEY_PREFERRED_ROLE);
    const preferredRole: UserRoleMode = rawRole === 'ADMIN' ? 'ADMIN' : 'STUDENT';
    return {
      isDarkMode,
      fontScale,
      language,
      wifiOnlyDownload,
      autoDeleteWatched,
      preferredRole,
    };
  } catch {
    return {
      isDarkMode: false,
      fontScale: 1,
      language: 'ar',
      wifiOnlyDownload: true,
      autoDeleteWatched: false,
      preferredRole: 'STUDENT',
    };
  }
}

export function savePreference<K extends keyof AppPreferences>(
  key: K,
  value: AppPreferences[K],
): void {
  try {
    switch (key) {
      case 'isDarkMode':
        localStorage.setItem(KEY_DARK_MODE, String(value));
        break;
      case 'fontScale':
        localStorage.setItem(KEY_FONT_SCALE, String(value));
        break;
      case 'language':
        localStorage.setItem(KEY_LANGUAGE, String(value));
        break;
      case 'wifiOnlyDownload':
        localStorage.setItem(KEY_WIFI_ONLY, String(value));
        break;
      case 'autoDeleteWatched':
        localStorage.setItem(KEY_AUTO_DELETE_WATCHED, String(value));
        break;
      case 'preferredRole':
        localStorage.setItem(KEY_PREFERRED_ROLE, String(value));
        break;
    }
  } catch {
    /* ignore */
  }
}

export function loadPlaybackPositions(): Record<string, number> {
  return readJson<Record<string, number>>(KEY_POSITIONS, {});
}

export function savePlaybackPosition(lectureId: string, seconds: number): Record<string, number> {
  const current = loadPlaybackPositions();
  current[lectureId] = Math.max(0, Math.floor(seconds));
  writeJson(KEY_POSITIONS, current);
  return current;
}

export function loadCompletedLectureIds(): string[] {
  return readJson<string[]>(KEY_COMPLETED, []);
}

export function markLectureCompletedLocal(lectureId: string): string[] {
  const list = loadCompletedLectureIds();
  if (!list.includes(lectureId)) {
    list.push(lectureId);
    writeJson(KEY_COMPLETED, list);
  }
  return list;
}

export function loadTimestampedNotes(): TimestampedLectureNote[] {
  return readJson<TimestampedLectureNote[]>(KEY_NOTES, []);
}

export function saveTimestampedNotes(notes: TimestampedLectureNote[]): void {
  writeJson(KEY_NOTES, notes);
}

export function isIosBannerDismissed(): boolean {
  try {
    return localStorage.getItem(KEY_IOS_BANNER_DISMISSED) === 'true';
  } catch {
    return false;
  }
}

export function dismissIosBanner(): void {
  try {
    localStorage.setItem(KEY_IOS_BANNER_DISMISSED, 'true');
  } catch {
    /* ignore */
  }
}
