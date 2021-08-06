cmake_minimum_required(VERSION 3.4.1)

set(distribution_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../distribution)
#include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../libs)

add_library(libusb SHARED IMPORTED)
set_target_properties(libusb PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/libusb-1.0.24/android/libs/${ANDROID_ABI}/libusb1.0.so)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

add_library(native-lib SHARED native-lib.cpp)
target_include_directories(native-lib  PRIVATE
        ${distribution_DIR}/libusb-1.0.24/libusb)

target_link_libraries(native-lib
        libusb)