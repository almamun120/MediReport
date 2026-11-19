-keep class com.medireport.portal.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
# JavaMail / activation use reflection and dynamic class loading
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.activation.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn javax.naming.**
-dontwarn org.apache.harmony.**
-dontwarn com.sun.mail.**
-dontwarn javax.mail.**
-dontwarn javax.activation.**
# NanoHTTPD
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# ViewModels (also created through the lifecycle library)
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
# stack traces of the on-screen crash report stay readable
-keepattributes SourceFile,LineNumberTable
