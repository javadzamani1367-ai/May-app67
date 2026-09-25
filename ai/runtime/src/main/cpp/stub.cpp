// Unsupported ABI: every entry point reports failure.
#include <jni.h>

extern "C" {
JNIEXPORT jboolean JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeInit(JNIEnv *, jobject, jstring) { return JNI_FALSE; }
JNIEXPORT jstring JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeLastError(JNIEnv * env, jobject) { return env->NewStringUTF("unsupported device"); }
JNIEXPORT jlong JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeLoad(JNIEnv *, jobject, jstring, jint, jint, jint) { return 0; }
JNIEXPORT void JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeFree(JNIEnv *, jobject, jlong) {}
JNIEXPORT jint JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeCountTokens(JNIEnv *, jobject, jlong, jstring) { return 0; }
JNIEXPORT void JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeCancel(JNIEnv *, jobject, jlong) {}
JNIEXPORT jint JNICALL Java_ir_roozban_ai_runtime_LlamaNative_nativeGenerate(JNIEnv *, jobject, jlong, jstring, jstring, jfloat, jfloat, jfloat, jint, jint, jobject) { return -1; }
}
