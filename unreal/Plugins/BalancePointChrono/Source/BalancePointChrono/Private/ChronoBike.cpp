#include "ChronoBike.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <stdexcept>

#include "chrono/core/ChRotation.h"
#include "chrono/core/ChTypes.h"
#include "chrono/functions/ChFunctionConst.h"
#include "chrono/physics/ChBodyEasy.h"
#include "chrono/physics/ChLinkLock.h"
#include "chrono/physics/ChLinkMotorRotationTorque.h"
#include "chrono/physics/ChLinkTSDA.h"
#include "chrono/physics/ChSystemSMC.h"
#include "chrono/timestepper/ChTimestepperHHT.h"

namespace bp {
namespace {
using chrono::ChBody;
using chrono::ChFrame;
using chrono::ChQuaterniond;
using chrono::ChVector3d;

constexpr double kPi = 3.14159265358979323846;
constexpr double kGravity = 9.80665;

static double Clamp(double v, double lo, double hi) { return std::max(lo, std::min(v, hi)); }

static ChFrame<> RevoluteFrameY(const ChVector3d& p) {
    return ChFrame<>(p, chrono::QuatFromAngleX(-kPi * 0.5));
}

static ChFrame<> SteeringFrame(const ChVector3d& p, double rake) {
    return ChFrame<>(p, chrono::QuatFromAngleY(-rake));
}

struct TireState {
    double alphaRelax = 0.0;
    double kappaRelax = 0.0;
};

struct TireForce {
    double fx = 0.0;
    double fy = 0.0;
    double mz = 0.0;
};

static TireForce PacejkaLike(double fz, double alpha, double kappa, double gamma,
                             double mu, double kAlpha, double kGamma,
                             double relaxLength, double vx, double dt, TireState& state) {
    TireForce out;
    if (fz <= 1.0) {
        state.alphaRelax *= std::max(0.0, 1.0 - dt * 8.0);
        state.kappaRelax *= std::max(0.0, 1.0 - dt * 8.0);
        return out;
    }

    const double speed = std::max(std::abs(vx), 0.05);
    const double decayA = std::exp(-speed * dt / std::max(relaxLength, 0.02));
    const double decayK = std::exp(-speed * dt / 0.30);
    state.alphaRelax = alpha + (state.alphaRelax - alpha) * decayA;
    state.kappaRelax = kappa + (state.kappaRelax - kappa) * decayK;

    const double capacity = std::max(1.0, mu * fz);
    constexpr double C = 1.80;
    constexpr double Kx = 12.0;
    const double B = (kAlpha * fz) / std::max(C * capacity, 1e-9);
    const double fyPure = Clamp(capacity * std::sin(C * std::atan(B * state.alphaRelax))
                                + kGamma * fz * gamma,
                                -capacity, capacity);
    const double fxPure = capacity * std::tanh(Kx * state.kappaRelax);

    const double gxa = std::cos(std::atan(10.0 * state.alphaRelax));
    const double gyk = std::cos(std::atan(8.0 * state.kappaRelax));
    out.fx = fxPure * gxa;
    out.fy = fyPure * gyk;

    const double util = std::sqrt((out.fx * out.fx + out.fy * out.fy) / (capacity * capacity));
    if (util > 1.0) {
        out.fx /= util;
        out.fy /= util;
    }

    const double pneumaticTrail = 0.030 * std::cos(1.5 * std::atan(10.0 * state.alphaRelax));
    out.mz = -out.fy * pneumaticTrail + 0.020 * gamma * fz;
    return out;
}

static ChVector3d HorizontalUnit(const ChVector3d& v) {
    ChVector3d h(v.x(), v.y(), 0.0);
    const double n = h.Length();
    if (n < 1e-8)
        return ChVector3d(1, 0, 0);
    return h / n;
}

} // namespace

BikeParams BikeParams::CRF450R2026() {
    BikeParams p;
    p.rake = 27.3 * kPi / 180.0;
    return p;
}

struct ChronoBike::Impl {
    explicit Impl(BikeParams p) : params(std::move(p)) {}

