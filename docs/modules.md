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

## Render

| Module | What it does | Settings |
|---|---|---|
| Hitbox | Outlines entity hitboxes | Color, Expand amount |
| BlockOutline | Recolors the block selection outline | Color |
| Zoom | Zooms in by narrowing your field of view while enabled | Factor |

## Combat feedback

These only affect particles, never damage or hit logic. They call the same critical-hit
effect the game already uses, so nothing is sent that a server would not see from a real
crit.

| Module | What it does |
|---|---|
| MoreParticles | Triples the crit particle burst; on a real crit only, unless Sharpness is on |
| Sharpness | Shows a crit particle burst on every hit; defers to MoreParticles when that is on |

## Misc

| Module | What it does | Settings |
|---|---|---|
| AutoText | Sends a preset message or command when its bound key is pressed | Key, Message |

For AutoText, bind a key and set the message in the click GUI. A message starting with `/`
is sent as a command.

## Cosmetics

Cosmetics are worn by any player the backend says owns them, so you see other players'
cosmetics too, not just your own. The client fetches each visible player's active
cosmetics from the backend and caches them.

Currently rebuilt: TopHat, Halo, Bandana. More are planned (Wings, Tail, Rabbit ears,
Reifen, Susanoo, Rotate, Stripes, and capes). An in-game menu to preview and select owned
cosmetics is planned.

## Opening the menu

The click GUI key defaults to Right Shift. Module hotkeys can be set per module in the GUI
(right click a module to expand it, then bind a key). Hotkeys never fire while a screen is
open, so typing in chat is safe.
