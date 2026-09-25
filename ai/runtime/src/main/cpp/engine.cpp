#include "engine.h"

#include <algorithm>
#include <chrono>
#include <cmath>

#include "ggml-backend.h"
#include "llama.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "RoozbanLlm", __VA_ARGS__)
#else
#include <cstdio>
#define LOGW(...) (std::fprintf(stderr, __VA_ARGS__), std::fprintf(stderr, "\n"))
#endif

namespace roozban {
namespace {

bool g_initialized = false;

void log_callback(ggml_log_level level, const char * text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) LOGW("%s", text);
}

double now_ms() {
    using namespace std::chrono;
    return duration<double, std::milli>(steady_clock::now().time_since_epoch()).count();
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

/**
 * Samples one token. The grammar is checked on the chosen token only and applied to the whole
 * vocabulary just when that token breaks it: with ~150k tokens, filtering everything at every
 * step is what made answers take minutes on phones.
 */
llama_token sample(llama_context * ctx, int n_vocab, llama_sampler * chain, llama_sampler * grammar, std::vector<llama_token_data> & buf) {
    const float * logits = llama_get_logits_ith(ctx, -1);
    auto fill = [&]() {
        buf.resize(n_vocab);
        for (int t = 0; t < n_vocab; t++) buf[t] = llama_token_data{t, logits[t], 0.0f};
    };
    fill();
    llama_token_data_array cur = {buf.data(), buf.size(), -1, false};
    llama_sampler_apply(chain, &cur);
    llama_token id = cur.data[cur.selected].id;
    if (grammar == nullptr) return id;

    llama_token_data single = {id, 1.0f, 0.0f};
    llama_token_data_array one = {&single, 1, -1, false};
    llama_sampler_apply(grammar, &one);
    if (!std::isinf(single.logit)) return id;

    fill();
    cur = {buf.data(), buf.size(), -1, false};
    llama_sampler_apply(grammar, &cur);
    llama_sampler_apply(chain, &cur);
    return cur.data[cur.selected].id;
}

}  // namespace

bool init(const char * lib_dir) {
    if (g_initialized) return true;
    llama_log_set(log_callback, nullptr);
    if (lib_dir != nullptr && lib_dir[0] != 0) ggml_backend_load_all_from_path(lib_dir);
    else ggml_backend_load_all();
    llama_backend_init();
    g_initialized = ggml_backend_reg_count() > 0;
    return g_initialized;
}

Engine * load(const std::string & path, int n_ctx, int n_threads, int n_batch, std::string & error) {
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model * model = llama_model_load_from_file(path.c_str(), mp);
    if (model == nullptr) {
        error = "could not load model";
        return nullptr;
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
        error = "could not create context (not enough memory?)";
        return nullptr;
    }
    auto * e = new Engine();
    e->model = model;
    e->ctx = ctx;
    e->vocab = llama_model_get_vocab(model);
    e->n_batch = n_batch;
    return e;
}

void free(Engine * e) {
    if (e == nullptr) return;
    llama_free(e->ctx);
    llama_model_free(e->model);
    delete e;
}

int count_tokens(Engine * e, const std::string & text) { return (int) tokenize(e->vocab, text).size(); }

int generate(
    Engine * e, const std::string & prompt_text, const std::string & grammar_text, const GenParams & params,
    const std::function<void(int)> & on_progress, const std::function<bool(const char *, size_t)> & on_piece,
    std::string & error, GenStats * stats) {
    e->cancel.store(false);
    std::vector<llama_token> prompt = tokenize(e->vocab, prompt_text);
    const int n_ctx = (int) llama_n_ctx(e->ctx);
    if (prompt.empty()) {
        error = "empty prompt";
        return -1;
    }
    if ((int) prompt.size() >= n_ctx - 8) {
        error = "prompt too long";
        return -1;
    }

    // Reuse the cached prefix; always re-decode at least the last prompt token for fresh logits.
    size_t common = 0;
    while (common < e->cached.size() && common < prompt.size() && e->cached[common] == prompt[common]) common++;
    if (common == prompt.size()) common--;
    llama_memory_t mem = llama_get_memory(e->ctx);
    if (!llama_memory_seq_rm(mem, 0, (llama_pos) common, -1)) {
        llama_memory_clear(mem, true);
        common = 0;
    }
    e->cached.resize(common);

    double t0 = now_ms();
    const int todo = (int) (prompt.size() - common);
    for (int i = 0; i < todo; i += e->n_batch) {
        int n = std::min(e->n_batch, todo - i);
        if (llama_decode(e->ctx, llama_batch_get_one(prompt.data() + common + i, n)) != 0 || e->cancel.load()) {
            llama_memory_clear(mem, true);
            e->cached.clear();
            if (e->cancel.load()) return 0;
            error = "decode failed";
            return -1;
        }
        if (on_progress) on_progress((int) (100L * (i + n) / todo));
    }
    e->cached.assign(prompt.begin(), prompt.end());
    double t1 = now_ms();

    llama_sampler * chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (params.temperature <= 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(chain, llama_sampler_init_top_p(params.top_p, 1));
        llama_sampler_chain_add(chain, llama_sampler_init_min_p(params.min_p, 1));
        llama_sampler_chain_add(chain, llama_sampler_init_temp(params.temperature));
        llama_sampler_chain_add(chain, llama_sampler_init_dist(params.seed));
    }
    llama_sampler * grammar = nullptr;
    if (!grammar_text.empty()) {
        grammar = llama_sampler_init_grammar(e->vocab, grammar_text.c_str(), "root");
        if (grammar == nullptr) {
            llama_sampler_free(chain);
            error = "bad grammar";
            return -1;
        }
    }

    const int n_vocab = llama_vocab_n_tokens(e->vocab);
    std::vector<llama_token_data> buf;
    std::string pending;
    char piece[256];
    int produced = 0;
    while (produced < params.max_tokens && (int) e->cached.size() < n_ctx - 1 && !e->cancel.load()) {
        llama_token tok = sample(e->ctx, n_vocab, chain, grammar, buf);
        if (grammar != nullptr) llama_sampler_accept(grammar, tok);
        llama_sampler_accept(chain, tok);
        if (llama_vocab_is_eog(e->vocab, tok)) break;
        int n = llama_token_to_piece(e->vocab, tok, piece, sizeof(piece), 0, false);
        if (n > 0) pending.append(piece, n);
        produced++;
        size_t ready = complete_utf8(pending);
        if (ready > 0) {
            bool go_on = on_piece(pending.data(), ready);
            pending.erase(0, ready);
            if (!go_on) break;
        }
        if (llama_decode(e->ctx, llama_batch_get_one(&tok, 1)) != 0) {
            LOGW("decode failed after %d tokens", produced);
            break;
        }
        e->cached.push_back(tok);
    }
    if (grammar != nullptr) llama_sampler_free(grammar);
    llama_sampler_free(chain);
    if (stats != nullptr) {
        stats->prompt_tokens = (int) prompt.size();
        stats->reused_tokens = (int) common;
        stats->generated = produced;
        stats->prompt_ms = t1 - t0;
        stats->generate_ms = now_ms() - t1;
    }
    return produced;
}

}  // namespace roozban
