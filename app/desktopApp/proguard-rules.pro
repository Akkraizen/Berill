-dontoptimize

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
    *** Companion;
}

-keep class org.sqlite.** { *; }

-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.swing.** { *; }

-keep class com.sun.jna.** { *; }
-keep interface dev.akkraizen.berill.desktop.ui.window.DwmApi { *; }
-keep interface dev.akkraizen.berill.desktop.windows.Advapi32 { *; }
-keep interface dev.akkraizen.berill.desktop.windows.User32 { *; }
-keep interface dev.akkraizen.berill.desktop.windows.Kernel32 { *; }
-dontwarn com.sun.jna.**

-keep class dev.akkraizen.berill.sdk.** { *; }
-keep class kotlin.** { *; }
-keep class dev.akkraizen.berill.core.external.PluginRunnerMainKt { *; }

-keep class com.sun.jna.** { *; }
-keep class dev.akkraizen.berill.core.secure.** { *; }

-keep class kotlinx.coroutines.** { *; }
