-keep class app.notier.MainActivity {
    public <init>();
}
-keep class app.notier.UpdateJob { public <init>(); }
-keepattributes SourceFile,LineNumberTable
-keep class com.google.android.gms.common.api.GoogleApiActivity { public <init>(); }
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.annotations.jvm.**
-dontwarn java.lang.management.**
# Compile-time annotations in the Google libraries have no runtime role.
-dontwarn com.google.android.apps.common.proguard.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
