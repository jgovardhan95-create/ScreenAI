# Add project specific ProGuard rules here.
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod

# Keep Retrofit & Moshi network models
-keep class com.example.network.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# Keep Room entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Strip verbose logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
