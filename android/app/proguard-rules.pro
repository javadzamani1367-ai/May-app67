# NanoHTTPD reflects on nothing, but keep its public surface for the sync server.
-keep class fi.iki.elonen.** { *; }

# SQLCipher loads its native bridge by name.
-keep class net.zetetic.database.** { *; }

# Room generated implementations.
-keep class ir.ilam.inspection.data.db.** { *; }

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

# Keep line numbers so a crash report names a line and not just a class.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
