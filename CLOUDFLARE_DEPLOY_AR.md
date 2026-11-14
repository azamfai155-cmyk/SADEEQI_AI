# نشر خادم صديقي على Cloudflare Workers

هذا الدليل يطابق تطبيق Android الحالي وملف `wrangler.jsonc` في هذا الفرع.

## إعدادات المشروع
- Root directory: `/`
- Worker name: `sadeeqi-ai` (من `wrangler.jsonc`)
- Build command: اتركه فارغاً
- Deploy command: `npx wrangler deploy`
- ملف الدخول: `backend/src/worker.js`

يجب أن يبقى اسم Worker في لوحة Cloudflare مطابقاً لـ `sadeeqi-ai`، ما لم يتم تعديل `name` في `wrangler.jsonc` أيضاً.

## الأسرار (Secrets)
من Cloudflare → Workers & Pages → `sadeeqi-ai` → Settings → Variables and Secrets، أضف:
- `OPENAI_API_KEY`: مفتاح مشروع OpenAI API، كنوع Secret.
- `SADEEQI_SESSION_SECRET`: سر عشوائي قوي لا يقل عن 32 حرفاً، كنوع Secret. أنشئه بمولّد موثوق ولا تضعه في GitHub أو التطبيق أو المحادثة.

**مهم:** لا تستخدم `SADEEQI_APP_TOKEN` لهذا الإصدار. الخادم الحالي يصدر جلسات قصيرة العمر موقّعة باستخدام `SADEEQI_SESSION_SECRET`.

## المتغيرات العادية
- `OPENAI_MODEL`: اسم نموذج متاح في مشروع API الخاص بك.
- `ALLOW_WEB_SEARCH`: `true` للسماح بطلبات البحث التي يطلبها التطبيق، أو `false` لتعطيلها.
- `SADEEQI_SYSTEM_PROMPT`: اختياري؛ اتركه غير مضبوط لاستخدام تعليمات صديقي الافتراضية.

## حدود الاستخدام
يعرّف `wrangler.jsonc` محددي المعدل:
- `SESSION_RATE_LIMITER`: حتى 5 طلبات لكل عنوان IP في الدقيقة.
- `CHAT_RATE_LIMITER`: حتى 10 طلبات لكل عنوان IP في الدقيقة.

تأكد من قبول Cloudflare لإعدادات Rate Limiting عند النشر. يتوقف الخادم بأمان ويرجع 503 إذا لم تتوفر هذه الروابط؛ لا تحذفها لتجاوز الحماية.

## النشر والاختبار
بعد حفظ المتغيرات والأسرار، انشر Worker. ثم افتح:
`https://sadeeqi-ai.<نطاق-workers-الخاص-بحسابك>.workers.dev/health`

يجب أن يظهر JSON يتضمن `service: sadeeqi-ai` و`runtime: cloudflare-workers`. وتصبح `configured: true` بعد ضبط الأسرار والمتغيرات وروابط محددات المعدل.

نجاح `/health` وحده لا يثبت أن طلب OpenAI يعمل؛ أرسل رسالة فعلية من التطبيق وتحقق من وصول الرد.

## ملاحظة أمان
الجلسة الموقعة صالحة لعشر دقائق ومفتاح توقيعها محفوظ على الخادم. محددات المعدل حماية أولية حسب عنوان IP وليست نظام حسابات أو حصص مستخدمين. قبل الإطلاق العام، أضف تسجيل مستخدمين ومراقبة تكاليف وحدوداً لكل حساب.
