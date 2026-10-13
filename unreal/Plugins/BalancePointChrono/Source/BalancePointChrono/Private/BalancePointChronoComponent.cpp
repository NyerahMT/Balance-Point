#include "BalancePointChronoComponent.h"
#include "ChronoBike.h"
#include "GameFramework/Actor.h"

UBalancePointChronoComponent::UBalancePointChronoComponent()
{
    PrimaryComponentTick.bCanEverTick = true;
}

void UBalancePointChronoComponent::BeginPlay()
{
    Super::BeginPlay();
    Bike = new bp::ChronoBike(bp::BikeParams::CRF450R2026());
    Bike->Build();
    Bike->Reset(0.0);
}

void UBalancePointChronoComponent::EndPlay(const EEndPlayReason::Type EndPlayReason)
{
    delete Bike;
    Bike = nullptr;
    Super::EndPlay(EndPlayReason);
}

void UBalancePointChronoComponent::ResetBike(float ForwardSpeedMps)
{
    if (Bike)
        Bike->Reset(ForwardSpeedMps);
}

void UBalancePointChronoComponent::TickComponent(float DeltaTime, ELevelTick TickType, FActorComponentTickFunction* ThisTickFunction)
{
    Super::TickComponent(DeltaTime, TickType, ThisTickFunction);
    if (!Bike || DeltaTime <= 0.0f)
        return;

    bp::BikeInput Input;
    Input.throttle = FMath::Clamp(Throttle, 0.0f, 1.0f);
    Input.frontBrake = FMath::Clamp(FrontBrake, 0.0f, 1.0f);
    Input.rearBrake = FMath::Clamp(RearBrake, 0.0f, 1.0f);
    const float turnRight = FMath::Clamp(Turn, -1.0f, 1.0f);
    // Unreal Y is right, Chrono Y is left; invert the player turn axis.
    Input.useLeanControl = UseLeanControl;
    Input.turnIntent = -turnRight * FMath::Clamp(LeanGain, 0.0f, 1.0f);
    Input.maxSteeringTorqueNm = FMath::Clamp(MaxHandlebarTorqueNm, 0.0f, 35.0f);
    Input.steerTorqueNm = -turnRight * Input.maxSteeringTorqueNm;
    Bike->SetInput(Input);
    Bike->Step(FMath::Min(DeltaTime, 0.033333f));

    const bp::BikeState S = Bike->State();
    SpeedMps = static_cast<float>(S.speedForward);
    SteerAngleDeg = FMath::RadiansToDegrees(static_cast<float>(S.steerAngle));
    BikeLeanDeg = FMath::RadiansToDegrees(static_cast<float>(S.roll));
    TargetLeanDeg = FMath::RadiansToDegrees(static_cast<float>(S.targetLeanAngle));
    AppliedSteeringTorqueNm = static_cast<float>(S.appliedSteeringTorqueNm);
    FrontNormalLoadN = static_cast<float>(S.frontNormalLoad);
    RearNormalLoadN = static_cast<float>(S.rearNormalLoad);

    // Chrono: X forward, Y left, Z up, metres. Unreal: X forward, Y right, Z up, cm.
    if (AActor* Owner = GetOwner()) {
        Owner->SetActorLocation(FVector(S.x * 100.0, -S.y * 100.0, S.z * 100.0));
        Owner->SetActorRotation(FRotator(
            FMath::RadiansToDegrees(static_cast<float>(S.pitch)),
            -FMath::RadiansToDegrees(static_cast<float>(S.yaw)),
            -FMath::RadiansToDegrees(static_cast<float>(S.roll))));
    }
}
