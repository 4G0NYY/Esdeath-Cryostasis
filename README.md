# Esdeath: Cryostasis

A modern rebuild of the abandoned EsdeathClient (Minecraft 1.8.9) as a Fabric mod for
Minecraft 1.21.x. Quality-of-life HUD modules and cosmetics, client side only. This is not
a cheat: it ships nothing a fair-play server would flag.

No Minecraft code lives in this repository. The build ships as a Fabric mod jar only.

## Status

- Phase 0 (recover the specification): done. See `docs/feature-spec.md` and
  `docs/backend-api.md`.
- Phase 1 (mod skeleton and framework): done. Module manager, settings, versioned JSON
  config, keybind and input layer, internal event bus, click GUI, and a HUD engine with
  anchored, draggable elements.
- Phase 2 (modules): most modules done. HUD: FPS, CPS, XYZ, ReachDisplay, PingTag, Plains,
  MLGHelper, ArrayList. Movement: ToggleSprint. Render: Hitbox, BlockOutline, Zoom. Combat
  particles: MoreParticles, Sharpness. Misc: AutoText. Deferred (need the backend or did
  not survive decompilation): Connector, Chat, MotionBlur, ItemAnimation.
- Phase 3 (cosmetics backend): done. A local dev instance in `backend/` implements the
  full REST contract and is smoke-tested end to end.
- Phase 4 (cosmetics rendering): framework done. The backend-driven cosmetic layer is
  wired onto the player renderer, with the TopHat, Halo, and Bandana models rebuilt.
  Visual correctness still needs a live client pass; the remaining cosmetics and the
  in-game cosmetics menu are pending.

The parts that need a running client to verify (visual rendering, in-world modules) are
marked as such in `Todo.md`. See it for the full roadmap and per-item progress.

## Installing

### With the launcher (recommended)

The desktop launcher (`launcher/`, attached to each
[release](https://github.com/4G0NYY/Esdeath-Cryostasis/releases/latest)) installs and updates
the mod for you, and adds one thing the CLI installer cannot: a Settings field to point the
client at any backend, defaulting to the hosted `cryostasis.ramon.moe/api`. Self-hosting the
open-source backend? Enter your instance there and the launcher pins it into the launch
profile. It then hands off to the official Minecraft launcher to play. See
[`launcher/README.md`](launcher/README.md).

### With the installer

For a scripted or headless setup, the command-line installer does the install and update side
without the GUI or the backend picker.

Grab the installer for your platform from the
[latest release](https://github.com/4G0NYY/Esdeath-Cryostasis/releases/latest) and run it:

| Platform | File |
| --- | --- |
| Windows | `esdeath-installer-windows-amd64.exe` |
| Linux | `esdeath-installer-linux-amd64` |
| macOS (Apple silicon) | `esdeath-installer-macos-apple-silicon` |
| macOS (Intel) | `esdeath-installer-macos-intel` |

It checks that Minecraft Java Edition is present, offers to install Fabric if it is
missing, installs Fabric API (the mod requires it), downloads the newest mod jar into your
`mods` folder, and adds an "Esdeath Cryostasis" profile to the official launcher. Then pick
that profile and hit Play.

Re-run it any time to update: it pulls the newest mod jar and Fabric API, removes the
versions they replace, and leaves every other mod alone. It is a single static binary and
needs no Java of its own, though installing Fabric does require a JVM somewhere (the
launcher's bundled runtime counts).

Useful flags:

- `-y` answer yes to every prompt, for unattended runs.
- `-dir <path>` point at `.minecraft` if it is not in the default location.
- `-mc <version>` target a Minecraft version other than 1.21.8.

### By hand

Distribution is a plain Fabric mod, so it works with any existing Fabric launcher (the
official launcher with a Fabric profile, Prism, MultiMC, and so on). A custom launcher is
not required.

1. Install Fabric Loader for Minecraft 1.21.8 and Fabric API.
2. Drop the `esdeath-cryostasis-<version>.jar` into your `mods` folder.
3. Launch the game and press Right Shift to open the click GUI.

Prebuilt jars and installers are attached to tagged GitHub Releases; see the `build`
workflow.

## Target stack

Minecraft 1.21.8, Java 21, Fabric Loader plus Fabric API, Gradle with the Loom plugin,
official Mojang mappings, and Mixin for game hooks.

## Building

The project uses the Gradle wrapper, so no local Gradle install is needed. A JDK 21 is
required.

```
./gradlew build
```

The mod jar lands in `build/libs/`. Ignore the `-sources` jar; the plain
`esdeath-cryostasis-<version>.jar` is the mod.

## Running in a dev client

```
./gradlew runClient
```

This launches a development Minecraft client with the mod loaded. Press Right Shift in game
to open the click GUI. Left click a module to toggle it, right click to expand its
settings, and drag panel headers to rearrange.

## Layout

- `src/main/java/moe/ramon/cryostasis/` mod source.
  - `module/` module base class, categories, and the manager.
  - `setting/` boolean, number, mode, color, and keybind settings.
  - `hud/` HUD module base with anchored positioning and the HUD manager.
  - `gui/` the click GUI screen.
  - `event/` the lightweight typed event bus.
  - `input/` keyboard and click routing.
  - `config/` versioned JSON save and load.
  - `service/` shared services such as the click tracker.
  - `mixin/client/` game hooks (mouse and keyboard).
  - `modules/` concrete modules.
- `installer/` the standalone Go CLI installer (no dependencies, one binary per platform).
- `docs/` recovered specification and backend API design.

## Credits

Original EsdeathClient by txb1. This rebuild is an independent modernization.
