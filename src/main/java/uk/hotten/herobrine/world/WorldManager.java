package uk.hotten.herobrine.world;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.mw.WorldConfig;
import com.bergerkiller.bukkit.mw.WorldInventory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.Getter;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import uk.hotten.herobrine.events.GameStateUpdateEvent;
import uk.hotten.herobrine.game.runnables.MapVotingRunnable;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.utils.Console;
import uk.hotten.herobrine.utils.GameState;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.world.data.Datapoint;
import uk.hotten.herobrine.world.data.MapBase;
import uk.hotten.herobrine.world.data.MapData;
import uk.hotten.herobrine.world.data.VotingMap;

public class WorldManager implements Listener {

    private JavaPlugin plugin;
    private GameLobby gameLobby;

    private String fileBase;

    @Getter
    private MapBase availableMaps;

    @Getter
    private MapData gameMapData;

    @Getter
    private World gameWorld;

    @Getter
    private World hubWorld;

    @Getter
    private HashMap<Integer, VotingMap> votingMaps;

    @Getter
    private HashMap<Player, Integer> playerVotes;

    private int maxVotingMaps;

    @Getter
    private int endVotingAt;

    @Getter
    private boolean votingRunning = false;

    public Location herobrineSpawn;
    public Location survivorSpawn;
    public Location alter;
    public ArrayList<Location> shardSpawns;

    private ArrayList<Chunk> noUnload;

    public WorldManager(JavaPlugin plugin, GameLobby gameLobby) {

        Console.info(gameLobby, "Loading World Manager...");
        this.plugin = plugin;
        this.gameLobby = gameLobby;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        fileBase = plugin.getConfig().getString("mapBase");
        maxVotingMaps = gameLobby.getLobbyConfig().getVotingMaps();
        endVotingAt = gameLobby.getLobbyConfig().getEndVotingAt();

        votingMaps = new HashMap<>();
        playerVotes = new HashMap<>();

        shardSpawns = new ArrayList<>();
        noUnload = new ArrayList<>();

        loadHubMap();
        loadMapBase();
        Console.info(gameLobby, "World manager is ready!");

    }

    private File resolveBaseDir() {

        if (fileBase == null || fileBase.isBlank()) {

            return plugin.getServer().getWorldContainer();

        }

        File f = new File(fileBase);
        if (f.isAbsolute())
            return f;
        return new File(plugin.getServer().getWorldContainer(), fileBase);

    }

    // Vanilla main worlds the plugin must never load/unload/delete or modify
    // gamerules on.
    // Cross-world player teleport is still handled by MyWorlds; we just refuse to
    // manage these
    // worlds ourselves so we cannot corrupt the server's overworld dimensions.
    public static boolean isProtectedMainWorld(String worldName) {

        if (worldName == null)
            return false;
        return worldName.equalsIgnoreCase("world") || worldName.equalsIgnoreCase("world_nether")
                || worldName.equalsIgnoreCase("world_the_end");

    }

    private ObjectMapper newYamlMapper() {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;

    }

    private void sanitizeWorldFolder(File worldDir) {

        if (worldDir == null)
            return;
        try {

            File uid = new File(worldDir, "uid.dat");
            if (uid.exists())
                FileUtils.forceDelete(uid);
            File lock = new File(worldDir, "session.lock");
            if (lock.exists())
                FileUtils.forceDelete(lock);

        } catch (Exception ignored) {

        }

    }

