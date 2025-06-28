cmake_minimum_required(VERSION 3.10)
project(imageresizer)

add_library(imageresizer SHARED
        image_resizer.c    # <-- your JNI glue + all STB_IMPLEMENTATION includes
)

find_library(log-lib log)

target_compile_options(imageresizer PUBLIC
        -O3                # optimize aggressively
        -funroll-loops     # unroll loops
        -ffast-math        # allow faster math approximations
        -march=armv8-a+simd  # enable ARM NEON on arm64
)
target_include_directories(imageresizer PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(imageresizer PRIVATE ${log-lib})
