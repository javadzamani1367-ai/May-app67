// JNI bridge to llama.cpp for the assistant. One model and one context per process (the `:ai`
// service process); calls are serialized by the Kotlin side.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstring>
#include <string>
#include <vector>

#include "llama.h"
#include "ggml-backend.h"

#define TAG "RoozbanLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int n_batch = 512;
    // Tokens whose keys/values are in the context memory, for prompt-prefix reuse.
    std::vector<llama_token> cached;
    std::atomic<bool> cancel{false};
};

std::string g_error;
bool g_initialized = false;

void log_callback(ggml_log_level level, const char * text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
}

std::string to_string(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    const char * chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text) {
    int n = -llama_tokenize(vocab, text.data(), (int32_t) text.size(), nullptr, 0, true, true);
    std::vector<llama_token> tokens(n > 0 ? n : 0);
    if (n > 0) {
        n = llama_tokenize(vocab, text.data(), (int32_t) text.size(), tokens.data(), (int32_t) tokens.size(), true, true);
        tokens.resize(n > 0 ? n : 0);
    }
    return tokens;
}

// Length of the longest prefix of `bytes` that ends on a UTF-8 character boundary.
size_t complete_utf8(const std::string & bytes) {
    size_t i = bytes.size();
    size_t back = 0;
    while (i > 0 && back < 4) {
        unsigned char c = bytes[i - 1];
        if ((c & 0xC0) != 0x80) {
            size_t need = (c & 0x80) == 0 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 1;
            return (back + 1 >= need) ? bytes.size() : i - 1;
        }
        i--;
        back++;
    }
    return bytes.size();
}

bool decode(Session * s, const llama_token * tokens, int count) {
    for (int i = 0; i < count; i += s->n_batch) {
        int n = std::min(s->n_batch, count - i);
        if (llama_decode(s->ctx, llama_batch_get_one(const_cast<llama_token *>(tokens + i), n)) != 0) return false;
        if (s->cancel.load()) return false;
    }
    return true;
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeInit(JNIEnv * env, jobject, jstring lib_dir) {
    if (g_initialized) return JNI_TRUE;
    llama_log_set(log_callback, nullptr);
    std::string dir = to_string(env, lib_dir);
    if (!dir.empty()) ggml_backend_load_all_from_path(dir.c_str());
    llama_backend_init();
    g_initialized = ggml_backend_reg_count() > 0;
    LOGI("backends: %zu", ggml_backend_reg_count());
    return g_initialized ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeLastError(JNIEnv * env, jobject) {
    return env->NewStringUTF(g_error.c_str());
}

JNIEXPORT jlong JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeLoad(JNIEnv * env, jobject, jstring jpath, jint n_ctx, jint n_threads, jint n_batch) {
    std::string path = to_string(env, jpath);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model * model = llama_model_load_from_file(path.c_str(), mp);
    if (model == nullptr) {
        g_error = "could not load model";
        return 0;
    }
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) n_ctx;
    cp.n_batch = (uint32_t) n_batch;
    cp.n_ubatch = (uint32_t) n_batch;
    cp.n_threads = n_threads;
    cp.n_threads_batch = n_threads;
    cp.no_perf = true;
    llama_context * ctx = llama_init_from_model(model, cp);
    if (ctx == nullptr) {
        llama_model_free(model);
        g_error = "could not create context (not enough memory?)";
        return 0;
    }
    auto * s = new Session();
    s->model = model;
    s->ctx = ctx;
    s->vocab = llama_model_get_vocab(model);
    s->n_batch = n_batch;
    LOGI("loaded %s ctx=%d threads=%d", path.c_str(), n_ctx, n_threads);
    return reinterpret_cast<jlong>(s);
}

JNIEXPORT void JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (s == nullptr) return;
    llama_free(s->ctx);
    llama_model_free(s->model);
    delete s;
}

JNIEXPORT jint JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeCountTokens(JNIEnv * env, jobject, jlong handle, jstring text) {
    auto * s = reinterpret_cast<Session *>(handle);
    return (jint) tokenize(s->vocab, to_string(env, text)).size();
}

