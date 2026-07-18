//go:build windows

package main

import (
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"syscall"

	"github.com/4G0NYY/Esdeath-Cryostasis/internal/engine"
)

// The identity of the installed app. These names are the contract between install and
// uninstall and with Windows itself: the registry key is what Add/Remove Programs lists, the
// launcher and shortcut names are what the user sees, so they must match on both runs.
const (
	appDisplayName = "Esdeath: Cryostasis"
	appPublisher   = "ramon (4G0NYY)"

	// launcherFileName is what the downloaded launcher is stored as inside the install folder.
	// A human name (not the CI asset name) because it is the exe the user's shortcuts point at.
	launcherFileName = "Esdeath Cryostasis Launcher.exe"

	// uninstallerFileName is the copy of this setup binary left in the install folder so
	// Add/Remove Programs has something to call after the original download is long gone.
	uninstallerFileName = "uninstall.exe"

	// registryKey is the per-user Add/Remove Programs entry. HKCU (not HKLM) so the whole
	// install needs no administrator rights: everything lands under the user's own profile.
	registryKey = `HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\EsdeathCryostasis`

	// shortcutName is the .lnk base name in the Start Menu and (optionally) on the Desktop.
	shortcutName = "Esdeath Cryostasis.lnk"
)

// installDir is <LocalAppData>\Programs\Esdeath Cryostasis, the conventional home for a
// per-user application. Falls back to the user config dir's parent only if LOCALAPPDATA is
// somehow unset, which does not happen on a real Windows session.
func installDir() string {
	base := os.Getenv("LOCALAPPDATA")
	if base == "" {
		base, _ = os.UserConfigDir() // AppData\Roaming; still user-writable
	}
	return filepath.Join(base, "Programs", "Esdeath Cryostasis")
}

func startMenuShortcut() string {
	return filepath.Join(os.Getenv("APPDATA"), "Microsoft", "Windows", "Start Menu", "Programs", shortcutName)
}

func desktopShortcut() string {
	return filepath.Join(os.Getenv("USERPROFILE"), "Desktop", shortcutName)
}

// runInstall performs the whole idempotent install: fetch the newest launcher, drop it into the
// install folder, leave an uninstaller beside it, wire up the shortcuts and the Add/Remove
// Programs entry, and offer to start it. Every step overwrites rather than appends, so a second
// run is an update, not a mess.
func runInstall(opts installOptions) error {
	dir := installDir()
	launcherPath := filepath.Join(dir, launcherFileName)

	step("Finding the latest release")
	release, err := engine.LatestRelease()
	if err != nil {
		return err
	}
	asset, err := engine.LauncherAsset(release, runtime.GOOS)
	if err != nil {
		return fmt.Errorf("%w\n     The latest release does not carry a Windows launcher yet", err)
	}
	ok("%s (%s, %s)", release.TagName, asset.Name, humanSize(asset.Size))

	step("Installing to %s", dir)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}
	if err := engine.DownloadTo(asset.URL, launcherPath); err != nil {
		return fmt.Errorf("%w\n     If the launcher is already open, close it and run setup again", err)
	}
	ok("launcher %s", launcherFileName)

	// Leave a copy of ourselves behind so Add/Remove Programs (and a later manual uninstall) has
	// a stable binary to call: the setup the user downloaded lives in Downloads or a temp folder
	// and may be gone by the time they uninstall.
	uninstaller := filepath.Join(dir, uninstallerFileName)
	if err := copySelf(uninstaller); err != nil {
		return fmt.Errorf("could not place the uninstaller: %w", err)
	}
	ok("uninstaller")

	step("Creating shortcuts")
	if err := createShortcut(startMenuShortcut(), launcherPath, dir); err != nil {
		return fmt.Errorf("could not create the Start Menu shortcut: %w", err)
	}
	ok("Start Menu")
	if opts.desktopShortcut {
		if err := createShortcut(desktopShortcut(), launcherPath, dir); err != nil {
			// A Desktop shortcut is a convenience, not the install: warn and carry on rather than
			// failing an otherwise complete install over it.
			warn("could not create the Desktop shortcut: %v", err)
		} else {
			ok("Desktop")
		}
	}

	step("Registering with Add/Remove Programs")
	if err := writeUninstallKey(dir, launcherPath, uninstaller, release.TagName, asset.Size); err != nil {
		return fmt.Errorf("could not write the Add/Remove Programs entry: %w", err)
	}
	ok("listed as %q", appDisplayName)

	fmt.Println()
	fmt.Printf("  %s%sInstalled.%s Open %s%q%s to install the mod and play.\n",
		bold, green, reset, iceBlue, appDisplayName, reset)
	info("it lives in your Start Menu; the launcher keeps the mod up to date from here on")

	if opts.launch {
		if err := startDetached(launcherPath, dir); err != nil {
			warn("could not start the launcher automatically: %v", err)
		} else {
			info("starting the launcher...")
		}
	}
	return nil
}

