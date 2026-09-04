# NanoHTTPD reflects on nothing, but keep its public surface for the sync server.
-keep class fi.iki.elonen.** { *; }

# SQLCipher loads its native bridge by name.
-keep class net.zetetic.database.** { *; }

# Room generated implementations.
-keep class ir.ilam.inspection.data.db.** { *; }

# ZXing capture activity is referenced from a manifest entry only.
-keep class com.journeyapps.barcodescanner.** { *; }
