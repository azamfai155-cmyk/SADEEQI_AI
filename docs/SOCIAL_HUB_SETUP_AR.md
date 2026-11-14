# SADEEQI SOCIAL HUB — خطة التهيئة والتكامل

## الحالة الحالية

هذه المرحلة تضيف:
- شاشة عربية RTL ضمن القائمة الجانبية.
- endpoint محمي `GET /v1/social/providers` يعرض 15 موصلاً، وعدد المنصات التي اكتملت بيانات المطور والتشفير لها، وعدد الحسابات المتصلة فعلياً.
- لا يتم الادعاء بأن أي حساب متصل؛ جميع الحسابات تعرض «غير مربوط» حتى إكمال OAuth وتخزين الرمز والتحقق من حساب المنصة.
- اختبارات للخادم تتحقق من المصادقة ومن عدم إظهار اتصالات وهمية.

**لم يتم بعد تنفيذ OAuth أو إرسال/قراءة الرسائل أو النشر أو الإحصائيات.** زر الربط معطّل عمداً إلى أن يكتمل تسجيل دخول المستخدم الآمن وقاعدة البيانات ومفاتيح التطبيقات ومراجعات المنصات.

## ملاحظة أمنية قبل OAuth

النسخة الحالية من تطبيق Android تستخدم `SADEEQI_APP_TOKEN` ثابتاً مضمناً في APK. هذا الرمز قابل للاستخراج من التطبيق، لذلك لا يصلح كهوية مالك لحسابات التواصل أو كوسيلة حماية طويلة الأمد لرموز OAuth.

قبل تفعيل ربط الحسابات يجب إضافة تسجيل دخول مستخدم حقيقي (OIDC/OAuth مع مزود هوية) وإصدار access token قصير العمر مرتبط بمعرّف مستخدم، والتحقق منه في Worker. لا تستخدم رمز APK الحالي كـ owner_id ولا تسمح لجميع نسخ التطبيق بالوصول إلى حساب واحد مشترك.

رموز المنصات يجب أن تبقى في الخادم فقط. عند تفعيل OAuth:
- تشفير access/refresh tokens باستخدام AES-256-GCM بمفتاح 32 بايت في Cloudflare Worker Secret `SOCIAL_TOKEN_ENCRYPTION_KEY`.
- عدم تسجيل الرموز أو إعادتها إلى Android.
- حفظ ciphertext فقط في D1، مع ربط كل صف بمعرّف المستخدم الموثق.
- استخدام state عشوائي أحادي الاستخدام، PKCE حيث تدعمه المنصة، redirect URI مطابق حرفياً، وانتهاء قصير لحالة OAuth.
- فصل/إبطال الاتصال وحذف الرموز المشفرة عند طلب المستخدم.
- طلب أقل scopes ممكنة، مع موافقة منفصلة قبل النشر أو الإرسال أو الحذف.

## مخطط قاعدة البيانات

يوجد migration جاهز في:
`migrations/0001_social_hub.sql`

أنشئ قاعدة D1 من حساب Cloudflare ثم اربطها في `wrangler.jsonc` باسم `SOCIAL_DB` وأضف `database_id` الحقيقي إلى إعداداتك. لا نضع database_id افتراضياً أو نخترع واحداً.

مثال أوامر Cloudflare:
```bash
npx wrangler d1 create sadeeqi-social
npx wrangler d1 migrations apply sadeeqi-social --remote
npx wrangler secret put SOCIAL_TOKEN_ENCRYPTION_KEY
```
أضف migration directory إلى إعداد Wrangler بعد إنشاء قاعدة البيانات. أنشئ مفتاحاً عشوائياً آمناً بطول 32 بايت، ثم خزّنه كـ Worker Secret، ولا تضعه في GitHub أو APK.

## موصلات المنصات والقيود

| المنصة | أساس الربط | القيود المهمة |
|---|---|---|
| Facebook | Meta OAuth / Graph API | صفحات وصلاحيات معتمدة ومراجعة التطبيق |
| Instagram | Instagram Graph API / Meta OAuth | الوظائف التجارية تتطلب حساباً احترافياً وصلاحيات معتمدة |
| WhatsApp | WhatsApp Business Cloud API | أرقام أعمال وتهيئة Meta Business؛ ليس صندوق رسائل WhatsApp الشخصي |
| Messenger | Meta OAuth / Messenger Platform | رسائل صفحات مع pages_messaging ومراجعة |
| Telegram | Bot API | بوت فقط؛ لا يقرأ رسائل الحساب الشخصي الخاص |
| TikTok | Login Kit / Content Posting API | موافقة scopes وتدقيق؛ تطبيق غير مدقق قد ينشر خاصاً فقط |
| YouTube | Google OAuth / YouTube Data API | quota وموافقة scopes؛ الرفع يحتاج youtube.upload |
| X | X OAuth 2.0 | القراءة والكتابة بحسب خطة API المتاحة |
| Snapchat | Snap Kit / Marketing API | منتجات وصلاحيات وأهلية منفصلة |
| LinkedIn | LinkedIn OAuth 2.0 | Community Management access يتطلب موافقة |
| Discord | OAuth2 / Bot API | البوت يرى فقط الخوادم والقنوات التي أضيف إليها |
| Reddit | Reddit OAuth | سياسة Data API ومعدلات الطلبات |
| Pinterest | Pinterest OAuth | صلاحيات API وموافقة/أهلية الحساب |
| Threads | Threads API / Meta OAuth | نشر وInsights للحسابات المؤهلة والصلاحيات الممنوحة |
| Twitch | Twitch OAuth | الوظائف تعتمد على scopes وApp registration |

