# Changelog

All notable changes to TheHerobrine-OG are documented here.

## 1.5.8 - 2026-07-29

### Changes

- Repair Herobrine's visible-phase combat and phase-three loadout:
  - Equip and select the phase-three sword in hotbar slot 0 and place poison
    vials in slot 1.
  - Give Herobrine one normal golden apple in hotbar slot 2.
  - Stop clearing valid KnockbackSync knockback one tick after visible-phase
    hits while retaining knockback immunity during the invisible phases.
  - Preserve arrows as projectile hits with their direction and enchantment
    behavior instead of converting them to synthetic melee hits.
  - Apply game damage only after OldCombatMechanics has accepted a hit, and run
    hit effects only for non-cancelled damage.
- Declare KnockbackSync-OG as a soft dependency so combat listeners are ordered
  consistently when it is installed.
- Identify the active Herobrine in chat with a dedicated red role prefix and
  bold message text.
- Bundle the canonical `joinsigns.yaml` registry and install it on first startup.
- Correct the bundled default lobby's voting-map count.
- Update the GxUI-OG and Utilities-OG integrations.

## 1.5.7 - 2026-06-16

### Changes

- Pin spawned shards to their configured block centers so they cannot drift.
- Add the `/hbwizard` command for an on-demand map setup walkthrough.
- Return joining players to the correct lobby spawn, improve hub-return
  behavior, and preserve real pre-lobby return locations while switching
  between managed worlds.
- Add lobby-local chat delivery and fix duplicate chat messages.
- Route `/vote` and `/v` to Herobrine voting only inside managed lobby worlds.
- Accept full, case-insensitive, and numeric lobby aliases such as `HB1`, `hb1`,
  and `1` in `/hbjoin`.
- Fix players remaining invisible after returning to a lobby.
- Fix managed-world mob cleanup.
- Fix join-sign rendering for 1.8 clients.
- Update GxUI-OG and Utilities-OG.

## 1.5.6 - 2026-05-20

### Changes

- Add persistent, permission-controlled join signs with live lobby status and
  click-to-join behavior.
- Add `/hub` for leaving a lobby or game and returning to the main world.
- Improve join-sign labels and aggregate lobby status rendering.
- Rewrite the README around the current MyWorlds setup and administration flow.

## 1.5.5 - 2026-05-15

### Changes

- Add `/hbsetspawn`, tab completion, and a guided map setup wizard.
- Harden shard spawning, bounds checking, cleanup, and map validation.
- Add configurable Herobrine skin textures for the visible phase.
- Add startup diagnostics that explain invalid maps and failed game starts.
- Expand spectator joining, toggling, navigation, and stat-handling behavior
  while correcting related edge cases.
- Add a void-world generator for first-time map setup.
- Correct several map setup edge cases.
- Prepare managed worlds for MyWorlds multi-world inventories.
- Bundle static default lobby configuration resources.
- Update bundled BKCommonLib, MyWorlds, and ProtocolLib dependencies.

## 1.5.2 - 2026-04-22

### Changes

- Make MyWorlds a required dependency for the Purpur 1.19.4 target.
- Fail startup cleanly when SQL or Redis cannot initialize.
- Validate lobby resources before registering new lobbies.
- Rebuild active lobbies when `/hbreloadconfigs` runs so refreshed lobby
  configuration takes effect immediately.
- Remove ProtocolLib lobby listeners during shutdown and close Redis pools when
  the plugin is disabled.
- Update GxUI-OG and Utilities-OG.

## 1.5.1 - 2026-04-13

### Changes

- Complete the initial MyWorlds compatibility work across plugin and lobby
  lifecycle handling.
- Add optional IllegalStack-OG compatibility, exempting active lobby players
  and reliably removing exemptions when they leave or a lobby shuts down.
- Disable animal and monster spawning in managed game worlds.
- Update the TrueOG APIs and adapt to the newer GxUI-OG interface.

## 1.5.0 - 2026-03-30

