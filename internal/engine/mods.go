package engine

import (
	"os"
	"path/filepath"
	"strings"
)

// InstallMod places the release jar in the mods folder, replacing any older Esdeath jar.
func InstallMod(mcDir string, asset *Asset, p Progress) (dest string, replaced []string, err error) {
	return installJar(mcDir, asset.Name, asset.URL, modArtifactPrefix, p)
}

// InstallFabricAPI puts Fabric API in the mods folder. The mod declares a hard dependency on
// it, so without this the game refuses to start with "incompatible mods found", which is a
// confusing way to learn a dependency is missing.
func InstallFabricAPI(mcDir, mcVersion string, p Progress) (version string, replaced []string, err error) {
	version, url, filename, err := latestFabricApi(mcVersion)
	if err != nil {
		return "", nil, err
	}
	_, replaced, err = installJar(mcDir, filename, url, fabricApiPrefix, p)
	return version, replaced, err
}

// installJar downloads one jar into the mods folder and clears out older jars sharing its
// prefix. Sweeping is what makes a re-run an update: Fabric loads every jar in the folder, so
// leaving the previous version behind would crash the game on a duplicate mod id rather than
// upgrade it.
func installJar(mcDir, name, url, sweepPrefix string, p Progress) (dest string, replaced []string, err error) {
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
			p.Warn("could not remove the old jar %s: %v", filepath.Base(path), err)
			continue
		}
		replaced = append(replaced, filepath.Base(path))
	}
	return dest, replaced, nil
}

// InstalledModJar returns the filename of the Esdeath mod jar currently in the mods folder, or
// "" when none is installed. When several are present it returns the newest by version, which
// is what Fabric would load and what a fresh install leaves behind. The launcher uses it to
// show the installed version and to decide whether an update is available.
func InstalledModJar(mcDir string) string {
	entries, err := os.ReadDir(filepath.Join(mcDir, "mods"))
	if err != nil {
		return ""
	}
	best := ""
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		lower := strings.ToLower(entry.Name())
		if !strings.HasPrefix(lower, modArtifactPrefix) || !strings.HasSuffix(lower, ".jar") {
			continue
		}
		if strings.Contains(lower, "-sources") {
			continue
		}
		if best == "" || CompareVersions(ModJarVersion(entry.Name()), ModJarVersion(best)) > 0 {
			best = entry.Name()
		}
	}
	return best
}

// ModJarVersion extracts the version from an Esdeath mod jar filename, so
// esdeath-cryostasis-0.4.1.jar yields 0.4.1. It returns "" when the name is not one of ours.
func ModJarVersion(name string) string {
	lower := strings.ToLower(name)
	if !strings.HasPrefix(lower, modArtifactPrefix) || !strings.HasSuffix(lower, ".jar") {
		return ""
	}
	return name[len(modArtifactPrefix) : len(name)-len(".jar")]
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
