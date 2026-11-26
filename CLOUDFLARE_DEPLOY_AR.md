# نشر خادم صديقي على Cloudflare Workers

ملف Worker الفعلي هو `backend/src/worker.js`، وإعداد Wrangler في جذر المستودع هو `wrangler.jsonc`. اسم Worker المعتمد في Cloudflare وWrangler هو `sadeeqi-ai-v2`، لذلك يكون العنوان المتوقع:
`https://sadeeqi-ai-v2.azamfai155.workers.dev`

ملاحظة: قد يظهر المعرّف `sadeeqi-ai` داخل استجابات التطبيق أو سجلاته بوصفه معرّفاً داخلياً للخدمة؛ هذا لا يغيّر اسم Worker الذي يجب أن يطابق قيمة `name` في `wrangler.jsonc`.

## إعداد Cloudflare

من لوحة Cloudflare افتح Workers & Pages ثم Worker باسم `sadeeqi-ai-v2`:
- Root directory: `/`
- Build command: اتركه فارغاً
- Deploy command: `npx wrangler deploy`
- ملف الإعداد: `wrangler.jsonc`
- Entry point: `backend/src/worker.js`

لا تنشئ Worker جديداً باسم مختلف ولا تحذف أي Worker أو نشر قائم. راجع الاسم والنطاق الحاليين قبل أي تعديل في لوحة Cloudflare.

## إعداد المصادقة ومزود الذكاء الاصطناعي

الإصدار الحالي من التطبيق يستخدم Supabase Auth؛ لذلك يجب أن يتحقق Worker من JWT الصادر عن مشروع Supabase نفسه.

أضف في Worker → Settings → Variables and Secrets:

### Variables (قيم عادية)
- `AUTH_MODE`: `supabase`
- `SUPABASE_URL`: عنوان مشروع Supabase مثل `https://<project-ref>.supabase.co`
- `AI_PROVIDER`: `groq`
- `GROQ_MODEL`: `openai/gpt-oss-20b`
- `ALLOW_WEB_SEARCH`: `false` للاختبار الأول، ثم غيّره فقط إذا أردت تفعيل البحث وكان المزود يدعمه.

### Secrets (قيم سرية)
- `GROQ_API_KEY`: مفتاح Groq من لوحة المطور الرسمية.

بديل Groq: إذا كان حساب المشروع يستخدم OpenAI بدلاً منه، اضبط `AI_PROVIDER=openai` وأضف `OPENAI_API_KEY` كـ Secret و`OPENAI_MODEL` كـ Variable. لا تضبط مفاتيح المزود في أكثر من مكان دون حاجة.

### انتقال مؤقت فقط
إذا كان لا يزال لديك إصدار Android قديم يعتمد الرمز المشترك، يمكن استخدام `AUTH_MODE=dual` مؤقتاً مع `SADEEQI_APP_TOKEN` كـ Secret. لا تستخدم هذا الوضع للإصدار الجديد بعد التأكد من تسجيل الدخول بـ Supabase. وضع `supabase` يرفض الرمز المشترك.

لا تضع أي مفتاح أو رمز سري في GitHub أو ملف Wrangler أو Android APK أو المحادثة. مفتاح Supabase anon/public ليس مفتاح service-role، ولا يغني عن التحقق من JWT على الخادم.

## فحص الصحة بعد النشر

افتح:
`https://sadeeqi-ai-v2.azamfai155.workers.dev/health`

يجب أن يعرض JSON:
- `runtime: cloudflare-workers`
- `auth_mode: supabase`
- `provider: groq` (أو المزود الذي اخترته)
- `configured: true`

لا تكشف استجابة الصحة أي مفاتيح سرية. إذا كانت `configured: false` فتحقق من أسماء المتغيرات والأسرار في Worker، لا تنشر أي قيمة سرية في سجل أو رسالة.

بعدها اختبر طلب دردشة حقيقياً من APK تجريبي مسجل الدخول، ثم اختبر Social Hub. نجاح `/health` وحده لا يثبت نجاح تسجيل الدخول أو محادثة AI.

## ترتيب النقل الآمن

1. جهّز إعدادات Worker والأسرار وتحقق من `/health`.
2. اختبر مصادقة Supabase وطلب الدردشة مباشرة على Worker.
3. بعد نجاح الاختبار فقط، حدّث عنوان API في إعداد Android وGitHub Actions إلى عنوان Worker الصحيح.
4. ابنِ APK جديداً واختبر تسجيل الحساب والدخول وتجديد الجلسة والدردشة وSocial Hub.
5. لا تغيّر إعدادات Vercel ولا تحذف أي مشروع أو نشر قائم أثناء اختبار Cloudflare. أبقِ النسخة الحالية كما هي حتى ينجح اختبار Worker والتطبيق.

## ما هو مدعوم وما ليس مدعوماً

- يدعم Worker مسارات `/health` و`/v1/chat` و`/v1/social/providers`.
- يدعم التحقق من Supabase JWT وفق issuer وaudience وJWKS، مع وضع انتقال مزدوج اختياري.
- يدعم Groq Responses API أو OpenAI Responses API وفق متغيرات البيئة.
- قائمة Social Hub تعرض جاهزية إعداد المطور فقط؛ OAuth وربط الحسابات والرسائل والنشر غير منفذة حتى الآن.
- لا يدّعي اختبار الصحة نجاح الدردشة؛ يجب اختبارها بمستخدم مسجل الدخول.
