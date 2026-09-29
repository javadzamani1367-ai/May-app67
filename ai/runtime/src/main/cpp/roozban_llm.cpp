// JNI bridge to the engine (engine.cpp). One model per process (the `:ai` service process);
// calls are serialized by the Kotlin side.

#include <jni.h>

#include <string>

#include "speech.h"
#ifdef ROOZBAN_SPEECH_ONLY
#include "ggml-backend.h"
#else
#include "engine.h"
#endif

namespace {

std::string g_error;

std::string to_string(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    const char * chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

#ifndef ROOZBAN_SPEECH_ONLY
roozban::Engine * engine(jlong handle) { return reinterpret_cast<roozban::Engine *>(handle); }
#endif

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeLastError(JNIEnv * env, jobject) {
    return env->NewStringUTF(g_error.c_str());
}

#ifdef ROOZBAN_SPEECH_ONLY
// Speech only: the language model is not built; its entry points report failure.
JNIEXPORT jboolean JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeInit(JNIEnv * env, jobject, jstring lib_dir) {
    static bool initialized = false;
    if (initialized) return JNI_TRUE;
    std::string dir = to_string(env, lib_dir);
    if (!dir.empty()) ggml_backend_load_all_from_path(dir.c_str());
    else ggml_backend_load_all();
    initialized = ggml_backend_reg_count() > 0;
    return initialized ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeLoad(JNIEnv *, jobject, jstring, jint, jint, jint) {
    g_error = "language model not included";
    return 0;
}
JNIEXPORT void JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeFree(JNIEnv *, jobject, jlong) {}
JNIEXPORT jint JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeCountTokens(JNIEnv *, jobject, jlong, jstring) { return 0; }
JNIEXPORT void JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeCancel(JNIEnv *, jobject, jlong) {}
JNIEXPORT jint JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeWarmUp(JNIEnv *, jobject, jlong, jstring, jstring, jobject) { return -1; }
JNIEXPORT jint JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeGenerate(JNIEnv *, jobject, jlong, jstring, jstring, jfloat, jfloat, jfloat, jint, jint, jobject) { return -1; }

#else
JNIEXPORT jboolean JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeInit(JNIEnv * env, jobject, jstring lib_dir) {
    std::string dir = to_string(env, lib_dir);
    return roozban::init(dir.c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeLoad(JNIEnv * env, jobject, jstring path, jint n_ctx, jint n_threads, jint n_batch) {
    return reinterpret_cast<jlong>(roozban::load(to_string(env, path), n_ctx, n_threads, n_batch, g_error));
}

JNIEXPORT void JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    roozban::free(engine(handle));
}

JNIEXPORT jint JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeCountTokens(JNIEnv * env, jobject, jlong handle, jstring text) {
    return roozban::count_tokens(engine(handle), to_string(env, text));
}

JNIEXPORT void JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeCancel(JNIEnv *, jobject, jlong handle) {
    if (handle != 0) engine(handle)->cancel.store(true);
}

/** Returns 1 restored from [cache_path], 0 computed, -1 on error. sink.onProgress(int) gets 0..100. */
JNIEXPORT jint JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeWarmUp(JNIEnv * env, jobject, jlong handle, jstring prefix, jstring cache_path, jobject sink) {
    jclass cls = env->GetObjectClass(sink);
    jmethodID on_progress = env->GetMethodID(cls, "onProgress", "(I)V");
    return roozban::warm_up(
        engine(handle), to_string(env, prefix), to_string(env, cache_path),
        [&](int percent) { env->CallVoidMethod(sink, on_progress, (jint) percent); }, g_error);
}

/** sink: onPiece(byte[]): boolean, onProgress(int). Returns generated tokens or -1. */
JNIEXPORT jint JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeGenerate(
        JNIEnv * env, jobject, jlong handle, jstring prompt, jstring grammar,
        jfloat temperature, jfloat top_p, jfloat min_p, jint seed, jint max_tokens, jobject sink) {
    jclass cls = env->GetObjectClass(sink);
    jmethodID on_piece = env->GetMethodID(cls, "onPiece", "([B)Z");
    jmethodID on_progress = env->GetMethodID(cls, "onProgress", "(I)V");
    roozban::GenParams p;
    p.temperature = temperature;
    p.top_p = top_p;
    p.min_p = min_p;
    p.seed = (uint32_t) seed;
    p.max_tokens = max_tokens;
    return roozban::generate(
        engine(handle), to_string(env, prompt), to_string(env, grammar), p,
        [&](int percent) { env->CallVoidMethod(sink, on_progress, (jint) percent); },
        [&](const char * data, size_t size) {
            jbyteArray bytes = env->NewByteArray((jsize) size);
            env->SetByteArrayRegion(bytes, 0, (jsize) size, reinterpret_cast<const jbyte *>(data));
            bool go_on = env->CallBooleanMethod(sink, on_piece, bytes) == JNI_TRUE;
            env->DeleteLocalRef(bytes);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return false;
            }
            return go_on;
        },
        g_error);
}

#endif  // ROOZBAN_SPEECH_ONLY

JNIEXPORT jlong JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeSpeechLoad(JNIEnv * env, jobject, jstring path) {
    return reinterpret_cast<jlong>(roozban::speech_load(to_string(env, path), g_error));
}

JNIEXPORT void JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeSpeechFree(JNIEnv *, jobject, jlong handle) {
    roozban::speech_free(reinterpret_cast<roozban::Speech *>(handle));
}

/** PCM16 samples at 16 kHz → text, or null on error (see nativeLastError). */
JNIEXPORT jstring JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeTranscribe(JNIEnv * env, jobject, jlong handle, jshortArray pcm, jstring language, jstring prompt, jint n_threads, jint beam) {
    const jsize n = env->GetArrayLength(pcm);
    std::vector<float> samples(n);
    jshort * data = env->GetShortArrayElements(pcm, nullptr);
    for (jsize i = 0; i < n; i++) samples[i] = data[i] / 32768.0f;
    env->ReleaseShortArrayElements(pcm, data, JNI_ABORT);
    std::string text;
    roozban::SpeechOptions o;
    o.language = to_string(env, language);
    o.prompt = to_string(env, prompt);
    o.threads = n_threads;
    o.beam = beam;
    if (!roozban::transcribe(reinterpret_cast<roozban::Speech *>(handle), samples, o, text, g_error)) {
        return nullptr;
    }
    return env->NewStringUTF(text.c_str());
}

}  // extern "C"
