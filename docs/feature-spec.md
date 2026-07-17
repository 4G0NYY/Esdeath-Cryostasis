# Esdeath Client Feature Specification (recovered from the 1.8.9 jar)

This document is the recovered behavior specification of the original EsdeathClient
(Minecraft 1.8.9, client version 3.8.6, built 2020). It was produced by decompiling the
`me/txb1` package with Vineflower and decrypting the obfuscator's string tables. It is
the parity target for the 1.21.x rebuild. It records behavior only; no decompiled code is
reused.

Where the obfuscation left something genuinely ambiguous, that is called out inline
rather than guessed.

---

## 1. Architecture of the original

The client was a compiled, obfuscated fat jar with the whole vanilla 1.8.9 client baked
in. Author code lived under `me/txb1/`. Cross-cutting game hooks were delivered through
the DarkMagician6 `eventapi` reflection event bus: modules called `EventManager.register(this)`
in `onEnable()` and exposed `@EventTarget` handler methods. The game loop posted empty
marker events (`EventTick`, `EventRender`, and so on) from baked-in edits.

The rebuild replaces this with Fabric plus Mixin and a direct-dispatch module manager, so
there is no reflection on the tick or render path. See the mapping notes at the end of
each section.

### Module base class

Fields: `name`, `displayName` (equal to `name` for every registered module), `category`,
`visible` (default true, controls ArrayList inclusion), `toggled` (default false, so every
module ships disabled and startup state comes entirely from config).

Lifecycle: `onEnable()` (declared `throws UnknownHostException`), `onDisable()`, `toggle()`.
There were **no per-module keybinds anywhere**; modules were toggled only through the
ClickGUI. The single exception was ToggleSprint, which hardcoded its own key check.

### Categories

The original enum had four values: `COMBAT`, `PLAYER`, `RENDER`, `WORLD`.

The rebuild uses `COMBAT`, `MOVEMENT`, `RENDER`, `HUD`, `PLAYER`, `MISC`, splitting HUD out
so pure overlays are easy to find and making each HUD module render itself rather than
routing through a master panel.

### The Visuals master panel (important)

In the original, several "modules" were pure toggle flags with no logic of their own.
`Visuals` was the master HUD panel that read the enabled state of `CPS`, `XYZ`, `Plains`,
and `ReachDisplay` and drew all of their output from one place, top-left. The rebuild does
not copy this coupling: each HUD element is a self-contained module per the project ground
rules.

---

## 2. Modules

Registration order in the original ModuleManager (19 modules):
Visuals, CPS, Plains, XYZ, ItemAnimation, ToggleSprint, MoreParticles, Sharpness, Hitbox,
ReachDisplay, HardZoom, ArrayList, Connector, AutoText, BlockOutLine, PingTag, MLGHelper,
MotionBlur, CleanChat. (A `Chat` module class exists but was never registered, so it was
dead in the shipped client.)

| Module | Category | Own event hook | Draws HUD | Behavior summary |
|---|---|---|---|---|
| Visuals | RENDER | EventClickMouse, EventRender | top-left panel | Master panel: FPS always, plus CPS/XYZ/Plains/ReachDisplay/online-count when those are on |
| CPS | RENDER | none (flag) | via Visuals | Rolling click count over the last 1200 ms |
| XYZ | RENDER | none (flag) | via Visuals | Player integer coordinates |
| Plains | RENDER | none (flag) | via Visuals | Biome name at player position |
| ReachDisplay | RENDER | EventHitEntity (empty) | via Visuals | Reach in blocks while attacking, formatted to 2 decimals, capped at 3, held for a countdown after the swing |
| ArrayList | RENDER | EventRender | top-right | Enabled module names, sorted by rendered width descending, right-aligned, rainbow |
| ItemAnimation | RENDER | none (flag) | no | Alters held-item swing animation (effect lives in an external render hook) |
| ToggleSprint | PLAYER | EventUpdate | no | Sets sprinting while forward (LWJGL key 17 / W) is held |
| MoreParticles | RENDER | EventHitEntity | no | Triples crit particles; when Sharpness is off, only fires on a legit crit (fall check, not on ground/ladder/water, no blindness, not riding) |
| Sharpness | RENDER | EventHitEntity | no | Single crit particle on every hit; defers entirely to MoreParticles when that is on |
| Hitbox | RENDER | static draw fn | 3D box | Draws entity bounding box expanded by width/2, rainbow, called from an entity-render hook |
| HardZoom | RENDER | none (flag) | no | Modifies render FOV while active (external FOV hook / EventZoom) |
| Connector | PLAYER | EventTick | no | Registers presence and current server to the backend, fetches the online-user list every 400 ticks |
| AutoText | PLAYER | EventTick | no | Keybound chat macros, key to message map loaded from config |
| BlockOutLine | WORLD | none (flag) | no | Modifies or recolors the block selection outline (external render hook) |
| PingTag | RENDER | none (flag) | no | Displays player ping (external hook) |
| MLGHelper | RENDER | EventRender | bottom-left | While sneaking, ray-traces down up to 200 blocks and shows jump/walk advice (German) when the fall is at least 10 |
| MotionBlur | RENDER | none (flag) | no | Frame-blend motion blur (external framebuffer hook); prints a chat notice on enable |
| CleanChat | RENDER | none (flag) | no | Cleans or deduplicates chat (external chat hook) |

Notes for the rebuild, the Mixin points the flag-only modules imply:
- FOV modifier (HardZoom), block-outline render cancel or recolor (BlockOutLine),
  nametag or tab ping draw (PingTag), chat cleanup (CleanChat), framebuffer motion blur
  (MotionBlur), held-item swing animation (ItemAnimation).
