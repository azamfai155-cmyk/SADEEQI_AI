# نشر خادم صديقي على Vercel

## إعداد المشروع
- اربط مستودع GitHub: azamfai155-cmyk/SADEEQI_AI
- اختر الفرع: migration/vercel (أو main بعد دمج طلب النقل)
- Framework Preset: Other
- Root Directory: .
- Build Command: اتركه فارغاً
- Output Directory: اتركه فارغاً
- Install Command: npm install

## متغيرات البيئة المطلوبة
أضفها في Vercel Project Settings → Environment Variables، لكل من Production وPreview عند الحاجة:
- OPENAI_API_KEY: مفتاح OpenAI API (Secret)
- OPENAI_MODEL: اسم النموذج المسموح لحساب API
- SADEEQI_APP_TOKEN: رمز عشوائي قوي وطويل، ولا تشاركه
اختياري:
- SADEEQI_SYSTEM_PROMPT: تعليمات شخصية المساعد
- ALLOWED_ORIGIN: أصل واجهة ويب محدد فقط؛ لا يلزم لتطبيق Android
- ALLOW_WEB_SEARCH: ضع false لتعطيل البحث

## التحقق
بعد اكتمال النشر:
- افتح https://YOUR-VERCEL-DOMAIN/health
- يجب أن يعرض JSON مع runtime = vercel-nodejs و configured = true
- تطبيق Android يستخدم عنوان الأساس https://YOUR-VERCEL-DOMAIN ويطلب /v1/chat

## حدود الاستضافة
دالة Vercel مضبوطة على 60 ثانية وحد جسم 4 ميغابايت. رفع الصور الكبيرة قد يتطلب تقليل حجم الصورة في التطبيق. لا تضع مفتاح OpenAI داخل تطبيق Android أو GitHub.
