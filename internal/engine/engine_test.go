package engine

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

	stale, err := staleJars(dir, "esdeath-cryostasis-0.2.0.jar", modArtifactPrefix)
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
// because the filesystem resolves both to one file. Sweeping it would delete the install and
// still report success. The variant is written on its own here: writing it alongside the
// lowercase name would collapse into a single entry on exactly the platforms this guards, and
// the test would prove nothing.
func TestStaleModJarsKeepsCaseVariant(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "Esdeath-Cryostasis-0.2.0.JAR"), []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}

	stale, err := staleJars(dir, "esdeath-cryostasis-0.2.0.jar", modArtifactPrefix)
	if err != nil {
		t.Fatal(err)
	}
	if len(stale) != 0 {
		t.Errorf("swept %v, want the case variant left alone", stale)
	}
}

// The two prefixes share one mods folder, so each sweep must leave the other's jar alone.
func TestStaleJarsPrefixesDoNotCollide(t *testing.T) {
	dir := t.TempDir()
	for _, name := range []string{
		"esdeath-cryostasis-0.2.0.jar",
		"fabric-api-0.133.4+1.21.8.jar",
		"fabric-api-0.136.1+1.21.8.jar",
		"fabric-language-kotlin-1.13.0.jar",
	} {
		if err := os.WriteFile(filepath.Join(dir, name), []byte("x"), 0o644); err != nil {
			t.Fatal(err)
		}
	}

	stale, err := staleJars(dir, "fabric-api-0.136.1+1.21.8.jar", fabricApiPrefix)
	if err != nil {
		t.Fatal(err)
	}

	got := map[string]bool{}
	for _, path := range stale {
		got[filepath.Base(path)] = true
	}
	if !got["fabric-api-0.133.4+1.21.8.jar"] {
		t.Error("the older Fabric API should be swept")
	}
	if got["fabric-api-0.136.1+1.21.8.jar"] {
		t.Error("swept the Fabric API being installed")
	}
	if got["esdeath-cryostasis-0.2.0.jar"] {
		t.Error("the Fabric API sweep took the mod jar with it")
	}
	// Shares the "fabric-" stem but is a different mod entirely.
	if got["fabric-language-kotlin-1.13.0.jar"] {
		t.Error("swept an unrelated fabric- mod")
	}
}

func TestPickFabricApiVersion(t *testing.T) {
	// Shaped like the real maven-metadata.xml: many game versions interleaved.
	versions := []string{
		"0.133.4+1.21.8",
		"0.136.1+1.21.8",
		"0.134.0+1.21.8",
		"0.99.0+1.21.8",
		"0.140.0+1.21.9",
		"0.110.0+1.20.1",
	}

	// 0.136.1 over 0.99.0 is the case a string compare gets wrong.
	if got := pickFabricApiVersion(versions, "1.21.8"); got != "0.136.1+1.21.8" {
		t.Errorf("got %q, want the newest build for 1.21.8", got)
	}
	if got := pickFabricApiVersion(versions, "1.21.9"); got != "0.140.0+1.21.9" {
		t.Errorf("got %q, want the only build for 1.21.9", got)
	}
	// Must not fall back to a build for a different game version.
	if got := pickFabricApiVersion(versions, "1.21.4"); got != "" {
		t.Errorf("got %q, want empty when no build matches", got)
	}
}

func TestCompareVersions(t *testing.T) {
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
		got := compareVersions(c.a, c.b)
		sign := "="
		if got < 0 {
			sign = "<"
		} else if got > 0 {
			sign = ">"
		}
		if sign != c.want {
			t.Errorf("compareVersions(%q, %q) = %s, want %s", c.a, c.b, sign, c.want)
		}
	}
}

// findFabricVersion feeds the profile's lastVersionId, so picking the wrong folder points the
// launcher at a loader that is not there.
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

	if got := FindFabricVersion(dir, "1.21.8"); got != "fabric-loader-0.17.2-1.21.8" {
		t.Errorf("got %q, want the newest complete install for 1.21.8", got)
	}
	if got := FindFabricVersion(dir, "1.21.7"); got != "" {
		t.Errorf("got %q, want empty for a version with no Fabric", got)
	}
	if got := FindFabricVersion(filepath.Join(dir, "nope"), "1.21.8"); got != "" {
		t.Errorf("got %q, want empty when there is no versions folder", got)
	}
}

// The release carries the sources jar and the installer binaries alongside the mod.
func TestModAsset(t *testing.T) {
	release := &Release{
		TagName: "v0.2.0",
		Assets: []Asset{
			{Name: "esdeath-installer-windows-amd64.exe"},
			{Name: "esdeath-cryostasis-0.2.0-sources.jar"},
			{Name: "esdeath-cryostasis-0.2.0.jar"},
		},
	}
	asset, err := ModAsset(release)
	if err != nil {
		t.Fatal(err)
	}
	if asset.Name != "esdeath-cryostasis-0.2.0.jar" {
		t.Errorf("picked %q, want the mod jar", asset.Name)
	}

	if _, err := ModAsset(&Release{TagName: "v0.0.1"}); err == nil {
		t.Error("want an error when the release has no mod jar, got nil")
	}
}

// mergeJvmProperty is how the backend override reaches the client, so its three cases (fresh,
// replace, preserve) are the contract the launcher depends on.
func TestMergeJvmProperty(t *testing.T) {
	cases := []struct {
		name     string
		existing string
		url      string
		want     string
	}{
		{
			name:     "empty gets the flag",
			existing: "",
			url:      "https://cryostasis.ramon.moe/api",
			want:     "-Dcryostasis.api=https://cryostasis.ramon.moe/api",
		},
		{
			name:     "existing value is replaced in place, not duplicated",
			existing: "-Xmx4G -Dcryostasis.api=http://old/api -XX:+UseG1GC",
			url:      "https://new.example/api",
			want:     "-Xmx4G -Dcryostasis.api=https://new.example/api -XX:+UseG1GC",
		},
		{
			name:     "other args are preserved and the flag is appended",
			existing: "-Xmx4G -XX:+UseG1GC",
			url:      "https://cryostasis.ramon.moe/api",
			want:     "-Xmx4G -XX:+UseG1GC -Dcryostasis.api=https://cryostasis.ramon.moe/api",
		},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := mergeJvmProperty(c.existing, c.url); got != c.want {
				t.Errorf("mergeJvmProperty(%q, %q) = %q, want %q", c.existing, c.url, got, c.want)
			}
		})
	}
}
