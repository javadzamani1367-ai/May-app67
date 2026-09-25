cmake_minimum_required(VERSION 3.22.1)

project(roozban_llm LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# The model is useless unoptimized, so debug builds compile llama.cpp as release too.
set(CMAKE_BUILD_TYPE Release)

if(ANDROID_ABI STREQUAL "armeabi-v7a")
    # 32-bit phones cannot hold these models; a stub keeps the app installable there and
    # the assistant reports itself unsupported.
    add_library(roozban_llm SHARED stub.cpp)
    target_link_libraries(roozban_llm log)
    return()
endif()

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
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
    # x86_64 is only for emulators.
    set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(llama.cpp build-llama)

add_library(roozban_llm SHARED roozban_llm.cpp)
target_link_libraries(roozban_llm llama ggml log)
