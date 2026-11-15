# دليل إنشاء تطبيقات منصات التواصل الرسمية لصديقي

**المستودع:** `azamfai155-cmyk/SADEEQI_AI`  
**بيئة الخادم المعتمدة:** `https://sadeeqi-ai-pdrk.vercel.app`  
**تاريخ الدليل:** 2026-11-14

> هذا الدليل يجهّز حسابات المطوّر الرسمية. لا توجد حالياً مفاتيح API مقدمة من صاحب المشروع، ولا توجد حسابات اجتماعية متصلة. لا تنشئ Redirect URI من هذا الدليل قبل أن نكمل وننشر مسارات OAuth الفعلية؛ تسجيل callback غير موجود بعد. شاشة Social Hub تعرض حالة الاستعداد فقط ولا تنفذ OAuth أو النشر.

## 1. ما الذي سنبنيه

- اتصال مستقل لكل حساب عبر صفحة OAuth الرسمية للمنصة.
- إظهار اسم الحساب والمنصة وحالة الاتصال وتاريخ انتهاء التفويض.
- صلاحيات منفصلة للقراءة والنشر والرسائل، مع أقل نطاقات لازمة.
- تخزين رموز OAuth على الخادم فقط، مشفرة، وعدم إرسالها إلى تطبيق Android.
- فصل الحساب وإلغاء/حذف رموزه، وتجديد الرموز حيث تدعم المنصة ذلك.
- تأكيد المستخدم قبل النشر أو إرسال رسالة أو حذف محتوى.
- سجلات تدقيق لا تحتوي كلمات مرور أو access tokens أو client secrets.

## 2. روابط إنشاء التطبيقات الرسمية

### Meta: Facebook وInstagram وMessenger وWhatsApp وThreads
- بوابة المطورين: https://developers.facebook.com/
- لوحة التطبيقات: https://developers.facebook.com/apps/
- الوثائق: https://developers.facebook.com/docs/
- Instagram Platform: https://developers.facebook.com/docs/instagram-platform/
- Messenger Platform: https://developers.facebook.com/docs/messenger-platform/
- WhatsApp Cloud API: https://developers.facebook.com/docs/whatsapp/cloud-api/
- Threads API: https://developers.facebook.com/docs/threads/

**خطوات البدء:**
1. سجّل الدخول بحساب Meta الذي تملكه وتديره.
2. افتح My Apps ثم Create App، واختر نوع الاستخدام الذي يطابق منتجات Meta المطلوبة (غالباً Business عند إدارة صفحات وأصول أعمال؛ اتبع الاختيارات المعروضة في لوحة Meta).
3. أكمل اسم التطبيق وبريد التواصل وبيانات النشاط.
4. أضف المنتجات المطلوبة فقط: Facebook Login، Instagram API، Messenger، WhatsApp، Threads حسب الحاجة.
5. اضبط App Domains وPrivacy Policy URL وTerms URL وData Deletion instructions بعد تجهيز صفحاتها الحقيقية.
6. لا تطلب صلاحيات متقدمة إلا بعد تنفيذ الميزة المقابلة وإعداد فيديو شرح واضح؛ قد تتطلب مراجعة App Review والتحقق التجاري.
7. لا تشارك App Secret هنا. سنضيفه لاحقاً في Vercel Environment Variables بعد بناء OAuth.

**قيود مهمة:** Instagram API ليس وصولاً عاماً لكل حساب شخصي؛ قدرات الإدارة تعتمد على نوع الحساب وصلاحيات Meta. WhatsApp Cloud API مخصص لرقم WhatsApp Business المُهيأ، ولا يتيح قراءة صندوق WhatsApp الشخصي العادي. Messenger يتطلب صفحة وصلاحيات Messenger. صلاحيات النشر والرسائل قد تحتاج مراجعة Meta.

