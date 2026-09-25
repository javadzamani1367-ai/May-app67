package ir.roozban.ai.runtime;

/** Streams one generation back to the app process. */
oneway interface ILlmCallback {
    void onProgress(int percent);
    void onPiece(String piece);
    void onDone(int tokens);
    void onError(String message);
}
