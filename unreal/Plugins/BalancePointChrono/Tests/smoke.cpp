#include "ChronoBike.h"

#include <cmath>
#include <iostream>

static bool finite_state(const bp::BikeState& s) {
    return std::isfinite(s.x) && std::isfinite(s.y) && std::isfinite(s.z) &&
           std::isfinite(s.roll) && std::isfinite(s.pitch) && std::isfinite(s.yaw) &&
           std::isfinite(s.speedForward) && std::isfinite(s.steerAngle);
}

int main() {
    bp::ChronoBike bike(bp::BikeParams::CRF450R2026());
    bike.Build();
    bike.Reset(8.0);

    bp::BikeInput input;
    bike.SetInput(input);
    for (int i = 0; i < 250; ++i)
        bike.Step(0.001);

    auto s = bike.State();
    if (!finite_state(s)) {
        std::cerr << "non-finite state after settle\n";
        return 2;
    }
    if (!s.frontGrounded || !s.rearGrounded) {
        std::cerr << "expected both wheels grounded after settle: front=" << s.frontNormalLoad
                  << " rear=" << s.rearNormalLoad << "\n";
        return 3;
    }

    // This is intentionally a raw handlebar-torque check, not a target-roll controller.
    // A positive command must create a measurable steering response through the actual
    // raked steering joint; gameplay mapping is validated later against turn direction.
    input.steerTorqueNm = 4.0;
    bike.SetInput(input);
    const double steer0 = s.steerAngle;
    for (int i = 0; i < 150; ++i)
        bike.Step(0.001);
    s = bike.State();
    if (!finite_state(s) || std::abs(s.steerAngle - steer0) < 1e-4) {
        std::cerr << "steering joint did not respond to torque\n";
        return 4;
    }

    std::cout << "PASS chrono multibody smoke"
              << " speed=" << s.speedForward
              << " steer=" << s.steerAngle
              << " FzF=" << s.frontNormalLoad
              << " FzR=" << s.rearNormalLoad << "\n";
    return 0;
}
