package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/4G0NYY/Esdeath-Cryostasis/internal/engine"
	wruntime "github.com/wailsapp/wails/v2/pkg/runtime"
)

// launcherVersion is the launcher's own version, shown on the About screen. It is separate
// from the mod version, which comes from the latest GitHub release at runtime.
const launcherVersion = "0.1.0"

// App is the Wails-bound backend. Its exported methods are the entire surface the frontend
// can call; everything else in the package is private to it.
type App struct {
	ctx context.Context

	mu   sync.Mutex
	cfg  *Config
	busy bool // an install or play is running, so a second must not start on top of it
}

func NewApp() *App {
	return &App{}
}

// startup captures the Wails context (needed to emit events and open dialogs) and loads the
// config once, so the first screen already has real settings to show.
func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
	cfg, err := LoadConfig()
	if err != nil {
		// A broken config file should not brick the launcher: fall back to defaults and let the
		// user re-save over it from Settings.
		cfg = withDefaults(&Config{})
	}
	a.cfg = cfg
}

// AppVersion reports the launcher's own version for the About screen.
func (a *App) AppVersion() string { return launcherVersion }

// GetConfig returns the current settings.
func (a *App) GetConfig() Config {
	a.mu.Lock()
	defer a.mu.Unlock()
	return *a.cfg
}

// SaveConfig validates, normalises, and persists new settings, returning the stored form so
// the GUI can show exactly what was written (a trailing slash trimmed, for instance).
func (a *App) SaveConfig(cfg Config) (Config, error) {
	cfg.SchemaVersion = configSchemaVersion
	cfg.normalise()
	if err := cfg.validate(); err != nil {
		return cfg, err
	}
	if err := cfg.Save(); err != nil {
		return cfg, err
	}
	a.mu.Lock()
	a.cfg = &cfg
	a.mu.Unlock()
	return cfg, nil
}

// BackendStatus is the result of probing a backend's /version endpoint from Settings.
type BackendStatus struct {
	OK      bool   `json:"ok"`
	Version string `json:"version"`
	Message string `json:"message"`
}

// backendProbe has its own short timeout: the Test button should fail fast on a wrong or dead
// address rather than hang on the five-minute download client the engine uses.
var backendProbe = &http.Client{Timeout: 6 * time.Second}

// TestBackend probes a backend URL so a self-hoster can confirm their instance answers before
// saving it. It reuses the client's own contract: GET <base>/version, the same endpoint the
// client and the docs use as the liveness check.
func (a *App) TestBackend(rawURL string) BackendStatus {
	base := strings.TrimRight(strings.TrimSpace(rawURL), "/")
	if base == "" {
		return BackendStatus{Message: "Enter a backend URL first."}
	}

	resp, err := backendProbe.Get(base + "/version")
	if err != nil {
		return BackendStatus{Message: "Could not reach it: " + err.Error()}
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return BackendStatus{Message: fmt.Sprintf("It answered %s, not 200. Check the URL includes /api.", resp.Status)}
	}

	// The version endpoint returns {"version": "..."}; tolerate a body that is not that shape,
	// since a 200 already proves something is answering at the path.
	var payload struct {
		Version string `json:"version"`
	}
	_ = json.NewDecoder(resp.Body).Decode(&payload)
	if payload.Version != "" {
		return BackendStatus{OK: true, Version: payload.Version, Message: "Reachable, running " + payload.Version + "."}
	}
	return BackendStatus{OK: true, Message: "Reachable."}
}

// Status is the launcher's read of the install: where Minecraft is, what is installed, and
// whether a newer mod release exists.
type Status struct {
	MinecraftDir     string `json:"minecraftDir"`
	MinecraftFound   bool   `json:"minecraftFound"`
	FabricVersion    string `json:"fabricVersion"`
	InstalledVersion string `json:"installedVersion"`
	LatestVersion    string `json:"latestVersion"`
	UpdateAvailable  bool   `json:"updateAvailable"`
	BackendURL       string `json:"backendUrl"`
	Problem          string `json:"problem"`
}

