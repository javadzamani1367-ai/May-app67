# Rules for what the shared module reaches through the manifest, reflection or
# service loading. They travel with the module, so every app built on :core
# gets them without repeating them.

# SQLCipher loads its native bridge by name.
-keep class net.zetetic.database.** { *; }

# ZXing capture activity is referenced from a manifest entry only.
-keep class com.journeyapps.barcodescanner.** { *; }

# R8 is the leading suspect for the field crashes: these libraries are reached
# through the manifest, reflection or service loading, which shrinking cannot
# see. Keeping them costs a little size and removes a whole class of failure.
-keep class com.google.zxing.** { *; }
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# CameraX and Play Services hand work to listeners that R8 can otherwise
# consider unreachable.
-keepclassmembers class * implements com.google.android.gms.tasks.OnSuccessListener {
    public void onSuccess(...);
}
-keepclassmembers class * implements com.google.android.gms.tasks.OnFailureListener {
    public void onFailure(...);
}

# The offline map: mapsforge reads its render theme and symbols as resources
# and builds renderers by name; androidsvg draws the symbols.
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**
