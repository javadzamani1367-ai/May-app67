// CI benchmark for speech: the app's speech code on the host.
// usage: roozban-speech-bench model.bin threads beam prompt-file|- list-file|file.wav...
// Prints one tab-separated line per file: RESULT <path> <ms> <text>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include "speech.h"
#include "ggml-backend.h"

// Minimal PCM16 mono 16 kHz WAV reader.
static bool read_wav(const std::string & path, std::vector<float> & out) {
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

static std::string read_text(const std::string & path) {
    std::ifstream in(path);
    std::stringstream ss;
    ss << in.rdbuf();
    std::string s = ss.str();
    while (!s.empty() && (s.back() == '\n' || s.back() == '\r')) s.pop_back();
    return s;
}

int main(int argc, char ** argv) {
    if (argc < 6) {
        std::fprintf(stderr, "usage: %s model threads beam prompt-file|- list-file|file.wav...\n", argv[0]);
        return 2;
    }
    ggml_backend_load_all();
    std::string error;
    roozban::Speech * s = roozban::speech_load(argv[1], error);
    if (s == nullptr) {
        std::fprintf(stderr, "load failed: %s\n", error.c_str());
        return 1;
    }
    roozban::SpeechOptions o;
    o.threads = std::atoi(argv[2]);
    o.beam = std::atoi(argv[3]);
    if (std::strcmp(argv[4], "-") != 0) o.prompt = read_text(argv[4]);
    std::vector<std::string> files;
    for (int i = 5; i < argc; i++) {
        std::string a = argv[i];
        if (a.size() > 4 && a.substr(a.size() - 4) == ".txt") {
            std::ifstream in(a);
            for (std::string line; std::getline(in, line);) if (!line.empty()) files.push_back(line);
        } else {
            files.push_back(a);
        }
    }
    for (const auto & f : files) {
        std::vector<float> pcm;
        if (!read_wav(f, pcm)) {
            std::printf("RESULT\t%s\t-1\t(not a PCM16 WAV)\n", f.c_str());
            continue;
        }
        o.language = f.find("-en.") != std::string::npos ? "en" : "fa";
        std::string text;
        roozban::SpeechStats st;
        bool ok = roozban::transcribe(s, pcm, o, text, error, &st);
        for (auto & c : text) if (c == '\t' || c == '\n') c = ' ';
        std::printf("RESULT\t%s\t%.0f\t%s\n", f.c_str(), st.ms, ok ? text.c_str() : ("ERROR " + error).c_str());
        std::fflush(stdout);
    }
    roozban::speech_free(s);
    return 0;
}
