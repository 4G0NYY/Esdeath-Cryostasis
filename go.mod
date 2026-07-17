// Root module for the repository's Go code. It holds the shared install engine
// (internal/engine) and the command-line installer (installer/). The desktop launcher is a
// nested module under launcher/ with its own go.mod, so the heavy Wails dependency stays out
// of this module and the installer keeps building with no third-party dependencies at all.
module github.com/4G0NYY/Esdeath-Cryostasis

go 1.23