    // Force-unload an existing MyWorlds world by name so its world folder can be
    // safely
    // deleted/replaced. Auto-loaded worlds from a previous server boot otherwise
    // hold
    // open file handles AND cause WorldConfig.loadWorld() to return the stale
    // instance
    // instead of the freshly copied data.
    private boolean forceUnloadIfPresent(String worldName) {

        if (isProtectedMainWorld(worldName)) {

            Console.error(gameLobby,
                    "Refusing to unload protected main world '" + worldName + "' -- TheHerobrine-OG never touches it.");
            return false;

        }

        World existing = Bukkit.getWorld(worldName);
        if (existing == null) {

            Console.debug(gameLobby, "No live world named '" + worldName + "' to unload.");
            return true;

        }

        if (!existing.getPlayers().isEmpty()) {

            World main = Bukkit.getWorld("world");
            if (main == null && !Bukkit.getWorlds().isEmpty())
                main = Bukkit.getWorlds().get(0);
            if (main != null && main != existing) {

                Console.info(gameLobby, "Evacuating " + existing.getPlayers().size() + " player(s) from '" + worldName
                        + "' to '" + main.getName() + "' before unload.");
                Location fallbackDest = main.getSpawnLocation();
                LobbyManager evacuateLm = LobbyManager.getInstance();
                for (Player p : new ArrayList<>(existing.getPlayers())) {

                    Location savedLoc = evacuateLm != null ? evacuateLm.getAndRemovePreJoinLocation(p.getUniqueId())
                            : null;
                    p.teleport(savedLoc != null && savedLoc.getWorld() != null ? savedLoc : fallbackDest);

                }

            } else {

                Console.error(gameLobby,
                        "Cannot unload world '" + worldName + "': players present and no fallback world available.");
                return false;

            }

        }

        WorldConfig wc = WorldConfig.get(worldName);
        boolean unloaded = wc.unloadWorld();
        if (!unloaded)
            Console.error(gameLobby, "Failed to unload world '" + worldName + "' before recreating.");
        else
            Console.info(gameLobby, "Unloaded existing world '" + worldName + "' before recreating.");
        return unloaded;

    }

    public void loadMapBase() {

        File baseDir = resolveBaseDir();
        File file = new File(baseDir, gameLobby.getLobbyConfig().getId() + ".yaml");

        if (!file.exists()) {

            Console.error(gameLobby, "No map base file found: " + file.getPath());
            Console.error(gameLobby,
                    "Expected format: maps: - map1 - map2 (where each map folder contains mapdata.yaml)");
            return;

        }

        try {

            availableMaps = newYamlMapper().readValue(file, MapBase.class);

        } catch (Exception e) {

            Console.error(gameLobby, "Error parsing map base file: " + file.getPath());
            e.printStackTrace();
            return;

        }

        if (availableMaps == null || availableMaps.getMaps() == null || availableMaps.getMaps().isEmpty()) {

            Console.error(gameLobby, "Map base loaded but contains 0 maps: " + file.getPath());
            Console.error(gameLobby,
                    "Expected format: maps: - map1 - map2 (map folders under " + baseDir.getPath() + ")");
            return;

        }

        Console.info(gameLobby, "Map base loaded!");
        pickVotingMaps(false);

    }

    public void pickVotingMaps(boolean startRunnable) {

        if (availableMaps == null || availableMaps.getMaps() == null || availableMaps.getMaps().isEmpty()) {

            Console.error(gameLobby, "Cannot pick voting maps: map base is empty.");
            return;

        }

        List<String> maps = new ArrayList<>(availableMaps.getMaps());
        int reps = 0;

        if (maps.size() < maxVotingMaps) {

            Console.info(gameLobby,
                    "Only " + maps.size() + " map(s) available for config '" + gameLobby.getLobbyConfig().getId()
                            + "'. Capping votingMaps from " + maxVotingMaps + " to " + maps.size() + ".");
            maxVotingMaps = maps.size();

        }

        votingMaps.clear();

        while (reps < maxVotingMaps && !maps.isEmpty()) {

            Random rand = new Random();
            String map = maps.get(rand.nextInt(maps.size()));
            Console.debug(gameLobby, "Map -> " + map);

            File baseDir = resolveBaseDir();
            File file = new File(new File(baseDir, map), "mapdata.yaml");

            MapData mapData;
            try {

                mapData = newYamlMapper().readValue(file, MapData.class);
                Console.debug(gameLobby, "Parsed map data id " + (reps + 1));

            } catch (Exception e) {

                Console.error(gameLobby, "Error parsing mapdata.yaml for map '" + map + "': " + file.getPath());
                e.printStackTrace();
                maps.remove(map);
                continue;

            }

            votingMaps.put(reps + 1, new VotingMap((reps + 1), mapData, map));
            maps.remove(map);
            reps++;

        }

        if (votingMaps.isEmpty()) {

            Console.error(gameLobby, "No valid maps could be loaded from the map base.");
            return;

        }

        votingRunning = true;
        if (startRunnable)
            new MapVotingRunnable(gameLobby).runTaskTimer(plugin, 0, 20);
        Console.info(gameLobby, "Picked voting maps!");

    }