### TikTok
- بوابة المطورين: https://developers.tiktok.com/
- إنشاء التطبيق: https://developers.tiktok.com/docs/en/getting-started-create-an-app
- Display API: https://developers.tiktok.com/doc/display-api-get-started/
- Content Posting API: https://developers.tiktok.com/docs/en/content-posting-api-get-started

**الخطوات:**
1. أنشئ حساب TikTok for Developers وسجّل الدخول.
2. Manage apps ثم Connect an app.
3. اختر مالك التطبيق، وأدخل الاسم والوصف والأيقونة وبيانات المطور.
4. أضف Login Kit وDisplay API، وأضف Content Posting API فقط إذا أردت النشر.
5. سجّل منصات التشغيل المطلوبة. إعداد Android قد يطلب package name ورابط المتجر وبصمة التوقيع؛ لا نرسل بيانات نهائية قبل تثبيت هوية الإصدار.
6. جهّز Privacy Policy وTerms وURLs المطلوبة، ثم أرسل التطبيق للمراجعة.
7. بعد إنشاء التطبيق سيظهر Client Key وClient Secret؛ احتفظ بهما سريين.

**قيود:** بعض scopes تحتاج موافقة. نشر التطبيقات غير المدققة قد يكون مقيداً بوضع خاص (private-only) إلى أن يجتاز التطبيق التدقيق.

### YouTube / Google
- Google Cloud Console: https://console.cloud.google.com/
- إعداد OAuth: https://developers.google.com/identity/protocols/oauth2
- YouTube Data API: https://developers.google.com/youtube/v3
- إرشادات OAuth ليوتيوب: https://developers.google.com/youtube/data/guides/authentication

**الخطوات:**
1. أنشئ Google Cloud Project باسم SADEEQI AI Social.
2. افتح APIs & Services ثم Library وفعّل YouTube Data API v3.
3. اضبط OAuth consent screen/Google Auth Platform: اسم التطبيق، بريد الدعم، بيانات المطور، وسياسة الخصوصية.
4. أنشئ OAuth Client ID من نوع Web application (لأن تبادل client secret سيكون في الخادم).
5. لا تضف Authorized redirect URI حتى نزودك بالعنوان النهائي بعد تنفيذ callback ونشره.
6. أضف أقل scopes لازمة؛ قراءة القناة والنشر تحتاج موافقة المستخدم، ورفع الفيديو يستخدم scope مناسباً مثل youtube.upload.
7. قد تتطلب بعض الاستخدامات التحقق من Google، كما توجد حصص يومية للـ API.

### X
- بوابة المطورين: https://developer.x.com/
- وثائق OAuth 2.0: https://docs.x.com/fundamentals/authentication/oauth-2-0/overview

**الخطوات:**
1. أنشئ حساب مطور وProject/App من Developer Portal.
2. فعّل OAuth 2.0 وأدخل بيانات التطبيق.
3. اختر Web App / Automated App بحسب الخيارات المتاحة.
4. فعّل Authorization Code with PKCE واطلب scopes التي نحتاجها فقط (مثل users.read أو tweet.read أو tweet.write عند دعمها).
5. لا تسجل callback قبل أن نرسل لك العنوان النهائي من الكود المنشور.
6. راجع خطة الوصول والتسعير وحدود القراءة والكتابة؛ إمكانات X قد تعتمد على الخطة المدفوعة.

### Telegram
- إنشاء البوت: https://t.me/BotFather
- دليل البوتات: https://core.telegram.org/bots
- Bot API: https://core.telegram.org/bots/api

**الخطوات:**
1. افتح Telegram وابحث عن @BotFather الرسمي.
2. أرسل `/newbot`.
3. اختر اسماً للبوت ثم username ينتهي عادةً بـ `bot`.
4. سيعطيك Bot Token. لا ترسله في الدردشة ولا تضعه في Android أو GitHub؛ سنحفظه كـ Vercel Secret بعد بناء موصل Telegram.
5. لاحقاً نضبط webhook HTTPS ونختبر الرسائل.

