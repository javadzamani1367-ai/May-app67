// CI benchmark for speech: the app's speech code on the host.
// usage: roozban-speech-bench model.bin threads file.wav [more.wav...]
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

#include "speech.h"
#include "ggml-backend.h"

// Minimal PCM16 mono 16 kHz WAV reader (the samples used in CI).
static bool read_wav(const char * path, std::vector<float> & out) {
    std::ifstream in(path, std::ios::binary);
    std::vector<char> b((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    if (b.size() < 44 || std::memcmp(b.data(), "RIFF", 4) != 0) return false;
    size_t pos = 12;
    while (pos + 8 <= b.size()) {
        uint32_t len;
        std::memcpy(&len, b.data() + pos + 4, 4);
        if (std::memcmp(b.data() + pos, "data", 4) == 0) {
            size_t n = std::min<size_t>(len, b.size() - pos - 8) / 2;
            out.resize(n);
            for (size_t i = 0; i < n; i++) {
                int16_t v;
                std::memcpy(&v, b.data() + pos + 8 + i * 2, 2);
                out[i] = v / 32768.0f;
            }
            return true;
        }
        pos += 8 + len + (len & 1);
    }
    return false;
}

int main(int argc, char ** argv) {
    if (argc < 4) {
        std::fprintf(stderr, "usage: %s model threads wav...\n", argv[0]);
        return 2;
    }
    ggml_backend_load_all();
    std::string error;
    roozban::Speech * s = roozban::speech_load(argv[1], error);
    if (s == nullptr) {
        std::fprintf(stderr, "load failed: %s\n", error.c_str());
        return 1;
    }
    for (int i = 3; i < argc; i++) {
        std::vector<float> pcm;
        if (!read_wav(argv[i], pcm)) {
            std::printf("SPEECH %s: not a PCM16 WAV\n", argv[i]);
            continue;
        }
        std::string lang = std::strstr(argv[i], "en") ? "en" : "fa";
        std::string text;
        roozban::SpeechStats st;
        bool ok = roozban::transcribe(s, pcm, lang, "", std::atoi(argv[2]), text, error, &st);
        std::printf("SPEECH %s audio=%.1fs took=%.1fs ok=%d\n    %s\n", argv[i], st.audio_ms / 1000, st.ms / 1000, ok, ok ? text.c_str() : error.c_str());
    }
    roozban::speech_free(s);
    return 0;
}
