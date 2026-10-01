# NanoHTTPD reflects on nothing, but keep its public surface for the sync server.
-keep class fi.iki.elonen.** { *; }

# SQLCipher loads its native bridge by name.
-keep class net.zetetic.database.** { *; }

# Room generated implementations.
-keep class ir.ilam.inspection.data.db.** { *; }

# Keep line numbers so a crash report names a line and not just a class.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
