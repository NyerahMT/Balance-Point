#pragma once

#include <algorithm>
#include <cmath>

namespace bp {

// Gameplay controller only: applies torque to the physical steering joint. It
// never applies roll/yaw forces to the chassis or fabricates tire contact.
class RideSteeringController {
public:
    struct Observation {
        double speedForward = 0.0;    // m/s, bike-forward positive
        double roll = 0.0;            // rad, left lean negative in Chrono XYZ
        double rollRate = 0.0;        // rad/s
        double steerAngle = 0.0;      // rad, positive steers left
        bool frontGrounded = false;
    };

    void Reset() {
        filteredTurn_ = 0.0;
        previousSteer_ = 0.0;
        hasPreviousSteer_ = false;
        targetRoll_ = 0.0;
        lastTorque_ = 0.0;
    }

    // Positive turn input requests a left turn (Chrono: +Y is left).
    double Step(double turn, double torqueLimitNm, const Observation& s, double dt) {
        if (!(dt > 0.0) || !std::isfinite(dt))
            return lastTorque_;

        const double command = std::isfinite(turn) ? Clamp(turn, -1.0, 1.0) : 0.0;
        const double limit = std::isfinite(torqueLimitNm)
                                 ? Clamp(torqueLimitNm, 0.0, 35.0) : 0.0;
        // Fast but non-instant stick release avoids oscillatory turn corrections.
        const double response = std::abs(command) < std::abs(filteredTurn_) ? 0.065 : 0.095;
        filteredTurn_ += (command - filteredTurn_) *
                         (1.0 - std::exp(-dt / response));

        const double speed = std::max(0.0, s.speedForward);
        const double leanBlend = Smoothstep(1.5, 5.5, speed);
        // Lean stays comfortably inside the nominal hardpack traction envelope.
        // The controller requests lean; only the tire/steering joint can produce it.
        targetRoll_ = -0.70 * filteredTurn_ * leanBlend;

        const double steerRate = hasPreviousSteer_
                                     ? Clamp((s.steerAngle - previousSteer_) / dt, -15.0, 15.0)
                                     : 0.0;
        previousSteer_ = s.steerAngle;
        hasPreviousSteer_ = true;

        if (!s.frontGrounded) {
            // No front contact, no authority to command chassis lean. Preserve
            // free-flight dynamics; only damp spinning handlebars.
            lastTorque_ = Clamp(-2.5 * steerRate, -limit, limit);
            return lastTorque_;
        }

        // At walking pace turn the bars into the corner; once moving, briefly
        // steer opposite to the target lean, then steer into the established turn.
        const double slowSteer = 0.30 * filteredTurn_;
        const double speedSq = std::max(16.0, speed * speed);
        const double steadySteer = Clamp(
            -std::atan(1.48082 * 9.80665 * std::tan(s.roll) / speedSq),
            -0.15, 0.15);
        const double countersteer = Clamp(
            0.85 * (targetRoll_ - s.roll) - 0.28 * s.rollRate,
            -0.14, 0.14);
        const double desiredSteer = Clamp(
            slowSteer * (1.0 - leanBlend) +
                (steadySteer + countersteer) * leanBlend,
            -0.40, 0.40);

        // Torque-limited bar servo, not a direct chassis roll/yaw controller.
        lastTorque_ = Clamp(65.0 * (desiredSteer - s.steerAngle) - 3.5 * steerRate,
                            -limit, limit);
        return lastTorque_;
    }

    double TargetRoll() const { return targetRoll_; }
    double LastTorque() const { return lastTorque_; }

private:
    static double Clamp(double x, double lo, double hi) {
        return std::max(lo, std::min(x, hi));
    }
    static double Smoothstep(double lo, double hi, double x) {
        const double t = Clamp((x - lo) / (hi - lo), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    double filteredTurn_ = 0.0;
    double previousSteer_ = 0.0;
    bool hasPreviousSteer_ = false;
    double targetRoll_ = 0.0;
    double lastTorque_ = 0.0;
};

} // namespace bp