// GetStatus reads the current state without changing anything, so the Play screen can show
// what is installed and whether an update is waiting. The release lookup is best effort: being
// offline degrades the update check, it does not fail the whole read.
func (a *App) GetStatus() Status {
	a.mu.Lock()
	cfg := *a.cfg
	a.mu.Unlock()

	st := Status{BackendURL: cfg.BackendURL}

	mcDir, err := engine.FindMinecraft(cfg.MinecraftDir)
	if err != nil {
		st.MinecraftDir = a.attemptedMinecraftDir(cfg)
		st.Problem = err.Error()
		return st
	}
	st.MinecraftDir = mcDir
	st.MinecraftFound = true
	st.FabricVersion = engine.FindFabricVersion(mcDir, a.mcVersionFrom(cfg))
	st.InstalledVersion = engine.ModJarVersion(engine.InstalledModJar(mcDir))

	if release, err := engine.LatestRelease(); err == nil {
		st.LatestVersion = strings.TrimPrefix(release.TagName, "v")
		st.UpdateAvailable = st.InstalledVersion == "" ||
			engine.CompareVersions(st.LatestVersion, st.InstalledVersion) > 0
	}
	return st
}

// PlayResult tells the GUI what happened after Play: the install is ready, and whether the
// launcher managed to open the official Minecraft launcher for the user.
type PlayResult struct {
	Ready          bool   `json:"ready"`
	LauncherOpened bool   `json:"launcherOpened"`
	ProfileName    string `json:"profileName"`
}

// Install updates Fabric, Fabric API, and the mod, then writes the profile with the backend
// pinned. Progress streams over the progress event while it runs.
func (a *App) Install() error {
	if err := a.begin(); err != nil {
		return err
	}
	defer a.end()

	_, _, err := a.ensureReady()
	if err != nil {
		a.emit("error", "%v", err)
		return err
	}
	a.emit("done", "Everything is up to date. Open Minecraft and pick %q.", engine.ProfileName)
	return nil
}

// Play does everything Install does, then opens the official launcher so the user can hit Play
// on the Esdeath profile. Handing off to the official launcher is what lets this ship without a
// Microsoft login or a game-launch pipeline; those are the direct-launch seams for later.
func (a *App) Play() (PlayResult, error) {
	if err := a.begin(); err != nil {
		return PlayResult{}, err
	}
	defer a.end()

	if _, _, err := a.ensureReady(); err != nil {
		a.emit("error", "%v", err)
		return PlayResult{}, err
	}

	opened := openOfficialLauncher()
	if opened {
		a.emit("done", "Opened the Minecraft launcher. Pick %q and hit Play.", engine.ProfileName)
	} else {
		a.emit("done", "Ready. Open the Minecraft launcher, pick %q, and hit Play.", engine.ProfileName)
	}
	return PlayResult{Ready: true, LauncherOpened: opened, ProfileName: engine.ProfileName}, nil
}

// OpenGitHub opens the project repository in the user's browser.
func (a *App) OpenGitHub() {
	wruntime.BrowserOpenURL(a.ctx, engine.RepoURL)
}

// OpenMinecraftFolder opens the resolved (or attempted) .minecraft directory in the file
// manager, a convenience for checking what the launcher wrote.
func (a *App) OpenMinecraftFolder() error {
	a.mu.Lock()
	cfg := *a.cfg
	a.mu.Unlock()

	dir, err := engine.FindMinecraft(cfg.MinecraftDir)
	if err != nil {
		dir = a.attemptedMinecraftDir(cfg)
	}
	if dir == "" {
		return errors.New("no Minecraft folder to open")
	}
	return openPath(dir)
}

// SelectMinecraftDir opens a folder picker for the .minecraft directory override, returning
// the chosen path (empty when the user cancels).
func (a *App) SelectMinecraftDir() (string, error) {
	return wruntime.OpenDirectoryDialog(a.ctx, wruntime.OpenDialogOptions{
		Title: "Select your .minecraft folder",
	})
}

// begin claims the single install slot, so Play and Install cannot run over each other and
// corrupt a half-written mods folder. end releases it.
func (a *App) begin() error {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.busy {
		return errors.New("an install is already running")
	}
	a.busy = true
	return nil
}

func (a *App) end() {
	a.mu.Lock()
	a.busy = false
	a.mu.Unlock()
}

func (a *App) mcVersionFrom(cfg Config) string {
	if cfg.MinecraftVersion != "" {
		return cfg.MinecraftVersion
	}
	return engine.DefaultMinecraftVersion
}

// attemptedMinecraftDir is the path the launcher would use, shown when Minecraft was not
// found so the user can see where it looked.
func (a *App) attemptedMinecraftDir(cfg Config) string {
	if cfg.MinecraftDir != "" {
		return cfg.MinecraftDir
	}
	dir, _ := engine.DefaultMinecraftDir()
	return dir
}
