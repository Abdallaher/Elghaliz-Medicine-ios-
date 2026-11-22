# Elghaliz Medicine — نسخة Windows (Web/Electron)

نسخة سطح مكتب من تطبيق **Elghaliz Medicine** الأصلي (أندرويد/‏Kotlin+Compose) أُعيد بناؤها كتطبيق ويب **React + TypeScript** يعمل على Windows (مُهيأ للتغليف بـ Electron، وجاهز لاحقاً لـ iOS عبر Capacitor/PWA).

> **مهم:** التطبيق يتصل بنفس باك-إند Firebase الأصلي (مشروع `gen-lang-client-0295730039`). لم يتغيّر شيء في الخادم؛ أُعيد بناء الواجهة فقط.
>
> حالياً يعمل في **الوضع التجريبي (Demo)** — بيانات عيّنة محلية، بدون إنترنت. لتفعيل الاتصال الحقيقي بـ Firebase راجع `docs/LIVE_FIREBASE.md`.

---

## ✅ التشغيل الآن

الخادم المحلي يعمل حالياً على:

**http://127.0.0.1:8787**

افتح الرابط في المتصفح. أو شغّل:

```
run-windows.bat
```
(يشغّل الخادم ويفتح التطبيق في نافذة Edge مستقلة.)

للدخول التجريبي السريع أضف: `?as=student` أو `?as=admin` إلى الرابط.

---

## 🛠️ البناء والتطوير

المتطلّبات: Node.js 18+.

```bash
# تثبيت الاعتماديات
pnpm install      # أو npm install

# تطوير مباشر (Hot reload) على http://localhost:5175
pnpm dev

# بناء إنتاجي → مجلد dist/
pnpm build

# تشغيل مُجمَّع مسبقاً
pnpm serve        # http://127.0.0.1:8787
```

---

## 🖥️ التغليف كتطبيق Windows مستقل (Electron)

هذه الخطوة تتطلّب تنزيل Electron (≈150MB) مرة واحدة:

```bash
pnpm add -D electron electron-builder
pnpm dist            # مجلد قابل للتشغيل المباشر في ./release/win-unpacked
pnpm dist:installer  # مثبّت NSIS في ./release/
```

ثم شغّل `release/win-unpacked/Elghaliz Medicine.exe`. ملفات Electron جاهزة في `electron/main.cjs`.

---

## 📁 البنية

```
src/
  config.ts              إعدادات Firebase + الوضع التجريبي
  firebase.ts            نقطة تهيئة Firebase (معطّلة في Demo)
  data/
    types.ts             نماذج البيانات (من MedicalModels.kt)
    defaults.ts          التصنيفات الافتراضية + دوال التجزئة
    seed.ts              بيانات العيّنة للوضع التجريبي
    dataStore.ts         مخزن البيانات التفاعلي (محاضرات/طلبات/تنزيلات…)
  store/appStore.ts      حالة الجلسة/التبويب/الوضع الليلي/الإشعارات
  lib/
    session.ts           تسجيل الدخول/الأدوار/الصلاحيات
    format.ts            تنسيق الوقت/التاريخ بالأرقام العربية
  components/            TopBar, BottomNav, Modal, VideoPlayer, …
  screens/               AuthScreen, Library, Exams, Downloads/Admin, Settings, PendingApproval
  styles/                tokens.css, global.css, screens.css (هوية Elghaliz)
electron/main.cjs        غلاف Electron لسطح المكتب
server.mjs               خادم ملفات محلي بسيط
```

---

## 🎨 الهوية البصرية

مطابقة للأصل: أخضر زمردي `#0F3D2E`، ذهبي `#C9A24D`، خلفية كريمية `#F7F3EA`، خطوط Cairo (العناوين) و Tajawal (النصوص) — مُضمّنة محلياً وتعمل دون إنترنت. واجهة عربية RTL بالكامل.

---

## 📱 لاحقاً: iPhone / iOS

الكود جاهز ليُغلَّف بـ **Capacitor** لتحويله لتطبيق iOS من نفس قاعدة الكود (يتطلّب جهاز Mac + Xcode + حساب Apple Developer للبناء النهائي). أو يمكن استخدامه كـ **PWA** مباشرة من Safari (Add to Home Screen).

---

## 🔐 تشفير DRM (منقول ومُختبَر)

نُقلت خوارزمية `EncryptedVideoVault` من الأندرويد إلى `src/lib/drm.ts` (قفل أول 64 بايت من ترويسة MP4 بـ XOR مع جدول مفاتيح مشتق من SHA-256 — مطابق للأصل). للتحقق:

```bash
pnpm test:drm        # أو: node scripts/verify-drm.mjs
```

النتيجة: جميع الاختبارات ناجحة (round-trip، قفل الترويسة فقط، حتمية جدول المفاتيح).

> هذه الوحدة جاهزة للربط بخادم البث المحلي داخل عملية Electron الرئيسية عند تفعيل الفيديو المشفّر الحقيقي.

## 🔄 التحديثات (قابل للتحديث)

- يعرض التطبيق **إصداره الحالي** (1.0.0) في *الإعدادات → التحديثات* مع زر **«التحقق من التحديثات»**.
- لتفعيل التحقق الفعلي: اضبط `UPDATE_FEED_URL` في `src/config.ts` إلى رابط يُرجع JSON:
  ```json
  { "version": "1.0.1", "url": "https://your-host/ElghalizMedicine-1.0.1.exe", "notes": "..." }
  ```
- **تحديث تلقائي كامل (اختياري)**: في تطبيق Electron المُثبَّت، استخدم `electron-updater` مع بناء **NSIS installer** ونشر ملفات التحديث (`latest.yml` + الـ exe) على GitHub Releases أو استضافة عامة. (البناء الحالي `--dir` قابل للتشغيل المباشر؛ لإصدار مثبّت قابلوالتحديث التلقائي استخدم `pnpm dist:installer`.)
- **لإعادة التغليف بعد أي تعديل**: شغّل `package-windows.bat` (يبني الويب + يغلّف إلى exe بلا إنترنت).

لمخزن Google Drive 5TB وتفاصيل الباك-إند الكاملة راجع `docs/LIVE_FIREBASE.md` و`elghaliz-medicine/BACKEND_DATA_SPEC.md`.
