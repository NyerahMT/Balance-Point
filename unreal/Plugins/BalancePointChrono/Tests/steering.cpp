#include "RideSteeringController.h"

#include <cassert>
#include <cmath>
#include <iostream>

int main() {
    bp::RideSteeringController::Observation state;
    state.frontGrounded = true;
    bp::RideSteeringController controller;

    // At parking speeds the stick turns the handlebar directly.
    for (int i = 0; i < 120; ++i)
        controller.Step(1.0, 16.0, state, 0.001);
    assert(controller.TargetRoll() == 0.0);
    assert(controller.LastTorque() > 1.0);

    // At riding speeds turn intent initially countersteers to establish lean.
    controller.Reset();
    state.speedForward = 9.0;
    for (int i = 0; i < 120; ++i)
        controller.Step(1.0, 16.0, state, 0.001);
    assert(controller.TargetRoll() < -0.4);
    assert(controller.LastTorque() < -1.0);

    // Once leaned left, the steady-state bar demand is into the left turn.
    state.roll = controller.TargetRoll();
    controller.Step(1.0, 16.0, state, 0.001);
    assert(controller.LastTorque() > 0.0);

    // Neutral stick requests upright, not a permanent stored lean.
    for (int i = 0; i < 200; ++i)
        controller.Step(0.0, 16.0, state, 0.001);
    assert(std::abs(controller.TargetRoll()) < 0.08);
    assert(controller.LastTorque() > 0.0);

    // Wheelie/flight: don't artificially torque chassis to meet a lean demand.
    state.frontGrounded = false;
    controller.Step(1.0, 16.0, state, 0.001);
    assert(std::abs(controller.LastTorque()) < 1e-9);

    state.frontGrounded = true;
    controller.Step(1.0, 2.0, state, 0.001);
    assert(std::abs(controller.LastTorque()) <= 2.0);

    controller.Reset();
    assert(controller.TargetRoll() == 0.0);
    assert(controller.LastTorque() == 0.0);

    std::cout << "PASS: low speed, countersteer, steady lean, recovery, flight, limits, reset\n";
    return 0;
}
