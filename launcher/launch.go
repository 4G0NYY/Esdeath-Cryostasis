package main

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
)

// This file holds the launch side of the launcher: opening the official Minecraft launcher
// today, and the interfaces a future direct launch would implement. The profile-based flow
// writes the Esdeath profile (with the backend pinned into its javaArgs) and hands off to the
// official launcher, so no Microsoft login and no game-launch pipeline are needed yet.

// writeFileAtomic replaces a file through a rename, so a crash mid-write cannot leave a
// truncated file behind. Small enough to keep local rather than reach into the engine.
func writeFileAtomic(path string, data []byte) error {
	tmp, err := os.CreateTemp(filepath.Dir(path), ".esdeath-tmp-*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName)

	if _, err := tmp.Write(data); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(tmpName, path)
}

// openPath opens a folder or file in the OS file manager. Best effort: the button that calls
// it is a convenience, so a missing handler is reported to the caller rather than treated as
// fatal to anything.
func openPath(path string) error {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("explorer", path)
	case "darwin":
		cmd = exec.Command("open", path)
	default:
		cmd = exec.Command("xdg-open", path)
	}
	return cmd.Start()
}

// openOfficialLauncher tries to start the Minecraft launcher so the user does not have to find
// it themselves. It is best effort and reports whether it managed it: the profile is already
// written by the time this runs, so the worst case is the user opening the launcher by hand,
// which the UI tells them to do regardless. The implementation is per-OS (see launch_*.go),
// because starting the launcher — Store app, standalone, or distro package — differs enough
// that there is no sensible shared version.

// Account is a linked Microsoft account. It exists now because both seams below traffic in it;
// nothing populates it yet.
type Account struct {
	UUID string `json:"uuid"`
	Name string `json:"name"`
}

// AccountManager is the seam for Microsoft account login. Implementing it needs a registered
// Azure application (the client_id the device-code flow authenticates against), which this
// project does not have yet, so there is no implementation. The profile-based launch does not
// need it: the official Minecraft launcher owns the session today. Kept as an interface so the
// GUI and the Play flow can adopt direct login later without being reshaped around it.
type AccountManager interface {
	// LoginDeviceCode runs the Microsoft device-code flow (Xbox Live, then XSTS, then the
	// Minecraft token) and returns the account once the user approves it in a browser.
	LoginDeviceCode(ctx context.Context) (Account, error)
	Accounts() []Account
	Remove(uuid string) error
}

// DirectLauncher is the seam for launching the game from this process instead of handing off
// to the official launcher: provision a JRE, build the classpath, and spawn java with the
// account's session and -Dcryostasis.api already set. Future work, gated on AccountManager.
type DirectLauncher interface {
	Launch(ctx context.Context, acc Account, backendURL string) error
}
