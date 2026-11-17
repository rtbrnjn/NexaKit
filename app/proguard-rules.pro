# PDFBox-Android & FontBox
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.xml.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }

# ExifInterface static TAG_* fields used via reflection for EXIF stripping
-keepclassmembers class androidx.exifinterface.media.ExifInterface {
    public static final java.lang.String TAG_*;
}

# EncryptedSharedPreferences / Tink
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# Room Database & Entities
-keep class com.mediaforge.studio.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
