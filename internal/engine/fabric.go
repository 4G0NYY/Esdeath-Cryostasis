package engine

import (
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
)

const fabricInstallerMeta = "https://meta.fabricmc.net/v2/versions/installer"

type fabricInstallerRelease struct {
	URL     string `json:"url"`
	Version string `json:"version"`
	Stable  bool   `json:"stable"`
}

// FindFabricVersion returns the installed Fabric version id for the given Minecraft version,
// or "" when Fabric is not installed for it. Fabric names its version folders
// fabric-loader-<loader>-<minecraft>, and that folder name is exactly the id a launcher
// profile has to point at.
func FindFabricVersion(mcDir, mcVersion string) string {
	entries, err := os.ReadDir(filepath.Join(mcDir, "versions"))
	if err != nil {
		return ""
	}

	suffix := "-" + mcVersion
	var found []string
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		name := entry.Name()
		if !strings.HasPrefix(name, "fabric-loader-") || !strings.HasSuffix(name, suffix) {
			continue
		}
		// A version folder without its json is a half-finished install the launcher cannot
		// boot, so treat it as absent and let the installer redo it.
		if _, err := os.Stat(filepath.Join(mcDir, "versions", name, name+".json")); err != nil {
			continue
		}
		found = append(found, name)
	}
	if len(found) == 0 {
		return ""
	}

	// Several loader versions can coexist for one Minecraft version. Pick the newest.
	sort.Slice(found, func(i, j int) bool {
		return compareVersions(loaderOf(found[i], mcVersion), loaderOf(found[j], mcVersion)) < 0
	})
	return found[len(found)-1]
}

func loaderOf(versionID, mcVersion string) string {
	return strings.TrimSuffix(strings.TrimPrefix(versionID, "fabric-loader-"), "-"+mcVersion)
}

// CompareVersions orders dotted numeric versions. Exported for the launcher, which compares
// the installed mod jar version against the latest release to decide if an update is offered.
// Returns a negative number when a < b, zero when equal, positive when a > b.
func CompareVersions(a, b string) int { return compareVersions(a, b) }

// compareVersions orders dotted numeric versions so that 0.17.2 sorts above 0.9.9, which a
// plain string compare gets wrong.
func compareVersions(a, b string) int {
	aParts := strings.Split(a, ".")
	bParts := strings.Split(b, ".")
	for i := 0; i < len(aParts) || i < len(bParts); i++ {
		var aNum, bNum int
		if i < len(aParts) {
			aNum, _ = strconv.Atoi(aParts[i])
		}
		if i < len(bParts) {
			bNum, _ = strconv.Atoi(bParts[i])
		}
		if aNum != bNum {
			return aNum - bNum
		}
	}
	return 0
}

// InstallFabric runs the official Fabric installer headless. Reimplementing what it does
// would mean owning Fabric's version-json format, so shelling out to their tool keeps this
// correct as that format changes.
func InstallFabric(mcDir, mcVersion string, p Progress) error {
	java, err := FindJava(mcDir)
	if err != nil {
		return err
	}
	p.Info("using java at %s", java)

	var releases []fabricInstallerRelease
	if err := getJSON(fabricInstallerMeta, &releases); err != nil {
		return fmt.Errorf("fetching the Fabric installer list: %w", err)
	}
	var chosen *fabricInstallerRelease
	for i := range releases {
		if releases[i].Stable {
			chosen = &releases[i]
			break
		}
	}
	if chosen == nil {
		return errors.New("Fabric published no stable installer")
	}

	tmp, err := os.MkdirTemp("", "esdeath-fabric-*")
	if err != nil {
		return err
	}
	defer os.RemoveAll(tmp)

	jar := filepath.Join(tmp, "fabric-installer.jar")
	p.Info("downloading Fabric installer %s", chosen.Version)
	if err := downloadFile(chosen.URL, jar); err != nil {
		return fmt.Errorf("downloading the Fabric installer: %w", err)
	}

	// -noprofile because the installer's own profile would sit next to ours and confuse the
	// version list. We create the Esdeath profile ourselves afterwards.
	cmd := exec.Command(java, "-jar", jar, "client",
		"-dir", mcDir,
		"-mcversion", mcVersion,
		"-noprofile")
	output, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("the Fabric installer failed: %w\n%s", err, strings.TrimSpace(string(output)))
	}
	return nil
}

// FindJava locates a JVM to run the Fabric installer with. Many players only ever get Java
// through the launcher's bundled runtime, so PATH alone is not enough to go on.
func FindJava(mcDir string) (string, error) {
	exe := "java"
	if runtime.GOOS == "windows" {
		exe = "java.exe"
	}

	if home := os.Getenv("JAVA_HOME"); home != "" {
		candidate := filepath.Join(home, "bin", exe)
		if isExecutableFile(candidate) {
			return candidate, nil
		}
	}
	if path, err := exec.LookPath("java"); err == nil {
		return path, nil
	}
	if bundled := findBundledJava(mcDir, exe); bundled != "" {
		return bundled, nil
	}
	return "", errors.New("no Java installation found. Install Java 21 (https://adoptium.net) " +
		"or run the Minecraft launcher once so it downloads its own runtime, then re-run this installer")
}

// findBundledJava walks the launcher's runtime folder, whose layout is versioned and
// platform-specific, so matching on the java binary itself beats guessing at path shapes.
func findBundledJava(mcDir, exe string) string {
	roots := []string{filepath.Join(mcDir, "runtime")}
	if runtime.GOOS == "windows" {
		if local := os.Getenv("LOCALAPPDATA"); local != "" {
			// Globbed to the Store launcher's own runtime rather than walking Packages, which
			// holds every installed Store app and would take far longer to scan than this
			// fallback is worth.
			matches, _ := filepath.Glob(filepath.Join(local, "Packages", "Microsoft.4297127D64EC6*",
				"LocalCache", "Local", "runtime"))
			roots = append(roots, matches...)
		}
		roots = append(roots, `C:\Program Files (x86)\Minecraft Launcher\runtime`)
	}

	for _, root := range roots {
		var found string
		filepath.WalkDir(root, func(path string, entry os.DirEntry, err error) error {
			if err != nil || found != "" {
				return nil
			}
			if !entry.IsDir() && entry.Name() == exe && filepath.Base(filepath.Dir(path)) == "bin" {
				if isExecutableFile(path) {
					found = path
					return filepath.SkipAll
				}
			}
			return nil
		})
		if found != "" {
			return found
		}
	}
	return ""
}

func isExecutableFile(path string) bool {
	stat, err := os.Stat(path)
	return err == nil && !stat.IsDir()
}
