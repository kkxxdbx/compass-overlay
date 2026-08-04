# Add project specific ProGuard rules here.

# App classes are referenced by manifest (activity/service) and kept automatically.
# Keep the whole app package for safety (no reflection-heavy code expected).
-keep class com.compassoverlay.** { *; }

# UpdateChecker uses BuildConfig.VERSION_CODE/VERSION_NAME via BuildConfig.
-keep class com.compassoverlay.BuildConfig { *; }

# Umeng analytics SDK: keep all classes and their members.
-keep class com.umeng.** { *; }
-keepclassmembers class com.umeng.** { *; }
-keepclassmembers enum * { *; }
-dontwarn com.umeng.**
