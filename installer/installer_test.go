package main

import (
	"os"
	"path/filepath"
	"testing"
)

// The sweep decides what gets deleted from a real mods folder, so its edge cases are worth
// pinning down rather than discovering on a player's install.
func TestStaleModJars(t *testing.T) {
	dir := t.TempDir()
	files := []string{
		"esdeath-cryostasis-0.1.0.jar",
		"esdeath-cryostasis-0.2.0.jar",
		"sodium-1.2.3.jar",
		"notes.txt",
	}
	for _, name := range files {
		if err := os.WriteFile(filepath.Join(dir, name), []byte("x"), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	if err := os.Mkdir(filepath.Join(dir, "esdeath-cryostasis-dir.jar"), 0o755); err != nil {
		t.Fatal(err)
	}

	stale, err := staleModJars(dir, "esdeath-cryostasis-0.2.0.jar")
	if err != nil {
		t.Fatal(err)
	}

	got := map[string]bool{}
	for _, path := range stale {
		got[filepath.Base(path)] = true
	}

	if !got["esdeath-cryostasis-0.1.0.jar"] {
		t.Error("the previous version should be swept, otherwise Fabric sees a duplicate mod id")
	}
	if got["esdeath-cryostasis-0.2.0.jar"] {
		t.Error("swept the jar being installed")
	}
	if got["sodium-1.2.3.jar"] {
		t.Error("swept another author's mod")
	}
	if got["notes.txt"] {
		t.Error("swept a non-jar")
	}
	if got["esdeath-cryostasis-dir.jar"] {
		t.Error("swept a directory")
	}
}

// On Windows and macOS a jar whose name differs only in case IS the jar just downloaded,
// because the filesystem resolves both to one file. Sweeping it would delete the install
// and still report success. The variant is written on its own here: writing it alongside
// the lowercase name would collapse into a single entry on exactly the platforms this
// guards, and the test would prove nothing.
func TestStaleModJarsKeepsCaseVariant(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "Esdeath-Cryostasis-0.2.0.JAR"), []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}

	stale, err := staleModJars(dir, "esdeath-cryostasis-0.2.0.jar")
	if err != nil {
		t.Fatal(err)
	}
	if len(stale) != 0 {
		t.Errorf("swept %v, want the case variant left alone", stale)
	}
}

func TestCompareLoaderVersions(t *testing.T) {
	cases := []struct {
		a, b string
		want string // "<", ">", or "="
	}{
		// The reason this is not a string compare.
		{"0.17.2", "0.9.9", ">"},
		{"0.9.9", "0.17.2", "<"},
		{"0.17.2", "0.17.2", "="},
		{"0.17.10", "0.17.2", ">"},
		{"1.0", "0.99.99", ">"},
		{"0.17", "0.17.0", "="},
	}
	for _, c := range cases {
		got := compareLoaderVersions(c.a, c.b)
		sign := "="
		if got < 0 {
			sign = "<"
		} else if got > 0 {
			sign = ">"
		}
		if sign != c.want {
			t.Errorf("compareLoaderVersions(%q, %q) = %s, want %s", c.a, c.b, sign, c.want)
		}
	}
}

// findFabricVersion feeds the profile's lastVersionId, so picking the wrong folder points
// the launcher at a loader that is not there.
func TestFindFabricVersion(t *testing.T) {
	dir := t.TempDir()
	versions := filepath.Join(dir, "versions")

	add := func(name string, withJSON bool) {
		if err := os.MkdirAll(filepath.Join(versions, name), 0o755); err != nil {
			t.Fatal(err)
		}
		if withJSON {
			path := filepath.Join(versions, name, name+".json")
			if err := os.WriteFile(path, []byte("{}"), 0o644); err != nil {
				t.Fatal(err)
			}
		}
	}

	add("fabric-loader-0.9.9-1.21.8", true)
	add("fabric-loader-0.17.2-1.21.8", true)
	add("fabric-loader-0.19.3-1.21.9", true)  // another game version
	add("fabric-loader-0.99.0-1.21.8", false) // half-finished install, no json
	add("1.21.8", true)                       // vanilla

	if got := findFabricVersion(dir, "1.21.8"); got != "fabric-loader-0.17.2-1.21.8" {
		t.Errorf("got %q, want the newest complete install for 1.21.8", got)
	}
	if got := findFabricVersion(dir, "1.21.7"); got != "" {
		t.Errorf("got %q, want empty for a version with no Fabric", got)
	}
	if got := findFabricVersion(filepath.Join(dir, "nope"), "1.21.8"); got != "" {
		t.Errorf("got %q, want empty when there is no versions folder", got)
	}
}

// The release carries the sources jar and the installer binaries alongside the mod.
func TestModAsset(t *testing.T) {
	release := &ghRelease{
		TagName: "v0.2.0",
		Assets: []ghAsset{
			{Name: "esdeath-installer-windows-amd64.exe"},
			{Name: "esdeath-cryostasis-0.2.0-sources.jar"},
			{Name: "esdeath-cryostasis-0.2.0.jar"},
		},
	}
	asset, err := modAsset(release)
	if err != nil {
		t.Fatal(err)
	}
	if asset.Name != "esdeath-cryostasis-0.2.0.jar" {
		t.Errorf("picked %q, want the mod jar", asset.Name)
	}

	if _, err := modAsset(&ghRelease{TagName: "v0.0.1"}); err == nil {
		t.Error("want an error when the release has no mod jar, got nil")
	}
}
