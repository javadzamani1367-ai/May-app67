// CI benchmark: runs the app's own engine code (engine.cpp) on the host with a real model and
// the app's prompts and grammars, printing timings and output.
// usage: roozban-bench model.gguf threads dir-with-NAME.prompt/NAME.gbnf-files...
#include <cstdio>
#include <cstdlib>
#include <dirent.h>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <algorithm>
#include <chrono>
#include <cstdio>

#include "engine.h"

static std::string read(const std::string & path) {
    std::ifstream in(path);
    std::stringstream ss;
    ss << in.rdbuf();
    return ss.str();
}

int main(int argc, char ** argv) {
    if (argc < 4) {
        std::fprintf(stderr, "usage: %s model threads dir\n", argv[0]);
        return 2;
    }
    roozban::init(nullptr);
    std::string error;
    roozban::Engine * e = roozban::load(argv[1], 4096, std::atoi(argv[2]), 256, error);
    if (e == nullptr) {
        std::fprintf(stderr, "load failed: %s\n", error.c_str());
        return 1;
    }
    std::string dir = argv[3];
    std::vector<std::string> names;
    if (DIR * d = opendir(dir.c_str())) {
        while (dirent * ent = readdir(d)) {
            std::string n = ent->d_name;
            if (n.size() > 7 && n.substr(n.size() - 7) == ".prompt") names.push_back(n.substr(0, n.size() - 7));
        }
        closedir(d);
    }
    std::sort(names.begin(), names.end());
    std::string prefix = read(dir + "/prefix.txt");
    if (!prefix.empty()) {
        // As the app does: compute the fixed prefix and save it, then restore it in a fresh engine.
        std::string cache = dir + "/prefix.kv";
        std::remove(cache.c_str());
        auto t0 = std::chrono::steady_clock::now();
        int rc = roozban::warm_up(e, prefix, cache, nullptr, error);
        auto t1 = std::chrono::steady_clock::now();
        std::printf("BENCH warmup computed rc=%d prefix=%d tokens %.0f ms\n", rc, roozban::count_tokens(e, prefix),
            std::chrono::duration<double, std::milli>(t1 - t0).count());
        roozban::free(e);
        e = roozban::load(argv[1], 4096, std::atoi(argv[2]), 256, error);
        t0 = std::chrono::steady_clock::now();
        rc = roozban::warm_up(e, prefix, cache, nullptr, error);
        t1 = std::chrono::steady_clock::now();
        std::FILE * f = std::fopen(cache.c_str(), "rb");
        long size = 0;
        if (f) { std::fseek(f, 0, SEEK_END); size = std::ftell(f); std::fclose(f); }
        std::printf("BENCH warmup restored rc=%d %.0f ms, cache file %.1f MB\n", rc,
            std::chrono::duration<double, std::milli>(t1 - t0).count(), size / 1048576.0);
    }
    for (const auto & name : names) {
        std::string out;
        roozban::GenStats s;
        roozban::GenParams p;
        int n = roozban::generate(e, read(dir + "/" + name + ".prompt"), read(dir + "/" + name + ".gbnf"), p, nullptr,
            [&](const char * d, size_t len) { out.append(d, len); return true; }, error, &s);
        std::printf("BENCH %s prompt=%d reused=%d read=%.0fms gen=%d tokens %.0fms (%.1f tok/s) rc=%d\n    %s\n",
            name.c_str(), s.prompt_tokens, s.reused_tokens, s.prompt_ms, s.generated, s.generate_ms,
            s.generated * 1000.0 / std::max(1.0, s.generate_ms), n, out.c_str());
    }
    roozban::free(e);
    return 0;
}
