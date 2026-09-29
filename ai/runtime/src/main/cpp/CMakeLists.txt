cmake_minimum_required(VERSION 3.22.1)

project(roozban_llm LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# The model is useless unoptimized, so debug builds compile llama.cpp as release too.
set(CMAKE_BUILD_TYPE Release)


# whisper.cpp compiled against llama.cpp's ggml: one copy of ggml for both engines.
function(add_whisper_core)
    add_library(whisper_core STATIC whisper.cpp/src/whisper.cpp)
    set_target_properties(whisper_core PROPERTIES POSITION_INDEPENDENT_CODE ON)
    target_include_directories(whisper_core PUBLIC whisper.cpp/include PRIVATE whisper.cpp/src)
    target_compile_definitions(whisper_core PRIVATE WHISPER_VERSION="1.9.4")
    target_link_libraries(whisper_core PUBLIC ggml)
endfunction()

if(NOT ANDROID)
    # Host build for the CI benchmark: the app's engine code against a native llama.cpp.
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
    set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
    add_subdirectory(llama.cpp build-llama)
    add_executable(roozban-bench bench.cpp engine.cpp)
    target_link_libraries(roozban-bench llama ggml)
    add_whisper_core()
    add_executable(roozban-speech-bench speech_bench.cpp speech.cpp)
    target_link_libraries(roozban-speech-bench whisper_core ggml)
    return()
endif()

# The app ships speech recognition only: whisper.cpp on ggml. The llama.cpp language model of
# the earlier assistant is no longer built (the assistant is rule-based); its JNI entry points
# report failure. ggml still comes from the llama.cpp checkout.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    # One CPU backend per ARM feature level, picked at runtime: plain ARMv8.0 phones work,
    # newer ones get dot-product and int8 matmul kernels.
    set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
else()
    # 32-bit phones and x86_64 emulators: one plain CPU backend.
    set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(llama.cpp/ggml build-ggml)

add_whisper_core()
add_library(roozban_llm SHARED roozban_llm.cpp speech.cpp)
target_compile_definitions(roozban_llm PRIVATE ROOZBAN_SPEECH_ONLY)
target_link_libraries(roozban_llm whisper_core ggml log)
