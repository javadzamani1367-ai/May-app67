# JNI calls PieceSink.onPiece by name.
-keep interface ir.roozban.ai.runtime.LlamaNative$PieceSink { *; }
-keep class * implements ir.roozban.ai.runtime.LlamaNative$PieceSink { boolean onPiece(byte[]); void onProgress(int); }
-keepclasseswithmembernames class ir.roozban.ai.runtime.LlamaNative { native <methods>; }
