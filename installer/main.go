// Command esdeath-installer sets up Esdeath: Cryostasis against an existing Minecraft Java
// Edition install: it checks for the game, installs Fabric if it is missing, drops the
// newest released mod jar into the mods folder, and adds a launcher profile pointing at it.
//
// Re-running it is the supported way to update. Every step checks the current state before
// acting, so a second run only refreshes the jar and leaves everything else alone.
package main

import (
	"errors"
	"flag"
	"fmt"
	"os"
	"path/filepath"
)

const (
	repoOwner = "4G0NYY"
	repoName  = "Esdeath-Cryostasis"
	repoURL   = "https://github.com/4G0NYY/Esdeath-Cryostasis"

	// Matches archives_base_name in gradle.properties, which names the released jar.
	modArtifactPrefix = "esdeath-cryostasis-"

	profileKey  = "esdeath-cryostasis"
	profileName = "Esdeath Cryostasis"
)

// defaultMinecraftVersion tracks minecraft_version in gradle.properties. Bump both together
// when the mod moves to a new game version.
const defaultMinecraftVersion = "1.21.8"

func main() {
	setupConsole()

	assumeYes := flag.Bool("y", false, "answer yes to every prompt, for unattended runs")
	mcVersion := flag.String("mc", defaultMinecraftVersion, "Minecraft version to target")
	mcDirFlag := flag.String("dir", "", "path to .minecraft, if it is not in the default location")
	flag.Parse()

	banner()

	if err := run(*mcDirFlag, *mcVersion, *assumeYes); err != nil {
		fmt.Printf("\n   %sInstall failed:%s %v\n", red, reset, err)
		pause()
		os.Exit(1)
	}
	pause()
}

func run(mcDirFlag, mcVersion string, assumeYes bool) error {
	step("Looking for Minecraft Java Edition")
	mcDir, err := findMinecraft(mcDirFlag)
	if err != nil {
		if errors.Is(err, errNoMinecraft) {
			return fmt.Errorf("%w\n     Install Minecraft Java Edition and run the launcher once, "+
				"then re-run this installer.\n     If it lives somewhere unusual, point at it with -dir", err)
		}
		return err
	}
	ok("found %s", mcDir)

	step("Checking for Fabric %s", mcVersion)
	fabricVersion := findFabricVersion(mcDir, mcVersion)
	if fabricVersion == "" {
		warn("Fabric is not installed for Minecraft %s", mcVersion)
		if !confirm("Install Fabric now?", assumeYes) {
			return errors.New("Fabric is required to run this mod, nothing was changed")
		}
		if err := installFabric(mcDir, mcVersion); err != nil {
			return err
		}
		if fabricVersion = findFabricVersion(mcDir, mcVersion); fabricVersion == "" {
			return errors.New("the Fabric installer reported success but no version folder appeared")
		}
		ok("installed %s", fabricVersion)
	} else {
		ok("found %s", fabricVersion)
	}

	step("Fetching the latest release")
	release, err := latestRelease()
	if err != nil {
		return err
	}
	asset, err := modAsset(release)
	if err != nil {
		return err
	}
	ok("%s (%s, %s)", release.TagName, asset.Name, humanSize(asset.Size))

	step("Installing the mod")
	dest, replaced, err := installMod(mcDir, asset)
	if err != nil {
		return err
	}
	for _, old := range replaced {
		info("removed the previous jar %s", old)
	}
	ok("installed to %s", filepath.Join("mods", filepath.Base(dest)))

	step("Setting up the launcher profile")
	created, err := upsertProfile(mcDir, fabricVersion)
	if err != nil {
		return err
	}
	if created {
		ok("created the %q profile", profileName)
	} else {
		ok("updated the %q profile", profileName)
	}

	fmt.Println()
	fmt.Printf("  %s%sDone.%s Pick %s%q%s in the Minecraft launcher and hit Play.\n",
		bold, green, reset, iceBlue, profileName, reset)
	info("if the launcher is already open, restart it so the new profile shows up")
	return nil
}

func humanSize(bytes int64) string {
	const unit = 1024
	if bytes < unit {
		return fmt.Sprintf("%d B", bytes)
	}
	div, exp := int64(unit), 0
	for n := bytes / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f %cB", float64(bytes)/float64(div), "KMGT"[exp])
}