JNIEXPORT void JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeCancel(JNIEnv *, jobject, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    if (s != nullptr) s->cancel.store(true);
}

/**
 * Generates up to max_tokens after the prompt, calling sink.onPiece(byte[]) with complete UTF-8
 * pieces; stops when it returns false, on end of generation or on cancel.
 * Returns the number of generated tokens, or -1 on error (see nativeLastError).
 */
JNIEXPORT jint JNICALL
Java_ir_roozban_ai_runtime_LlamaNative_nativeGenerate(
        JNIEnv * env, jobject, jlong handle, jstring jprompt, jstring jgrammar,
        jfloat temperature, jfloat top_p, jfloat min_p, jint seed, jint max_tokens, jobject sink) {
    auto * s = reinterpret_cast<Session *>(handle);
    s->cancel.store(false);
    jclass sink_class = env->GetObjectClass(sink);
    jmethodID on_piece = env->GetMethodID(sink_class, "onPiece", "([B)Z");

    std::vector<llama_token> prompt = tokenize(s->vocab, to_string(env, jprompt));
    const int n_ctx = (int) llama_n_ctx(s->ctx);
    if (prompt.empty()) {
        g_error = "empty prompt";
        return -1;
    }
    if ((int) prompt.size() >= n_ctx - 8) {
        g_error = "prompt too long";
        return -1;
    }

    // Reuse the cached prefix; always re-decode at least the last prompt token for fresh logits.
    size_t common = 0;
    while (common < s->cached.size() && common < prompt.size() && s->cached[common] == prompt[common]) common++;
    if (common == prompt.size()) common--;
    llama_memory_t mem = llama_get_memory(s->ctx);
    if (!llama_memory_seq_rm(mem, 0, (llama_pos) common, -1)) {
        llama_memory_clear(mem, true);
        common = 0;
    }
    s->cached.resize(common);
    if (!decode(s, prompt.data() + common, (int) (prompt.size() - common))) {
        llama_memory_clear(mem, true);
        s->cached.clear();
        g_error = s->cancel.load() ? "cancelled" : "decode failed";
        return s->cancel.load() ? 0 : -1;
    }
    s->cached = prompt;

    llama_sampler * chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    std::string grammar = to_string(env, jgrammar);
    if (!grammar.empty()) {
        llama_sampler * g = llama_sampler_init_grammar(s->vocab, grammar.c_str(), "root");
        if (g == nullptr) {
            llama_sampler_free(chain);
            g_error = "bad grammar";
            return -1;
        }
        llama_sampler_chain_add(chain, g);
    }
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(chain, llama_sampler_init_min_p(min_p, 1));
        llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(chain, llama_sampler_init_dist((uint32_t) seed));
    }

    std::string pending;
    int produced = 0;
    char buf[256];
    bool keep_going = true;
    while (keep_going && produced < max_tokens && (int) s->cached.size() < n_ctx - 1 && !s->cancel.load()) {
        llama_token tok = llama_sampler_sample(chain, s->ctx, -1);
        if (llama_vocab_is_eog(s->vocab, tok)) break;
        int n = llama_token_to_piece(s->vocab, tok, buf, sizeof(buf), 0, false);
        if (n > 0) pending.append(buf, n);
        produced++;
        size_t ready = complete_utf8(pending);
        if (ready > 0) {
            jbyteArray bytes = env->NewByteArray((jsize) ready);
            env->SetByteArrayRegion(bytes, 0, (jsize) ready, reinterpret_cast<const jbyte *>(pending.data()));
            keep_going = env->CallBooleanMethod(sink, on_piece, bytes) == JNI_TRUE;
            env->DeleteLocalRef(bytes);
            if (env->ExceptionCheck()) keep_going = false;
            pending.erase(0, ready);
        }
        if (!keep_going) break;
        if (llama_decode(s->ctx, llama_batch_get_one(&tok, 1)) != 0) {
            LOGW("decode failed after %d tokens", produced);
            s->cached.push_back(tok);
            break;
        }
        s->cached.push_back(tok);
    }
    llama_sampler_free(chain);
    return produced;
}

}  // extern "C"
