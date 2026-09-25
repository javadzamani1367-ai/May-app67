#include "speech.h"

#include <chrono>

#include "whisper.h"

namespace roozban {

Speech * speech_load(const std::string & path, std::string & error) {
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    cp.flash_attn = true;
    whisper_context * ctx = whisper_init_from_file_with_params(path.c_str(), cp);
    if (ctx == nullptr) {
        error = "could not load speech model";
        return nullptr;
    }
    auto * s = new Speech();
    s->ctx = ctx;
    return s;
}

void speech_free(Speech * s) {
    if (s == nullptr) return;
    whisper_free(s->ctx);
    delete s;
}

bool transcribe(Speech * s, const std::vector<float> & samples, const std::string & language, const std::string & prompt,
    int n_threads, std::string & text, std::string & error, SpeechStats * stats) {
    auto t0 = std::chrono::steady_clock::now();
    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = n_threads;
    p.language = language.c_str();
    p.detect_language = false;
    p.translate = false;
    p.no_context = true;
    p.no_timestamps = true;
    p.single_segment = false;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_special = false;
    p.print_timestamps = false;
    p.suppress_blank = true;
    p.initial_prompt = prompt.empty() ? nullptr : prompt.c_str();
    if (whisper_full(s->ctx, p, samples.data(), (int) samples.size()) != 0) {
        error = "transcription failed";
        return false;
    }
    text.clear();
    const int n = whisper_full_n_segments(s->ctx);
    for (int i = 0; i < n; i++) text += whisper_full_get_segment_text(s->ctx, i);
    if (stats != nullptr) {
        stats->ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
        stats->audio_ms = samples.size() / 16.0;
    }
    return true;
}

}  // namespace roozban