    public void sendVotingMessage(Player player) {

        ArrayList<Player> toSend = new ArrayList<>();
        if (player == null)
            toSend.addAll(gameLobby.getPlayers());
        else
            toSend.add(player);

        for (Player p : toSend) {

            Message.send(p, Message.format("&6Vote for a map with /v #."));
            Message.send(p, Message.format("&6Map choices up for voting:"));
            int current = 1;
            for (Map.Entry<Integer, VotingMap> e : votingMaps.entrySet()) {

                TextComponent textComponent = Message
                        .legacySerializerAnyCase(Message.format("&6&l" + current + ". &6"
                                + e.getValue().getMapData().getName() + " (&b" + e.getValue().getVotes() + "&6 votes)"))
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Message.legacySerializerAnyCase(
                                        "&6Click here to vote for &b" + e.getValue().getMapData().getName())))
                        .clickEvent(ClickEvent.runCommand("/v " + current));
                p.sendMessage(textComponent);
                current++;

            }

            p.sendMessage(" ");

        }

    }

    public void selectAndLoadMapFromVote() {

        VotingMap highest = null;
        int highestInt = -1;
        for (Map.Entry<Integer, VotingMap> e : votingMaps.entrySet()) {

            if (e.getValue().getVotes() > highestInt) {

                highest = e.getValue();
                highestInt = e.getValue().getVotes();

            }

        }

        if (highest == null) {

            Console.error(gameLobby, "No map could be selected from voting (empty voting map list).");
            votingRunning = false;
            return;

        }

        Console.debug(gameLobby, "Selected highest voted map -> " + highest.getMapData().getName());
        Message.broadcast(gameLobby,
                Message.format("&6Voting has ended! The map &b" + highest.getMapData().getName() + "&6 has won!"));
        votingRunning = false;

        VotingMap finalHighest = highest;
        Bukkit.getScheduler().runTask(plugin, () -> loadMap(finalHighest));

    }

    // Scans the world dir's region/ folder for r.X.Z.mca files, then samples chunks
    // (centre-out) to find the first one with non-air content. Returns a Location
    // one block above the highest non-air block for safe teleport. Null if no saved
    // chunks contain content (worth letting the caller leave spawn alone).
    private Location pickSavedRegionSpawn(World world, File worldDir) {

        File regionDir = new File(worldDir, "region");
        if (!regionDir.exists() || !regionDir.isDirectory())
            return null;

        java.util.regex.Pattern p = java.util.regex.Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");
        int minRX = Integer.MAX_VALUE, maxRX = Integer.MIN_VALUE;
        int minRZ = Integer.MAX_VALUE, maxRZ = Integer.MIN_VALUE;
        java.util.List<int[]> regions = new java.util.ArrayList<>();
        File[] files = regionDir.listFiles();
        if (files == null)
            return null;
        for (File f : files) {

            // Skip empty stub region files -- mca starts with an 8 KiB header.
            if (f.length() < 8192)
                continue;
            java.util.regex.Matcher m = p.matcher(f.getName());
            if (!m.matches())
                continue;
            int rx = Integer.parseInt(m.group(1));
            int rz = Integer.parseInt(m.group(2));
            regions.add(new int[] { rx, rz });
            if (rx < minRX)
                minRX = rx;
            if (rx > maxRX)
                maxRX = rx;
            if (rz < minRZ)
                minRZ = rz;
            if (rz > maxRZ)
                maxRZ = rz;

        }

        if (regions.isEmpty())
            return null;

        int centerRX = (minRX + maxRX) / 2;
        int centerRZ = (minRZ + maxRZ) / 2;

        // Sort regions by distance to bbox center so we sample inside-out.
        final int crx = centerRX;
        final int crz = centerRZ;
        regions.sort((a, b) -> {

            int da = (a[0] - crx) * (a[0] - crx) + (a[1] - crz) * (a[1] - crz);
            int db = (b[0] - crx) * (b[0] - crx) + (b[1] - crz) * (b[1] - crz);
            return Integer.compare(da, db);

        });

        int minHeight = world.getMinHeight();

        // Sample a sparse 4x4 grid of chunks per region, centre-out, looking for
        // any chunk whose top column is non-air. Limits cost per loadMap.
        int[] sampleOffsets = { 16, 8, 24, 4, 20, 12, 28, 0 };
        for (int[] r : regions) {

            int regionBaseChunkX = r[0] * 32;
            int regionBaseChunkZ = r[1] * 32;
            for (int dx : sampleOffsets) {

                for (int dz : sampleOffsets) {

                    int cx = regionBaseChunkX + dx;
                    int cz = regionBaseChunkZ + dz;
                    Chunk c;
                    try {

                        c = world.getChunkAt(cx, cz);
                        c.load(true);

                    } catch (Throwable t) {

                        continue;

                    }

                    int blockX = (cx << 4) + 8;
                    int blockZ = (cz << 4) + 8;
                    int y = world.getHighestBlockYAt(blockX, blockZ);
                    if (y > minHeight) {

                        Console.info(gameLobby, "Found content in chunk " + cx + "," + cz + " (region " + r[0] + ","
                                + r[1] + ") -- top block at y=" + y);
                        c.setForceLoaded(true);
                        noUnload.add(c);
                        return new Location(world, blockX + 0.5, y + 1, blockZ + 0.5);

                    }

                }

            }

        }

        return null;

    }

    public void loadMap(VotingMap map) {

        String worldName = gameLobby.getLobbyId() + "-" + map.getInternalName();
        Console.info(gameLobby, "Loading map '" + map.getMapData().getName() + "' as world '" + worldName + "'");

        if (isProtectedMainWorld(worldName)) {

            Console.error(gameLobby,
                    "Refusing to load map into protected main world '" + worldName + "'. Pick a different lobby id.");
            return;

        }

        File baseDir = resolveBaseDir();
        File toCopy = new File(baseDir, map.getInternalName());
        File currentDir = new File(plugin.getServer().getWorldContainer(), worldName);

        if (!toCopy.exists() || !toCopy.isDirectory()) {

            Console.error(gameLobby, "Map folder does not exist: " + toCopy.getPath());
            return;

        }

        // Crucial: an auto-loaded leftover world from a previous server boot will hold
        // file handles AND wc.loadWorld() will short-circuit to the stale instance.
        if (!forceUnloadIfPresent(worldName)) {

            Console.error(gameLobby, "Aborting map load: could not unload existing world '" + worldName + "'.");
            return;

        }

        try {

            if (currentDir.exists()) {

                Console.info(gameLobby, "Removing previous world directory: " + currentDir.getPath());
                FileUtils.deleteDirectory(currentDir);

            }

            currentDir.mkdirs();
            Console.info(gameLobby, "Copying map template " + toCopy.getPath() + " -> " + currentDir.getPath());
            FileUtils.copyDirectory(toCopy, currentDir);
            sanitizeWorldFolder(currentDir);

        } catch (Exception e) {

            e.printStackTrace();
            Console.error(gameLobby, "Error copying map directory '" + toCopy.getPath() + "' to '"
                    + currentDir.getPath() + "': " + e.getMessage());
            return;

        }

        WorldConfig wc = WorldConfig.get(worldName);
        // Force void generation for any chunks not already saved in the source's
        // region/ folder. Source maps from WorldDownloader typically have
        // generator=minecraft:flat in level.dat, which surprises admins teleporting
        // outside the captured area with a sea of grass+bedrock.
        wc.setChunkGeneratorName(plugin.getName() + ":void");
        World world;
        try {

            world = wc.loadWorld();

        } catch (Exception e) {

            e.printStackTrace();
            Console.error(gameLobby, "Exception during MyWorlds loadWorld for '" + worldName + "': " + e.getMessage());
            return;

        }

        if (world == null) {

            Console.error(gameLobby,
                    "Failed to load MyWorlds game world: " + worldName + " (WorldConfig.loadWorld returned null).");
            return;

        }

        gameWorld = world;
        Console.info(gameLobby, "Game world '" + worldName + "' loaded with void chunk-generator override.");

        // Share inventory between this lobby's hub and game world so the player
        // does not get an inventory swap when teleported hub -> game at round
        // start. Both stay isolated from the server's main-world bundle.
        shareInventory(gameLobby.getLobbyId() + "-hub", worldName);

        // Move world spawn into a saved chunk so /world tp lands an admin in real
        // terrain, not at the level.dat origin (often empty).
        Location savedSpawn = pickSavedRegionSpawn(world, currentDir);
        if (savedSpawn != null) {

            world.setSpawnLocation(savedSpawn);
            Console.info(gameLobby, "World spawn moved into saved region at " + savedSpawn.getBlockX() + ","
                    + savedSpawn.getBlockY() + "," + savedSpawn.getBlockZ() + " for editing convenience.");

        } else {

            Console.info(gameLobby, "No saved region/ data found; world spawn left unchanged.");

        }

        wc.spawnControl.setAnimals(false);
        wc.spawnControl.setMonsters(false);
        wc.difficulty = Difficulty.NORMAL;
        wc.updateDifficulty(world);
        wc.timeControl.setTime(18000);
        wc.timeControl.setLocking(true);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);

        gameMapData = map.getMapData();
        survivorSpawn = null;
        herobrineSpawn = null;
        alter = null;
        shardSpawns.clear();

        if (gameMapData.getDatapoints() == null || gameMapData.getDatapoints().isEmpty()) {

            Console.error(gameLobby, "Map '" + map.getMapData().getName() + "' has no datapoints in mapdata.yaml!");
            return;

        }

        int survivorCount = 0;
        int herobrineCount = 0;
        int shardCountDp = 0;
        int alterCount = 0;
        for (Datapoint dp : gameMapData.getDatapoints()) {

            Location dLoc = new Location(gameWorld, dp.getX(), dp.getY(), dp.getZ());
            switch (dp.getType()) {

                case SURVIVOR_SPAWN -> {

                    survivorSpawn = dLoc;
                    survivorCount++;
                    Chunk ch = dLoc.getChunk();
                    ch.load(true);
                    ch.setForceLoaded(true);
                    noUnload.add(ch);

                }
                case HEROBRINE_SPAWN -> {

                    herobrineSpawn = dLoc;
                    herobrineCount++;
                    Chunk ch = dLoc.getChunk();
                    ch.load(true);
                    ch.setForceLoaded(true);
                    noUnload.add(ch);

                }
                case ALTER -> {

                    alter = dLoc;
                    alterCount++;

                }
                case SHARD_SPAWN -> {

                    shardSpawns.add(dLoc);
                    shardCountDp++;

                }
                default -> {

                }

            }

        }

        Console.info(gameLobby, "Datapoints parsed: survivor=" + survivorCount + " herobrine=" + herobrineCount
                + " alter=" + alterCount + " shards=" + shardCountDp);

        if (survivorSpawn == null)
            Console.error(gameLobby, "Map '" + map.getMapData().getName()
                    + "' is missing SURVIVOR_SPAWN datapoint -- survivors will not be teleported.");
        if (herobrineSpawn == null)
            Console.error(gameLobby, "Map '" + map.getMapData().getName()
                    + "' is missing HEROBRINE_SPAWN datapoint -- Herobrine will not be teleported.");
        if (alter == null)
            Console.error(gameLobby,
                    "Map '" + map.getMapData().getName() + "' is missing ALTER datapoint -- shard captures will fail.");
        if (shardSpawns.isEmpty())
            Console.error(gameLobby, "Map '" + map.getMapData().getName()
                    + "' has no SHARD_SPAWN datapoints -- shards will never spawn.");
        else if (shardSpawns.size() < MapSetupWizard.MIN_SHARD_SPAWNS)
            Console.error(gameLobby,
                    "Map '" + map.getMapData().getName() + "' has only " + shardSpawns.size()
                            + " SHARD_SPAWN datapoint(s); a minimum of " + MapSetupWizard.MIN_SHARD_SPAWNS
                            + " is recommended so shards do not respawn at the same location every round."
                            + " Run /hbsetspawn shard at additional spawns to fix.");
        if (Double.isNaN(gameMapData.getShardMin()))
            Console.error(gameLobby,
                    "Map '" + map.getMapData().getName() + "' is missing shardMin -- shards will be destroyed on spawn."
                            + " Run /hbsetspawn shardmin during map setup.");
        if (Double.isNaN(gameMapData.getShardMax()))
            Console.error(gameLobby,
                    "Map '" + map.getMapData().getName() + "' is missing shardMax -- shards will be destroyed on spawn."
                            + " Run /hbsetspawn shardmax during map setup.");
        if (!Double.isNaN(gameMapData.getShardMin()) && !Double.isNaN(gameMapData.getShardMax())
                && gameMapData.getShardMin() >= gameMapData.getShardMax())
            Console.error(gameLobby,
                    "Map '" + map.getMapData().getName() + "' has shardMin (" + gameMapData.getShardMin()
                            + ") >= shardMax (" + gameMapData.getShardMax()
                            + "); shards will be destroyed on every tick.");

        Console.info(gameLobby, "Finished loading map '" + map.getMapData().getName() + "'.");

    }

    public void loadHubMap() {

        String worldName = gameLobby.getLobbyId() + "-hub";
        Console.info(gameLobby, "Loading hub for " + gameLobby.getLobbyId() + " as world '" + worldName + "'");

        if (isProtectedMainWorld(worldName)) {

            Console.error(gameLobby,
                    "Refusing to load hub into protected main world '" + worldName + "'. Pick a different lobby id.");
            return;

        }

        File baseDir = resolveBaseDir();
        File toCopy = new File(baseDir, "hub");
        File currentDir = new File(plugin.getServer().getWorldContainer(), worldName);

        if (!toCopy.exists() || !toCopy.isDirectory()) {

            Console.error(gameLobby, "Hub template folder does not exist: " + toCopy.getPath());
            return;

        }

        File levelDat = new File(toCopy, "level.dat");
        if (!levelDat.exists()) {

            Console.error(gameLobby, "Hub template folder is missing level.dat at: " + levelDat.getPath());
            return;

        }

        if (!forceUnloadIfPresent(worldName)) {

            Console.error(gameLobby, "Aborting hub load: could not unload existing hub world '" + worldName + "'.");
            return;

        }

        try {

            if (currentDir.exists()) {

                Console.info(gameLobby, "Removing previous hub directory: " + currentDir.getPath());
                FileUtils.deleteDirectory(currentDir);

            }

            currentDir.mkdirs();
            Console.info(gameLobby, "Copying hub template " + toCopy.getPath() + " -> " + currentDir.getPath());
            FileUtils.copyDirectory(toCopy, currentDir);
            sanitizeWorldFolder(currentDir);

        } catch (Exception e) {

            e.printStackTrace();
            Console.error(gameLobby, "Error copying hub directory '" + toCopy.getPath() + "' to '"
                    + currentDir.getPath() + "': " + e.getMessage());
            return;

        }

        WorldConfig wc = WorldConfig.get(worldName);
        wc.setChunkGeneratorName(plugin.getName() + ":void");
        World world;
        try {

            world = wc.loadWorld();

        } catch (Exception e) {

            e.printStackTrace();
            Console.error(gameLobby,
                    "Exception during MyWorlds loadWorld for hub '" + worldName + "': " + e.getMessage());
            return;

        }

        if (world == null) {

            Console.error(gameLobby,
                    "Failed to load MyWorlds hub world: " + worldName + " (WorldConfig.loadWorld returned null).");
            return;

        }

        hubWorld = world;
        Console.info(gameLobby, "Hub world '" + worldName + "' loaded with void chunk-generator override.");

        // Detach the hub from any pre-existing MyWorlds inventory bundle (most
        // importantly the server's main-world bundle) so PlayerUtil.clearInventory
        // calls inside this lobby cannot wipe a player's main-world inventory.
        isolateInventory(worldName);

        Location savedHubSpawn = pickSavedRegionSpawn(world, currentDir);
        if (savedHubSpawn != null) {

            world.setSpawnLocation(savedHubSpawn);
            Console.info(gameLobby, "Hub spawn moved into saved region at " + savedHubSpawn.getBlockX() + ","
                    + savedHubSpawn.getBlockY() + "," + savedHubSpawn.getBlockZ() + ".");

        }

        wc.spawnControl.setAnimals(true);
        wc.spawnControl.setMonsters(true);
        wc.difficulty = Difficulty.PEACEFUL;
        wc.updateDifficulty(world);
        wc.timeControl.setTime(18000);
        wc.timeControl.setLocking(true);
        Console.info(gameLobby, "Finished loading hub.");

    }

    @EventHandler
    public void gameStart(GameStateUpdateEvent event) {

        if (!gameLobby.getLobbyId().equals(event.getLobbyId()))
            return;

        if (event.getNewState() == GameState.LIVE)
            Bukkit.getServer().getScheduler().runTaskLater(plugin, () -> {

                for (Chunk c : noUnload)
                    c.setForceLoaded(false);
                noUnload.clear();

            }, 100);

    }

    /**
     * Detaches the named world from any MyWorlds inventory bundle so it has its own
     * isolated inventory storage. Used on lobby world creation to keep the
     * main-world inventory separate, and again on deletion to avoid stale entries
     * accumulating in MyWorlds' inventories.yml.
     */
    private void isolateInventory(String worldName) {

        try {

            WorldInventory.detach(Collections.singletonList(worldName));
            WorldInventory.save();
            Console.info(gameLobby, "MyWorlds inventory bundle isolated for '" + worldName + "'.");

        } catch (Throwable t) {

            Console.error(gameLobby, "Failed to isolate MyWorlds inventory for '" + worldName + "': " + t.getMessage());
            t.printStackTrace();

        }

    }

    /**
     * Merges the given worlds into a single MyWorlds inventory bundle so a player
     * carrying lobby inventory between them (hub -> game) does not get an inventory
     * swap mid-match. Auto-saves.
     */
    private void shareInventory(String... worldNames) {

        try {

            WorldInventory.merge(Arrays.asList(worldNames));
            Console.info(gameLobby, "MyWorlds inventory bundle merged: " + String.join(", ", worldNames));

        } catch (Throwable t) {

            Console.error(gameLobby, "Failed to merge MyWorlds inventory bundle: " + t.getMessage());
            t.printStackTrace();

        }

    }

    public void clean() {

        clean(true);

    }

    public void clean(boolean clearVotes) {

        if (gameWorld == null)
            return;

        String gameWorldName = gameWorld.getName();

        if (isProtectedMainWorld(gameWorldName)) {

            Console.error(gameLobby, "Refusing to clean protected main world '" + gameWorldName
                    + "'. Dropping the reference without touching the world.");
            gameWorld = null;
            gameMapData = null;
            survivorSpawn = null;
            herobrineSpawn = null;
            alter = null;
            shardSpawns = new ArrayList<>();
            noUnload = new ArrayList<>();
            return;

        }

        Console.info(gameLobby, "Cleaning the map...");
        LobbyManager cleanLm = LobbyManager.getInstance();
        for (Player p : Bukkit.getServer().getOnlinePlayers()) {

            if (p.getWorld() != null && p.getWorld().getName().equals(gameWorldName)) {

                Location cleanSaved = cleanLm != null ? cleanLm.getAndRemovePreJoinLocation(p.getUniqueId()) : null;
                if (cleanSaved != null && cleanSaved.getWorld() != null) {

                    p.teleport(cleanSaved);

                } else {

                    World main = Bukkit.getServer().getWorld("world");
                    if (main == null)
                        main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                    if (main != null)
                        p.teleport(main.getSpawnLocation());

                }

            }

        }

        // Drop the world from any shared MyWorlds inventory bundle before deleting
        // so its entry doesn't haunt inventories.yml after the world dir is gone.
        isolateInventory(gameWorldName);

        // WorldConfig.deleteWorld() refuses if the world is loaded. Unload first.
        WorldConfig wc = WorldConfig.get(gameWorldName);
        if (wc.isLoaded() && !wc.unloadWorld())
            Console.error(gameLobby, "Failed to unload game world '" + gameWorldName + "' before deletion.");
        boolean deleted = wc.deleteWorld();
        if (!deleted)
            Console.error(gameLobby, "WorldConfig.deleteWorld() returned false for '" + gameWorldName
                    + "' -- world directory may persist.");

        gameWorld = null;
        gameMapData = null;
        survivorSpawn = null;
        herobrineSpawn = null;
        alter = null;
        shardSpawns = new ArrayList<>();
        noUnload = new ArrayList<>();

        if (clearVotes) {

            votingMaps = new HashMap<>();
            playerVotes = new HashMap<>();

        } else {

            if (gameLobby.getGameManager().getGameState() == GameState.WAITING) {

                votingRunning = true;
                new MapVotingRunnable(gameLobby).runTaskTimer(plugin, 0, 20);

            }

        }

        Console.info(gameLobby, "Cleaned!");

    }

    public void cleanHub() {

        if (hubWorld == null)
            return;

        String hubWorldName = hubWorld.getName();

        if (isProtectedMainWorld(hubWorldName)) {

            Console.error(gameLobby, "Refusing to clean protected main world '" + hubWorldName
                    + "'. Dropping the reference without touching the world.");
            hubWorld = null;
            return;

        }

        LobbyManager hubCleanLm = LobbyManager.getInstance();
        for (Player p : Bukkit.getServer().getOnlinePlayers()) {

            if (p.getWorld() != null && p.getWorld().getName().equals(hubWorldName)) {

                Location hubSaved = hubCleanLm != null ? hubCleanLm.getAndRemovePreJoinLocation(p.getUniqueId()) : null;
                if (hubSaved != null && hubSaved.getWorld() != null) {

                    p.teleport(hubSaved);

                } else {

                    World main = Bukkit.getServer().getWorld("world");
                    if (main == null)
                        main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                    if (main != null)
                        p.teleport(main.getSpawnLocation());

                }

            }

        }

        // Drop the hub from any MyWorlds inventory bundle before deleting so its
        // entry doesn't haunt inventories.yml after the world dir is gone.
        isolateInventory(hubWorldName);

        WorldConfig hwc = WorldConfig.get(hubWorldName);
        if (hwc.isLoaded() && !hwc.unloadWorld())
            Console.error(gameLobby, "Failed to unload hub world '" + hubWorldName + "' before deletion.");
        boolean hubDeleted = hwc.deleteWorld();
        if (!hubDeleted)
            Console.error(gameLobby, "WorldConfig.deleteWorld() returned false for hub '" + hubWorldName
                    + "' -- world directory may persist.");
        hubWorld = null;

    }

    public boolean isReady() {

        return hubWorld != null && availableMaps != null && availableMaps.getMaps() != null && !votingMaps.isEmpty();

    }

}
