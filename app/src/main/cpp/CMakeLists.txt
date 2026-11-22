cmake_minimum_required(VERSION 3.22.1)
project(profynus_mp3 LANGUAGES C CXX)
include(FetchContent)
# Pinned, hash-verified upstream source; downloaded only into the build directory.
FetchContent_Declare(lame_source
    URL https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz
    URL_HASH SHA256=ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e)
FetchContent_GetProperties(lame_source)
if(NOT lame_source_POPULATED)
    FetchContent_Populate(lame_source)
endif()
file(GLOB LAME_SOURCES CONFIGURE_DEPENDS "${lame_source_SOURCE_DIR}/libmp3lame/*.c")
add_library(mp3lame SHARED ${LAME_SOURCES})
target_include_directories(mp3lame PUBLIC "${lame_source_SOURCE_DIR}/include"
    PRIVATE "${lame_source_SOURCE_DIR}/libmp3lame")
target_compile_definitions(mp3lame PRIVATE STDC_HEADERS HAVE_STDINT_H HAVE_LIMITS_H
    HAVE_MEMCPY HAVE_STRCHR HAVE_ERRNO_H HAVE_FCNTL_H NDEBUG
    ieee754_float32_t=float ieee754_float64_t=double)
target_compile_options(mp3lame PRIVATE -O3)
target_link_libraries(mp3lame PRIVATE m)
target_link_options(mp3lame PRIVATE "-Wl,-z,max-page-size=16384")
add_library(profynus_mp3 SHARED mp3_encoder.cpp)
target_compile_features(profynus_mp3 PRIVATE cxx_std_17)
target_compile_options(profynus_mp3 PRIVATE -O3)
target_link_options(profynus_mp3 PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(profynus_mp3 PRIVATE mp3lame)
