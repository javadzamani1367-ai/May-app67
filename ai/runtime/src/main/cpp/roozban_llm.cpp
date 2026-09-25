// JNI bridge to the engine (engine.cpp). One model per process (the `:ai` service process);
// calls are serialized by the Kotlin side.

#include <jni.h>

#include <string>

#include "engine.h"

namespace {

std::string g_error;

std::string to_string(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    const char * chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

roozban::Engine * engine(jlong handle) { return reinterpret_cast<roozban::Engine *>(handle); }

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeInit(JNIEnv * env, jobject, jstring lib_dir) {
    std::string dir = to_string(env, lib_dir);
    return roozban::init(dir.c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeLastError(JNIEnv * env, jobject) {
    return env->NewStringUTF(g_error.c_str());
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

}  // extern "C"
