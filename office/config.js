/*
 * إعدادات واجهة شاشة مكتب «رونق الإعمار» — كل البيانات تُعدَّل من هنا فقط، بلا لمس للتصميم.
 *
 * قواعد مهمة:
 *  - الحقول الفارغ ("") تعني «بيانات ناقصة»: تظهر على الشاشة كبطاقة واضحة تطلب الإكمال،
 *    ولا يُولَّد لها أي رمز QR.
 *  - الألوان أدناه «مقترحة» مستخرجة من اللوجو، وتحتاج اعتمادًا من دليل الهوية إن وُجد.
 *  - بيانات المشاريع والصور مأخوذة من بروفايل الشركة (بلا أرقام مالية).
 *  - هذا الملف منشور مع الصفحة؛ أي بيانات تضعها هنا (مثل أرقام بطاقة الاتصال) ستكون
 *    مرئية لمن يفتح رابط الصفحة. راجع README قبل وضع بيانات شخصية.
 */
window.RUMAAR_CONFIG = {
  brand: {
    nameAr: 'رونق الإعمار',
    legalNameAr: 'شركة رونق الإعمار للمقاولات',
    nameEn: 'RUMAAR',
    logo: 'media/logo.png',
    // ألوان مقترحة — تحتاج اعتمادًا. المصدر: لون اللوجو (#F0C018) ولون البروفايل الداكن.
    colors: {
      accent: '#F0C018',      // ذهبي اللوجو
      accentInk: '#141414',   // لون النص فوق الذهبي
      bg: '#0E0F11',          // خلفية الشاشة
      surface: '#17181B',     // البطاقات
      surface2: '#202226',    // البطاقات المرتفعة والقوائم
      line: '#2C2E33',        // الحدود الخفيفة
      text: '#F4F1EA',        // النص الأساسي
      muted: '#A39E93',       // النص الثانوي
      ok: '#4CC38A', warn: '#F0A733', bad: '#E5534B'
    },
    font: 'IBM Plex Sans Arabic',
    colorsApproved: false   // اجعلها true بعد اعتماد الألوان من دليل الهوية
  },

  // رسالة الترحيب في الشريط العلوي
  welcome: 'أهلًا وسهلًا بكم',

  // المنطقة الزمنية للساعة والتقويم (أسماء IANA)
  timeZone: 'Asia/Riyadh',
  numerals: 'latn',      // 'latn' أرقام 0-9 ، 'arab' أرقام ٠-٩
  weekStart: 0,          // 0 = الأحد أول الأسبوع، 6 = السبت

  // رابط بروفايل الشركة (يُولَّد له QR). اتركه فارغًا حتى يتوفر الرابط الصحيح.
  profileUrl: '',

  // بطاقة الاتصال (vCard). يلزم الاسم + هاتف أو بريد على الأقل لتوليد QR.
  contact: {
    name: '',            // الاسم كما يظهر في جهات الاتصال
    title: '',           // المنصب
    org: 'شركة رونق الإعمار للمقاولات',
    mobile: '',          // مثال الصيغة: +9665XXXXXXXX
    phone: '',           // هاتف المكتب
    email: '',
    website: '',         // مثال: https://...
    address: ''          // العنوان
  },

  // مداخل HDMI — الأسماء قابلة للتخصيص (مثل: «كاميرات المكتب»، «لابتوب الاجتماعات»)
  hdmiInputs: [
    { port: 1, name: 'HDMI 1' },
    { port: 2, name: 'HDMI 2' },
    { port: 3, name: 'HDMI 3' }
  ],

  // مشاركة شاشة اللابتوب داخل اللوحة: تحتاج جهاز استقبال لاسلكي موصول بأحد مداخل HDMI.
  // اترك receiverPort = null حتى يُركَّب الجهاز.
  screenShare: {
    receiverPort: null,
    receiverName: 'جهاز استقبال المشاركة',
    // الخطوة المطلوبة على اللابتوب تُكتب هنا بعد اختيار جهاز الاستقبال.
    laptopSteps: []
  },

  // مكتبة الوسائط: صور المشاريع، فيديوهات محلية، وروابط YouTube.
  // type: 'image' | 'video' | 'youtube'
  // project: { name, location, scope } يظهر في شريط المشروع.
  media: [
    { type: 'image', src: 'media/projects/fitness-time-xpress.jpg',
      project: { name: 'فيتنس تايم إكسبرس', location: 'جازان', scope: 'القطاع الرياضي • 1,000 م²' } },
    { type: 'image', src: 'media/projects/laban-square.jpg',
      project: { name: 'لبن سكوير', location: 'الرياض', scope: 'مجمع تجاري • 25,530 م²' } },
    { type: 'image', src: 'media/projects/educational-complex-jazan.jpg',
      project: { name: 'المجمع التعليمي — وزارة التعليم', location: 'جازان', scope: 'القطاع التعليمي • 10,890 م²' } },
    { type: 'image', src: 'media/projects/agility-logistics-city.jpg',
      project: { name: 'مدينة أجيليتي اللوجستية', location: 'المهجر، جدة', scope: 'القطاع الصناعي • 49,700 م²' } },
    { type: 'image', src: 'media/projects/fitness-time-oraija.jpg',
      project: { name: 'فيتنس تايم عريجاء', location: 'الرياض', scope: 'القطاع الرياضي • 3,800 م²' } },
    { type: 'image', src: 'media/projects/etoile-residential.jpg',
      project: { name: 'مجمع إيتوال السكني', location: 'الرياض', scope: 'القطاع السكني • 13,500 م²' } },
    { type: 'image', src: 'media/projects/adex-residential.jpg',
      project: { name: 'مجمع سكني خاص — مجموعة أدكس', location: 'حطين، الرياض', scope: 'القطاع السكني • 34,750 م²' } }
    // أمثلة لإضافة مصادر أخرى (احذف // للتفعيل):
    // { type: 'video', src: 'media/videos/project.mp4', title: 'فيديو المشروع' },
    // { type: 'youtube', videoId: 'XXXXXXXXXXX', title: 'عنوان الفيديو' },
    // { type: 'youtube', playlistId: 'PLxxxxxxxx', title: 'قائمة تشغيل' }
  ],

  slideSeconds: 10,           // مدة كل صورة في العرض التلقائي
  showProjectStrip: true,     // إظهار شريط المشروع (يمكن تغييره من الإعدادات على الشاشة)
  controlsAutoHideMs: 5000,   // إخفاء أدوات التحكم في ملء الشاشة بعد هذه المدة

  // 'auto' : يكتشف شاشة LG تلقائيًا، 'demo' : عرض تجريبي، 'lg' : فرض تكامل LG
  device: 'auto'
};