    BikeParams params;
    BikeInput input;
    bool built = false;

    std::unique_ptr<chrono::ChSystemSMC> system;
    std::shared_ptr<chrono::ChContactMaterialSMC> contactMaterial;
    std::shared_ptr<ChBody> frame;
    std::shared_ptr<ChBody> swingarm;
    std::shared_ptr<ChBody> forkUpper;
    std::shared_ptr<ChBody> forkLower;
    std::shared_ptr<ChBody> frontWheel;
    std::shared_ptr<ChBody> rearWheel;
    std::shared_ptr<chrono::ChLinkMotorRotationTorque> steerMotor;
    std::shared_ptr<chrono::ChFunctionConst> steerTorqueFunction;
    std::shared_ptr<chrono::ChLinkTSDA> frontSpring;
    std::shared_ptr<chrono::ChLinkTSDA> rearSpring;

    unsigned int frontAccumulator = 0;
    unsigned int rearAccumulator = 0;

    std::array<ChVector3d, 6> initialPos;
    std::array<ChQuaterniond, 6> initialRot;
    TireState frontTire;
    TireState rearTire;
    double frontAlpha = 0.0;
    double rearAlpha = 0.0;
    double frontKappa = 0.0;
    double rearKappa = 0.0;

    std::array<std::shared_ptr<ChBody>, 6> Bodies() const {
        return {frame, swingarm, forkUpper, forkLower, frontWheel, rearWheel};
    }

