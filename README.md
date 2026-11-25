# صديقي | SADEEQI AI 1.5.1 — مصدر تطوير

هذه نسخة مصدر مطوّرة، وليست APK مبنياً أو إصداراً تم اعتماده على جهاز فعلي.

## الإضافات
- ذاكرة Room دائمة، محتواها مشفر AES-256-GCM بمفتاح Android Keystore.
- واجهة حفظ محادثات واسترجاعها، مع API للبحث والحذف والتصدير والاستيراد المشفر.
- طبقة PhoneControl لأوامر Android المسموح بها: فتح WhatsApp وTelegram وYouTube، الكاميرا، الإعدادات، مستوى صوت الوسائط، وإعداد المنبه عبر تطبيق الساعة؛ تتطلب أوامر الهاتف تأكيداً من المستخدم.
- عميل تحديث GitHub Releases-compatible يدعم قراءة الإصدار والملاحظات والتنزيل وفحص شهادة APK وفتح مثبت النظام.

## قيود مهمة
- لا يوجد Wake Word حقيقي «يا صديقي» أو استماع دائم؛ الميكروفون يعمل فقط بالاستدعاء اليدوي داخل الواجهة.
- أوامر الهاتف المسموح بها موصولة بالمحادثة مع مربع تأكيد، وبجلسة الصوت مع تأكيد منطوق بنعم/لا. لا توجد أتمتة لمس عامة أو لوحة إدارة أذونات مكتملة.
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

## 1.2.0 limitations
This is source code only, not a built APK. The canonical Vercel backend health endpoint currently responds HTTP 200 and reports Groq configured; this confirms configuration health only, not a successful authenticated chat request. New Vercel deployments are currently blocked by the Hobby deployment-rate limit. Android build/device tests have not yet been verified. The app bootstrap bearer token is embedded at build time and is extractable; production must use short-lived user authentication. Image upload size is capped at 8 MiB in the app. Live audio/realtime and wake-word operation are not implemented here.


## SADEEQI MUSIC (source integration)
- Added AndroidX Media3 ExoPlayer and MediaSession service foundation.
- Local user-selected audio playback, queue, seek, repeat, shuffle, volume, persistent favorites/history and Arabic playback voice commands are implemented in source.
- A remote global catalogue or licensed streaming provider is **not connected** yet. Search commands clearly report this limitation rather than pretending to stream.
- See [docs/SADEEQI_MUSIC.md](docs/SADEEQI_MUSIC.md) for architecture, data estimates, legal integration requirements and device-validation checklist.
- CI now runs Android unit tests before building the debug APK. No APK/device success should be claimed until the relevant run succeeds and the artifact is verified.


## Ultimate Premium development audit — 2026-11-14
- Added a protected `GET /v1/social/providers` readiness endpoint. It lists provider API setup status but intentionally reports all accounts as disconnected; OAuth and account-management actions are not implemented.
- Social Hub network loading now runs on an IO dispatcher rather than the UI thread.
- Activated the Room-backed AES-GCM encrypted memory implementation in the Android app source and added a migration from the earlier encrypted SharedPreferences store.
- Moved the encrypted-memory instrumentation acceptance test into `app/src/androidTest`. It has not yet been run on an emulator or physical device.
- Android version is 1.5.1 (versionCode 7), and Android/CI configuration targets `https://sadeeqi-ai-pdrk.vercel.app`.
- Vercel deployment status currently reports a build-rate limit. No new deployment or APK artifact is claimed as verified.
- Full implementation and acceptance gaps: [reports/SADEEQI_ULTIMATE_AUDIT_2026-11-14.md](reports/SADEEQI_ULTIMATE_AUDIT_2026-11-14.md).

**Security note:** the current Android client uses per-user Supabase sessions; its session store is encrypted with AES-GCM using an Android Keystore key. The backend can still accept the legacy `SADEEQI_APP_TOKEN` only while `AUTH_MODE=legacy` or `AUTH_MODE=dual` migration is enabled. Do not distribute builds configured with the shared token; complete migration to `AUTH_MODE=supabase` before public release.

## Supabase user authentication (migration rollout)

The Android client no longer embeds the shared backend bearer token. It signs users in with Supabase Auth and stores the access/refresh session encrypted with an AES-GCM key held by Android Keystore. Chat and Social Hub requests send the user's short-lived access token; the backend validates its signature and claims against the Supabase JWKS endpoint. The Supabase anon/public key is a client identifier, not a service-role secret.

### Required configuration

1. Create a Supabase project on the free plan and enable Email/Password under Authentication providers.
2. Copy the Project URL and the anon/public key (never use the service-role key in Android).
3. Add GitHub Actions repository secrets: `SADEEQI_SUPABASE_URL` and `SADEEQI_SUPABASE_ANON_KEY`.
4. Add Vercel Production environment variables: `SUPABASE_URL` = the Project URL, and `AUTH_MODE` = `dual` during migration. Keep the existing `SADEEQI_APP_TOKEN` only during migration so already-installed older APKs continue to work.
5. Deploy the backend and build/install the new APK. Test sign-up, email confirmation (if enabled), sign-in, chat, Social Hub, and token refresh.
6. Only after the new APK is distributed and old clients are retired, change Vercel `AUTH_MODE` to `supabase` and delete the legacy `SADEEQI_APP_TOKEN` variable. This permanently rejects the shared-token scheme.

Do not commit Supabase secrets, the service-role key, Groq/OpenAI keys, or user passwords. The project URL and anon/public key may be embedded in the APK; backend provider credentials remain server-only. These branch changes are source code and are not a production deployment.

## Private Supabase Storage (source integration)
- Added `SupabaseStorageClient` for authenticated per-user private file upload, replacement, download, listing and deletion.
- Object paths are generated under the authenticated user's UUID; caller-supplied paths are validated and Supabase RLS remains the final authorization boundary.
- The bucket is expected to remain private with the four user-folder policies documented in [docs/SUPABASE_STORAGE_SETUP_AR.md](docs/SUPABASE_STORAGE_SETUP_AR.md).
- This is a storage-layer integration only; no file-management UI or two-account Android acceptance test is claimed yet.
