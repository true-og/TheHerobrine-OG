# The Herobrine!

Remake of HiveMC's "The Herobrine!" v2 for Purpur `1.19.4`, using `MyWorlds` for world management.

## Requirements
- Purpur `1.19.4`
- `ProtocolLib`
- `MyWorlds`
- Redis/KeyDB (stores kit selections)
- MariaDB/MySQL (stores player statistics)

## Quick Setup

1. Install the required plugins and configure your database connections in `config.yml`.
2. Drop map world folders into your `maps/` directory (or whatever path is set as `mapBase` in `config.yml`).
3. Each map needs a `mapdata.yaml` file — use `/hbsetspawn` to place all required points and the wizard will generate it for you.
4. Create a `maps/hub` world folder to serve as each lobby's waiting area.
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

Admins can place physical signs in any persistent world (such as the server hub) that players right-click to join a game. The sign automatically shows the lobby name and current status.

To create one, place a sign with `[Herobrine]` on the first line and a lobby config ID on the second line. Requires the `theherobrine.signs.create` permission.

## Commands

### Player Commands
| Command | Description |
|---------|-------------|
| `/vote [map]` or `/v [map]` | Vote for a map during the voting phase |
| `/hbjoin <lobby>` | Join or spectate a lobby by ID |
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
