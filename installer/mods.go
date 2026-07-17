package main

import (
	"os"
	"path/filepath"
	"strings"
)

// installMod places the release jar in the mods folder and clears out any older Esdeath
// jar. Sweeping first is what makes a re-run an update: Fabric loads every jar in the
// folder, so leaving the previous version behind would crash the game on a duplicate mod
// id rather than upgrade it.
func installMod(mcDir string, asset *ghAsset) (dest string, replaced []string, err error) {
	modsDir := filepath.Join(mcDir, "mods")
	if err := os.MkdirAll(modsDir, 0o755); err != nil {
		return "", nil, err
	}

	dest = filepath.Join(modsDir, asset.Name)
	stale, err := staleModJars(modsDir, asset.Name)
	if err != nil {
		return "", nil, err
	}

	if err := downloadFile(asset.URL, dest); err != nil {
		return "", nil, err
	}

	// Removed only after the new jar is safely on disk, so a failed download leaves the
	// existing install working.
	for _, path := range stale {
		if err := os.Remove(path); err != nil {
			warn("could not remove the old jar %s: %v", filepath.Base(path), err)
			continue
		}
		replaced = append(replaced, filepath.Base(path))
	}
	return dest, replaced, nil
}

// staleModJars lists Esdeath jars in the mods folder other than the one being installed.
func staleModJars(modsDir, keep string) ([]string, error) {
	entries, err := os.ReadDir(modsDir)
	if err != nil {
		return nil, err
	}

	var stale []string
	for _, entry := range entries {
		// Compared case-insensitively because Windows and macOS filesystems are: a jar named
		// with different case IS the file just downloaded, and removing it as stale would
		// delete the install and still report success.
		if entry.IsDir() || strings.EqualFold(entry.Name(), keep) {
			continue
		}
		name := strings.ToLower(entry.Name())
		if strings.HasPrefix(name, modArtifactPrefix) && strings.HasSuffix(name, ".jar") {
			stale = append(stale, filepath.Join(modsDir, entry.Name()))
		}
	}
	return stale, nil
}
