# Physics V3 Live Runtime

Physics V3 became Balance Point's live mobile motorcycle runtime in commit
`0e64ccf67eb37e4adc45a69465f5a7b79f7ff2a5`.

`BalancePointGame` owns `PhysicsV3MultibodyPlant` and routes live input,
simulation state, telemetry, drivetrain display, and wheelie detection through it.

The obsolete V2 plant, tire, rider-controller implementation, and their V2-only
tests were removed after confirming they are not part of the live runtime.
They remain recoverable from Git history.

**Important exception:** `PhysicsV2Powertrain` is still used by
`PhysicsV3MultibodyPlant`. Its source and regression test are deliberately
retained until a separate, tested rename/extraction.

The mobile Android/iOS packaging pipeline remains the validation path.
