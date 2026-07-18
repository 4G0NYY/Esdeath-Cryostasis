// Command esdeath-installer is the setup program for Esdeath: Cryostasis. It installs the
// desktop launcher as a regular Windows application — into the user's programs folder, with
// Start Menu and Desktop shortcuts and an Add/Remove Programs entry — and the launcher then
// installs and updates the mod itself. It is the single thing a player needs to download.
//
// The install is idempotent: re-running it fetches the newest launcher and refreshes the
// shortcuts and registry entry over the top of whatever is already there, so the same binary
// both installs and updates. Run with --uninstall (which is also the Add/Remove Programs
// button) to remove everything it created.
//
// It downloads the launcher from the latest GitHub release rather than carrying it, so the
// setup binary stays small and a re-run always lands the current launcher. The download and
// release lookup live in internal/engine, shared with the launcher and the old flow.
package main

import (
	"flag"
	"fmt"
	"os"
)

func main() {
	setupConsole()

	assumeYes := flag.Bool("y", false, "answer yes to every prompt, for unattended runs")
	uninstall := flag.Bool("uninstall", false, "remove Esdeath: Cryostasis instead of installing it")
	noShortcut := flag.Bool("no-desktop-shortcut", false, "do not create a Desktop shortcut")
	noLaunch := flag.Bool("no-launch", false, "do not start the launcher after installing")
	flag.Parse()

	banner()

	if *uninstall {
		if err := runUninstall(*assumeYes); err != nil {
			fail(err)
		}
		// Deliberately no pause: the install folder is removed by a detached command that only
		// succeeds once this process exits and releases its own exe, so setup must not linger.
		return
	}

	if err := runInstall(installOptions{
		assumeYes:       *assumeYes,
		desktopShortcut: !*noShortcut,
		launch:          !*noLaunch,
	}); err != nil {
		fail(err)
	}
	pause()
}

// fail prints the error in the installer's style, keeps the window open when double-clicked, and
// exits non-zero.
func fail(err error) {
	fmt.Printf("\n   %sFailed:%s %v\n", red, reset, err)
	pause()
	os.Exit(1)
}

// installOptions carries the choices the two front doors (flags now, a GUI later) feed into the
// install. Grouped into a struct so adding a knob does not reshape every call site.
type installOptions struct {
	assumeYes       bool
	desktopShortcut bool
	launch          bool
}
