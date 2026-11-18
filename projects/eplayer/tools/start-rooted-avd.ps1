param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$Avd = "EPlayer17KSU",
    [string]$Ramdisk,
    [switch]$Headless
)
$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($SdkRoot)) { throw "Set ANDROID_HOME or supply -SdkRoot." }
$emulator = Join-Path $SdkRoot "emulator/emulator.exe"
if ([string]::IsNullOrWhiteSpace($Ramdisk)) { $Ramdisk = Join-Path $SdkRoot "kernelsu/api37/ramdisk-ksu.img" }
foreach ($required in @($emulator, $Ramdisk)) {
    if (!(Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing: $required" }
}
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_AVD_HOME = Join-Path $SdkRoot "avd"
$arguments = @("-avd", $Avd, "-ramdisk", $Ramdisk, "-no-snapshot", "-feature", "-QuickbootFileBacked", "-memory", "4096")
if ($Headless) { $arguments += @("-no-window", "-no-audio", "-gpu", "swiftshader") }
& $emulator @arguments
exit $LASTEXITCODE
