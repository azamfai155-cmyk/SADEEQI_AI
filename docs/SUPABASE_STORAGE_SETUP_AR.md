# ربط التخزين الخاص في صديقي AI

## ما تمت إضافته
- `SupabaseStorageClient` يستخدم جلسة Supabase للمستخدم الحالي عبر `SupabaseAuthClient`.
- يستخرج UUID المستخدم من `GET /auth/v1/user` ولا يقبل UUID يمرره المستدعي.
- يضع كل ملف داخل مجلد UUID الخاص بالمستخدم.
- يدعم الرفع، الاستبدال، التنزيل، عرض قائمة الملفات والحذف.
- يتحقق محلياً من حد 10 MB وأنواع MIME المعتمدة.
- يتحقق من مسار الملف قبل التنزيل أو التعديل أو الحذف، ثم يعتمد أيضاً على RLS في Supabase كطبقة الحماية النهائية.

## إعداد البناء
يجب توفير خاصيتي Gradle:
- `sadeeqiSupabaseUrl`
- `sadeeqiSupabaseAnonKey`

أو متغيرات GitHub Actions السرية المستخدمة حالياً:
- `SADEEQI_SUPABASE_URL`
- `SADEEQI_SUPABASE_ANON_KEY`

لا تستخدم مفتاح `service_role` أو أي Secret Key داخل تطبيق Android. المفتاح المسموح به في التطبيق هو anon/publishable فقط.

## مثال استخدام من طبقة التطبيق
```kotlin
val storage = SupabaseStorageClient(context)
val objectPath = withContext(Dispatchers.IO) {
    storage.upload(file, "application/pdf")
}
val bytes = withContext(Dispatchers.IO) {
    storage.download(objectPath)
}
```

نفّذ عمليات الشبكة على IO dispatcher، ولا تعرض مسار الملف أو محتواه لمستخدم آخر.

## اختبار القبول المطلوب قبل الإطلاق
1. سجّل الدخول بالحساب A وارفع ملفاً، وتأكد أن المسار يبدأ بـ UUID الحساب A.
2. سجّل الدخول بالحساب B وتأكد أن القائمة لا تعرض ملف الحساب A.
3. جرّب تنزيل ملف A وحذفه وتعديله باستخدام جلسة B؛ يجب أن يرفض Supabase الطلب.
4. جرّب عمليات التنزيل والتعديل والحذف للحساب A نفسه؛ يجب أن تنجح.
5. جرّب ملفاً أكبر من 10 MB ونوع MIME غير مسموح؛ يجب أن يرفضهما التطبيق.
6. تحقق من أن الـ bucket يظل خاصاً وأن سياسات SELECT/INSERT/UPDATE/DELETE الأربع تظل مفعّلة.

## حدود التنفيذ الحالي
هذه إضافة لطبقة التخزين فقط. لم تُضف بعد شاشة إدارة ملفات للمستخدم ولم يُجر اختبار شبكي بحسابين حقيقيين من Android. لا تعتبر الميزة جاهزة للإنتاج قبل تنفيذ اختبار القبول أعلاه وبناء APK واختباره.