    void Build() {
        if (built)
            return;

        const auto& p = params;
        system = std::make_unique<chrono::ChSystemSMC>();
        system->SetGravitationalAcceleration(ChVector3d(0, 0, -kGravity));
        system->SetCollisionSystemType(chrono::ChCollisionSystem::Type::BULLET);
        system->SetTimestepperType(chrono::ChTimestepper::Type::HHT);
        if (auto hht = std::dynamic_pointer_cast<chrono::ChTimestepperHHT>(system->GetTimestepper())) {
            hht->SetAlpha(-0.25);
            hht->SetMaxIters(12);
            hht->SetAbsTolerances(1e-4, 1e-3);
            hht->SetStepControl(false);
        }

        contactMaterial = chrono::chrono_types::make_shared<chrono::ChContactMaterialSMC>();
        contactMaterial->SetFriction(0.0f); // tangential force is owned by the tire model below
        contactMaterial->SetRestitution(0.0f);
        contactMaterial->SetYoungModulus(5e8f);
        contactMaterial->SetPoissonRatio(0.3f);

        auto ground = chrono::chrono_types::make_shared<chrono::ChBodyEasyBox>(
            2000.0, 100.0, 0.10, 1000.0, false, true, contactMaterial);
        ground->SetFixed(true);
        ground->SetPos(ChVector3d(0, 0, -0.05));
        system->AddBody(ground);

        const double totalMass = p.bikeMass + p.riderMass;
        const double componentMass = p.frontWheelMass + p.rearWheelMass + p.forkMass + p.swingarmMass;
        const double frameMass = std::max(1.0, totalMass - componentMass);
        const double Rr = p.rearWheelRadius;
        const double Rf = p.frontWheelRadius;
        const double L = p.wheelbase;
        const double sr = std::sin(p.rake);
        const double cr = std::cos(p.rake);

        const ChVector3d rearAxle(0.0, 0.0, Rr);
        const ChVector3d frontAxle(L, 0.0, Rf);
        const ChVector3d frameCg(p.cgXFromRear, 0.0, p.cgHeight);
        const ChVector3d swingPivot(p.swingarmPivotX, 0.0, p.swingarmPivotZ);
        const ChVector3d swingCg((swingPivot.x() + rearAxle.x()) * 0.5, 0.0,
                                 (swingPivot.z() + rearAxle.z()) * 0.5);

        const double forkLength = 0.75;
        const double steerHeadZ = frontAxle.z() + forkLength * cr;
        const double steerHeadX = L + p.trail - steerHeadZ * std::tan(p.rake);
        const ChVector3d steerHead(steerHeadX, 0.0, steerHeadZ);
        const ChVector3d forkMid((steerHead.x() + frontAxle.x()) * 0.5, 0.0,
                                 (steerHead.z() + frontAxle.z()) * 0.5);
        const ChVector3d upperCg((steerHead.x() + forkMid.x()) * 0.5, 0.0,
                                 (steerHead.z() + forkMid.z()) * 0.5);
        const ChVector3d lowerCg((forkMid.x() + frontAxle.x()) * 0.5, 0.0,
                                 (forkMid.z() + frontAxle.z()) * 0.5);

        frame = chrono::chrono_types::make_shared<ChBody>();
        frame->SetMass(frameMass);
        frame->SetPos(frameCg);
        frame->SetInertiaXX(ChVector3d(p.frameIxx, p.frameIyy, p.frameIzz));
        frame->SetUseGyroTorque(true);
        system->AddBody(frame);

        swingarm = chrono::chrono_types::make_shared<ChBody>();
        swingarm->SetMass(p.swingarmMass);
        swingarm->SetPos(swingCg);
        swingarm->SetInertiaXX(ChVector3d(0.08, 0.35, 0.08));
        swingarm->SetUseGyroTorque(true);
        system->AddBody(swingarm);

        const double upperMass = p.forkMass * 0.40;
        const double lowerMass = p.forkMass * 0.60;
        forkUpper = chrono::chrono_types::make_shared<ChBody>();
        forkUpper->SetMass(upperMass);
        forkUpper->SetPos(upperCg);
        forkUpper->SetRot(SteeringFrame(upperCg, p.rake).GetRot());
        forkUpper->SetInertiaXX(ChVector3d(0.20, 0.20, 0.02));
        forkUpper->SetUseGyroTorque(true);
        system->AddBody(forkUpper);

        forkLower = chrono::chrono_types::make_shared<ChBody>();
        forkLower->SetMass(lowerMass);
        forkLower->SetPos(lowerCg);
        forkLower->SetRot(SteeringFrame(lowerCg, p.rake).GetRot());
        forkLower->SetInertiaXX(ChVector3d(0.24, 0.24, 0.02));
        forkLower->SetUseGyroTorque(true);
        system->AddBody(forkLower);

        frontWheel = chrono::chrono_types::make_shared<chrono::ChBodyEasySphere>(
            Rf, 1000.0, false, true, contactMaterial);
        frontWheel->SetMass(p.frontWheelMass);
        frontWheel->SetInertiaXX(ChVector3d(p.frontWheelIyy * 0.5, p.frontWheelIyy, p.frontWheelIyy * 0.5));
        frontWheel->SetPos(frontAxle);
        frontWheel->SetUseGyroTorque(true);
        system->AddBody(frontWheel);

        rearWheel = chrono::chrono_types::make_shared<chrono::ChBodyEasySphere>(
            Rr, 1000.0, false, true, contactMaterial);
        rearWheel->SetMass(p.rearWheelMass);
        rearWheel->SetInertiaXX(ChVector3d(p.rearWheelIyy * 0.5, p.rearWheelIyy, p.rearWheelIyy * 0.5));
        rearWheel->SetPos(rearAxle);
        rearWheel->SetUseGyroTorque(true);
        system->AddBody(rearWheel);

        auto swingJoint = chrono::chrono_types::make_shared<chrono::ChLinkLockRevolute>();
        swingJoint->Initialize(frame, swingarm, RevoluteFrameY(swingPivot));
        system->AddLink(swingJoint);

        auto rearAxleJoint = chrono::chrono_types::make_shared<chrono::ChLinkLockRevolute>();
        rearAxleJoint->Initialize(swingarm, rearWheel, RevoluteFrameY(rearAxle));
        system->AddLink(rearAxleJoint);

        steerMotor = chrono::chrono_types::make_shared<chrono::ChLinkMotorRotationTorque>();
        steerMotor->Initialize(frame, forkUpper, SteeringFrame(steerHead, p.rake));
        steerTorqueFunction = chrono::chrono_types::make_shared<chrono::ChFunctionConst>(0.0);
        steerMotor->SetTorqueFunction(steerTorqueFunction);
        system->AddLink(steerMotor);

        auto forkSlider = chrono::chrono_types::make_shared<chrono::ChLinkLockPrismatic>();
        forkSlider->Initialize(forkUpper, forkLower, SteeringFrame(forkMid, p.rake));
        system->AddLink(forkSlider);

        const ChVector3d forkAxis(-sr, 0.0, cr);
        const double frontArm = 0.05;
        const ChVector3d fsUpper = forkMid + forkAxis * frontArm;
        const ChVector3d fsLower = forkMid - forkAxis * frontArm;
        const double frontDesignLength = 2.0 * frontArm;
        const double frontStaticLoad = totalMass * kGravity * (p.cgXFromRear / p.wheelbase);
        const double frontSpringForce = std::max(0.0, frontStaticLoad - (p.frontWheelMass + lowerMass) * kGravity) / cr;
        const double frontRestLength = frontDesignLength + frontSpringForce / p.frontSpringK;

        frontSpring = chrono::chrono_types::make_shared<chrono::ChLinkTSDA>();
        frontSpring->Initialize(forkUpper, forkLower, false, fsUpper, fsLower);
        frontSpring->SetRestLength(frontRestLength);
        frontSpring->SetSpringCoefficient(p.frontSpringK);
        frontSpring->SetDampingCoefficient(p.frontCompressionC);
        system->AddLink(frontSpring);

        auto frontAxleJoint = chrono::chrono_types::make_shared<chrono::ChLinkLockRevolute>();
        frontAxleJoint->Initialize(forkLower, frontWheel, RevoluteFrameY(frontAxle));
        system->AddLink(frontAxleJoint);

        const double xMid = (swingPivot.x() + rearAxle.x()) * 0.5;
        const double zMid = (swingPivot.z() + rearAxle.z()) * 0.5;
        const double rearDesignLength = 0.18;
        const ChVector3d rsSwing(xMid, 0.0, zMid);
        const ChVector3d rsFrame(xMid, 0.0, zMid + rearDesignLength);
        const double rearStaticLoad = totalMass * kGravity * (1.0 - p.cgXFromRear / p.wheelbase);
        const double armX = std::max(0.05, std::abs(swingPivot.x() - xMid));
        const double rearTorque = std::abs(rearAxle.x() - swingPivot.x()) *
                                  std::max(0.0, rearStaticLoad - p.rearWheelMass * kGravity) -
                                  armX * p.swingarmMass * kGravity;
        const double rearRestLength = rearDesignLength +
                                      std::max(0.0, rearTorque) / (armX * p.rearSpringK);

        rearSpring = chrono::chrono_types::make_shared<chrono::ChLinkTSDA>();
        rearSpring->Initialize(frame, swingarm, false, rsFrame, rsSwing);
        rearSpring->SetRestLength(rearRestLength);
        rearSpring->SetSpringCoefficient(p.rearSpringK);
        rearSpring->SetDampingCoefficient(p.rearCompressionC);
        system->AddLink(rearSpring);

        frontAccumulator = frontWheel->AddAccumulator();
        rearAccumulator = rearWheel->AddAccumulator();

        auto bodies = Bodies();
        for (size_t i = 0; i < bodies.size(); ++i) {
            initialPos[i] = bodies[i]->GetPos();
            initialRot[i] = bodies[i]->GetRot();
        }

        built = true;
        Reset(0.0);
    }

