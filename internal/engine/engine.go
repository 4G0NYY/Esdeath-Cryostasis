// Package engine is the shared install core for Esdeath: Cryostasis. It finds a Minecraft
// install, installs Fabric and Fabric API, drops the newest released mod jar into the mods
// folder, and writes the launcher profile that points at it.
//
// The command-line installer and the desktop launcher both drive this package, which is the
// whole reason it exists: the download and profile logic lives in exactly one place, and the
// two front ends differ only in how they present progress and whether they prompt.
package engine

const (
	RepoOwner = "4G0NYY"
	RepoName  = "Esdeath-Cryostasis"
	RepoURL   = "https://github.com/4G0NYY/Esdeath-Cryostasis"

	ProfileKey  = "esdeath-cryostasis"
	ProfileName = "Esdeath Cryostasis"
)

// DefaultMinecraftVersion tracks minecraft_version in gradle.properties. Bump both together
// when the mod moves to a new game version.
const DefaultMinecraftVersion = "1.21.8"

// Prefixes owned by this installer. A jar in the mods folder matching one of these is ours to
// replace; anything else is another author's mod and is never touched.
const (
	modArtifactPrefix = "esdeath-cryostasis-"
	fabricApiPrefix   = "fabric-api-"
)

// Progress receives the step detail the engine produces while it works. The CLI prints it
// with ANSI colors; the launcher forwards it to the GUI as events. The engine owns no
// presentation itself, so it stays usable from either front end and from tests.
//
// Only the lower-level detail flows through here. Major step boundaries ("Installing the
// mod") are the caller's to announce, because the caller decides the order of operations.
type Progress interface {
	// Info reports a sub-step detail, such as which Java binary was chosen.
	Info(format string, args ...any)
	// Warn reports a non-fatal problem the user should see but that does not stop the install.
	Warn(format string, args ...any)
}

// NopProgress discards every message. Use it when a caller does not care about detail, such
// as a test or a headless refresh.
type NopProgress struct{}

func (NopProgress) Info(string, ...any) {}
func (NopProgress) Warn(string, ...any) {}