**قيد مهم:** Telegram Bot API يربط بوتاً، ولا يمنح صديقي وصولاً عاماً إلى محادثات حسابك الشخصي أو رسائل الناس الخاصة. يجب أن يبدأ المستخدم المحادثة مع البوت أو يضيفه إلى مجموعة/قناة ويمنحه الصلاحيات المناسبة.

### Discord
- Developer Portal: https://discord.com/developers/applications
- OAuth2: https://discord.com/developers/docs/topics/oauth2

1. New Application ثم سمِّه SADEEQI AI.
2. افتح OAuth2 وأضف Redirect URI بعد استلام المسار النهائي منا.
3. أنشئ Bot فقط إذا أردت إدارة خوادم/قنوات أضفته إليها.
4. فعّل أقل scopes وصلاحيات ممكنة؛ لا تمنح Administrator دون ضرورة.
5. احفظ Client Secret وBot Token في Secrets على الخادم فقط.

### LinkedIn
- بوابة المطورين: https://www.linkedin.com/developers/
- الوثائق: https://learn.microsoft.com/linkedin/

1. أنشئ تطبيقاً واربطه بصفحة مؤسسة LinkedIn عند طلب ذلك.
2. أكمل معلومات التطبيق والشعار وروابط سياسة الخصوصية.
3. أضف OAuth 2.0 redirect URL النهائي بعد اكتمال callback.
4. اطلب المنتجات والصلاحيات المطلوبة. قدرات Community Management والنشر ليست تلقائية وقد تحتاج مراجعة/موافقة LinkedIn.

### Snapchat
- بوابة المطورين: https://developers.snap.com/
- Snap Kit: https://developers.snap.com/snap-kit

1. أنشئ حساب مطور/تطبيق من بوابة Snap.
2. اختر المنتج المناسب (Snap Kit أو Marketing API حسب الوظيفة).
3. أكمل بيانات التطبيق والروابط وسياسة الخصوصية.
4. فعّل OAuth/Redirect URI بعد تزويدك بالمسار النهائي.
5. لاحظ أن بعض واجهات Snap مخصصة لتسجيل الدخول أو المشاركة، ولا تعني إدارة كاملة لحساب Snapchat الشخصي.

### Reddit
- بوابة التطبيقات: https://www.reddit.com/prefs/apps
- وثائق المطور: https://www.reddit.com/dev/api/

1. سجّل الدخول إلى حساب Reddit الذي ستدير التطبيق به.
2. افتح صفحة apps وأنشئ تطبيقاً من نوع web app عند توفره.
3. أضف وصف التطبيق وRedirect URI بعد اكتمال callback.
4. اطلب OAuth scopes المناسبة واقرأ Data API Terms وقيود الاستخدام ومعدل الطلبات.
5. احتفظ بالـ secret على الخادم.

### Pinterest
- بوابة المطورين: https://developers.pinterest.com/
- ربط التطبيق: https://developers.pinterest.com/docs/getting-started/connect-app/
- OAuth: https://developers.pinterest.com/docs/getting-started/set-up-authentication-and-authorization/

1. استخدم حساب Pinterest Business، وتحقق من البريد.
2. افتح My Apps ووافق على Developer Terms.
3. Connect app وأكمل طلب التسجيل وانتظر مراجعة Pinterest.
4. بعد الموافقة، أضف Redirect URI النهائي وScopes اللازمة.
5. Pinterest يستخدم OAuth 2.0؛ Client Secret يبقى على الخادم.

## 3. ترتيب التنفيذ المقترح

لن نطلب منك إنشاء 10 تطبيقات في وقت واحد. نبدأ بالأكثر فائدة والأسهل اختباراً:

