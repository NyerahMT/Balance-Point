using UnrealBuildTool;
using System.Collections.Generic;

public class BalancePointTarget : TargetRules
{
    public BalancePointTarget(TargetInfo Target) : base(Target)
    {
        Type = TargetType.Game;
        DefaultBuildSettings = BuildSettingsVersion.Latest;
        IncludeOrderVersion = EngineIncludeOrderVersion.Latest;
        ExtraModuleNames.Add("BalancePoint");
    }
}
