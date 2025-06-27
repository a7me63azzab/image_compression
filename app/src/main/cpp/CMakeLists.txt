cmake_minimum_required(VERSION 3.10)
project(imageresizer)

add_library(imageresizer SHARED
        image_resizer.c    # <-- your JNI glue + all STB_IMPLEMENTATION includes
)

find_library(log-lib log)

target_include_directories(imageresizer PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(imageresizer PRIVATE ${log-lib})