1. **Telegram Bot**: لا يحتاج OAuth للمستخدم، لكن يتطلب Bot Token وWebhook.
2. **YouTube**: Google OAuth + YouTube Data API.
3. **Meta**: تطبيق واحد ومنتجات منفصلة حسب Facebook Pages وInstagram Professional وMessenger وWhatsApp Business وThreads.
4. **TikTok**: Login Kit ثم Content Posting API ومراجعة النشر.
5. X وDiscord وLinkedIn وPinterest وReddit وSnapchat حسب توفر المنتجات والموافقات.

## 4. إعدادات الخادم — لا تدخلها الآن

المفاتيح التالية أمثلة أسماء إعدادات وليست قيماً. لن نضيفها قبل أن يصبح OAuth callback وقاعدة البيانات والهوية جاهزة:

- Meta: `META_CLIENT_ID`, `META_CLIENT_SECRET`
- TikTok: `TIKTOK_CLIENT_KEY`, `TIKTOK_CLIENT_SECRET`
- Google: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- X: `X_CLIENT_ID`, `X_CLIENT_SECRET`
- Discord: `DISCORD_CLIENT_ID`, `DISCORD_CLIENT_SECRET`
- LinkedIn: `LINKEDIN_CLIENT_ID`, `LINKEDIN_CLIENT_SECRET`
- Snapchat: `SNAP_CLIENT_ID`, `SNAP_CLIENT_SECRET`
- Reddit: `REDDIT_CLIENT_ID`, `REDDIT_CLIENT_SECRET`
- Pinterest: `PINTEREST_APP_ID`, `PINTEREST_APP_SECRET`
- Telegram: `TELEGRAM_BOT_TOKEN`

ملاحظة: يستخدم Worker الحالي الاسمين META_CLIENT_ID وMETA_CLIENT_SECRET لمعرّفات تطبيق Meta وسرّه. تبقى هذه القيم على الخادم فقط، ولا تعني أن OAuth أو ربط الحسابات أصبح مفعّلاً.

## 5. متطلبات أمنية قبل تشغيل OAuth

- هوية مستخدم حقيقية؛ لا يجوز اعتبار الرمز الثابت الموجود في APK هوية مالك حسابات التواصل لأنه قابل للاستخراج.
- قاعدة بيانات دائمة مرتبطة بمعرّف المستخدم الموثق.
- OAuth Authorization Code، مع PKCE حيث تدعمه المنصة، وstate عشوائي أحادي الاستخدام ومحدود الصلاحية.
- تشفير رموز الوصول والتحديث في الخادم بمفتاح سري مستقل.
- عدم إرجاع الرموز لتطبيق Android وعدم تسجيلها في Logs.
- Webhook يتحقق من توقيع المنصة، مع منع إعادة الإرسال وتحديد المعدل.
- طلب أقل الصلاحيات وموافقة مستقلة قبل النشر/الإرسال/الحذف.
- صفحات Privacy Policy وTerms وData Deletion حقيقية قبل مراجعة التطبيقات.

**لا تضع أي Client Secret أو Bot Token في مستودع GitHub أو محادثة ChatGPT أو تطبيق Android.** عندما نصل إلى مرحلة إدخال المفاتيح، سنستخدم Vercel Project → Settings → Environment Variables، مع اختيار Production/Preview المناسب، ثم Redeploy بعد اكتمال الاختبار.

## 6. المطلوب منك الآن

ابدأ بإنشاء حساب مطور واحد فقط على Telegram عبر @BotFather. أرسل `/newbot`، وأنشئ اسماً وusername للبوت. احتفظ بالتوكن لنفسك ولا ترسله لي. أخبرني فقط عندما تصل إلى شاشة التوكن؛ بعدها أوجهك لحفظه في Vercel Secrets بطريقة آمنة.

وبالتوازي، أنشئ حساباً في Google Cloud Console حتى نبدأ إعداد YouTube OAuth. لا تنشئ Redirect URI حتى أعطيك العنوان النهائي بعد تجهيز callback.
