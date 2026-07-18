//go:build darwin

package main

import "os/exec"

// openOfficialLauncher starts the Minecraft launcher app bundle. `open -a` handles finding it
// in /Applications regardless of the exact install path.
func openOfficialLauncher() bool {
	return exec.Command("open", "-a", "Minecraft").Start() == nil
}
