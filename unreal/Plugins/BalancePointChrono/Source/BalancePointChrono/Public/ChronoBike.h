#pragma once

#include <memory>

namespace bp {

struct BikeParams {
    double wheelbase = 1.48082;
    double rake = 0.47655;              // 27.3 deg
    double trail = 0.1143;              // 4.5 in
    double frontWheelRadius = 0.3538;
    double rearWheelRadius = 0.3373;
    double bikeMass = 112.94;
    double riderMass = 85.0;
    double cgXFromRear = 0.7222;
    double cgHeight = 0.826;

    double frameIxx = 32.0;
    double frameIyy = 52.0;
    double frameIzz = 38.0;
    double frontWheelMass = 4.8;
    double rearWheelMass = 6.5;
    double frontWheelIyy = 0.82;
    double rearWheelIyy = 1.05;
    double forkMass = 7.8;
    double swingarmMass = 4.5;
    double swingarmLength = 0.5852;
    double swingarmPivotX = 0.5852;
    double swingarmPivotZ = 0.38;

    double frontTravel = 0.3099;
    double rearTravel = 0.3099;
    double frontSpringK = 10000.0;
    double rearSpringK = 10600.0;
    double frontCompressionC = 1450.0;
    double rearCompressionC = 1650.0;

    double frontMu = 0.95;
    double rearMu = 1.05;
    double frontCorneringPerLoad = 11.0;
    double rearCorneringPerLoad = 12.5;
    double frontCamberPerLoad = 0.72;
    double rearCamberPerLoad = 0.66;
    double frontRelaxLength = 0.20;
    double rearRelaxLength = 0.24;

    static BikeParams CRF450R2026();
};

struct BikeInput {
    double throttle = 0.0;
    double frontBrake = 0.0;
    double rearBrake = 0.0;
    double steerTorqueNm = 0.0;
};

struct BikeState {
    double x = 0.0;
    double y = 0.0;
    double z = 0.0;
    double roll = 0.0;
    double pitch = 0.0;
    double yaw = 0.0;
    double rollRate = 0.0;
    double pitchRate = 0.0;
    double yawRate = 0.0;
    double speedForward = 0.0;
    double steerAngle = 0.0;
    double frontNormalLoad = 0.0;
    double rearNormalLoad = 0.0;
    double frontSlipAngle = 0.0;
    double rearSlipAngle = 0.0;
    double frontSlipRatio = 0.0;
    double rearSlipRatio = 0.0;
    bool frontGrounded = false;
    bool rearGrounded = false;
};

class ChronoBike {
public:
    explicit ChronoBike(BikeParams params = BikeParams::CRF450R2026());
    ~ChronoBike();
    ChronoBike(ChronoBike&&) noexcept;
    ChronoBike& operator=(ChronoBike&&) noexcept;

    ChronoBike(const ChronoBike&) = delete;
    ChronoBike& operator=(const ChronoBike&) = delete;

    void Build();
    void Reset(double forwardSpeedMps = 0.0);
    void SetInput(const BikeInput& input);
    void Step(double dtSeconds);
    BikeState State() const;
    bool IsBuilt() const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace bp
