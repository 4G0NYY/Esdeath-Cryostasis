package main

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
)

// errNoMinecraft is returned when no usable .minecraft directory exists, which for this
// installer is indistinguishable from Minecraft Java Edition not being installed.
var errNoMinecraft = errors.New("minecraft not found")

// defaultMinecraftDir returns the stock .minecraft location for the host OS.
func defaultMinecraftDir() (string, error) {
	switch runtime.GOOS {
	case "windows":
		// APPDATA already points at the roaming profile, which is where the launcher puts
		// .minecraft even when the user profile lives on another drive.
		appData := os.Getenv("APPDATA")
		if appData == "" {
			return "", errors.New("APPDATA is not set, cannot locate .minecraft")
		}
		return filepath.Join(appData, ".minecraft"), nil
	case "darwin":
		home, err := os.UserHomeDir()
		if err != nil {
			return "", err
		}
		return filepath.Join(home, "Library", "Application Support", "minecraft"), nil
	default:
		home, err := os.UserHomeDir()
		if err != nil {
			return "", err
		}
		return filepath.Join(home, ".minecraft"), nil
	}
}

// findMinecraft resolves and validates the game directory. A bare directory is not enough:
// launcher_profiles.json only exists once the launcher has actually run, and without it
// there is no profile list to add ourselves to.
func findMinecraft(override string) (string, error) {
	dir := override
	if dir == "" {
		var err error
		dir, err = defaultMinecraftDir()
		if err != nil {
			return "", err
		}
	}

	stat, err := os.Stat(dir)
	if err != nil || !stat.IsDir() {
		return "", fmt.Errorf("%w: no game directory at %s", errNoMinecraft, dir)
	}
	if _, err := os.Stat(filepath.Join(dir, "launcher_profiles.json")); err != nil {
		return "", fmt.Errorf("%w: %s exists but has no launcher_profiles.json, "+
			"so the launcher has never been run", errNoMinecraft, dir)
	}
	return dir, nil
}