    void Reset(double v0) {
        if (!built)
            throw std::runtime_error("ChronoBike::Build must be called before Reset");
        auto bodies = Bodies();
        for (size_t i = 0; i < bodies.size(); ++i) {
            bodies[i]->SetPos(initialPos[i]);
            bodies[i]->SetRot(initialRot[i]);
            bodies[i]->SetLinVel(ChVector3d(v0, 0, 0));
            bodies[i]->SetAngVelParent(ChVector3d(0, 0, 0));
            bodies[i]->SetLinAcc(ChVector3d(0, 0, 0));
            bodies[i]->SetAngAccParent(ChVector3d(0, 0, 0));
        }
        frontWheel->SetAngVelParent(ChVector3d(0, v0 / params.frontWheelRadius, 0));
        rearWheel->SetAngVelParent(ChVector3d(0, v0 / params.rearWheelRadius, 0));
        system->SetChTime(0.0);
        steerTorqueFunction->SetConstant(0.0);
        frontTire = {};
        rearTire = {};
        frontAlpha = rearAlpha = frontKappa = rearKappa = 0.0;
    }

    void ApplyTire(std::shared_ptr<ChBody> wheel, std::shared_ptr<ChBody> headingBody,
                   bool front, double dt) {
        const auto& p = params;
        const double radius = front ? p.frontWheelRadius : p.rearWheelRadius;
        const double fz = std::max(0.0, wheel->GetContactForce().z());
        if (fz < 1.0)
            return;

        const ChVector3d up(0, 0, 1);
        const ChVector3d heading = HorizontalUnit(headingBody->GetRot().GetAxisX());
        ChVector3d left = Vcross(up, heading);
        const double leftLen = left.Length();
        if (leftLen > 1e-8)
            left /= leftLen;

        const ChVector3d vel = wheel->GetLinVel();
        const double vx = Vdot(vel, heading);
        const double vy = Vdot(vel, left);
        const double alpha = std::atan2(-vy, std::max(std::abs(vx), 0.5));

        const ChVector3d axle = headingBody->GetRot().GetAxisY();
        const double omega = Vdot(wheel->GetAngVelParent(), axle);
        const double kappa = (omega * radius - vx) / std::max(std::abs(vx), 2.0);
        const double gamma = State().roll;

        TireState& ts = front ? frontTire : rearTire;
        TireForce tf = PacejkaLike(fz, alpha, kappa, gamma,
                                   front ? p.frontMu : p.rearMu,
                                   front ? p.frontCorneringPerLoad : p.rearCorneringPerLoad,
                                   front ? p.frontCamberPerLoad : p.rearCamberPerLoad,
                                   front ? p.frontRelaxLength : p.rearRelaxLength,
                                   vx, dt, ts);

        if (front) {
            frontAlpha = alpha;
            frontKappa = kappa;
        } else {
            rearAlpha = alpha;
            rearKappa = kappa;
        }

        const ChVector3d force = heading * tf.fx + left * tf.fy;
        const ChVector3d contactPoint = wheel->GetPos() - up * radius;
        const unsigned int acc = front ? frontAccumulator : rearAccumulator;
        wheel->AccumulateForce(acc, force, contactPoint, false);
        wheel->AccumulateTorque(acc, up * tf.mz, false);
    }

