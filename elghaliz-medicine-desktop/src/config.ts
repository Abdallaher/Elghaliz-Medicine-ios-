// Firebase configuration for the Elghaliz Medicine web/desktop client.
//
// The values below are derived from the project's app/google-services.json.
// To enable LIVE mode you must:
//   1. In Firebase Console (project gen-lang-client-0295730039) add a *Web* app.
//   2. Copy its `appId` into FIREBASE_CONFIG.appId below.
//   3. Enable the Google sign-in provider (Authentication > Sign-in method).
//   4. Add your web origin (e.g. http://localhost:5175) under Authentication > Settings > Authorized domains,
//      and add the same origin to the Google OAuth client's authorized JavaScript origins.
//
// Until an appId is provided, the app runs in DEMO MODE with local sample data.

export const FIREBASE_CONFIG = {
  apiKey: 'AIzaSyDBbJvLAM-v4dwizcsHslVBUeC-M_PZ8QI',
  authDomain: 'gen-lang-client-0295730039.firebaseapp.com',
  projectId: 'gen-lang-client-0295730039',
  storageBucket: 'gen-lang-client-0295730039.firebasestorage.app',
  messagingSenderId: '933895225355',
  appId: '1:933895225355:android:9e8a1f25102b4427af470e',
};

// Named (non-default) Firestore database used by the original Android app.
export const FIRESTORE_DATABASE_ID =
  'ai-studio-android-elghaliz-72f133e1-54f8-4875-8f82-71b60492c2b8';

// Email that is force-promoted to ADMIN, mirroring the app + firestore rules.
export const FORCED_ADMIN_EMAIL = 'abdallahxh909@gmail.com';

// Demo mode runs entirely offline with seeded sample data (no Firebase needed).
export const DEMO_MODE = !FIREBASE_CONFIG.appId;

// ---- App version & update check ----
export const APP_VERSION = '1.0.0';

// Set UPDATE_FEED_URL to a URL that returns JSON like:
//   { "version": "1.0.1", "url": "https://.../ElghalizMedicine-1.0.1.exe", "notes": "..." }
// When set, the Settings screen can check for and point to a newer version.
// For true silent auto-update in the packaged Electron app, use electron-updater with a
// published NSIS build (see README "ط§ظ„طھط­ط¯ظٹط«ط§طھ").
export const UPDATE_FEED_URL = '';

