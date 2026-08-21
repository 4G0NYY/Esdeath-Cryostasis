# Modules and cosmetics

User-facing reference for everything Esdeath: Cryostasis currently ships. Open the click
GUI with Right Shift, left click a module to toggle it, right click to expand its
settings, and drag a category header to move the panel. Settings save automatically when
you close the menu.

## HUD modules

These draw information on your screen. Each is a draggable element: open the click GUI and
drag it to reposition. Positions are stored as screen anchors, so they stay put when you
resize the window.

| Module | What it shows | Settings |
|---|---|---|
| FPS | Current framerate | Label on/off |
| CPS | Clicks per second | Button (Left, Right, Both), Label on/off |
| XYZ | Your coordinates | none |
| ReachDisplay | Distance to the entity you last attacked, held briefly after the swing | none |
| PingTag | Your latency to the current server | none |
| Plains | The biome you are standing in | none |
| MLGHelper | While sneaking, your fall height and a water-bucket cue | none |
| ArrayList | The list of active modules, top right, sorted by width | Background on/off |

## Movement

| Module | What it does | Settings |
|---|---|---|
| ToggleSprint | Sprints automatically while you move forward | none |
| NoCobweb | Walk through cobwebs at full speed, ignoring their slowdown | none |
| NoSoulsand | Cross soul sand at full speed, ignoring its slowdown | none |
| Jesus | Walk on the surface of water instead of swimming in it | Lava on/off |

Jesus works by giving the liquid a solid top, the same way vanilla lets a strider stand on
lava, so you walk, sprint, and jump on water exactly as you would on land. Hold sneak to sink,
and swimming and diving underneath are unchanged. Lava is a separate toggle and off by default.
Like the two below it, it only moves the local player: singleplayer is consistent, but a
fair-play multiplayer server still has you in the water and may pull you back.

NoCobweb and NoSoulsand only touch the local player. In singleplayer they cover the
integrated server too, so there is no rubber-banding; on a fair-play multiplayer server the
server still applies the slowdown, so use them with that in mind.

## Render

| Module | What it does | Settings |
|---|---|---|
| Hitbox | Outlines entity hitboxes | Color, Expand amount |
| BlockOutline | Recolors the block selection outline | Color |
| Zoom | Zooms in by narrowing your field of view while enabled | Factor |
| Xray | Reveals selected ores and blocks through terrain, hiding everything else | Per-material toggles (Coal, Iron, Copper, Gold, Redstone, Lapis, Diamond, Emerald, Quartz, Netherite, Amethyst), Containers, Spawners, Vaults, Suspicious, and an Extra field for custom block ids |
| NoBlind | Ignores the blindness effect, keeping vision clear | none |
| Nightvision | Keeps the world bright like the night vision potion and ignores darkness | none |
| Freecam | Detaches the camera and flies it through anything, leaving your body behind | Speed, Range |
| Freelook | Hold a key to look around without turning your body | Key, View |

Xray ships with a sensible default selection (the valuable ores plus containers, spawners,
and vaults; coal and suspicious blocks are off to cut clutter). Toggle materials in the click
GUI, or list extra blocks by id in the Extra setting, comma separated, for example
`minecraft:beacon, minecraft:reinforced_deepslate`. Toggling Xray or changing its selection
rebuilds the visible chunks so the change shows at once.

Nightvision also themes the sky: while Rainbow mode is on, the horizon sweeps through the
same rainbow the HUD uses, so the world matches the client theme.

Freecam is an out-of-body camera. Turn it on and the view lifts out of your player: the
movement keys fly it, the mouse turns it, and it passes straight through blocks. Hold the
sprint key to move at double speed. Your body stays exactly where it was standing, facing
where it was facing, because the movement input is emptied and the mouse is routed to the
camera instead of to you, so nothing about this goes to the server. The view switches to
third person for the duration, which is what lets you see yourself from the outside; your
previous view comes back when you turn it off, as does the camera.