    BikeState State() const {
        BikeState s;
        if (!built)
            return s;
        const auto p = frame->GetPos();
        const auto rot = frame->GetRot();
        const auto ang = rot.GetCardanAnglesXYZ();
        const auto vel = frame->GetLinVel();
        const auto omega = frame->GetAngVelParent();
        const auto forward = HorizontalUnit(rot.GetAxisX());
        const auto bodyY = rot.GetAxisY();
        const auto bodyX = rot.GetAxisX();
        const auto bodyZ = rot.GetAxisZ();
        s.x = p.x(); s.y = p.y(); s.z = p.z();
        s.roll = ang.x(); s.pitch = ang.y(); s.yaw = ang.z();
        s.rollRate = Vdot(omega, bodyX);
        s.pitchRate = Vdot(omega, bodyY);
        s.yawRate = Vdot(omega, bodyZ);
        s.speedForward = Vdot(vel, forward);
        s.steerAngle = steerMotor ? steerMotor->GetMotorAngle() : 0.0;
        s.frontNormalLoad = std::max(0.0, frontWheel->GetContactForce().z());
        s.rearNormalLoad = std::max(0.0, rearWheel->GetContactForce().z());
        s.frontGrounded = s.frontNormalLoad > 5.0;
        s.rearGrounded = s.rearNormalLoad > 5.0;
        s.frontSlipAngle = frontAlpha;
        s.rearSlipAngle = rearAlpha;
        s.frontSlipRatio = frontKappa;
        s.rearSlipRatio = rearKappa;
        return s;
    }

