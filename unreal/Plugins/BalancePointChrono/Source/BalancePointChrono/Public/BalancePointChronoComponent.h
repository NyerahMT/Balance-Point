#pragma once

#include "CoreMinimal.h"
#include "Components/ActorComponent.h"
#include "BalancePointChronoComponent.generated.h"

namespace bp { class ChronoBike; }

UCLASS(ClassGroup=(BalancePoint), meta=(BlueprintSpawnableComponent))
class BALANCEPOINTCHRONO_API UBalancePointChronoComponent : public UActorComponent
{
    GENERATED_BODY()

public:
    UBalancePointChronoComponent();

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category="Balance Point|Input", meta=(ClampMin="0.0", ClampMax="1.0"))
    float Throttle = 0.0f;

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category="Balance Point|Input", meta=(ClampMin="-1.0", ClampMax="1.0"))
    float Turn = 0.0f;

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category="Balance Point|Input", meta=(ClampMin="0.0", ClampMax="1.0"))
    float FrontBrake = 0.0f;

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category="Balance Point|Input", meta=(ClampMin="0.0", ClampMax="1.0"))
    float RearBrake = 0.0f;

    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category="Balance Point|Input")
    float MaxHandlebarTorqueNm = 12.0f;

    UPROPERTY(BlueprintReadOnly, Category="Balance Point|Telemetry")
    float SpeedMps = 0.0f;

    UPROPERTY(BlueprintReadOnly, Category="Balance Point|Telemetry")
    float SteerAngleDeg = 0.0f;

    UPROPERTY(BlueprintReadOnly, Category="Balance Point|Telemetry")
    float FrontNormalLoadN = 0.0f;

    UPROPERTY(BlueprintReadOnly, Category="Balance Point|Telemetry")
    float RearNormalLoadN = 0.0f;

    UFUNCTION(BlueprintCallable, Category="Balance Point|Physics")
    void ResetBike(float ForwardSpeedMps = 0.0f);

protected:
    virtual void BeginPlay() override;
    virtual void EndPlay(const EEndPlayReason::Type EndPlayReason) override;
    virtual void TickComponent(float DeltaTime, ELevelTick TickType, FActorComponentTickFunction* ThisTickFunction) override;

private:
    bp::ChronoBike* Bike = nullptr;
};
