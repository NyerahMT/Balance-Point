#!/usr/bin/env python3
"""One-shot, deterministic splice of the validated Physics V2 plant into gameplay.

The legacy rendering dimensions remain presentation-only. All motorcycle dynamics come from
PhysicsV2Plant after this migration. The script is intentionally strict/idempotent: it either
finds every expected legacy seam and patches it, or exits without silently producing a partial
migration.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GAME = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/BalancePointGame.java"
DISPLAY = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/InstrumentDisplay.java"
PLANT = ROOT / "core/src/main/java/com/nyerahworks/balancepoint/PhysicsV2Plant.java"
MARKER = "// PHYSICS_V2_RUNTIME"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def patch_plant() -> None:
    text = PLANT.read_text(encoding="utf-8")
    old = (
        "        float clutchCommand = clamp(0.16f + horizontalSpeed / 6.0f, "
        "0.16f, 1f);\n"
        "        float rearDriveTorque = powertrain.step(rearWheelOmega, input.throttle, "
        "clutchCommand, dt);\n"
    )
    new = (
        "        // Automatic launch clutch is a physical plate-force command, not an RPM target.\n"
        "        // With the throttle closed at rest the clutch is open, so idle cannot creep the bike.\n"
        "        // Engagement grows with rider torque request and road speed while transmitted torque\n"
        "        // still comes exclusively from the clutch slip law in PhysicsV2Powertrain.\n"
        "        float clutchCommand;\n"
        "        if (input.throttle < 0.03f && horizontalSpeed < 0.8f) {\n"
        "            clutchCommand = 0f;\n"
        "        } else {\n"
        "            clutchCommand = clamp(0.08f + 0.18f * input.throttle\n"
        "                    + horizontalSpeed / 6.0f, 0f, 1f);\n"
        "        }\n"
        "        float rearDriveTorque = powertrain.step(rearWheelOmega, input.throttle, "
        "clutchCommand, dt);\n"
    )
    if old in text:
        text = replace_once(text, old, new, "plant launch clutch")
        PLANT.write_text(text, encoding="utf-8")
    elif "idle cannot creep the bike" not in text:
        raise SystemExit("plant launch clutch seam not found")


def patch_display() -> None:
    text = DISPLAY.read_text(encoding="utf-8")
    if "void update(float speed, float rpm, float redlineRpm, int gear)" in text:
        return
    old = (
        "    void update(float speed, MotorcycleDrivetrain drivetrain) {\n"
        "        float rpmNorm = MathUtils.clamp(\n"
        "                drivetrain.getRpm() / drivetrain.getRedlineRpm(), 0f, 1f);\n"
    )
    new = (
        "    void update(float speed, MotorcycleDrivetrain drivetrain) {\n"
        "        update(speed, drivetrain.getRpm(), drivetrain.getRedlineRpm(), "
        "drivetrain.getGear());\n"
        "    }\n\n"
        "    void update(float speed, float rpm, float redlineRpm, int gear) {\n"
        "        float rpmNorm = MathUtils.clamp(rpm / Math.max(redlineRpm, 1f), 0f, 1f);\n"
    )
    text = replace_once(text, old, new, "instrument overload")
    text = replace_once(
        text,
        "MathUtils.clamp(drivetrain.getGear(), 0, 9)",
        "MathUtils.clamp(gear, 0, 9)",
        "instrument gear source",
    )
    DISPLAY.write_text(text, encoding="utf-8")


def patch_game() -> None:
    text = GAME.read_text(encoding="utf-8")
    if MARKER in text:
        return

    terrain_field = "    private TerrainVisuals terrainVisuals;\n"
    v2_fields = (
        "    private TerrainVisuals terrainVisuals;\n\n"
        "    // PHYSICS_V2_RUNTIME -- physical dimensions live inside the plant; the old constants above\n"
        "    // remain only because GameScene/DirtBikeVisualRig use the imported GLB's presentation scale.\n"
        "    private final PhysicsV2Plant physicsV2 = new PhysicsV2Plant();\n"
        "    private final PhysicsV2Plant.Terrain physicsV2Terrain = new PhysicsV2Plant.Terrain() {\n"
        "        @Override public float height(float x, float z) {\n"
        "            return terrainVisuals == null ? 0f : terrainVisuals.groundHeight(x, z);\n"
        "        }\n"
        "        @Override public float slopeX(float x, float z) {\n"
        "            return terrainVisuals == null ? 0f : terrainVisuals.groundSlopeX(x, z);\n"
        "        }\n"
        "        @Override public float slopeZ(float x, float z) {\n"
        "            return terrainVisuals == null ? 0f : terrainVisuals.groundSlopeZ(x, z);\n"
        "        }\n"
        "        @Override public float traction(float x, float z) {\n"
        "            return terrainVisuals == null ? 1f : terrainVisuals.tractionCoefficient(x, z);\n"
        "        }\n"
        "    };\n"
    )
    text = replace_once(text, terrain_field, v2_fields, "V2 fields")

    text = replace_once(
        text,
        "drivetrain.shiftUp();",
        "physicsV2.shiftUp();",
        "shift up input",
    )
    text = replace_once(
        text,
        "drivetrain.shiftDown(speed);",
        "physicsV2.shiftDown();",
        "shift down input",
    )

    text = replace_once(
        text,
        "instrumentDisplay.update(speed, drivetrain);",
        (
            "instrumentDisplay.update(speed, physicsV2.powertrain().getRpm(),\n"
            "                    physicsV2.powertrain().getRedlineRpm(), "
            "physicsV2.powertrain().getGear());"
        ),
        "instrument telemetry",
    )

    text = replace_once(
        text,
        "engineAudio.update(drivetrain.getRpm(), throttle, drivetrain.isShifting(), crashed,",
        (
            "engineAudio.update(physicsV2.powertrain().getRpm(), throttle,\n"
            "                physicsV2.powertrain().isShifting(), crashed,"
        ),
        "audio telemetry",
    )

    start = text.find("    private void simulate(float dt) {")
    end = text.find("    private void sampleWheelSurface(", start)
    if start < 0 or end < 0 or end <= start:
        raise SystemExit("simulate replacement seam not found")

    v2_simulate = (
        "    private void simulate(float dt) {\n"
        "        if (crashed) {\n"
        "            simulateCrash(dt);\n"
        "            return;\n"
        "        }\n\n"
        "        float previousSpeed = speed;\n"
        "        PhysicsV2Plant.Input v2Input = physicsV2.input();\n"
        "        v2Input.throttle = throttle;\n"
        "        v2Input.rearBrake = rearBrake;\n"
        "        // Player turn intent is physical steering-torque authority in the V2 plant.\n"
        "        v2Input.steer = steer;\n"
        "        physicsV2.step(physicsV2Terrain, dt);\n\n"
        "        // Dynamic state comes exclusively from the V2 plant. The Y conversion below is only\n"
        "        // an imported-mesh origin adapter: GameScene still expects its historical visual root.\n"
        "        bikeX = physicsV2.x();\n"
        "        bikeZ = physicsV2.z();\n"
        "        chassisY = physicsV2.y() - 0.826f + (WHEEL_RADIUS + COM_HEIGHT);\n"
        "        speed = physicsV2.speed();\n"
        "        verticalVelocity = physicsV2.verticalVelocity();\n"
        "        pitch = physicsV2.pitch();\n"
        "        pitchVelocity = physicsV2.pitchRate();\n"
        "        roll = physicsV2.roll();\n"
        "        rollVelocity = physicsV2.rollRate();\n"
        "        yaw = physicsV2.yaw();\n"
        "        yawVelocity = physicsV2.yawRate();\n"
        "        terrainRoll = 0f;\n"
        "        terrainRollVelocity = 0f;\n"
        "        wheelSpin = physicsV2.rearWheelSpin();\n"
        "        frontGrounded = physicsV2.frontGrounded();\n"
        "        terrainAirborne = physicsV2.airborne();\n"
        "        frontNormalLoad = physicsV2.telemetry().frontFz;\n"
        "        longitudinalAcceleration = (speed - previousSpeed) / Math.max(dt, 0.0001f);\n\n"
        "        // Presentation-only legacy values. They do not feed back into PhysicsV2Plant.\n"
        "        effectiveComForward = COM_FORWARD;\n"
        "        riderLean += (riderLeanTarget - riderLean) * Math.min(1f, dt * 6f);\n"
        "        rearWheelAngularSpeed = wheelSpin / Math.max(dt, 0.0001f);\n"
        "        bikeY = chassisY - COM_HEIGHT;\n\n"
        "        if (physicsV2.rearGrounded() && !frontGrounded && speed > 3f) {\n"
        "            wheelieTime += dt;\n"
        "            bestWheelieTime = Math.max(bestWheelieTime, wheelieTime);\n"
        "        } else if (frontGrounded) {\n"
        "            wheelieTime = 0f;\n"
        "        }\n"
        "    }\n\n"
    )
    text = text[:start] + v2_simulate + text[end:]

    old_reset = (
        "        drivetrain.reset();\n"
        "        suspension.reset();\n"
        "        lateralDynamics.reset();\n"
        "        riderDynamics.reset();\n"
        "        landingDynamics.reset();\n"
    )
    new_reset = old_reset + "        physicsV2.reset(physicsV2Terrain);\n"
    text = replace_once(text, old_reset, new_reset, "V2 reset")

    old_visual_suspension = (
        "state.rearSuspensionTravel = suspension.rear().rideOffset();\n"
        "        state.frontSuspensionTravel = suspension.front().rideOffset();"
    )
    new_visual_suspension = (
        "state.rearSuspensionTravel = physicsV2.rearCompression() - 0.111f;\n"
        "        state.frontSuspensionTravel = physicsV2.frontCompression() - 0.095f;"
    )
    text = replace_once(
        text,
        old_visual_suspension,
        new_visual_suspension,
        "V2 visual suspension",
    )

    text = replace_once(
        text,
        "state.gear = drivetrain.getGear();\n        state.rpm = drivetrain.getRpm();",
        (
            "state.gear = physicsV2.powertrain().getGear();\n"
            "        state.rpm = physicsV2.powertrain().getRpm();"
        ),
        "V2 HUD telemetry",
    )

    GAME.write_text(text, encoding="utf-8")


def main() -> None:
    patch_plant()
    patch_display()
    patch_game()
    print("Physics V2 runtime splice applied successfully")


if __name__ == "__main__":
    main()
