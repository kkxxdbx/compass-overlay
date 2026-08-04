# Add project specific ProGuard rules here.

# App classes are referenced by manifest (activity/service) and kept automatically.
# Keep the whole app package for safety (no reflection-heavy code expected).
-keep class com.compassoverlay.** { *; }

# UpdateChecker uses BuildConfig.VERSION_CODE/VERSION_NAME via BuildConfig.
-keep class com.compassoverlay.BuildConfig { *; }
