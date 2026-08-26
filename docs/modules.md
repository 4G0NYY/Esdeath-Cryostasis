# Modules and cosmetics

User-facing reference for everything Esdeath: Cryostasis currently ships. Open the click
GUI with Right Shift, left click a module to toggle it, right click to expand its
settings, and drag a category header to move the panel. Settings save automatically when
you close the menu.

Editing a setting depends on what kind it is. A numeric setting is a slider: grab the handle
and drag it, or click anywhere on the track to jump there, and the value above it updates as
you go. The scroll wheel still nudges a slider by one step, which is how you land on an exact
number the track is too narrow to single out. Everything else is a click: a toggle flips, a mode
cycles to the next option, a colour steps through the palette, and a key setting waits for the
next key you press. Right clicking any setting puts it back to the value it shipped with.

## HUD modules

These draw information on your screen. Every one of them can be moved: press Right Alt to open
the HUD editor, then drag. Positions are stored as screen anchors, so an element dropped against
an edge stays against it when you resize the window or change GUI scale.

Until you move one, HUD elements stack themselves down the top-left corner, each below the last,
so turning several on never leaves them overlapping. Dragging one out of that column takes it out
for good and the column closes up behind it. Right click an element in the editor to put it back;
Backspace puts every element back.

Dropping an element snaps it to the screen edges, the screen centre, and to the edges and centres
of the other elements, with a guide line drawn along whatever caught it, so two elements line up
exactly rather than nearly. Arrow keys nudge the selected element one pixel at a time. An element
that has nothing to show at that moment, like the reach readout between swings, still appears in
the editor as a small grey chip so it can be placed.

| Module | What it shows | Settings |
|---|---|---|
| FPS | Current framerate | Label on/off |
| CPS | Clicks per second | Button (Left, Right, Both), Label on/off |
| XYZ | Your coordinates | none |
| ReachDisplay | Distance to the entity you last attacked, held briefly after the swing | none |
| PingTag | Your latency to the current server | none |
| Plains | The biome you are standing in | none |
| Durability | Uses left in each hand and each worn armor piece | Hand, Offhand, Armor, Percent |
| MLGHelper | While sneaking, your fall height and a water-bucket cue | none |
| ArrayList | The list of active modules, top right, sorted by width | Background on/off |
| OnlineList | Everyone currently running Cryostasis, with their rank colour and status | Max Rows, Show Away, Show Status, Header |

OnlineList is the presence roster: it lists the players whose clients are currently connected to
the backend, whichever game server each of them is on, so it is not the same list as the server's
own tab menu. A green square means they are playing, an amber one means their character has been
parked long enough to count as away, and the text after the name is whatever status they set for
themselves, or their state when they have set none. Somebody who has never signed in to the
backend has no name this client trusts and is left off rather than shown as unknown.

## Movement

| Module | What it does | Settings |
|---|---|---|
| ToggleSprint | Sprints automatically while you move forward | none |
| NoCobweb | Walk through cobwebs at full speed, ignoring their slowdown | none |
| NoSoulsand | Cross soul sand at full speed, ignoring its slowdown | none |
| Jesus | Walk on the surface of water instead of swimming in it | Lava on/off |
| Fly | Flies you wherever you point | Mode (Motion, Abilities), Speed |

Jesus works by giving the liquid a solid top, the same way vanilla lets a strider stand on
lava, so you walk, sprint, and jump on water exactly as you would on land. Hold sneak to sink,
and swimming and diving underneath are unchanged. Lava is a separate toggle and off by default.
Like the two below it, it only moves the local player: singleplayer is consistent, but a
fair-play multiplayer server still has you in the water and may pull you back.

Fly steers on the movement keys, with jump and sneak for up and down and sprint held for
double speed, the same controls Freecam uses. The Mode setting picks which of the two kinds of
flight you get. Abilities sets the creative flight flags and tells the server about them, so you
fly through the game's own code and it looks smooth; that works in creative and on a server with
`allow-flight` on, and where flight is not permitted the server clears the flags again and the
flight stops. Motion asks nobody: it overwrites your velocity every tick, so gravity never
accumulates, which is why it works in singleplayer survival where Abilities does not. What it
cannot do is hide, and a fair-play server watching your position packets will pull you back down
or kick you. Fall distance is cleared every tick either way, so landing is safe locally; the
server banks its own from the movement packets, so turn on Zoot before you land on one.

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
| StatusTag | Marks Cryostasis players on their name tag with the client emblem, their rank, and their status | Emblem, Rank, Away, Status |

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

