# نشر خادم صديقي على Cloudflare Workers

تمت إضافة نقطة تشغيل متوافقة مع Cloudflare Workers في `backend/src/worker.js` مع إبقاء خادم Express الأصلي دون حذف.

## إعدادات Cloudflare الحالية
- Root directory: `/`
- Build command: اتركه فارغاً
- Deploy command: `npx wrangler deploy`
- يجب أن يكتشف Wrangler ملف `wrangler.jsonc` في جذر المستودع.

## أسرار يجب إضافتها قبل/بعد النشر
من لوحة Cloudflare: Workers & Pages → ssadeeqii-ai → Settings → Variables and Secrets، أضف كـ **Secrets**:
- `OPENAI_API_KEY`: مفتاح OpenAI API
- `SADEEQI_APP_TOKEN`: رمز عشوائي طويل (32 بايت عشوائية على الأقل)

وأضف كمتغيرات عادية:
- `OPENAI_MODEL`: اسم نموذج متاح في مشروع API الخاص بك
- `ALLOW_WEB_SEARCH`: `true` أو `false`
- `SADEEQI_SYSTEM_PROMPT`: اختياري؛ يمكن تركه لاستخدام تعليمات صديقي الافتراضية.

لا ترفع أي مفاتيح أو رموز إلى GitHub ولا ترسلها في المحادثة.

## اختبار بعد النشر
افتح `https://<worker-subdomain>.workers.dev/health` ويجب أن ترى JSON فيه `service: sadeeqi-ai` و `runtime: cloudflare-workers`. تكون `configured: true` فقط بعد ضبط الأسرار والمتغيرات.

اختبار `/v1/chat` يحتاج POST مع `Authorization: Bearer <SADEEQI_APP_TOKEN>` وجسم JSON يحوي `input`.

## تنبيه أمني مهم
تطبيق Android الحالي يضم رمز التطبيق في إعدادات البناء. أي رمز ثابت داخل APK يمكن استخراجه؛ لذلك هذا حاجز تجريبي وليس مصادقة قوية لتطبيق منشور للعامة. قبل الإطلاق العام يجب استبداله بتسجيل مستخدم/رموز قصيرة العمر أو بوابة مصادقة مناسبة، مع حدود استخدام ومكافحة إساءة الاستخدام.
