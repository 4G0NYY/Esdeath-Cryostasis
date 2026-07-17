# Esdeath: Cryostasis launcher

A desktop launcher for the Esdeath: Cryostasis client. It installs and updates the mod, lets
you choose which backend the client talks to, and hands off to the official Minecraft launcher
to play. It is styled to match the in-game client (the navy and steel `gui.Theme` palette) and
links to the project on GitHub.

The launcher exists for one job the plain installer cannot do: the client reads its backend
from the `cryostasis.api` system property, and the launcher pins that into the Esdeath launch
profile's `javaArgs`. That is what lets a self-hoster point the client at their own backend
instead of the hosted default, from a field in Settings.

## Stack

- **Go core** (`app.go`, `install.go`, `launch.go`, `config.go`): the Wails-bound backend.
- **Shared engine** (`../internal/engine`): the download and profile logic, shared with the
  command-line installer so there is one implementation of it, not two. This module reaches it
  through a `replace` directive in `go.mod`.
- **Svelte frontend** (`frontend/`): the themed UI, built by Vite and embedded into the binary.

The launcher is its own Go module, separate from the root module, so the Wails dependency
stays out of the installer's dependency-free build.

## What it does today

- Finds Minecraft, installs Fabric, Fabric API, and the newest released mod jar, and writes the
  "Esdeath Cryostasis" profile. This is the shared engine, the same steps the CLI installer runs.
- Pins the chosen backend into the profile as `-Dcryostasis.api=<url>`, preserving any other
  `javaArgs` the user set.
- Tests a backend URL from Settings (a `GET <url>/version`) before saving it.
- Opens the official Minecraft launcher to play, best effort, and always tells the user which
  profile to pick.
- Stores settings as versioned JSON (`schemaVersion`) so an old config migrates forward.

## What is deferred

Microsoft account login and launching the game directly from the launcher (provision a JRE,
build the classpath, spawn Java) are left as the `AccountManager` and `DirectLauncher`
interfaces in `launch.go`. They are gated on a registered Microsoft Azure application, which the
project does not have yet. Until then the official Minecraft launcher owns the session, which is
why the profile-based flow needs no login.

## Develop

Requires Go 1.23+, Node 20+, and the [Wails CLI](https://wails.io) v2.13.0:

```
go install github.com/wailsapp/wails/v2/cmd/wails@v2.13.0
wails dev      # live-reloading dev build
wails build    # packaged binary in build/bin
```

`wails dev` and `wails build` run the frontend install and build for you. To work on just the
Go side, build the frontend once first, because the binary embeds it:

```
cd frontend && npm install && npm run build && cd ..
go build ./...
```

On Linux the WebKitGTK dev packages are needed: `libgtk-3-dev` and `libwebkit2gtk-4.1-dev`.

## Release

The `launcher` job in `.github/workflows/build.yml` builds a native binary per platform
(Windows, Linux, macOS) on a tagged release and attaches it, alongside the mod jar, the CLI
installer, and the backend container image.
