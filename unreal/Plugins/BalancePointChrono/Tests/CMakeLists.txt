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
