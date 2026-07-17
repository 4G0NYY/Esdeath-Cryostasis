package main

import (
	"os"
	"path/filepath"
	"strings"
)

// Prefixes owned by this installer. A jar in the mods folder matching one of these is ours
// to replace; anything else is another author's mod and is never touched.
const (
	modArtifactPrefix = "esdeath-cryostasis-"
	fabricApiPrefix   = "fabric-api-"
)

// installMod places the release jar in the mods folder, replacing any older Esdeath jar.
func installMod(mcDir string, asset *ghAsset) (dest string, replaced []string, err error) {
	return installJar(mcDir, asset.Name, asset.URL, modArtifactPrefix)
}

// installFabricApi puts Fabric API in the mods folder. The mod declares a hard dependency
// on it, so without this the game refuses to start with "incompatible mods found", which is
// a confusing way to learn a dependency is missing.
func installFabricApi(mcDir, mcVersion string) (version string, replaced []string, err error) {
	version, url, filename, err := latestFabricApi(mcVersion)
	if err != nil {
		return "", nil, err
	}
	_, replaced, err = installJar(mcDir, filename, url, fabricApiPrefix)
	return version, replaced, err
}

// installJar downloads one jar into the mods folder and clears out older jars sharing its
// prefix. Sweeping is what makes a re-run an update: Fabric loads every jar in the folder,
// so leaving the previous version behind would crash the game on a duplicate mod id rather
// than upgrade it.
func installJar(mcDir, name, url, sweepPrefix string) (dest string, replaced []string, err error) {
	modsDir := filepath.Join(mcDir, "mods")
	if err := os.MkdirAll(modsDir, 0o755); err != nil {
		return "", nil, err
	}

	dest = filepath.Join(modsDir, name)
	stale, err := staleJars(modsDir, name, sweepPrefix)
	if err != nil {
		return "", nil, err
	}

	if err := downloadFile(url, dest); err != nil {
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

// staleJars lists jars in the mods folder that share a prefix with the one being installed,
// other than that jar itself.
func staleJars(modsDir, keep, prefix string) ([]string, error) {
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
		if strings.HasPrefix(name, prefix) && strings.HasSuffix(name, ".jar") {
			stale = append(stale, filepath.Join(modsDir, entry.Name()))
		}
	}
	return stale, nil
}
