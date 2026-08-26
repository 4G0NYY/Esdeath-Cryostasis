# Esdeath: Cryostasis

![Showcase](/assets/Showcase.png)

A modern rebuild of the abandoned EsdeathClient (Minecraft 1.8.9) as a Fabric mod for
Minecraft 1.21.x. It started as quality-of-life HUD modules and cosmetics, and it grew a
combat and movement half, so call it what it is: Killaura, Reach, Fly, and Xray now sit in
the same menu as the FPS counter. Know that before you take it onto a server that cares.

The modules are not all the same kind of thing, and the documentation says which is which,
module by module. Some never leave your own client: the HUD, the cosmetics, Freecam, Zoom.
Some only hold up in singleplayer, where the integrated server shares your player and so
agrees with whatever the client decided. The rest is the kind a fair-play server exists to
reject, and it will. See [`docs/modules.md`](docs/modules.md) for the per-module detail,
which is the honest version rather than a feature list.

No Minecraft code lives in this repository. The build ships as a Fabric mod jar only.

## Status

- Phase 0 (recover the specification): done. See `docs/feature-spec.md` and
  `docs/backend-api.md`.
- Phase 1 (mod skeleton and framework): done. Module manager, settings, versioned JSON
  config, keybind and input layer, internal event bus, click GUI, and a HUD engine with
  anchored, draggable elements.
- Phase 2 (modules): done, and past the original spec. Forty-four modules across six
  categories.
  - HUD: FPS, CPS, XYZ, ReachDisplay, PingTag, Plains, MLGHelper, ArrayList, Rainbow,
    OnlineList.
  - Movement: ToggleSprint, SafeWalk, AutoPath, Zoot, Spider, Jesus, NoCobweb, NoSoulsand,
    Fly.
  - Render: Hitbox, BlockOutline, Zoom, CleanChat, Xray, NoBlind, Nightvision, Freecam,
    Freelook, StatusTag.
  - Combat: MoreParticles, Sharpness, Killaura, AutoDodge, Reach.
  - Player: AutoTool, AutoEquip, AutoTotem, FastBreak, NoHunger.
  - Misc: AutoText, TakeAll, TabGui, DiscordPresence, GlobalChat.

  Still deferred, for want of the backend or of anything left to recover from the
  decompilation: Connector, MotionBlur, ItemAnimation.
- Phase 3 (cosmetics backend): done, and hosted. `backend/` implements the full REST
  contract and runs at `cryostasis.ramon.moe` behind the session-proof handshake. Beyond
  cosmetics it now serves ranks, a Cryostasis-wide global chat, and derived presence:
  online, away and offline are worked out from each client's heartbeat rather than stored,
  so nothing has to announce that a player left. The same service serves the public site at
  the root of that domain, which reads its live roster from the API beneath it.
- Phase 4 (cosmetics rendering): framework done. The backend-driven cosmetic layer is
  wired onto the player renderer, with the TopHat, Halo, and Bandana models rebuilt, and
  the in-game menu reads the catalogue from the backend. Visual correctness still needs a
  live client pass, and the remaining cosmetics are pending.

The parts that need a running client to verify (visual rendering, in-world modules) are
marked as such in `Todo.md`. See it for the full roadmap and per-item progress.

## Installing

### Windows: the setup installer (recommended)

Download `EsdeathCryostasisSetup.exe` from the
[latest release](https://gitlab.ramon.moe/4G0NYY/Esdeath-Cryostasis/-/releases/permalink/latest) and run it.
It installs the desktop launcher as a regular Windows app: into your programs folder, with a
Start Menu (and Desktop) shortcut and an entry in **Add/Remove Programs**, and with no
administrator rights required. That single download is all you need; the launcher takes over
from there and installs and updates the mod itself.

Open the launcher, and it installs Fabric, Fabric API, and the newest mod jar into your
`.minecraft`, writes the "Esdeath Cryostasis" launch profile, and hands off to the official
Minecraft launcher to play. It also adds one thing nothing else does: a Settings field to point
the client at any backend, defaulting to the hosted `cryostasis.ramon.moe/api`. Self-hosting
the open-source backend? Enter your instance there and it pins that into the profile. See
[`launcher/README.md`](launcher/README.md).

The setup installer is idempotent: run it again any time to pull the newest launcher over the
top of the old one. To remove everything, use **Add/Remove Programs** (or run the installer with
`--uninstall`); your launcher settings under `AppData` are left in place.

Useful flags:

- `-y` answer yes to every prompt, for unattended runs.
- `--uninstall` remove the launcher, shortcuts, and registry entry (what Add/Remove Programs calls).
- `--no-desktop-shortcut` skip the Desktop shortcut.
- `--no-launch` do not start the launcher after installing.

On Linux and macOS there is no setup installer, because the "install as a desktop app" model
is Windows-specific. Download the launcher binary for your platform from the release and run it
directly; it does the same mod install and backend setup.

### By hand

Distribution is a plain Fabric mod, so it works with any existing Fabric launcher (the
official launcher with a Fabric profile, Prism, MultiMC, and so on). A custom launcher is
not required.

1. Install Fabric Loader for Minecraft 1.21.8 and Fabric API.
2. Drop the `esdeath-cryostasis-<version>.jar` into your `mods` folder.
3. Launch the game and press Right Shift to open the click GUI.

Prebuilt jars, the setup installer, and the launcher binaries are attached to tagged
releases on the GitLab instance linked above, built by the pipeline in `.gitlab-ci.yml`. An
equivalent GitHub Actions workflow is kept in `.github/workflows/build.yml`.

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
  - `mixin/client/` game hooks, which is where any module that has to change vanilla
    behavior rather than sit alongside it does its work.
  - `modules/` concrete modules.
  - `cosmetics/` the backend-driven cosmetic layer and its models.
  - `backend/` the client side of the API: the shared connection, the session handshake
    that authenticates it, and global chat.
- `launcher/` the desktop launcher (Go and Wails): installs Fabric, the mod jar, and the
  launch profile, and picks the cosmetics backend.
- `backend/` the REST API: cosmetics, presence, ranks, and global chat.
- `installer/` the Windows setup installer (Go, no dependencies): installs the launcher as a
  desktop app with Add/Remove Programs support, and uninstalls it.
- `docs/` recovered specification and backend API design.

## Credits

Original EsdeathClient by txb1. This rebuild is an independent modernization.
(Also adds a few little hacks ;)
