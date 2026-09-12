# The Herobrine!

Remake of HiveMC's "The Herobrine!" v2 for Purpur `1.19.4`, using `MyWorlds` for world management.

Current version: `1.6.1` ([changelog](CHANGELOG.md)).

## Requirements
- Purpur `1.19.4`
- `ProtocolLib`
- `MyWorlds`
- Redis/KeyDB (stores kit selections)
- MariaDB/MySQL (stores player statistics)

Optional, integrated when present: `Chat-OG`, `Scoreboard-OG` `1.2.0` or newer, `IllegalStack-OG`.

## Scoreboards

Every lobby player gets a sidebar: their points, captures, kills and deaths while waiting, then the shard count and survivor count during the round. With `Scoreboard-OG` `1.2.0` or newer installed, the sidebar is drawn through Scoreboard-OG's sidebar API, so the network board comes back on its own the moment the player leaves the lobby and the player's `/togglescoreboard` preference is respected. Without it (or with an older Scoreboard-OG), the bundled ScoreboardLib board is used as before and the main scoreboard is restored on leave. Nametag colours are carried by a per-player Bukkit scoreboard either way.

## Quick Setup

1. Install the required plugins and configure your database connections in `config.yml`. The bundled file ships `^{NAME}` placeholders that TrueOG's deployment fills in; on a manual install replace them with real values or the plugin disables itself at startup.
2. Drop map world folders into your `maps/` directory (or whatever path is set as `mapBase` in `config.yml`).
3. Each map needs a `mapdata.yaml` file — use `/hbsetspawn` to place all required points and the wizard will generate it for you.
4. Create a `maps/HB1_Hub` world folder to serve as each lobby's waiting area (the `hub:` field in `lobbies/default.yaml` names this folder). `maps/` is resolved against the server root, next to `world/` and `plugins/`, not inside the plugin folder.
5. Create a lobby config file at `maps/<config-id>.yaml` listing which maps belong to it.
6. Run `/hbcreatelobby <config-id>` to bring a lobby online.

If anything is missing or misconfigured, the plugin will tell you exactly what needs to be fixed rather than starting in a broken state.

## Lobby Config Format

Create a file at `maps/<config-id>.yaml`:
```yaml
maps:
  - map1
  - map2
  - map3
```

Each map listed must have a folder in `maps/` with a valid `mapdata.yaml` inside.

## Map Data Format

`mapdata.yaml` is generated automatically by `/hbsetspawn`, but here is what it looks like:
```yaml
name: Map 1
builder: Good Builder
shardMin: -100
shardMax: 1000
datapoints:
  - type: SURVIVOR_SPAWN
    x: -2
    y: 156
    z: 925
  - type: HEROBRINE_SPAWN
    x: 110
    y: 140
    z: 854
  - type: ALTER
    x: -6
    y: 157
    z: 925
  - type: SHARD_SPAWN
    x: 110
    y: 149
    z: 834
```

## Join Signs

Admins can place physical signs that players right-click to join a game. The sign updates itself with the lobby name, current status, and player counts; right-clicking joins the best joinable lobby for that config.

To set one up:

1. Find the lobby config ID: the `id:` field of a file in `plugins/TheHerobrine-OG/lobbies/` (the bundled `lobbies/default.yaml` on a fresh install).
2. Place a sign in a persistent world such as the server hub. The plugin warns if you place one in a managed lobby world, because those worlds are deleted on shutdown and take the sign with them.
3. Write `[Herobrine]` on the first line and the lobby config ID on the second line. Requires the `theherobrine.signs.create` permission.
4. The sign confirms registration and redraws with live data shortly after.

Breaking a registered sign requires `theherobrine.signs.destroy` and unregisters it; everyone else is blocked from breaking it.

## Placeholders

Registered through Utilities-OG as MiniPlaceholders, resolved for the viewing
player, and updated the moment the underlying value changes.

| Placeholder | Value |
|-------------|-------|
| `<hb_score>` | Total points, including points earned in the current round |
| `<hb_rank>` | Colored rank name for that score (`Spirit` through `Divine`); `Death Bringer` for the top player once they also reach `Divine` (300,000) |
| `<hb_class>` | `&4THE HEROBRINE` while the player is the Herobrine of a live round, otherwise empty |

## Commands

`/v` and `/vote` are claimed for map voting before any other plugin sees them, for anyone who is in a lobby or standing in a lobby world, so VotingPlugin cannot take the `/vote` label away from map voting. Everywhere else `/vote` behaves normally. Neither label is declared in plugin.yml, so the plugin never competes for them.

### Player Commands
| Command | Description |
|---------|-------------|
| `/vote [map]` or `/v [map]` | Vote for a map during the voting phase |
| `/hbjoin <lobby>` | Join or spectate a lobby by ID (`HB1`, or just `1`) |
| `/hub` | Leave your current lobby and return to the main world |

### Admin Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/hbcreatelobby <config-id>` | `theherobrine.command.createlobby` | Start a new lobby |
| `/hbdeletelobby <lobby-id>` | `theherobrine.command.deletelobby` | Stop and remove a lobby |
| `/hbreloadconfigs` | `theherobrine.command.reloadconfigs` | Reload all lobby config files |
| `/hbsetherobrine <player>` | `theherobrine.command.setherobrine` | Force a player to be Herobrine |
| `/hbforcestart [time]` | `theherobrine.command.forcestart` | Force the game to start |
| `/hbpausetimer` | `theherobrine.command.pausetimer` | Pause the start countdown |
| `/hbdropshard` | `theherobrine.command.dropshard` | Force the shard carrier to drop the shard |
| `/hbspectate` | `theherobrine.command.spectate` | Toggle spectator mode while in the lobby |
| `/hbsetspawn <type> [index] [mapName]` | `theherobrine.command.setspawn` | Place a map datapoint at your location |

## Permissions

### Signs
| Permission | Default | Description |
|------------|---------|-------------|
| `theherobrine.signs.create` | OP | Place `[Herobrine]` join signs |
| `theherobrine.signs.destroy` | OP | Break `[Herobrine]` join signs |

### Kits
Kit permissions can be required or left open in `config.yml`.

| Kit | Permission |
|-----|------------|
| Archer | `theherobrine.kit.classic.archer` |
| Priest | `theherobrine.kit.classic.priest` |
| Scout | `theherobrine.kit.classic.scout` |
| Wizard | `theherobrine.kit.classic.wizard` |
| Mage | `theherobrine.kit.unlockable.mage` |
| Paladin | `theherobrine.kit.unlockable.paladin` |
| Sorcerer | `theherobrine.kit.unlockable.sorcerer` |

### Other
| Permission | Description |
|------------|-------------|
| `theherobrine.overfill` | Join a lobby that is already full |