- Combat feedback (MoreParticles, Sharpness) needs an "attacked entity" event and reads
  fall state; both call `onCriticalHit` for particles only, not damage.
- `EventClickMouse` in the original declared itself cancellable but never actually
  cancelled, so treat the click hook as fire-only.

Encrypted strings that did not recover: several MLGHelper and MotionBlur and AutoText chat
messages (DES/Blowfish layer), which are user-facing notices, not behavior.

---

## 3. Cosmetics

All original cosmetics were 1.8 `ModelRenderer` box models drawn as `LayerRenderer`s on
the vanilla `RenderPlayer`. Every cosmetic is server-gated per player: it draws only when
the backend reports the target player owns that cosmetic (`Server.hasCosmetic`, cached in a
`uuid:key` map, refreshed asynchronously). The 1.8 immediate-mode models do not carry over,
so each is rebuilt as a 1.21 `ModelPart` or mesh (Phase 4).

Common render pattern: push matrix, bind the cosmetic texture, apply a small upward
translate while sneaking, render the model part, pop. Default UV space is 64x32 unless a
part sets its own texture size.

| Cosmetic | Backend key | Texture (assets/minecraft/EsdeathClient/) | Attach | Animation |
|---|---|---|---|---|
| Halo | halo | halo.png | above head | Vertical bob between about y=1.4 and 3.3, fixed 0.2 rad tilt |
| Rotate | rotate | halo.png (shared) | above head | Continuous Y spin (about 1/15 rad per frame); hidden while sneaking |
| RabbitEars | rabbitears | rabbit.png | head | Follows head pitch and yaw, 0.06 rad outward Z splay per ear |
| Reifen | reifen | reifen.png | lower body | None; hidden while sneaking |
| TopHat (name "Hat") | hat | hat.png | head | None; follows head |
| Bandana | bandana | shi.png | head | None; follows head |
| Stripes | stripes | stripes.png | ring of 6 bars, radius 11 | Vertical bob; the horizontal ring rotation appears lost in decompilation |
| Tail | tail | stripes.png (shared) | lower back | Cape-swing physics driven by horizontal motion |
| Susanoo | susanoo | susanoo.jpg | large cage around player | None over time (static splay of inner ribs) |
| Wings | wings | wings.png (texture 30x30) | back | 1-second sine flap plus cape-swing, purple tint, drawn flipped behind the player |

Concrete box geometry for each cosmetic (addBox offsets, rotation points, and the exact
flap and swing math for Wings and Tail) is recorded in the per-agent Phase 0 notes and
should be transcribed into the model builders during Phase 4. Fields lost to decompilation:
the `TEXTURE` ResourceLocation declarations (values are the decoded texture paths above)
and the `WingRenderer` field initialization.

### Cosmetics GUI

`CosmeticGui` lists every registered cosmetic as a grid of buttons (5 columns, 70 px
horizontal and 30 px vertical spacing) plus a Refresh button. Clicking a cosmetic toggles
ownership on the backend (`addCosmetic` / `removeCosmetic`) and updates the local cache.

### Capes

Capes are a separate system. Cape textures come from the backend cape list
(`Server.getAllCapes`, entries roughly `name-rarity.png`) and are gated by the player's
rank (`Premium`, `Epic`, `Chef`). Selecting a cape calls `Server.setCape(name)`. The local
player's own cape was downloaded from a hardcoded HTTP URL
(`http://45.89.141.147/resources/EsdeathClient/bp.png`) and displayed through the OptiFine
cape pipeline; there was no custom cape physics in author code (the Tail and Wings
cosmetics reimplement cape-swing math themselves). `ModelUtils` handled cape image
normalization to power-of-two sizes.

### Recovered texture assets

Present in the jar under `assets/minecraft/EsdeathClient/` and reusable directly:
`halo.png`, `rabbit.png`, `reifen.png`, `hat.png`, `shi.png` (bandana), `stripes.png`,
`wings.png`, `susanoo.jpg`, plus GUI art (`button.jpg`, `MainBackground.jpg`,
`MainBackground2.jpg`, `easygif.gif`). No standalone cape textures ship in the jar; capes
came from the backend at runtime.

---

## 4. Accounts

The account manager was a one-shot alt-login screen (username and password fields), not a
stored account list; nothing was persisted to disk. On login it authenticated against
Mojang Yggdrasil through `com.mojang.authlib` (`YggdrasilAuthenticationService`, empty
client token, `Agent.MINECRAFT`) and swapped the live `Minecraft.session` field via
reflection. Status messages were in German.

This does not carry over: Mojang password auth no longer exists. The rebuild uses Microsoft
OAuth for account linking and alt switching (Phase 5).

---

## 5. Config

Gson JSON, written to `json/EsdeathClient.json` relative to the game directory. Persisted:
a per-module `"1"`/`"0"` enabled flag keyed by module name, plus `art` (background style),
`theme` (name or hex color), `cape` (selected cape index), and `autotext` (a packed
`id:text,id:text` string). Keybinds were not persisted (there were none to persist). On
load, modules whose stored value is `"1"` are toggled on, theme and autotext are restored,
and the cape list is fetched asynchronously.

The rebuild keeps JSON but versions it (a top-level `version` field with a migration
pathway) and persists settings and keybinds per module, since the modern module system has
both. See `ConfigManager` in the mod source.

---

## 6. Backend

The backend was a raw TCP socket protocol, not HTTP. It is fully reconstructed in
[backend-api.md](backend-api.md), including the command vocabulary and a proposed modern
REST redesign, since the original server (`cwbwtraining.de`) is gone and Phase 3 is a fresh
design rather than a restore.
