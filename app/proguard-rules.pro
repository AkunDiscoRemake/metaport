# Keep ARCore + reflection entry points used by the Dev API.
-keep class com.google.ar.core.** { *; }
-keepclassmembers class com.metaport.xr.devapi.** { *; }
-dontwarn com.google.ar.core.**
