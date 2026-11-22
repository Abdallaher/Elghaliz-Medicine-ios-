import { initializeApp } from 'firebase/app';
import { initializeFirestore, collection, getDocs } from 'firebase/firestore';
import { getAuth } from 'firebase/auth';

const cfg = {
  apiKey: 'AIzaSyDBbJvLAM-v4dwizcsHslVBUeC-M_PZ8QI',
  authDomain: 'gen-lang-client-0295730039.firebaseapp.com',
  projectId: 'gen-lang-client-0295730039',
  appId: '1:933895225355:android:9e8a1f25102b4427af470e',
  messagingSenderId: '933895225355',
  storageBucket: 'gen-lang-client-0295730039.firebasestorage.app',
};
const DB = 'ai-studio-android-elghaliz-72f133e1-54f8-4875-8f82-71b60492c2b8';

console.log('Initializing Firebase with project:', cfg.projectId, 'appId:', cfg.appId);
const app = initializeApp(cfg);
const db = initializeFirestore(app, { experimentalAutoDetectLongPolling: true }, DB);
const auth = getAuth(app);
console.log('auth currentUser:', auth.currentUser ? auth.currentUser.uid : 'null (not signed in)');

for (const col of ['categories', 'lectures', 'exams']) {
  try {
    const snap = await getDocs(collection(db, col));
    console.log(`READ ${col}: OK, docs=${snap.size}`);
  } catch (e) {
    console.log(`READ ${col}: ERROR code=${e.code || ''} msg=${(e.message || e).toString().slice(0, 160)}`);
  }
}
process.exit(0);