## إعدادات المطور المطلوبة

تُقرأ المفاتيح من Cloudflare Worker Secrets/Vars فقط، لا من تطبيق Android:

- Meta: `META_CLIENT_ID`, `META_CLIENT_SECRET` (تطبيق Meta واحد يخدم Facebook وInstagram وMessenger وThreads بعد تهيئة المنتجات والصلاحيات).
- Facebook عند فصل التطبيق: `FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET`.
- Instagram عند فصل التطبيق: `INSTAGRAM_CLIENT_ID`, `INSTAGRAM_CLIENT_SECRET`.
- TikTok: `TIKTOK_CLIENT_KEY`, `TIKTOK_CLIENT_SECRET`.
- Google/YouTube: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`.
- X: `X_CLIENT_ID`, `X_CLIENT_SECRET`.
- Snapchat: `SNAPCHAT_CLIENT_ID`, `SNAPCHAT_CLIENT_SECRET`.
- LinkedIn: `LINKEDIN_CLIENT_ID`, `LINKEDIN_CLIENT_SECRET`.
- Discord: `DISCORD_CLIENT_ID`, `DISCORD_CLIENT_SECRET`.
- Reddit: `REDDIT_CLIENT_ID`, `REDDIT_CLIENT_SECRET`.
- Pinterest: `PINTEREST_CLIENT_ID`, `PINTEREST_CLIENT_SECRET`.
- Twitch: `TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET`.
- WhatsApp Business Cloud API: `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`.
- Telegram Bot API: `TELEGRAM_BOT_TOKEN` (ليس OAuth ولا كلمة مرور حسابك).

في الخادم الحالي لا تُعد موصلات OAuth مهيأة إلا عند وجود بيانات المطور ومفتاح `SOCIAL_TOKEN_ENCRYPTION_KEY` معاً. Telegram Bot API استثناء لأنه يستخدم Bot Token خادماً ولا يربط حساب Telegram الشخصي. وحتى بعد إضافة المفاتيح، لا تعتبر المنصة جاهزة للإنتاج قبل ضبط redirect URIs، scopes، webhook URLs، مراجعة التطبيق، واختبار كل API.

## المراجع الرسمية

- Meta Graph API: https://developers.facebook.com/docs/graph-api/
- Instagram API: https://developers.facebook.com/docs/instagram-platform/
- WhatsApp Cloud API: https://developers.facebook.com/docs/whatsapp/cloud-api/
- Messenger Platform: https://developers.facebook.com/docs/messenger-platform/
- Telegram Bot API: https://core.telegram.org/bots/api
- TikTok Display API: https://developers.tiktok.com/doc/display-api-get-started/
- TikTok Content Posting API: https://developers.tiktok.com/doc/content-posting-api-get-started/
- YouTube OAuth: https://developers.google.com/youtube/v3/guides/authentication
- X API: https://developer.x.com/en/docs
- Snapchat for Developers: https://developers.snap.com/
- LinkedIn API: https://learn.microsoft.com/linkedin/
- Discord OAuth2: https://discord.com/developers/docs/topics/oauth2
- Reddit API: https://www.reddit.com/dev/api/
- Pinterest API: https://developers.pinterest.com/
- Threads API: https://developers.facebook.com/docs/threads/
- Twitch API: https://dev.twitch.tv/docs/

## مراحل التنفيذ التالية

1. إضافة هوية مستخدم آمنة وD1 binding حقيقي.
2. إكمال OAuth end-to-end لمنصة أولى بعد توفير Client ID/Secret وإعداد Redirect URI.
3. إضافة تشفير الرموز وتجديدها وفصل الحساب.
4. تنفيذ قراءة البيانات/النشر لكل API ضمن scopes الممنوحة.
5. إضافة Webhooks وصندوق الوارد والتقويم والإحصائيات.
6. توسيع المنصات واحدة تلو الأخرى مع اختبارات فعلية.

لا ترسل Client Secret أو Bot Token في المحادثة أو داخل ملفات Android. استخدم Cloudflare Worker Secrets بعد تجهيز حساب المطور.