## Combat

| Module | What it does | Settings |
|---|---|---|
| Killaura | Hits living entities that come within reach, by itself | Reach, Hits Per Second, Full Damage, Targets (Players, Mobs, All), Line of Sight, Rotate |
| AutoDodge | Strafes you out of the path of incoming arrows | Range, Strength, Only Grounded |
| Reach | Lets you hit and touch things further away | Entities, Blocks |

Killaura swings through the same call your own click does, so AutoTool still swaps to your best
weapon and the crit particles below still fire. Hits Per Second caps how often it swings, up to
the twenty a second that one swing per client tick allows. Full Damage adds the game's own gate
on top of that: a hit thrown before the weapon's cooldown finishes is scaled down, so leaving it
on trades swings for damage per swing, and turning it off spends those extra swings on the weak
hits you would get from mashing the button yourself. Reach is measured from your eye to the
nearest point of the target's box, which is what the game's own range check measures.

AutoDodge watches every arrow in flight near you, walks its path forward, and checks whether that
path would actually enter your hitbox. Only then does it shove you sideways, out of the line
rather than across it, and only once every few ticks so a volley cannot stack shoves into a
launch. Arrows you shot yourself and arrows already lodged in a block are ignored. The shove goes
into the movement your client was sending anyway, so it is the same as having strafed.

Reach raises the two distances the game keeps as attributes, 3 blocks to an entity and 4.5 to a
block by default, and everything downstream follows: the crosshair picks a target further out,
and the check on the swing accepts it. Neither setting ever shortens anything, so creative keeps
the longer reach it already has. In singleplayer this covers the integrated server too, so the
hits land. On a fair-play multiplayer server the check that counts is the server's own, and past
roughly 3 blocks of entity reach it throws the hits away, whatever Killaura is set to.

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

AutoTool ranks weapons by what they land over time rather than by the damage on the tooltip. An
axe hits harder per swing but recovers at roughly half a sword's rate, so a sword beats the axe of
its own material, gold aside. An axe far enough ahead in material still wins outright: a netherite
axe beats a stone sword, and an iron sword beats a diamond axe. Nothing is swapped to at all
unless it beats a bare fist, which is why a mace is left alone: its swing rate puts it below
punching for repeated hits, and the falling smash it is actually carried for is not something a
swap on attack can see coming.

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
| GlobalChat | Chat with everyone running Cryostasis, whatever server you are on | Prefix, Backlog |

For AutoText, bind a key and set the message in the click GUI. A message starting with `/`
is sent as a command.

TabGui needs no screen: up and down move through the categories, right (or enter) steps into one
and toggles a module, and left steps back out. The module column opens to the right of the
category row it belongs to and slides in, so the menu grows the way the keys read. The category
column is a fixed size and is what carries the menu's position, which means stepping in and out of
a category never moves the categories themselves. It only consumes the arrow keys while it is
enabled; every other key still reaches your module hotkeys.

TabGui is a HUD element like the rest, so the HUD editor moves it and its position is saved. It
starts against the left edge, a third of the way down.

GlobalChat is one channel shared by everyone running the client, independent of the server you
happen to be playing on. Messages arrive in your ordinary chat, tagged `[EC]` and coloured by the
sender's rank, so there is no extra window to keep open. To send one, start a normal chat line
with the prefix (`@` by default): `@anyone on hypixel?`. The line is intercepted before it
reaches the server you are connected to, so a message meant for Cryostasis never leaks into that
server's chat. Backlog is how many recent messages you see when the module is switched on, and
zero means start from now.

Sending needs the client to have signed in to the backend, which it does on its own shortly after
launch. Until then you will be told to try again in a moment. Messages are capped at 256
characters and rate limited, and staff can mute a player who abuses the channel. If you hold a
staff rank, `@/mute <player> [minutes] [reason]`, `@/unmute <player>` and `@/mutes` work from the
same prefix.

