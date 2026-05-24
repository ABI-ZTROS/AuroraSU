# Add project specific ProGuard rules here.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.view.View

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keep class kotlin.** { *; }

# Keep AuroraSU classes
-keep class com.aurora.su.** { *; }
