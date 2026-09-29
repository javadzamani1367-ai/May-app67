// Offline speech recognition on whisper.cpp, sharing llama.cpp's ggml. Free of JNI so the CI
// benchmark runs the same code.
#pragma once

#include <string>
#include <vector>

struct whisper_context;

namespace roozban {

struct Speech {
    whisper_context * ctx = nullptr;
};

Speech * speech_load(const std::string & path, std::string & error);

void speech_free(Speech * s);

struct SpeechStats {
    double ms = 0;
    double audio_ms = 0;
};

struct SpeechOptions {
    std::string language = "fa";
    /** Biases the vocabulary and script; empty for none. */
    std::string prompt;
    int threads = 4;
    /** 1 = greedy; more = beam search (slower, usually more accurate). */
    int beam = 1;
    /**
     * Encode only as much of Whisper's 30 s window as the audio fills: much faster on short
     * pieces, but some fine-tuned models get less accurate (measured by the long-form eval).
     */
    bool fit_audio_ctx = false;
};

/** 16 kHz mono float samples in [-1, 1] → text. Returns false on error. */
bool transcribe(Speech * s, const std::vector<float> & samples, const SpeechOptions & options,
    std::string & text, std::string & error, SpeechStats * stats = nullptr);

}  // namespace roozban
