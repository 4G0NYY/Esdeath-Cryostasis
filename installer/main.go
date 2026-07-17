// Command esdeath-installer sets up Esdeath: Cryostasis against an existing Minecraft Java
// Edition install: it checks for the game, installs Fabric if it is missing, drops the newest
// released mod jar into the mods folder, and adds a launcher profile pointing at it.
//
// Re-running it is the supported way to update. Every step checks the current state before
// acting, so a second run only refreshes the jar and leaves everything else alone.
//
// All the download and profile logic lives in internal/engine, which the desktop launcher
// shares. This command is just the interactive command-line front end over it.
package main

import (
	"errors"
	"flag"
	"fmt"
	"os"
	"path/filepath"

	"github.com/4G0NYY/Esdeath-Cryostasis/internal/engine"
)

func main() {
	setupConsole()

	assumeYes := flag.Bool("y", false, "answer yes to every prompt, for unattended runs")
	mcVersion := flag.String("mc", engine.DefaultMinecraftVersion, "Minecraft version to target")
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
	prog := cliProgress{}

	step("Looking for Minecraft Java Edition")
	mcDir, err := engine.FindMinecraft(mcDirFlag)
	if err != nil {
		if errors.Is(err, engine.ErrNoMinecraft) {
			return fmt.Errorf("%w\n     Install Minecraft Java Edition and run the launcher once, "+
				"then re-run this installer.\n     If it lives somewhere unusual, point at it with -dir", err)
		}
		return err
	}
	ok("found %s", mcDir)

	step("Checking for Fabric %s", mcVersion)
	fabricVersion := engine.FindFabricVersion(mcDir, mcVersion)
	if fabricVersion == "" {
		warn("Fabric is not installed for Minecraft %s", mcVersion)
		if !confirm("Install Fabric now?", assumeYes) {
			return errors.New("Fabric is required to run this mod, nothing was changed")
		}
		if err := engine.InstallFabric(mcDir, mcVersion, prog); err != nil {
			return err
		}
		if fabricVersion = engine.FindFabricVersion(mcDir, mcVersion); fabricVersion == "" {
			return errors.New("the Fabric installer reported success but no version folder appeared")
		}
		ok("installed %s", fabricVersion)
	} else {
		ok("found %s", fabricVersion)
	}

	step("Installing Fabric API")
	apiVersion, apiReplaced, err := engine.InstallFabricAPI(mcDir, mcVersion, prog)
	if err != nil {
		return err
	}
	for _, old := range apiReplaced {
		info("removed the previous jar %s", old)
	}
	ok("%s", apiVersion)

	step("Fetching the latest release")
	release, err := engine.LatestRelease()
	if err != nil {
		return err
	}
	asset, err := engine.ModAsset(release)
	if err != nil {
		return err
	}
	ok("%s (%s, %s)", release.TagName, asset.Name, humanSize(asset.Size))

	step("Installing the mod")
	dest, replaced, err := engine.InstallMod(mcDir, asset, prog)
	if err != nil {
		return err
	}
	for _, old := range replaced {
		info("removed the previous jar %s", old)
	}
	ok("installed to %s", filepath.Join("mods", filepath.Base(dest)))

	step("Setting up the launcher profile")
	// Empty ProfileOptions: the CLI installer sets up the plain mod and does not pin a backend,
	// so the client uses its built-in default. Pointing at a custom backend is the desktop
	// launcher's job, through the same UpsertProfile with a BackendURL set.
	created, err := engine.UpsertProfile(mcDir, fabricVersion, engine.ProfileOptions{})
	if err != nil {
		return err
	}
	if created {
		ok("created the %q profile", engine.ProfileName)
	} else {
		ok("updated the %q profile", engine.ProfileName)
	}

	fmt.Println()
	fmt.Printf("  %s%sDone.%s Pick %s%q%s in the Minecraft launcher and hit Play.\n",
		bold, green, reset, iceBlue, engine.ProfileName, reset)
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