// runUninstall removes everything the install created: the shortcuts, the Add/Remove Programs
// entry, and the install folder. The install folder holds the very binary running this, which
// Windows will not let a process delete out from under itself, so the folder removal is handed
// to a detached command that runs once we have exited. User settings under AppData are left
// alone, matching how a normal desktop app uninstall leaves your preferences behind.
func runUninstall(assumeYes bool) error {
	dir := installDir()

	if !confirm(fmt.Sprintf("Remove %s?", appDisplayName), assumeYes) {
		return errors.New("nothing was removed")
	}

	step("Removing shortcuts")
	removeIfExists(startMenuShortcut())
	removeIfExists(desktopShortcut())
	ok("shortcuts removed")

	step("Removing the Add/Remove Programs entry")
	if err := deleteUninstallKey(); err != nil {
		warn("could not remove the registry entry: %v", err)
	} else {
		ok("registry entry removed")
	}

	step("Removing program files")
	if err := scheduleDirRemoval(dir); err != nil {
		return fmt.Errorf("could not remove %s: %w", dir, err)
	}
	ok("scheduled removal of %s", dir)

	fmt.Println()
	fmt.Printf("  %s%sUninstalled.%s Your launcher settings were left in place.\n", bold, green, reset)
	return nil
}

// copySelf writes the running executable to dst. It is a no-op when we are already that file
// (a re-run launched from the install folder), so an update does not try to copy a locked
// binary over itself.
func copySelf(dst string) error {
	src, err := os.Executable()
	if err != nil {
		return err
	}
	if sameFile(src, dst) {
		return nil
	}
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	tmp := dst + ".new"
	out, err := os.OpenFile(tmp, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o755)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, in); err != nil {
		out.Close()
		os.Remove(tmp)
		return err
	}
	if err := out.Close(); err != nil {
		os.Remove(tmp)
		return err
	}
	// Rename into place so a half-copied uninstaller never sits under the real name. Remove any
	// existing one first: Windows rename fails if the target exists.
	os.Remove(dst)
	return os.Rename(tmp, dst)
}

func sameFile(a, b string) bool {
	ai, err := os.Stat(a)
	if err != nil {
		return false
	}
	bi, err := os.Stat(b)
	if err != nil {
		return false
	}
	return os.SameFile(ai, bi)
}

// createShortcut writes a .lnk pointing at target through the Windows shell's shortcut COM
// object, driven from PowerShell. Shelling to PowerShell (rather than binding the COM interface
// directly) keeps this package dependency free and mirrors how the launcher already talks to
// the shell. Overwrites any existing shortcut, so a re-run refreshes it.
func createShortcut(lnkPath, target, workingDir string) error {
	if err := os.MkdirAll(filepath.Dir(lnkPath), 0o755); err != nil {
		return err
	}
	script := fmt.Sprintf(
		`$s=(New-Object -ComObject WScript.Shell).CreateShortcut('%s');`+
			`$s.TargetPath='%s';$s.WorkingDirectory='%s';$s.IconLocation='%s,0';`+
			`$s.Description='%s';$s.Save()`,
		psQuote(lnkPath), psQuote(target), psQuote(workingDir), psQuote(target), psQuote(appDisplayName),
	)
	return runPowerShell(script)
}

// writeUninstallKey creates the Add/Remove Programs entry. The key is deleted first so a re-run
// cannot leave a stale value behind, then rebuilt with the current paths and version. The
// UninstallString points at the copy of ourselves in the install folder, invoked with the flag
// that runs the removal.
func writeUninstallKey(dir, launcherPath, uninstaller, tag string, size int64) error {
	_ = deleteUninstallKey() // ignore "key not found" on a first install

	version := strings.TrimPrefix(tag, "v")
	quotedUninstaller := `"` + uninstaller + `"`
	values := []struct {
		name, typ, data string
	}{
		{"DisplayName", "REG_SZ", appDisplayName},
		{"DisplayVersion", "REG_SZ", version},
		{"Publisher", "REG_SZ", appPublisher},
		{"DisplayIcon", "REG_SZ", launcherPath},
		{"InstallLocation", "REG_SZ", dir},
		{"UninstallString", "REG_SZ", quotedUninstaller + " --uninstall"},
		{"QuietUninstallString", "REG_SZ", quotedUninstaller + " --uninstall -y"},
		{"URLInfoAbout", "REG_SZ", engine.RepoURL},
		{"HelpLink", "REG_SZ", engine.RepoURL},
		{"EstimatedSize", "REG_DWORD", fmt.Sprintf("%d", size/1024)}, // Add/Remove Programs shows KB
		{"NoModify", "REG_DWORD", "1"},
		{"NoRepair", "REG_DWORD", "1"},
	}
	for _, v := range values {
		if err := runReg("add", registryKey, "/v", v.name, "/t", v.typ, "/d", v.data, "/f"); err != nil {
			return err
		}
	}
	return nil
}

