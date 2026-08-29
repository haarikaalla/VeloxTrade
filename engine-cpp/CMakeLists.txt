cmake_minimum_required(VERSION 3.20)
project(veloxtrade_engine LANGUAGES CXX VERSION 1.0.0)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

find_package(Threads REQUIRED)

add_library(velox_core STATIC
  src/order_book.cpp
  src/engine_service.cpp
  src/http_server.cpp
)
target_include_directories(velox_core PUBLIC include)
target_link_libraries(velox_core PUBLIC Threads::Threads)
if(WIN32)
  target_link_libraries(velox_core PUBLIC ws2_32)
endif()
if(MSVC)
  target_compile_options(velox_core PRIVATE /W4)
else()
  target_compile_options(velox_core PRIVATE -Wall -Wextra -Wpedantic)
endif()

add_executable(veloxtrade-engine src/main.cpp)
target_link_libraries(veloxtrade-engine PRIVATE velox_core)

enable_testing()
add_executable(engine-test tests/engine_test.cpp)
target_link_libraries(engine-test PRIVATE velox_core)
add_test(NAME engine-test COMMAND engine-test)

