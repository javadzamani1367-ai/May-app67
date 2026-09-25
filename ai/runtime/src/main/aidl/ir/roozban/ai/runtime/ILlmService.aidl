package ir.roozban.ai.runtime;

import ir.roozban.ai.runtime.ILlmCallback;

/** The model host running in the `:ai` process. */
interface ILlmService {
    /** False when this device has no usable native engine. */
    boolean available();

    /** Loads a model; returns null on success or an error message. */
    String load(String path, int contextTokens, int threads, int batchTokens);

    int countTokens(String text);

    /** Starts generating on the service's worker thread; results arrive on the callback. */
    oneway void generate(String prompt, String grammar, float temperature, float topP, float minP, int seed, int maxTokens, ILlmCallback callback);

    /** Computes or restores the fixed prompt prefix; onDone(1) when restored from the cache file. */
    oneway void warmUp(String prefix, String cachePath, ILlmCallback callback);

    oneway void cancel();

    void unload();
}