    void Step(double dt) {
        if (!built)
            throw std::runtime_error("ChronoBike::Build must be called before Step");
        if (!(dt > 0.0))
            return;

        const int substeps = std::max(1, static_cast<int>(std::ceil(dt / 0.001)));
        const double h = dt / static_cast<double>(substeps);
        for (int i = 0; i < substeps; ++i) {
            frontWheel->EmptyAccumulator(frontAccumulator);
            rearWheel->EmptyAccumulator(rearAccumulator);

            steerTorqueFunction->SetConstant(Clamp(input.steerTorqueNm, -35.0, 35.0));

            ApplyTire(frontWheel, forkLower, true, h);
            ApplyTire(rearWheel, frame, false, h);

            const auto rearAxle = frame->GetRot().GetAxisY();
            const auto frontAxle = forkLower->GetRot().GetAxisY();
            const double rearOmega = Vdot(rearWheel->GetAngVelParent(), rearAxle);
            const double frontOmega = Vdot(frontWheel->GetAngVelParent(), frontAxle);

            // Deliberately simple for the first Chrono milestone. Gearbox/engine will be
            // ported only after chassis/steering validation; no wheelie catch is applied.
            const double driveTorque = Clamp(input.throttle, 0.0, 1.0) * 340.0;
            const double rearBrake = Clamp(input.rearBrake, 0.0, 1.0) * 820.0;
            const double frontBrake = Clamp(input.frontBrake, 0.0, 1.0) * 900.0;
            const double rearSign = std::abs(rearOmega) > 0.1 ? (rearOmega > 0 ? 1.0 : -1.0) : 1.0;
            const double frontSign = std::abs(frontOmega) > 0.1 ? (frontOmega > 0 ? 1.0 : -1.0) : 1.0;
            rearWheel->AccumulateTorque(rearAccumulator, rearAxle * (driveTorque - rearSign * rearBrake), false);
            frontWheel->AccumulateTorque(frontAccumulator, frontAxle * (-frontSign * frontBrake), false);

            system->DoStepDynamics(h);
        }
    }
};

ChronoBike::ChronoBike(BikeParams params) : impl_(std::make_unique<Impl>(std::move(params))) {}
ChronoBike::~ChronoBike() = default;
ChronoBike::ChronoBike(ChronoBike&&) noexcept = default;
ChronoBike& ChronoBike::operator=(ChronoBike&&) noexcept = default;
void ChronoBike::Build() { impl_->Build(); }
void ChronoBike::Reset(double v) { impl_->Reset(v); }
void ChronoBike::SetInput(const BikeInput& input) { impl_->input = input; }
void ChronoBike::Step(double dt) { impl_->Step(dt); }
BikeState ChronoBike::State() const { return impl_->State(); }
bool ChronoBike::IsBuilt() const { return impl_->built; }

} // namespace bp
