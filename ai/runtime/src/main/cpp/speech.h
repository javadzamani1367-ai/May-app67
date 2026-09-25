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

/** 16 kHz mono float samples in [-1, 1] → text. [language] e.g. "fa". Returns false on error. */
bool transcribe(Speech * s, const std::vector<float> & samples, const std::string & language, const std::string & prompt,
    int n_threads, std::string & text, std::string & error, SpeechStats * stats = nullptr);

}  // namespace roozban