## Ranks

Your rank is a tag, not an entitlement: every cosmetic is free for every account regardless of
it. What it changes is how you appear in global chat, where the tag and your name take the rank's
colour, and it is shown in the corner of the cosmetics menu. Default, Premium, Epic and Chef are
display ranks; Mod and Admin also carry the chat moderation commands above. Ranks are granted
server-side and cannot be set from the client.

## Presence and status

The backend knows which clients are currently running, and works it out from a heartbeat rather
than being told: your client says "still here" every thirty seconds, and each beat also says
whether you have done anything in world since the last one. Three states fall out of that.

- **Online.** Your client is beating and you have moved, looked, or pressed something recently.
- **Away.** Your client is still beating, but nothing in world has happened for five minutes.
  Menus do not count as activity, so reading your inventory long enough will show you as away,
  which is the honest answer: your character is parked.
- **Offline.** No heartbeat for two minutes. Nothing has to announce that you left, so a crash,
  a lost connection and a clean quit all look the same and none of them leave you stuck online.

On top of the state you can set a **status**, a short line of your own, in the cosmetics menu.
The two are separate on purpose: the state is a fact about your client, the status is whatever
you want to say. Where both exist, the status is what other people see.

Your presence shows up in four places: the OnlineList HUD module, the roster beside the cosmetics
menu, on a player's name tag in world (the StatusTag render module), and on global chat lines,
where a sender who was away when they typed is tagged `[AFK]`. That last one is a snapshot taken
when the line was posted rather than a live lookup, so it says what was true at the time and does
not rewrite itself later.

Presence is not tied to any module: the heartbeat runs whenever the client is signed in to the
backend, the same as cosmetics and ranks. The modules above decide what you see, not what you
report.

## Cosmetics

Cosmetics are free for every account, so what the backend stores is not who owns what but who
is currently wearing what. You see other players' cosmetics too, not just your own: the client
fetches each visible player's active set and caches it.

Rebuilt so far: TopHat, Halo, Bandana, Wings, Tail, Rabbit ears, Reifen, Stripes, and Susanoo,
which is the whole catalogue the backend serves. Capes are still their own system and are not
built yet. The menu lists everything the backend offers, with anything this client has no model
for shown greyed out and marked "soon", so a cosmetic added server-side appears as soon as it
exists rather than waiting for the client update that draws it.

| Cosmetic | Where it sits | What it does |
|---|---|---|
| Halo | Above the head | Drifts up and down, level whichever way you look |
| TopHat | On the head | Follows your head |
| Bandana | On the head | Follows your head |
| Rabbit ears | On the head | Splayed outward and leaning back, follows your head |
| Wings | Upper back | Beats once a second and lags with your movement |
| Tail | Lower back | Four joints that whip and settle with your movement |
| Reifen | Waist | A red and white swim ring, hidden while sneaking |
| Stripes | Around you | Six bars turning slowly and drifting up and down |
| Susanoo | Around you | A translucent rib cage, still rather than animated |

Textures come from the backend's CDN when it has one for a cosmetic, and from the mod's own
resources otherwise, so the ones that ship with the client work whether or not the CDN is
reachable.

### Cosmetics menu

Open it in world with Right Ctrl. It previews your own player, rotating to follow the cursor,
lists the catalogue, and carries the presence column on the right. Click a row to toggle that
cosmetic on or off: the preview updates at once and the change is saved to the backend in the
background. Because the preview reuses the same renderer other players see, what you set here is
what they see too. The menu needs a world to preview a player, so it only opens once you are in
game.

The presence column is where you write your status. Type it into the field and press enter, or
just close the menu, either sends it. Clearing the field clears your status. Below it is the same
roster the OnlineList HUD shows, so you can see who is around without turning that module on.

## Opening the menu

The click GUI key defaults to Right Shift, the cosmetics menu to Right Ctrl, and the HUD editor to
Right Alt. The click GUI shows the other two along its bottom edge, so there is one key to
remember rather than three. Module hotkeys can be set per module in the GUI (right click a module
to expand it, then bind a key). Hotkeys never fire while a screen is open, so typing in chat is
safe.
