using UnrealBuildTool;
using System;
using System.IO;

public class BalancePointChrono : ModuleRules
{
    public BalancePointChrono(ReadOnlyTargetRules Target) : base(Target)
    {
        PCHUsage = PCHUsageMode.UseExplicitOrSharedPCHs;
        CppStandard = CppStandardVersion.Cpp17;
        bUseRTTI = true;
        bEnableExceptions = true;

        PublicDependencyModuleNames.AddRange(new string[] { "Core", "CoreUObject", "Engine" });

        string ChronoRoot = Environment.GetEnvironmentVariable("CHRONO_ROOT");
        if (String.IsNullOrEmpty(ChronoRoot))
        {
            string PlatformFolder = Target.Platform == UnrealTargetPlatform.IOS ? "IOS" :
                                    Target.Platform == UnrealTargetPlatform.Mac ? "Mac" :
                                    Target.Platform == UnrealTargetPlatform.Win64 ? "Win64" : "Linux";
            ChronoRoot = Path.GetFullPath(Path.Combine(ModuleDirectory, "..", "..", "ThirdParty", "Chrono", PlatformFolder));
        }

        string IncludeDir = Path.Combine(ChronoRoot, "include");
        string LibDir = Path.Combine(ChronoRoot, "lib");
        if (!Directory.Exists(IncludeDir) || !Directory.Exists(LibDir))
        {
            throw new BuildException("BalancePointChrono: Chrono install not found at " + ChronoRoot + ". Set CHRONO_ROOT or run the Chrono build script.");
        }

        PublicSystemIncludePaths.Add(IncludeDir);
        PublicDefinitions.Add("BP_WITH_CHRONO=1");

        string Pattern = Target.Platform == UnrealTargetPlatform.Win64 ? "*.lib" : "*.a";
        foreach (string Library in Directory.GetFiles(LibDir, Pattern, SearchOption.AllDirectories))
        {
            PublicAdditionalLibraries.Add(Library);
        }
    }
}
