//go:build windows

package main

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"time"
)

// hidden marks a helper command so Windows starts it without flashing a console window. The
// launcher is a GUI app, so the PowerShell probe and the launcher exe it spawns must come up
// silently; without this a black console box blinks on screen every time Play is pressed.
func hidden(cmd *exec.Cmd) *exec.Cmd {
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
	return cmd
}

// standaloneLauncherPaths are the install locations of the standalone (non-Store) Minecraft
// launcher, newest layout first. Present on machines that installed the launcher from
// minecraft.net rather than the Microsoft Store.
func standaloneLauncherPaths() []string {
	return []string{
		filepath.Join(os.Getenv("ProgramFiles(x86)"), "Minecraft Launcher", "MinecraftLauncher.exe"),
		filepath.Join(os.Getenv("ProgramFiles"), "Minecraft Launcher", "MinecraftLauncher.exe"),
		filepath.Join(os.Getenv("ProgramFiles(x86)"), "Minecraft", "MinecraftLauncher.exe"),
	}
}

// openOfficialLauncher starts the modern Minecraft launcher — the one that reads the
// launcher_profiles.json this installer wrote. It tries, in order: the standalone .exe at its
// known install paths, then the Microsoft Store build activated by its app id.
//
// It deliberately never fires a bare "minecraft:" as a last resort. On a machine where nothing
// has registered that protocol (which is the norm for the Store build), doing so pops the
// Windows "there's no app on your PC to open this minecraft link" dialog — exactly the failure
// this replaces. Returning false instead lets the UI tell the user to open the launcher by
// hand, which is the honest outcome when we genuinely could not find it.
func openOfficialLauncher() bool {
	for _, exe := range standaloneLauncherPaths() {
		if _, err := os.Stat(exe); err == nil && hidden(exec.Command(exe)).Start() == nil {
			return true
		}
	}
	if appID := storeLauncherAppID(); appID != "" {
		return launchByAppID(appID)
	}
	return false
}

// launchByAppID activates a Store (or any Start-menu) app through its AppUserModelID.
// explorer.exe is the standard shell entry point for the AppsFolder namespace and resolves the
// id to the app's activation for both packaged and unpackaged apps. explorer draws no console
// of its own, so this needs no hiding.
func launchByAppID(appID string) bool {
	return exec.Command("explorer.exe", `shell:AppsFolder\`+appID).Start() == nil
}

// storeLauncherAppID discovers the AppUserModelID of the installed Minecraft launcher so it can
// be activated by id (the Store build lives under the protected WindowsApps folder and has no
// stable exe path to run directly). It asks the Start menu first, since that matches whatever
// the user actually sees, and falls back to reading the app id straight out of the appx package
// manifest. Empty means no launcher was found, in which case the caller gives up cleanly.
func storeLauncherAppID() string {
	const ps = `$ErrorActionPreference = 'SilentlyContinue'
$app = Get-StartApps | Where-Object { $_.Name -like 'Minecraft Launcher*' } | Select-Object -First 1 -ExpandProperty AppID
if (-not $app) {
  $pkg = Get-AppxPackage -Name 'Microsoft.4297127D64EC6' | Select-Object -First 1
  if ($pkg) {
    $id = (Get-AppxPackageManifest $pkg).Package.Applications.Application | Select-Object -First 1 -ExpandProperty Id
    if ($id) { $app = $pkg.PackageFamilyName + '!' + $id }
  }
}
if ($app) { [Console]::Out.Write($app) }`

	// Bound the probe: PowerShell is normally sub-second here, but Play must not hang forever if
	// the shell stalls, so cap it and treat a timeout as "not found".
	ctx, cancel := context.WithTimeout(context.Background(), 12*time.Second)
	defer cancel()

	out, err := hidden(exec.CommandContext(ctx, "powershell", "-NoProfile", "-NonInteractive", "-Command", ps)).Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(out))
}