### Changes

- Replace Multiverse world management with MyWorlds.
- Remove the bundled Multiverse legacy API and migrate lobby/world operations
  to BKCommonLib and MyWorlds APIs.
- Update the GxUI-OG and Utilities-OG integrations.

## 1.4.0 - 2025-12-12

### Changes

- Introduce the multi-lobby architecture used by the TrueOG deployment.
- Refactor game, world, stat, kit, command, and message state to be scoped per
  lobby instead of relying on a single global game.
- Add commands and completion for creating, deleting, joining, reloading, and
  spectating lobbies.
- Begin the Multiverse 5 compatibility migration.
- Add the Gradle wrapper, automatic formatting, and source-backed GxUI-OG and
  Utilities-OG submodules.
- Modernize the Gradle tooling while retaining the Java 17 target.
- Update runtime and build dependencies throughout 2024 and 2025.

## 1.3.3 - 2024-10-25

### Changes

- Target Java 17 and add the Purpur 1.19.4 API.
- Convert the Gradle build and settings files to Kotlin DSL.
- Produce reproducible archives and modernize Shadow plugin packaging.

## 1.3.2 - 2024-01-15

### Changes

- Update the server target from Minecraft 1.18.2 to 1.19.4.
- Update ProtocolLib, Jackson, and MySQL Connector/J.
- Update packet sound access for the newer ProtocolLib API.

## 1.3.1 - 2023-05-05

### Changes

- Add a spectator compass GUI for teleporting between active players.
- Add per-map minimum and maximum shard Y bounds.
- Destroy out-of-bounds shards, keep spawned shards aligned, and clean up shard
  entities safely.
- Keep invisible-phase Herobrine players invisible after potion interactions.
- Correct the bundled Ancient map data.

## 1.3.0 - 2023-04-11

### Changes

- Rebalance and reorganize kit inventories, armor, and abilities.
- Add permission-aware kit definitions and richer item/ability descriptions.
- Add the start-timer pause command and improve timer behavior as a lobby fills
  or an operator pauses it.
- Add `/v` as a voting alias.
- Improve Herobrine smoke and waiting-state feedback.

## 1.2.0 - 2023-04-08

### Changes

- Hide Herobrine's name tag until the third shard makes Herobrine visible.
- Separate stat tracker shutdown from stat reset so completed game statistics
  are persisted before being cleared.
- Delay end checks after disconnects so Herobrine and survivor outcomes are
  evaluated against updated online state.
- Record disconnects during a live game as deaths.
- Add a configurable point threshold for displaying the DeathBringer rank.
- Add end-of-game diagnostic logging.

## 1.1.0 - 2023-04-07

### Changes

- Add a lobby statistics sidebar for points, captures, kills, and deaths.
- Rebalance Herobrine's weapon damage.
- Add explicit shard-destruction handling when a carrier falls into the void.
- Make spawned and dropped shards invulnerable.
- Correct stat access, lifecycle handling, and shard-destruction narration.
- Restore production defaults of 8 minimum players, 13 maximum players, and a
  90-second start timer.
- Document the MySQL and Redis requirements.

## 1.0.0 - 2023-04-06

### Changes

- Deliver the first tagged, playable remake with complete lobby, map, shard,
  capture, survivor, Herobrine, spectator, voting, and win-state flows.
- Add the Archer, Priest, Scout, Wizard, Mage, Paladin, and Sorcerer kits with
  their associated abilities.
- Add SQL-backed statistics, ranks, points, captures, kills, and deaths.
- Add force-start, Herobrine selection, shard-drop, and vote commands.
- Add scoreboards, colored name tags, map voting, configurable maps, and
  permission-gated kits.
- Refactor the 2021 prototype into the OG server target and rebalance combat,
  shards, kits, abilities, sounds, timers, and scoring.
- Fix Bat Bomb kill attribution, totem behavior, shard handling, game startup,
  world cleanup, and numerous pre-release gameplay edge cases.
