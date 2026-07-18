//go:build !windows

package main

import "errors"

// The setup program installs the launcher as a Windows desktop application — into the user's
// programs folder, with Start Menu shortcuts and an Add/Remove Programs entry — none of which
// has a meaning on Linux or macOS, where the launcher binary is run directly. These stubs exist
// so the package still builds on those platforms (CI vets and tests it from a Linux runner);
// they never run in a shipped binary, which is Windows only.

var errWindowsOnly = errors.New("the Esdeath: Cryostasis setup installer is for Windows; on Linux and macOS, run the launcher binary directly")

func runInstall(installOptions) error { return errWindowsOnly }

func runUninstall(bool) error { return errWindowsOnly }
