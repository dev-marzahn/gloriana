-keepclasseswithmembers class androidx.sqlite.driver.bundled.** {
    native <methods>;
}
-keep class androidx.sqlite.SQLiteException {
    <init>(java.lang.String);
}

-keep class com.google.crypto.tink.proto.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessage { *; }
-dontwarn com.google.api.client.http.**
-dontwarn org.conscrypt.**

-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class com.github.javakeyring.** { *; }
-keep class de.swiesend.secretservice.** { *; }
-keep class org.freedesktop.dbus.** { *; }

-dontwarn okhttp3.internal.platform.**
-dontwarn okhttp3.internal.graal.**
-dontwarn org.graalvm.nativeimage.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**