Range is a tether, not a preference. The client only has chunks loaded around your body, so
a camera that outran them would be looking into empty space; the tether pulls it back to
within that many blocks. Note that the crosshair stays where your body left it, so mining
and attacking still only reach what you could actually reach.

Freelook is the light version, for a glance over your shoulder while you run. Hold the key
(Left Alt by default, rebindable under the module) and the mouse aims the camera only: you
keep facing, and keep running, exactly where you were. Let go and the view snaps back, since
your body never turned in the first place. It goes to third person while held, and the View
setting picks which one: Third, Front, or First if you would rather keep the first-person
camera and just free the direction.

A press is ignored while a menu is open, so the key is safe to hold while typing, and a
release always counts, so nothing can leave your view stuck outside your body. If Freecam has
the camera out, it keeps it and Freelook stays out of its way.

## Combat feedback

These only affect particles, never damage or hit logic. They call the same critical-hit
effect the game already uses, so nothing is sent that a server would not see from a real
crit.

| Module | What it does |
|---|---|
| MoreParticles | Triples the crit particle burst; on a real crit only, unless Sharpness is on |
| Sharpness | Shows a crit particle burst on every hit; defers to MoreParticles when that is on |

## Player

| Module | What it does | Settings |
|---|---|---|
| AutoTool | Swaps to the best tool for the block you are breaking, and to the best weapon when you hit something | Weapon On Attack |
| AutoEquip | Wears the strongest armor you are carrying | none |
| AutoTotem | Keeps a Totem of Undying in your offhand whenever you are carrying one | none |
| FastBreak | Mines and breaks blocks faster | Multiplier |
| NoHunger | Keeps the hunger bar from draining | none |

NoHunger drops the exhaustion that food and saturation are spent on, so the bar holds where
it is. It never adds anything back: a bar already empty stays empty until you eat. Hunger is
owned by whichever side runs the food data, and that is never the client, so like NoCobweb
and NoSoulsand it holds in singleplayer (where it covers the integrated server too) and
changes nothing on a fair-play multiplayer server.

AutoEquip and AutoTotem move items through the inventory the same way your own clicks would,
so the server sees an ordinary slot change. Both stay out of the way while a screen is open
and do nothing in creative, and they wait a few ticks between swaps rather than clicking every
tick. AutoTotem never throws anything away: whatever was in your offhand goes back into the
slot the totem came from, so a shield ends up where the totem was.

## Misc

| Module | What it does | Settings |
|---|---|---|
| AutoText | Sends a preset message or command when its bound key is pressed | Key, Message |
| TabGui | An arrow-key menu in the corner with the same toggles as the click GUI | none |

For AutoText, bind a key and set the message in the click GUI. A message starting with `/`
is sent as a command.

TabGui sits in the bottom-left corner and needs no screen: up and down move through the
categories, right (or enter) steps into one and toggles a module, and left steps back out.
The module column opens to the right of the category column, so the menu grows the way the
keys read. It only consumes the arrow keys while it is enabled; every other key still
reaches your module hotkeys.

## Cosmetics

Cosmetics are worn by any player the backend says owns them, so you see other players'
cosmetics too, not just your own. The client fetches each visible player's active
cosmetics from the backend and caches them.

Currently rebuilt: TopHat, Halo, Bandana. More are planned (Wings, Tail, Rabbit ears,
Reifen, Susanoo, Rotate, Stripes, and capes).

### Cosmetics menu

Open it in world with Right Ctrl. It previews your own player, rotating to follow the cursor,
and lists every cosmetic the client can render. Click a row to toggle that cosmetic on or off:
the preview updates at once and the change is saved to the backend in the background. Because
the preview reuses the same renderer other players see, what you set here is what they see too.
The menu needs a world to preview a player, so it only opens once you are in game.

## Opening the menu

The click GUI key defaults to Right Shift, and the cosmetics menu to Right Ctrl. Module hotkeys
can be set per module in the GUI (right click a module to expand it, then bind a key). Hotkeys
never fire while a screen is open, so typing in chat is safe.
