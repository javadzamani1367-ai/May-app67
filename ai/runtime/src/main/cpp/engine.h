// The assistant's inference loop on top of llama.cpp, free of JNI so the same code runs in the
// app and in the CI benchmark (bench.cpp).
#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

struct llama_model;
struct llama_context;
struct llama_vocab;

namespace roozban {

struct Engine {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int n_batch = 256;
    // Tokens whose keys/values are in the context memory, for prompt-prefix reuse.
    std::vector<int32_t> cached;
    std::atomic<bool> cancel{false};
};

struct GenParams {
    float temperature = 0.0f;
    float top_p = 0.9f;
    float min_p = 0.05f;
    uint32_t seed = 42;
    int max_tokens = 384;
};

struct GenStats {
    int prompt_tokens = 0;
    int reused_tokens = 0;
    int generated = 0;
    double prompt_ms = 0;
    double generate_ms = 0;
};

/** Loads CPU backends (from lib_dir when non-empty) once per process. */
bool init(const char * lib_dir);

Engine * load(const std::string & path, int n_ctx, int n_threads, int n_batch, std::string & error);

void free(Engine * e);

int count_tokens(Engine * e, const std::string & text);

/**
 * Computes the fixed prompt [prefix] so later prompts starting with it only process the rest.
 * With [cache_path], the computed state is saved there and restored on the next load instead of
 * recomputed. Returns 1 when restored, 0 when computed, -1 with [error] set.
 */
int warm_up(Engine * e, const std::string & prefix, const std::string & cache_path,
    const std::function<void(int)> & on_progress, std::string & error);

/**
 * Generates after [prompt]. [on_progress] gets 0..100 while the prompt is read; [on_piece] gets
 * complete UTF-8 pieces and returns false to stop. Returns the number of generated tokens, or -1
 * with [error] set.
 */
int generate(
    Engine * e, const std::string & prompt, const std::string & grammar, const GenParams & params,
    const std::function<void(int)> & on_progress, const std::function<bool(const char *, size_t)> & on_piece,
    std::string & error, GenStats * stats = nullptr);

}  // namespace roozban
