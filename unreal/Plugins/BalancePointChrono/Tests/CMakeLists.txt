cmake_minimum_required(VERSION 3.18)
project(balancepoint_chrono_smoke LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(Chrono CONFIG REQUIRED)

add_executable(bp_chrono_smoke
    smoke.cpp
    ../Source/BalancePointChrono/Private/ChronoBike.cpp
)
target_include_directories(bp_chrono_smoke PRIVATE
    ../Source/BalancePointChrono/Public
)
target_link_libraries(bp_chrono_smoke PRIVATE ${CHRONO_TARGETS})

# Header-only gameplay control test requires no Chrono runtime.
add_executable(bp_ride_steering_test steering.cpp)
target_include_directories(bp_ride_steering_test PRIVATE
    ../Source/BalancePointChrono/Public
)
enable_testing()
add_test(NAME ride_steering COMMAND bp_ride_steering_test)
add_test(NAME chrono_smoke COMMAND bp_chrono_smoke)
