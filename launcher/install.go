package main

import (
	"errors"
	"fmt"

	"github.com/4G0NYY/Esdeath-Cryostasis/internal/engine"
	wruntime "github.com/wailsapp/wails/v2/pkg/runtime"
)

// progressEvent is the single event the frontend listens on for a running install. Every line
// the engine and the orchestrator produce arrives as one logLine, tagged with a level the UI
// styles (a step heading, an ok, an info detail, a warning).
const progressEvent = "engine:progress"

type logLine struct {
	Level string `json:"level"`
	Text  string `json:"text"`
}

// emit sends one progress line to the frontend. It is the launcher's equivalent of the CLI's
// colored print helpers: same information, delivered as an event instead of a line of text.
func (a *App) emit(level, format string, args ...any) {
	wruntime.EventsEmit(a.ctx, progressEvent, logLine{Level: level, Text: fmt.Sprintf(format, args...)})
}

// eventProgress adapts the engine's Progress seam onto emit, so detail from inside the engine
// (which Java it picked, an old jar it could not remove) reaches the same log the orchestrator
// writes to.
type eventProgress struct{ app *App }

func (p eventProgress) Info(format string, args ...any) { p.app.emit("info", format, args...) }
func (p eventProgress) Warn(format string, args ...any) { p.app.emit("warn", format, args...) }

// mcVersion is the configured Minecraft version, or the engine default when unset.
func (a *App) mcVersion() string {
	if a.cfg.MinecraftVersion != "" {
		return a.cfg.MinecraftVersion
	}
	return engine.DefaultMinecraftVersion
}

// ensureReady brings the install fully up to date and writes the profile with the chosen
// backend pinned into its javaArgs. It is the shared core of Install and Play: Install stops
// here and reports done, Play goes on to open the launcher. Progress streams to the frontend
// as it goes.
func (a *App) ensureReady() (mcDir, versionID string, err error) {
	prog := eventProgress{a}
	mcVersion := a.mcVersion()

	a.emit("step", "Looking for Minecraft Java Edition")
	mcDir, err = engine.FindMinecraft(a.cfg.MinecraftDir)
	if err != nil {
		if errors.Is(err, engine.ErrNoMinecraft) {
			return "", "", fmt.Errorf("%w. Install Minecraft Java Edition and run the official "+
				"launcher once, then try again. If it lives somewhere unusual, set its folder in Settings", err)
		}
		return "", "", err
	}
	a.emit("ok", "found %s", mcDir)

	a.emit("step", "Checking for Fabric %s", mcVersion)
	versionID = engine.FindFabricVersion(mcDir, mcVersion)
	if versionID == "" {
		a.emit("info", "Fabric is not installed for %s, installing it now", mcVersion)
		if err = engine.InstallFabric(mcDir, mcVersion, prog); err != nil {
			return "", "", err
		}
		if versionID = engine.FindFabricVersion(mcDir, mcVersion); versionID == "" {
			return "", "", errors.New("the Fabric installer reported success but no version folder appeared")
		}
		a.emit("ok", "installed %s", versionID)
	} else {
		a.emit("ok", "found %s", versionID)
	}

	a.emit("step", "Installing Fabric API")
	apiVersion, apiReplaced, err := engine.InstallFabricAPI(mcDir, mcVersion, prog)
	if err != nil {
		return "", "", err
	}
	for _, old := range apiReplaced {
		a.emit("info", "removed the previous jar %s", old)
	}
	a.emit("ok", "%s", apiVersion)

	a.emit("step", "Fetching the latest release")
	release, err := engine.LatestRelease()
	if err != nil {
		return "", "", err
	}
	asset, err := engine.ModAsset(release)
	if err != nil {
		return "", "", err
	}
	a.emit("ok", "%s (%s)", release.TagName, asset.Name)

	a.emit("step", "Installing the mod")
	if _, replaced, err := engine.InstallMod(mcDir, asset, prog); err != nil {
		return "", "", err
	} else {
		for _, old := range replaced {
			a.emit("info", "removed the previous jar %s", old)
		}
	}
	a.emit("ok", "installed %s", asset.Name)

	a.emit("step", "Writing the launcher profile")
	if _, err := engine.UpsertProfile(mcDir, versionID, engine.ProfileOptions{BackendURL: a.cfg.BackendURL}); err != nil {
		return "", "", err
	}
	a.emit("ok", "pointed the client at %s", a.cfg.BackendURL)

	return mcDir, versionID, nil
}