func deleteUninstallKey() error {
	return runReg("delete", registryKey, "/f")
}

// scheduleDirRemoval deletes the install folder. Because the uninstaller running this lives
// inside that folder, it cannot delete itself synchronously, so the actual removal is a
// detached batch script that waits for this process to exit and release its own exe, then
// retries rmdir until the folder is gone and finally deletes itself. main does not pause after
// a successful uninstall, so we exit promptly and the first or second attempt succeeds.
//
// The work is a temp .bat rather than a one-line `cmd /c "..."`: Go escapes the quotes around
// the install path as \" when it builds the command line, which cmd.exe does not understand, so
// the rmdir would target a mangled path and silently remove nothing. A script file carries its
// own quoting untouched, sidestepping that entirely.
//
// The delay is `ping`, not `timeout`: a DETACHED_PROCESS has no console, and timeout reads the
// console input handle, so it errors out instantly there and the loop would burn all its
// attempts in milliseconds while the exe is still locked. ping -n 2 sleeps ~1s with no such
// dependency, and the leading ping gives this process time to exit before the first attempt.
func scheduleDirRemoval(dir string) error {
	script := "@echo off\r\n" +
		"ping -n 2 127.0.0.1 >nul\r\n" +
		"for /l %%i in (1,1,30) do (\r\n" +
		"  rmdir /s /q \"" + dir + "\" 2>nul\r\n" +
		"  if not exist \"" + dir + "\" goto done\r\n" +
		"  ping -n 2 127.0.0.1 >nul\r\n" +
		")\r\n" +
		":done\r\n" +
		"del \"%~f0\"\r\n"

	f, err := os.CreateTemp("", "esdeath-uninstall-*.bat")
	if err != nil {
		return err
	}
	if _, err := f.WriteString(script); err != nil {
		f.Close()
		return err
	}
	if err := f.Close(); err != nil {
		return err
	}

	cmd := exec.Command("cmd", "/c", f.Name())
	// DETACHED_PROCESS so it outlives this process and its console; hidden so no window flashes.
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x00000008}
	return cmd.Start()
}

// startDetached launches the installed launcher without waiting on it, so setup can finish and
// close while the GUI comes up on its own.
func startDetached(exe, workingDir string) error {
	cmd := exec.Command(exe)
	cmd.Dir = workingDir
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x00000008}
	return cmd.Start()
}

func removeIfExists(path string) {
	if err := os.Remove(path); err != nil && !errors.Is(err, os.ErrNotExist) {
		warn("could not remove %s: %v", path, err)
	}
}

// runReg runs reg.exe, Windows' built-in registry editor, swallowing "key not found" from a
// delete on a first install and surfacing anything else. Shelling to reg.exe keeps the module
// dependency free (no golang.org/x/sys/windows/registry).
func runReg(args ...string) error {
	cmd := exec.Command("reg", args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
	out, err := cmd.CombinedOutput()
	if err != nil {
		trimmed := strings.TrimSpace(string(out))
		if args[0] == "delete" && strings.Contains(strings.ToLower(trimmed), "unable to find") {
			return os.ErrNotExist
		}
		if trimmed != "" {
			return fmt.Errorf("reg %s: %s", args[0], trimmed)
		}
		return fmt.Errorf("reg %s: %w", args[0], err)
	}
	return nil
}

func runPowerShell(script string) error {
	cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
	if out, err := cmd.CombinedOutput(); err != nil {
		if trimmed := strings.TrimSpace(string(out)); trimmed != "" {
			return fmt.Errorf("%s", trimmed)
		}
		return err
	}
	return nil
}

// psQuote escapes a string for a PowerShell single-quoted literal, where the only special
// character is the single quote itself, doubled to escape it. Used for the file paths fed into
// the shortcut script so a path with an apostrophe cannot break out of the quotes.
func psQuote(s string) string {
	return strings.ReplaceAll(s, "'", "''")
}

// humanSize renders a byte count as a short human string (1.2 MB), for the one-line report of
// what is being downloaded.
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
