//go:build !windows && !darwin

package main

// openOfficialLauncher has no reliable way to start the launcher on Linux and the BSDs: there
// is no standard binary name across distros and packaging, so it leaves that to the user, who
// the UI already tells to open the launcher and pick the profile.
func openOfficialLauncher() bool { return false }
