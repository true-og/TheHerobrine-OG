package uk.hotten.herobrine.world;

import java.io.File;
import java.util.ArrayList;
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
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;

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

    @Getter
    private MVWorldManager mvWorldManager;

    private String fileBase;

    @Getter
    private MapBase availableMaps;

    @Getter
    private MapData gameMapData;

    @Getter
    private MultiverseWorld gameWorld;

    @Getter
    private MultiverseWorld hubWorld;

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
        mvWorldManager = LobbyManager.getInstance().getMultiverseCore().getMVWorldManager();

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

            Console.error("Your config '" + gameLobby.getLobbyConfig().getId()
                    + "' is misconfigured. Please ensure 'votingMaps' does not exceed the maximum amount of maps you have for this configuration. You have a maximum of "
                    + maps.size() + " configured.");
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

            Message.send(p, Message.format("&6Vote for a map with /hbv #."));
            Message.send(p, Message.format("&6Map choices up for voting:"));
            int current = 1;
            for (Map.Entry<Integer, VotingMap> e : votingMaps.entrySet()) {

                TextComponent textComponent = Message
                        .legacySerializerAnyCase(Message.format("&6&l" + current + ". &6"
                                + e.getValue().getMapData().getName() + " (&b" + e.getValue().getVotes() + "&6 votes)"))
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Message.legacySerializerAnyCase(
                                        "&6Click here to vote for &b" + e.getValue().getMapData().getName())))
                        .clickEvent(ClickEvent.runCommand("/hbv " + current));
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

    public void loadMap(VotingMap map) {

        Console.info(gameLobby, "Loading map " + map.getMapData().getName());

        File baseDir = resolveBaseDir();
        File toCopy = new File(baseDir, map.getInternalName());
        File currentDir = new File(plugin.getServer().getWorldContainer(),
                gameLobby.getLobbyId() + "-" + map.getInternalName());

        if (!toCopy.exists() || !toCopy.isDirectory()) {

            Console.error(gameLobby, "Map folder does not exist: " + toCopy.getPath());
            return;

        }

        try {

            if (currentDir.exists())
                FileUtils.deleteDirectory(currentDir);
            currentDir.mkdirs();
            FileUtils.copyDirectory(toCopy, currentDir);
            sanitizeWorldFolder(currentDir);

        } catch (Exception e) {

            e.printStackTrace();
            Console.error(gameLobby, "Error copying directory!");
            return;

        }

        String worldName = gameLobby.getLobbyId() + "-" + map.getInternalName();
        boolean ok = mvWorldManager.addWorld(worldName, World.Environment.NORMAL, null, WorldType.NORMAL, false, null);
        if (!ok) {

            Console.error(gameLobby, "Failed to register/load MV game world: " + worldName);
            return;

        }

        gameWorld = mvWorldManager.getMVWorld(worldName);
        if (gameWorld == null || gameWorld.getCBWorld() == null) {

            Console.error(gameLobby, "MV returned null world after addWorld: " + worldName);
            return;

        }

        gameWorld.setAllowAnimalSpawn(false);
        gameWorld.setAllowMonsterSpawn(false);
        gameWorld.setDifficulty(Difficulty.NORMAL);
        gameWorld.setTime("midnight");
        gameWorld.getCBWorld().setGameRule(GameRule.DO_FIRE_TICK, false);
        gameWorld.getCBWorld().setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        gameWorld.getCBWorld().setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);

        gameMapData = map.getMapData();

        for (Datapoint dp : gameMapData.getDatapoints()) {

            Location dLoc = new Location(gameWorld.getCBWorld(), dp.getX(), dp.getY(), dp.getZ());
            switch (dp.getType()) {

                case SURVIVOR_SPAWN -> {

                    survivorSpawn = dLoc;
                    Chunk ch = dLoc.getChunk();
                    ch.load(true);
                    ch.setForceLoaded(true);
                    noUnload.add(ch);

                }
                case HEROBRINE_SPAWN -> {

                    herobrineSpawn = dLoc;
                    Chunk ch = dLoc.getChunk();
                    ch.load(true);
                    ch.setForceLoaded(true);
                    noUnload.add(ch);

                }
                case ALTER -> alter = dLoc;
                case SHARD_SPAWN -> shardSpawns.add(dLoc);
                default -> {

                }

            }

        }

        Console.info(gameLobby, "Finished loading!");

    }

    public void loadHubMap() {

        Console.info(gameLobby, "Loading hub for " + gameLobby.getLobbyId());

        File baseDir = resolveBaseDir();
        File toCopy = new File(baseDir, "hub");
        File currentDir = new File(plugin.getServer().getWorldContainer(), gameLobby.getLobbyId() + "-hub");

        if (!toCopy.exists() || !toCopy.isDirectory()) {

            Console.error(gameLobby, "Hub template folder does not exist: " + toCopy.getPath());
            return;

        }

        File levelDat = new File(toCopy, "level.dat");
        if (!levelDat.exists()) {

            Console.error(gameLobby, "Hub template folder is missing level.dat at: " + levelDat.getPath());
            return;

        }

        try {

            if (currentDir.exists())
                FileUtils.deleteDirectory(currentDir);
            currentDir.mkdirs();
            FileUtils.copyDirectory(toCopy, currentDir);
            sanitizeWorldFolder(currentDir);

        } catch (Exception e) {

            e.printStackTrace();
            Console.error(gameLobby, "Error copying hub directory!");
            return;

        }

        String worldName = gameLobby.getLobbyId() + "-hub";
        boolean ok = mvWorldManager.addWorld(worldName, World.Environment.NORMAL, null, WorldType.NORMAL, false, null);
        if (!ok) {

            Console.error(gameLobby, "Failed to register/load MV hub world: " + worldName);
            return;

        }

        hubWorld = mvWorldManager.getMVWorld(worldName);
        if (hubWorld == null || hubWorld.getCBWorld() == null) {

            Console.error(gameLobby, "MV returned null hub world after addWorld: " + worldName);
            return;

        }

        hubWorld.setAllowAnimalSpawn(false);
        hubWorld.setAllowMonsterSpawn(false);
        hubWorld.setDifficulty(Difficulty.PEACEFUL);
        hubWorld.setTime("midnight");
        Console.info(gameLobby, "Finished loading!");

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

    public void clean() {

        clean(true);

    }

    public void clean(boolean clearVotes) {

        if (gameWorld == null)
            return;

        Console.info(gameLobby, "Cleaning the map...");

        for (Player p : Bukkit.getServer().getOnlinePlayers()) {

            if (p.getWorld() != null && p.getWorld().getName().equals(gameWorld.getName())) {

                World main = Bukkit.getServer().getWorld("world");
                if (main == null)
                    main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (main != null)
                    p.teleport(main.getSpawnLocation());

            }

        }

        mvWorldManager.deleteWorld(gameWorld.getName());

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

        for (Player p : Bukkit.getServer().getOnlinePlayers()) {

            if (p.getWorld() != null && p.getWorld().getName().equals(hubWorld.getName())) {

                World main = Bukkit.getServer().getWorld("world");
                if (main == null)
                    main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (main != null)
                    p.teleport(main.getSpawnLocation());

            }

        }

        mvWorldManager.deleteWorld(hubWorld.getName());
        hubWorld = null;

    }

}