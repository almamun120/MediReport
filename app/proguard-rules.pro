# Room / Kotlin keep rules are bundled with the libraries.
-keep class com.medireport.portal.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
# android.print helper (package-private access)
-keep class android.print.PdfPrinter { *; }
-dontwarn android.print.**
