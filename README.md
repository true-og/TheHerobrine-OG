# The Herobrine!

This is my remake of HiveMC's "The Herobrine!" v2 game-mode for Purpur `1.19.4`, using `MyWorlds` for lobby and round world management.

## Runtime Requirements
- Purpur `1.19.4`
- `ProtocolLib`
- `MyWorlds`
- Redis/KeyDB for saved kit selections
- MariaDB/MySQL for persistent statistics

## Database Requirements
The Herobrine requires 2 databases: a MySQL DB and a Redis DB. Redis is used for storing player kit selections, as it is fast. Player statistics are stored in a MySQL database.

### MySQL Setup
The plugin connects to a database called `theherobrine` and ensures the `hb_stat` table exists on startup. The table contains:
- `uuid`, type varchar(36) and unique 
- `points`, type int
- `captures`, type int
- `kills`, type int
- `deaths`, type int

If SQL or Redis cannot be initialized, the plugin now disables itself during startup instead of failing later during joins or round shutdown.

## Commands and Permissions
- `/hbvote [map id]` or `/hbv [map id]` - vote for a map - no permission
- `/hbjoin <lobby>` - join or spectate a lobby - no permission
- `/hbsetherobrine <player>` - set the player to be Herobrine - `theherobrine.command.setherobrine`
- `/hbforcestart [time]` - force the game to start at a specified time - `theherobrine.command.forcestart`
- `/hbdropshard` - force the shard carrier to drop the shard - `theherobrine.command.dropshard`
- `/hbpausetimer` - pause the start timer - `theherobrine.command.pausetimer`
- `/hbcreatelobby <configuration id>` - create a lobby from a lobby config - `theherobrine.command.createlobby`
- `/hbdeletelobby <lobby id>` - delete a running lobby - `theherobrine.command.deletelobby`
- `/hbspectate` - toggle spectator mode while waiting/starting - `theherobrine.command.spectate`
- `/hbreloadconfigs` - reload lobby configs and rebuild active lobbies - `theherobrine.command.reloadconfigs`

### Kit Permissions
Requiring permissions for classic and unlockable kits can be set in the config.yml.

- Archer - theherobrine.kit.classic.archer
- Priest - theherobrine.kit.classic.priest
- Scout - theherobrine.kit.classic.scout
- Wizard - theherobrine.kit.classic.wizard

- Mage - theherobrine.kit.unlockable.mage
- Paladin - theherobrine.kit.unlockable.paladin
- Sorcerer - theherobrine.kit.unlockable.sorcerer

### Other Permissions
- theherobrine.overfill - allow players to join above the limit 

## Map Setup
Your base server directory should include a folder called `maps` (or whatever is set in `config.yml` as `mapBase`).

Within that folder, each lobby configuration expects a `<config-id>.yaml` file. For example, the default lobby config uses `maps/default.yaml`.

This is how a demo `default.yaml` should look:
```yaml
maps:
  - map1
  - map2
  - map3
```

In the same `maps` directory, there should be a folder for each map listed in `<config-id>.yaml`, plus a `hub` world template.

- `maps/hub` must be a valid world folder and include `level.dat`
- `maps/<map-name>` must be a valid world folder and include `mapdata.yaml`

`mapdata.yaml` contains map information and the location of the survivor spawn, Herobrine spawn, altar, and shard spawns.
This is what an example file should look like:
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

If the hub world, the per-config map list, or all voting maps are missing/invalid, the lobby will now be rejected instead of being registered in a broken state.
