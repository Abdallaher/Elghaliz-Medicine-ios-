# تفعيل الاتصال الحقيقي بـ Firebase (من الوضع التجريبي إلى المباشر)

التطبيق حالياً في **وضع تجريبي** (بيانات محلية بدون إنترنت). للاتصال بـ Firebase الحقيقي (نفس بيانات ومستخدمي تطبيق الأندرويد):

## 1) أنشئ تطبيق ويب في Firebase Console

1. افتح [Firebase Console](https://console.firebase.google.com) → مشروع **gen-lang-client-0295730039**.
2. إعدادات المشروع (⚙️) → **General** → تحت "Your apps" اضغط أيقونة الويب **`</>`** لإضافة تطبيق ويب.
3. ستحصل على `firebaseConfig` يحتوي **`appId`** (مثل `1:933895225355:web:xxxxxxxx`). انسخ `appId` (و`authDomain` إن اختلف).

## 2) الصق القيم في `src/config.ts`

```ts
export const FIREBASE_CONFIG = {
  apiKey: 'AIzaSyDBbJvLAM-v4dwizcsHslVBUeC-M_PZ8QI',   // الموجود
  authDomain: 'gen-lang-client-0295730039.firebaseapp.com',
  projectId: 'gen-lang-client-0295730039',
  storageBucket: 'gen-lang-client-0295730039.firebasestorage.app',
  messagingSenderId: '933895225355',
  appId: '<الصق appId للويب هنا>',   // ← المطلوب
};
```

عندما يصبح `appId` غير فارغ، يتحوّل `DEMO_MODE` تلقائياً إلى `false`.

## 3) فعّل تسجيل الدخول عبر Google

1. **Authentication → Sign-in method** → فعّل **Google**.
2. **Authentication → Settings → Authorized domains**: أضف `localhost` (و`127.0.0.1` إن لزم) وأي نطاق إنتاج.
3. في [Google Cloud Console](https://console.cloud.google.com) → APIs & Services → Credentials → افتح **OAuth 2.0 Client ID (Web)** الخاص بالمشروع وأضف **Authorized JavaScript origins**: `http://localhost:5175` و`http://127.0.0.1:8787`.
4. تأكّد من تمكين واجهات: **Identity Toolkit API**, **Cloud Firestore API**, **Token Service API**.
5. مفتاح API الحالي قد يكون مقيّداً بحِزم أندرويد؛ لمشروع الويب أنشئ **مفتاح متصفح** بقيود HTTP referrer أو أضف نطاقك ضمن قيود المفتاح.

## 4) قاعدة Firestore المسمّاة (مهم)

التطبيق يستخدم قاعدة بيانات Firestore **مسمّاة** وليست الافتراضية:

```
ai-studio-android-elghaliz-72f133e1-54f8-4875-8f82-71b60492c2b8
```

وهي محدّدة مسبقاً في `src/config.ts` (`FIRESTORE_DATABASE_ID`). انشر قواعد الأمان من ملف المشروع الأصلي:

```bash
firebase deploy --only firestore
```

## 5) استعادة تهيئة Firebase في الكود

في `src/firebase.ts` استبدل الكود المعطّل بالتهيئة الحقيقية:

```ts
import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider } from 'firebase/auth';
import { initializeFirestore } from 'firebase/firestore';
import { FIREBASE_CONFIG, FIRESTORE_DATABASE_ID, DEMO_MODE } from './config';

let _auth = null, _db = null;
if (!DEMO_MODE) {
  const app = initializeApp(FIREBASE_CONFIG);
  _auth = getAuth(app);
  _db = initializeFirestore(app, { experimentalAutoDetectLongPolling: true }, FIRESTORE_DATABASE_ID);
}
export const fbAuth = _auth;
export const fbDb = _db;
export const googleProvider = new GoogleAuthProvider();
```

ثم ثبّت الحزمة: `pnpm add firebase`.

## 6) ربط الشاشات بالبيانات الحقيقية

طبقة البيانات الحالية في `src/data/dataStore.ts` تعمل محلياً (Demo). لربطها بـ Firebase:
- استبدل أفعال المخزن باستعلامات Firestore (نفس أسماء المجموعات والحقول في `elghaliz-medicine/BACKEND_DATA_SPEC.md`).
- استبدل `signIn()` في `src/lib/session.ts` بـ `signInWithPopup(fbAuth, googleProvider)` ثم قراءة `admin_members/{uid}` و`student_requests/{uid}` لتحديد الدور والموافقة (تماماً كما في تطبيق الأندرويد).

المواصفات الكاملة لبنية Firestore (المجموعات/الحقول/صيغ المعرّفات/قواعد القبول) موجودة في:
`elghaliz-medicine/BACKEND_DATA_SPEC.md`

## 7) ميزات متقدمة (مرحلة لاحقة)

- **تشفير/DRM للفيديو** وخادم البث المحلي: يُنقل منطق `EncryptedVideoVault` إلى عملية Electron الرئيسية (Node) — تفاصيله في مواصفات الباك-إند §4.
- **مخزن Google Drive 5TB**: نقاط النهاية وApps Script موثّقة في §3 من نفس الملف.
- **الاجتماعات المباشرة**: كاميرا عبر `getUserMedia` + دردشة Firestore.
