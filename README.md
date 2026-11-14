# صديقي | SADEEQI AI 1.5.1 — مصدر تطوير

هذه نسخة مصدر مطوّرة، وليست APK مبنياً أو إصداراً تم اعتماده على جهاز فعلي.

## الإضافات
- ذاكرة Room دائمة، محتواها مشفر AES-256-GCM بمفتاح Android Keystore.
- واجهة حفظ محادثات واسترجاعها، مع API للبحث والحذف والتصدير والاستيراد المشفر.
- طبقة PhoneControl لواجهات Android الرسمية (فتح تطبيقات/إعدادات، الصوت، الكاميرا، المنبه، SAF، الاتصال، SMS، جهات الاتصال بإذن).
- عميل تحديث GitHub Releases-compatible يدعم قراءة الإصدار والملاحظات والتنزيل وفحص شهادة APK وفتح مثبت النظام.

## قيود مهمة
- لا يوجد Wake Word حقيقي «يا صديقي» أو استماع دائم؛ الميكروفون يعمل فقط بالاستدعاء اليدوي داخل الواجهة.
- PhoneControl ليس موصولاً بعد بمحلل أوامر المحادثة، ولا توجد لوحة إدارة أذونات مكتملة.
- نظام التحديث يحتاج endpoint رسمي للمشروع وبصمة شهادة توقيع ثابتة.
- لم يُبنَ APK ولم يتم اختبار المشروع على هاتف فعلي؛ راجع `TEST_REPORT_1.1.0.md`.

## متطلبات البناء
- JDK 17
- Android SDK 35
- Gradle 8.11 أو أحدث (النسخة المحددة في Android Gradle Plugin 8.10.1 تتطلب Gradle متوافقاً؛ يفضل Gradle 8.11.1)
- افتح المجلد في Android Studio، نفّذ Gradle Sync ثم `assembleDebug`.

لا ترفع أي تحديث للمستخدمين إلا بعد توقيع جميع الإصدارات بمفتاح التوقيع نفسه. لا تشارك ملف keystore أو كلمة مروره.

## Encrypted memory hardening (source update)
The memory store now encrypts logical IDs, categories, timestamps, and values inside AES-GCM payloads; Room rows retain only opaque IDs and ciphertext metadata. Portable backups use PBKDF2-HMAC-SHA256 and AES-256-GCM with a user password. See `reports/01_ENCRYPTED_MEMORY.md` and the Android instrumentation test. These changes are source-level only until built and run on a physical Android device.

## OpenAI API integration (1.2.0)
- Android chat now calls `OpenAiGateway` over HTTPS; it no longer returns canned offline responses on the send path.
- Node backend in `backend/` proxies to the official OpenAI Responses API. Keep `OPENAI_API_KEY` on the server only.
- Server persona is configurable using `SADEEQI_SYSTEM_PROMPT`; model is configured using `OPENAI_MODEL`.
- Conversation context sends the last 24 local turns. Optional `web_search` is controlled by server configuration.
- Image picker sends selected image as a data URL to the Responses API. Android speech input and TTS remain system services.
- Setup guide: `docs/OPENAI_BACKEND_SETUP_AR.md`.

## SADEEQI MUSIC (source integration)
- Added AndroidX Media3 ExoPlayer and MediaSession service foundation.
- Local user-selected audio playback, queue, seek, repeat, shuffle, volume, persistent favorites/history and Arabic playback voice commands are implemented in source.
- A remote global catalogue or licensed streaming provider is **not connected** yet. Search commands clearly report this limitation rather than pretending to stream.
- See [docs/SADEEQI_MUSIC.md](docs/SADEEQI_MUSIC.md) for architecture, data estimates, legal integration requirements and device-validation checklist.

## Social Hub — developer onboarding
- Protected `GET /v1/social/providers` endpoint lists developer API readiness only; all user accounts remain disconnected until real OAuth is implemented.
- OAuth callbacks, authenticated user identity, encrypted token persistence, webhooks, inbox and publishing are not yet implemented.
- Follow [the Arabic official social platform registration guide](docs/SOCIAL_PLATFORM_REGISTRATION_AR.md) to create the official developer apps. Do not register callback URLs until the OAuth callback endpoints have been implemented and deployed.
- Never put provider client secrets or Telegram bot tokens in Android, GitHub source, or chat. Store them as server-side Vercel environment secrets only after the secure OAuth foundation is ready.
- The current shared app bearer token embedded in APK is extractable and must not be treated as a user identity.

## Ultimate Premium development audit — 2026-11-14
- Android version is 1.5.1 (versionCode 7), and Android/CI configuration targets `https://sadeeqi-ai-pdrk.vercel.app`.
- Full implementation and acceptance gaps: [reports/SADEEQI_ULTIMATE_AUDIT_2026-11-14.md](reports/SADEEQI_ULTIMATE_AUDIT_2026-11-14.md).

**Security note:** the current shared app bearer token is compiled into the Android build and can be extracted. The OpenAI API key stays on the server, but production authentication should move to per-user short-lived credentials before public release.
