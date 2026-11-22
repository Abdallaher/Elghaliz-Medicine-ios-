// Real Firebase initialization — active only when an appId is configured (LIVE mode).
// In DEMO mode (no appId) nothing is initialized and the app stays fully offline.
import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider } from 'firebase/auth';
import { initializeFirestore } from 'firebase/firestore';
import { DEMO_MODE, FIREBASE_CONFIG, FIRESTORE_DATABASE_ID } from './config';

let _auth: import('firebase/auth').Auth | null = null;
let _db: import('firebase/firestore').Firestore | null = null;
let _provider: GoogleAuthProvider | null = null;

if (!DEMO_MODE) {
  try {
    const app = initializeApp(FIREBASE_CONFIG);
    _auth = getAuth(app);
    // The Android app uses a named (non-default) Firestore database.
    _db = initializeFirestore(
      app,
      { experimentalAutoDetectLongPolling: true },
      FIRESTORE_DATABASE_ID,
    );
    _provider = new GoogleAuthProvider();
    _provider.setCustomParameters({ prompt: 'select_account' });
    // Allow uploading lecture videos to the user's Google Drive.
    _provider.addScope('https://www.googleapis.com/auth/drive.file');
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error('[firebase] init failed', err);
  }
}

export const fbAuth = _auth;
export const fbDb = _db;
export const googleProvider = _provider;
