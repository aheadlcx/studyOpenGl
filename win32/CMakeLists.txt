cmake_minimum_required(VERSION 3.10)
project(study_opengl_win32 CXX)

file(GLOB CHAPTER_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/src/chapters/ch*.cpp)

add_executable(study_gl
    src/main.cpp
    src/common/glfuncs.cpp
    ${CHAPTER_SOURCES}
)

target_include_directories(study_gl PRIVATE src/common)
target_link_libraries(study_gl opengl32 user32 gdi32)
target_compile_definitions(study_gl PRIVATE NOMINMAX)
if (MSVC)
    target_compile_options(study_gl PRIVATE /W3 /utf-8)
else()
    target_compile_options(study_gl PRIVATE -O2)
endif()
