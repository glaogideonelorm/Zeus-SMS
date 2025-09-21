# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep accessibility service
-keep class com.yourpackage.simpleussd.ussd.UssdAccessibilityService { *; }

# Keep USSD related classes
-keep class com.yourpackage.simpleussd.ussd.** { *; }

