package uk.hotten.herobrine.game;

import com.comphenix.protocol.ProtocolManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import me.tigerhix.lib.scoreboard.common.EntryBuilder;
import me.tigerhix.lib.scoreboard.type.Entry;
import me.tigerhix.lib.scoreboard.type.Scoreboard;
import me.tigerhix.lib.scoreboard.type.ScoreboardHandler;
import net.kyori.adventure.text.format.NamedTextColor;
import net.trueog.gxui.GUIItem;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import uk.hotten.herobrine.data.RedisManager;
import uk.hotten.herobrine.events.GameStateUpdateEvent;
import uk.hotten.herobrine.events.ShardCaptureEvent;
import uk.hotten.herobrine.events.ShardStateUpdateEvent;
import uk.hotten.herobrine.game.runnables.CaptureSequence;
import uk.hotten.herobrine.game.runnables.HerobrineItemHider;
import uk.hotten.herobrine.game.runnables.HerobrineSetup;
import uk.hotten.herobrine.game.runnables.HerobrineSmokeRunnable;
import uk.hotten.herobrine.game.runnables.NarrationRunnable;
import uk.hotten.herobrine.game.runnables.ShardHandler;
import uk.hotten.herobrine.game.runnables.StartingRunnable;
import uk.hotten.herobrine.game.runnables.SurvivorSetup;
import uk.hotten.herobrine.game.runnables.WaitingRunnable;
import uk.hotten.herobrine.kit.Kit;
import uk.hotten.herobrine.kit.abilities.BatBombAbility;
import uk.hotten.herobrine.kit.abilities.BlindingAbility;
import uk.hotten.herobrine.kit.abilities.DreamweaverAbility;
import uk.hotten.herobrine.kit.abilities.LocatorAbility;
import uk.hotten.herobrine.kit.kits.ArcherKit;
import uk.hotten.herobrine.kit.kits.MageKit;
import uk.hotten.herobrine.kit.kits.PaladinKit;
import uk.hotten.herobrine.kit.kits.PriestKit;
import uk.hotten.herobrine.kit.kits.ScoutKit;
import uk.hotten.herobrine.kit.kits.SorcererKit;
import uk.hotten.herobrine.kit.kits.WizardKit;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.stat.StatTracker;
import uk.hotten.herobrine.utils.Console;
import uk.hotten.herobrine.utils.GameState;
import uk.hotten.herobrine.utils.HerobrineSkin;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.utils.PlayerUtil;
import uk.hotten.herobrine.utils.ShardState;
import uk.hotten.herobrine.utils.WinType;
import uk.hotten.herobrine.world.WorldManager;

public class GameManager {

    @Getter
    private JavaPlugin plugin;

    private WorldManager worldManager;
    private RedisManager redis;

    @Getter
    private ProtocolManager protocolManager;

    @Getter
    private GameLobby gameLobby;

    @Getter
    private GMListener gmListener;

    @Getter
    private GameState gameState;

    @Getter
    private ShardState shardState;

    @Getter
    private int requiredToStart;

    @Getter
    private int maxPlayers;

    @Getter
    private boolean allowOverfill;

    @Getter
    private String networkName;

    @Getter
    private String networkWeb;

    @Getter
    private Player herobrine;

    private BatBombAbility hbBatBomb;
    private DreamweaverAbility hbDream;
    private BlindingAbility hbBlinding;

    @Getter
    private ArrayList<Player> survivors;

    @Getter
    private ArrayList<Player> spectators;

    @Getter
    private HashSet<UUID> deadSurvivors;

    @Getter
    private ArrayList<Player> hbLastHit;

    @Getter
    @Setter
    private Player passUser;

    @Getter
    private ShardHandler shardHandler;

    @Getter
    @Setter
    private boolean shardPreviousDestroyed;

    @Getter
    public int shardCount;

    @Getter
    @Setter
    private Player shardCarrier;

    public int startTimer;
    public boolean stAlmost = false;
    public boolean stFull = false;
    public boolean timerPaused = false;
    private BukkitTask waitingRunnable;
    private NarrationRunnable narrationRunnable;
    private BukkitTask pvpProtectionTask;

    @Getter
    private volatile boolean pvpProtected;

    @Getter
    private Kit[] kits;

    @Getter
    private Kit defaultKit;

    @Getter
    private HashMap<Player, Kit> playerKits;

    @Getter
    @Setter
    private StatTracker[] statTrackers;

    @Getter
    private HashMap<Player, Scoreboard> scoreboards;

    @Getter
    private HashMap<Player, String> teamPrefixes = new HashMap<>();

    @Getter
    private HashMap<Player, NamedTextColor> teamColours = new HashMap<>();

    private ScoreboardHandler gameScoreboardHandler;

    public GameManager(JavaPlugin plugin, GameLobby gameLobby, RedisManager redis, ProtocolManager protocolManager) {

        Console.info(gameLobby, "Loading Game Manager...");
        this.plugin = plugin;
        this.gameLobby = gameLobby;
        this.worldManager = gameLobby.getWorldManager();
        this.redis = redis;
        this.protocolManager = protocolManager;
        gmListener = new GMListener(this, gameLobby);
        plugin.getServer().getPluginManager().registerEvents(gmListener, plugin);

        gameState = GameState.BOOTING;
        shardState = ShardState.WAITING;

        requiredToStart = gameLobby.getLobbyConfig().getMinPlayers();
        maxPlayers = gameLobby.getLobbyConfig().getMaxPlayers();
        startTimer = gameLobby.getLobbyConfig().getStartTime();
        allowOverfill = gameLobby.getLobbyConfig().isAllowOverfill();
        networkName = plugin.getConfig().getString("networkName");
        networkWeb = plugin.getConfig().getString("networkWeb");
        boolean lockClassicKits = plugin.getConfig().getBoolean("lockClassicKits");
        boolean lockUnlockableKits = plugin.getConfig().getBoolean("lockUnlockableKits");

        shardCount = 0;
        shardPreviousDestroyed = false;

        survivors = new ArrayList<>();
        spectators = new ArrayList<>();
        deadSurvivors = new HashSet<>();
        hbLastHit = new ArrayList<>();

        kits = new Kit[] { new ArcherKit(this, lockClassicKits), new PriestKit(this, lockClassicKits),
                new ScoutKit(this, lockClassicKits), new WizardKit(this, lockClassicKits),
                new MageKit(this, lockUnlockableKits), new PaladinKit(this, lockUnlockableKits),
                new SorcererKit(this, lockUnlockableKits) };

        for (Kit k : kits) {

            if (k.getInternalName().equals("archer"))
                defaultKit = k;

        }

        playerKits = new HashMap<>();

        scoreboards = new HashMap<>();
        gameScoreboardHandler = new ScoreboardHandler() {

            @Override
            public String getTitle(Player player) {

                return "&cThe&lHerobrine!";

            }

            @Override
            public List<Entry> getEntries(Player player) {

                return new EntryBuilder().blank().next("&a✦ Shard Count").next(shardCount + "/3").blank()
                        .next("&a❂ Survivors").next("" + survivors.size()).blank().next("&8--------------")
                        .next("&b" + networkWeb).build();

            }

        };

        startWaiting();
        Console.info(gameLobby, "Game Manager is ready!");

    }

    public void setGameState(GameState newState) {

        new BukkitRunnable() {

            @Override
            public void run() {

                GameState old = gameState;
                if (old == null)
                    old = GameState.UNKNOWN;

                gameState = newState;
                Console.debug(gameLobby,
                        "Game state updated to " + newState.toString() + "(from " + old.toString() + ")!");
                plugin.getServer().getPluginManager()
                        .callEvent(new GameStateUpdateEvent(old, newState, gameLobby.getLobbyId()));

            }

        }.runTask(plugin);

    }

    // Should only be used in GameLobby's shutdown to force the state to dead.
    public void setGameStateSilently(GameState newState) {

        GameState old = gameState;
        gameState = newState;
        Console.debug(gameLobby,
                "Game state silently updated to " + newState.toString() + "(from " + old.toString() + ")!");

    }

    public void setShardState(ShardState newState) {

        new BukkitRunnable() {

            @Override
            public void run() {

                ShardState old = shardState;
                if (old == null)
                    old = ShardState.UNKNOWN;

                shardState = newState;
                Console.debug(gameLobby,
                        "Shard state updated to " + newState.toString() + "(from " + old.toString() + ")!");
                plugin.getServer().getPluginManager()
                        .callEvent(new ShardStateUpdateEvent(old, newState, gameLobby.getLobbyId()));
                if (gameState == GameState.LIVE)
                    narrationRunnable.timer = 0;

            }

        }.runTask(plugin);

    }

    public void startWaiting() {

        startWaiting(true);

    }

    public void startWaiting(boolean cleanGameWorld) {

        cancelPvpProtection();
        setGameState(GameState.WAITING);
        deadSurvivors.clear();
        if (waitingRunnable != null)
            waitingRunnable.cancel();

        // In case the timer decreased from players leaving and a world was loaded
        if (cleanGameWorld)
            Bukkit.getServer().getScheduler().runTask(plugin, () -> gameLobby.getWorldManager().clean(false));
        startTimer = getGameLobby().getLobbyConfig().getStartTime();

        waitingRunnable = new WaitingRunnable(this).runTaskTimerAsynchronously(plugin, 0, 10);

    }

    public boolean canJoin(Player player) {

        if (gameState == GameState.LIVE)
            return true;

        if (getSurvivors().size() >= getMaxPlayers()) {

            if (isAllowOverfill()) {

                if (!player.hasPermission("theherobrine.overfill")) {

                    return false;

                }

            } else {

                return false;

            }

        }

        return true;

    }

    private boolean playerListContains(List<Player> players, Player player) {

        if (player == null)
            return false;
        UUID uuid = player.getUniqueId();
        return players.stream().anyMatch(p -> p.getUniqueId().equals(uuid));

    }

    private boolean removeFromPlayerList(List<Player> players, Player player) {

        if (player == null)
            return false;
        UUID uuid = player.getUniqueId();
        return players.removeIf(p -> p.getUniqueId().equals(uuid));

    }

    public boolean isHerobrine(Player player) {

        return player != null && herobrine != null && herobrine.getUniqueId().equals(player.getUniqueId());

    }

    public boolean isSurvivor(Player player) {

        return playerListContains(survivors, player);

    }

    public boolean isSpectator(Player player) {

        return playerListContains(spectators, player);

    }

    public boolean isDeadSurvivor(Player player) {

        return player != null && deadSurvivors.contains(player.getUniqueId());

    }

    public boolean isShardCarrier(Player player) {

        return player != null && shardCarrier != null && shardCarrier.getUniqueId().equals(player.getUniqueId());

    }

    public void addSurvivor(Player player) {

        if (!isSurvivor(player))
            survivors.add(player);

    }

    public void addSpectator(Player player) {

        if (!isSpectator(player))
            spectators.add(player);

    }

    public boolean removeSurvivor(Player player) {

        return removeFromPlayerList(survivors, player);

    }

    public boolean removeSpectator(Player player) {

        return removeFromPlayerList(spectators, player);

    }

    public void markSurvivorDead(Player player) {

        if (player == null)
            return;
        deadSurvivors.add(player.getUniqueId());
        removeSurvivor(player);

    }

    public void startCheck() {

        if (getSurvivors().size() >= getRequiredToStart() && !timerPaused) {

            if (getGameState() != GameState.STARTING) {

                setGameState(GameState.STARTING);
                new StartingRunnable(this, gameLobby.getWorldManager()).runTaskTimerAsynchronously(getPlugin(), 0, 20);

            } else {

                if (getSurvivors().size() >= getMaxPlayers() - 3 && !stAlmost && startTimer > 30) {

                    Message.broadcast(gameLobby,
                            Message.format("&aWe almost have a full server! Shortening timer to 30 seconds!"));
                    stAlmost = true;
                    startTimer = 30;

                } else if (getSurvivors().size() >= getMaxPlayers() && !stFull && startTimer > 10) {

                    Message.broadcast(gameLobby, Message.format("&aWe have a full server! Starting in 10 seconds!"));
                    stFull = true;
                    startTimer = 10;

                }

            }

        }

    }

    public void start() {

        Console.info(gameLobby, "=== GAME START BEGIN === survivors=" + survivors.size() + " spectators="
                + spectators.size() + " lobbyPlayers=" + gameLobby.getPlayers().size());

        // Pre-flight checks: if the world or spawns aren't ready, the game cannot
        // start.
        // Bail with a loud diagnostic instead of NPE-ing halfway through and leaving
        // players in the hub with potion effects but no teleport.
        if (worldManager.getGameWorld() == null) {

            Console.error(gameLobby, "ABORTING start(): gameWorld is null -- WorldManager.loadMap() did not complete. "
                    + "Check earlier console output for 'Error copying directory' or 'Failed to load MyWorlds game world'.");
            Message.broadcast(gameLobby, Message
                    .format("&cGame failed to start: arena world did not load. See console. Returning to waiting..."));
            startWaiting();
            return;

        }

        final List<String> missingDatapoints = new ArrayList<>();
        if (worldManager.herobrineSpawn == null)
            missingDatapoints.add("HEROBRINE_SPAWN");
        if (worldManager.survivorSpawn == null)
            missingDatapoints.add("SURVIVOR_SPAWN");
        if (worldManager.alter == null)
            missingDatapoints.add("ALTER");
        if (worldManager.shardSpawns == null || worldManager.shardSpawns.isEmpty())
            missingDatapoints.add("SHARD_SPAWN");

        if (!missingDatapoints.isEmpty()) {

            Console.error(gameLobby,
                    "ABORTING start(): missing required map datapoint(s): " + String.join(", ", missingDatapoints)
                            + ". herobrineSpawn=" + worldManager.herobrineSpawn + " survivorSpawn="
                            + worldManager.survivorSpawn + " alter=" + worldManager.alter + " shardSpawns="
                            + (worldManager.shardSpawns != null ? worldManager.shardSpawns.size() : 0)
                            + " -- check the map's mapdata.yaml.");
            Message.broadcast(gameLobby,
                    Message.format("&cGame failed to start: arena map is missing required points."));
            final String gw = worldManager.getGameWorld() != null ? worldManager.getGameWorld().getName() : "<none>";
            Message.broadcast(gameLobby, Message.format("&eOperators: teleport to &b" + gw + "&e via &7/world tp " + gw
                    + "&e and use &7/hbsetspawn <survivor|herobrine|alter|shard>&e to set points."));
            Message.broadcast(gameLobby,
                    Message.format("&eA setup checklist will appear when an operator enters the arena world."));
            // Keep the game world loaded so an op can walk to spawn locations and set them.
            startWaiting(false);
            return;

        }

        if (survivors.isEmpty()) {

            Console.error(gameLobby, "ABORTING start(): no survivors to start with.");
            startWaiting();
            return;

        }

        try {

            setGameState(GameState.LIVE);
            startPvpProtection();
            narrationRunnable = new NarrationRunnable(this);
            narrationRunnable.runTaskTimerAsynchronously(plugin, 0, 10); // has to run before the shardstate updates
            setShardState(ShardState.WAITING);
            gameLobby.getStatManager().startTracking();
            if (passUser != null) {

                herobrine = passUser;
                passUser = null;
                Console.info(gameLobby, "Herobrine assigned via passUser: " + herobrine.getName());

            } else {

                herobrine = PlayerUtil.randomPlayer(gameLobby);
                Console.info(gameLobby, "Herobrine assigned at random: " + herobrine.getName());

            }

            removeSurvivor(herobrine);
            Console.info(gameLobby, "After herobrine pick: survivors=" + survivors.size());

            Console.info(gameLobby, "Calling setupHerobrine()...");
            setupHerobrine();
            Console.info(gameLobby, "Calling setupSurvivors()...");
            setupSurvivors();
            Console.info(gameLobby, "Applying spectator state to " + spectators.size() + " spectator(s)...");
            new ArrayList<>(spectators).forEach(this::makeSpectator);
            new HerobrineSetup(herobrine).runTaskAsynchronously(plugin);
            setTags(herobrine, "&c&lHEROBRINE ", NamedTextColor.RED, ScoreboardUpdateAction.UPDATE);
            for (Player p : survivors) {

                setTags(p, null, NamedTextColor.DARK_GREEN, ScoreboardUpdateAction.UPDATE);
                new SurvivorSetup(p).runTaskAsynchronously(plugin);

            }

            shardHandler = new ShardHandler(gameLobby);
            shardHandler.runTaskTimer(plugin, 0, 20);
            new HerobrineItemHider(this).runTaskTimer(plugin, 0, 1);
            new HerobrineSmokeRunnable(this).runTaskTimer(plugin, 0, 10);

            for (Player p : gameLobby.getPlayers()) {

                scoreboards.get(p).setHandler(gameScoreboardHandler);
                p.setHealth(20);
                p.setFoodLevel(20);
                if (isSpectator(p) || isDeadSurvivor(p))
                    makeSpectator(p);
                else
                    p.setGameMode(GameMode.SURVIVAL);

            }

            updateTags(ScoreboardUpdateAction.UPDATE);
            Console.info(gameLobby, "=== GAME START COMPLETE ===");

        } catch (Throwable t) {

            // Without this, a mid-start exception leaves the lobby in a half-initialized
            // state -- the chat broadcast already fired but no teleports/kits ran.
            Console.error(gameLobby, "EXCEPTION during start(): " + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + " -- see stack trace below.");
            t.printStackTrace();
            Message.broadcast(gameLobby,
                    Message.format("&cGame start crashed mid-sequence. See console. Returning to waiting..."));
            startWaiting();

        }

    }

    public void setupHerobrine() {

        if (worldManager.herobrineSpawn == null) {

            Console.error(gameLobby, "setupHerobrine: herobrineSpawn is null -- cannot teleport "
                    + (herobrine != null ? herobrine.getName() : "<null>") + ".");
            return;

        }

        boolean teleported = herobrine.teleport(worldManager.herobrineSpawn);
        Console.info(gameLobby,
                "setupHerobrine: teleported " + herobrine.getName() + " to "
                        + worldManager.herobrineSpawn.getWorld().getName() + " ("
                        + worldManager.herobrineSpawn.getBlockX() + "," + worldManager.herobrineSpawn.getBlockY() + ","
                        + worldManager.herobrineSpawn.getBlockZ() + ") -> result=" + teleported);
        herobrine.setHealth(20);
        herobrine.setFoodLevel(20);

        // Override visible skin with the configured Herobrine textures so that
        // when invisibility drops (e.g. after the third shard capture) the
        // player actually appears as Herobrine instead of their own skin.
        HerobrineSkin.apply(plugin, herobrine);

        PlayerUtil.addEffect(herobrine, PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false);
        PlayerUtil.addEffect(herobrine, PotionEffectType.JUMP, Integer.MAX_VALUE, 1, false, false);
        PlayerUtil.addEffect(herobrine, PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false);

        updateHerobrine();

    }

    public void updateHerobrine() {

        switch (shardCount) {

            case 0: {

                GUIItem item = new GUIItem(Material.STONE_AXE).displayName("&7The Thorbringer").unbreakable(true);
                herobrine.getInventory().setItem(0, item.build());

                hbBatBomb = new BatBombAbility(this, 1, 4);
                hbBatBomb.apply(herobrine);
                plugin.getServer().getPluginManager().registerEvents(hbBatBomb, plugin);

                giveVials(2, 1);

                hbDream = new DreamweaverAbility(this, 3, 2);
                hbDream.apply(herobrine);
                plugin.getServer().getPluginManager().registerEvents(hbDream, plugin);

                hbBlinding = new BlindingAbility(this, -1, 3);
                plugin.getServer().getPluginManager().registerEvents(hbBlinding, plugin);

                new LocatorAbility(this).apply(herobrine);

                PlayerUtil.addEffect(herobrine, PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2, false, false);
                break;

            }
            case 1: {

                herobrine.getInventory().remove(Material.STONE_AXE);
                GUIItem item = new GUIItem(Material.IRON_AXE).displayName("&7Axe of &lDeceit!").unbreakable(true);
                herobrine.getInventory().addItem(item.build());

                hbBlinding.apply(herobrine);

                giveVials(-1, 2);

                herobrine.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
                PlayerUtil.addEffect(herobrine, PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1, false, false);
                break;

            }
            case 2: {

                herobrine.getInventory().remove(Material.IRON_AXE);
                GUIItem item = new GUIItem(Material.IRON_SWORD).displayName("&7Sword of &lHELLBRINGING!")
                        .unbreakable(true);
                herobrine.getInventory().addItem(item.build());

                hbBatBomb.slot = -1;
                hbBatBomb.amount = 3;
                hbBatBomb.apply(herobrine);

                hbDream.slot = -1;
                hbDream.amount = 1;
                hbDream.apply(herobrine);

                giveVials(-1, 2);

                herobrine.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
                PlayerUtil.addEffect(herobrine, PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false);
                break;

            }
            case 3: {

                herobrine.getInventory().clear();

                giveVials(-1, 2);

                GUIItem item = new GUIItem(Material.IRON_SWORD).displayName("&bSword of &lChances!").unbreakable(true);
                herobrine.getInventory().addItem(item.build());

                herobrine.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
                break;

            }

        }

    }

    public void giveVials(int slot, int amount) {

        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        potion.setAmount(amount);
        PotionMeta pm = (PotionMeta) potion.getItemMeta();

        pm.displayName(Message.legacySerializerAnyCase("&aPoisonous Vial"));

        pm.setBasePotionData(new PotionData(PotionType.POISON, false, true));
        potion.setItemMeta(pm);

        if (slot == -1)
            herobrine.getInventory().addItem(potion);
        else
            herobrine.getInventory().setItem(slot, potion);

    }

    public void setupSurvivors() {

        if (worldManager.survivorSpawn == null) {

            Console.error(gameLobby, "setupSurvivors: survivorSpawn is null -- skipping teleport for "
                    + survivors.size() + " survivor(s). Kits will still apply.");

        } else {

            Console.info(gameLobby,
                    "setupSurvivors: teleporting " + survivors.size() + " survivor(s) to "
                            + worldManager.survivorSpawn.getWorld().getName() + " ("
                            + worldManager.survivorSpawn.getBlockX() + "," + worldManager.survivorSpawn.getBlockY()
                            + "," + worldManager.survivorSpawn.getBlockZ() + ")");

        }

        for (Player p : survivors) {

            try {

                if (worldManager.survivorSpawn != null) {

                    boolean teleported = p.teleport(worldManager.survivorSpawn);
                    if (!teleported)
                        Console.error(gameLobby, "Teleport returned false for survivor " + p.getName());

                }

                p.setHealth(20);
                p.setFoodLevel(20);
                PlayerUtil.addEffect(p, PotionEffectType.BLINDNESS, 60, 1, false, false);

            } catch (Throwable t) {

                Console.error(gameLobby, "Error setting up survivor " + p.getName() + ": " + t.getMessage());
                t.printStackTrace();

            }

        }

        setupKits();
        applyKits();

    }

    public void makeSpectator(Player player) {

        boolean alreadyOut = isSpectator(player) && isDeadSurvivor(player);
        markSurvivorDead(player);
        addSpectator(player);

        if (!alreadyOut)
            Message.send(player, Message.format("&7You are out of the game! Left-click to open the spectator menu."));
        applySpectatorState(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> applySpectatorState(player), 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> applySpectatorState(player), 20);

    }

    private void applySpectatorState(Player player) {

        if (player == null || !player.isOnline())
            return;

        if (worldManager.survivorSpawn != null)
            player.teleport(worldManager.survivorSpawn);

        PlayerUtil.clearInventory(player);
        PlayerUtil.clearEffects(player);

        PlayerUtil.addEffect(player, PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false);

        player.setGameMode(GameMode.SPECTATOR);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.getInventory().setItem(0, new GUIItem(Material.COMPASS).displayName("&7Spectator Menu").build());

        setTags(player, null, NamedTextColor.GRAY, ScoreboardUpdateAction.UPDATE);
        updateTags(ScoreboardUpdateAction.UPDATE);
        if (scoreboards.containsKey(player))
            scoreboards.get(player).setHandler(gameScoreboardHandler);

    }

    public void end(WinType type) {

        cancelPvpProtection();
        setGameState(GameState.ENDING);
        setShardState(ShardState.INACTIVE);
        voidKits();

        if (type == WinType.SURVIVORS) {

            PlayerUtil.broadcastTitle(gameLobby, "&aSURVIVORS WIN!", "", 1000, 3000, 1000);
            Message.broadcast(gameLobby, Message.format("&a&lThe Survivors &ehave defeated &c&lThe Herobrine!"));
            Message.broadcast(gameLobby, Message.format(type.getDesc()));
            PlayerUtil.broadcastSound(gameLobby, Sound.ENTITY_WITHER_DEATH, 1f, 1f);
            for (Player p : survivors)
                gameLobby.getStatManager().getPointsTracker().increment(p.getUniqueId(), 10);

            herobrine.getWorld().strikeLightningEffect(herobrine.getLocation().add(0, 0.5, 0));
            Bukkit.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {

                Location loc = herobrine.getLocation();
                for (int i = 0; i < 50; i++) {

                    try {

                        Bukkit.getServer().getScheduler()
                                .runTask(plugin,
                                        () -> PlayerUtil
                                                .spawnFirework(
                                                        loc.clone()
                                                                .add(new Vector(Math.random() - 0.5, 0,
                                                                        Math.random() - 0.5).multiply(20)),
                                                        Color.LIME));
                        TimeUnit.MILLISECONDS.sleep(100);

                    } catch (Exception e) {

                        e.printStackTrace();

                    }

                }

            });

        } else {

            PlayerUtil.broadcastTitle(gameLobby, "&cHEROBRINE &aWINS!", "", 1000, 3000, 1000);
            Message.broadcast(gameLobby, Message.format("&c&lThe Herobrine &ehas defeated all the survivors."));
            Message.broadcast(gameLobby, Message.format(type.getDesc()));
            PlayerUtil.broadcastSound(gameLobby, Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 1f);
            gameLobby.getStatManager().getPointsTracker().increment(herobrine.getUniqueId(), 10);

        }

        // Restore the original skin we overrode in setupHerobrine() before the
        // lobby tears down. Safe even if the override never applied (config off
        // or apply failed silently).
        if (herobrine != null)
            HerobrineSkin.restore(herobrine);

        gameLobby.getStatManager().stopTracking();
        gameLobby.getStatManager().push();
        Message.broadcast(gameLobby, Message.format("&7The lobby will restart in 15 seconds."));
        Bukkit.getServer().getScheduler().runTaskLater(plugin, () -> gameLobby.shutdown(true, true), 300);

    }

    private void startPvpProtection() {

        cancelPvpProtection();

        final World roundWorld = worldManager.getGameWorld();
        if (roundWorld == null)
            return;

        final int seconds = Math.max(0, plugin.getConfig().getInt("pvpProtectionSeconds", 30));
        if (seconds == 0) {

            roundWorld.setPVP(true);
            return;

        }

        pvpProtected = true;
        roundWorld.setPVP(false);
        pvpProtectionTask = new BukkitRunnable() {

            private int remaining = seconds;

            @Override
            public void run() {

                if (gameState != GameState.LIVE && gameState != GameState.STARTING) {

                    pvpProtected = false;
                    pvpProtectionTask = null;
                    cancel();
                    return;

                }

                if (remaining == 0) {

                    pvpProtected = false;
                    roundWorld.setPVP(true);
                    PlayerUtil.broadcastActionbar(gameLobby, "&aPvP is now enabled!");
                    pvpProtectionTask = null;
                    cancel();
                    return;

                }

                String time = Message.formatTime(remaining);
                PlayerUtil.broadcastActionbar(gameLobby, "&cPvP is disabled &7- &eEnables in &c" + time);
                remaining--;

            }

        }.runTaskTimer(plugin, 0, 20);

    }

    private void cancelPvpProtection() {

        if (pvpProtectionTask != null) {

            pvpProtectionTask.cancel();
            pvpProtectionTask = null;

        }

        pvpProtected = false;

    }

    public void endCheck() {

        if (gameState != GameState.LIVE)
            return;

        if (getHerobrine() == null)
            return;

        Console.debug(gameLobby, "=== END CHECK ===");
        Console.debug(gameLobby, "Survivors: " + getSurvivors().size());
        Console.debug(gameLobby, "Herobrine: " + (getHerobrine().isOnline() ? "Online" : "Offline"));
        if (getHerobrine().isOnline())
            Console.debug(gameLobby, "Herobrine World: " + getHerobrine().getWorld().getName());
        Console.debug(gameLobby, "======");

        if (getSurvivors().isEmpty()) {

            end(WinType.HEROBRINE);

        } else if (!getHerobrine().isOnline()
                || !getHerobrine().getWorld().getName().startsWith(gameLobby.getLobbyId()))
        {

            end(WinType.SURVIVORS);

        }

    }

    public void capture(Player player) {

        player.getInventory().remove(Material.NETHER_STAR);
        setTags(player, null, NamedTextColor.DARK_GREEN, ScoreboardUpdateAction.UPDATE);
        shardCarrier = null;
        shardCount++;
        if (shardCount == 3) {

            setShardState(ShardState.INACTIVE);
            herobrine.removePotionEffect(PotionEffectType.INVISIBILITY);
            updateTags(ScoreboardUpdateAction.UPDATE);

        } else
            setShardState(ShardState.WAITING);

        new CaptureSequence(player, this, gameLobby.getWorldManager()).runTaskAsynchronously(plugin);
        updateHerobrine();
        Bukkit.getServer().getPluginManager().callEvent(new ShardCaptureEvent(player, gameLobby.getLobbyId()));

    }

    // Uses 1.8 damage, the damage towards herobrine
    public double getHerobrineHitDamage(Material item, boolean strength) {

        double finalDamage = 0;
        boolean normal = false;
        switch (item) {

            case WOODEN_SWORD:
                finalDamage = 4; // 2 hearts
                break;
            case STONE_SWORD:
                finalDamage = 5; // 2.5 hearts
                break;
            case IRON_AXE:
                finalDamage = 5; // 2.5 hearts
                break;
            case IRON_SWORD:
                finalDamage = 6; // 3 hearts
                break;
            default:
                normal = true;
                break;

        }

        // Any non attacking weapon
        if (normal)
            return -1;

        if (strength)
            finalDamage += 3; // Actual strength increase

        return finalDamage;

    }

    // Using 1.8 damage, the damage towards survivors
    public double getSurvivorHitDamage(Material item) {

        double finalDamage = 0;
        switch (item) {

            case STONE_AXE:
                finalDamage = 3; // 0 shards, 1.5 hearts
                break;
            case IRON_AXE:
                finalDamage = 4; // 1 shard, 2 hearts
                break;
            case IRON_SWORD:
                finalDamage = (shardCount == 2 ? 5 : 6); // 2 shards, 2.5 hearts. 3 shards, 3 hearts
                break;
            default:
                return -1;

        }

        return finalDamage;

    }

    public void hubInventory(Player player) {

        PlayerUtil.clearEffects(player);
        PlayerUtil.clearInventory(player);

        GUIItem kitItem = new GUIItem(Material.COMPASS);
        kitItem.displayName("&a&lChoose &b&lClass");
        player.getInventory().setItem(0, kitItem.build());

    }

    // Kits
    public void setupKits() {

        for (Kit kit : kits) {

            plugin.getServer().getPluginManager().registerEvents(kit, plugin);

        }

    }

    public void voidKits() {

        for (Kit kit : kits) {

            HandlerList.unregisterAll(kit);

            kit.voidAbilities();

        }

        HandlerList.unregisterAll(hbBatBomb);
        HandlerList.unregisterAll(hbDream);
        HandlerList.unregisterAll(hbBlinding);

    }

    public void applyKits() {

        for (Player p : survivors) {

            Kit k = getLocalKit(p);
            k.apply(p);

        }

    }

    public void setKit(Player player, Kit kit, boolean inform) {

        playerKits.remove(player);
        playerKits.put(player, kit);
        saveKit(player, kit);

        if (inform)
            Message.send(player, Message.format("&eSet your class to " + kit.getDisplayName()));

    }

    public void saveKit(Player player, Kit kit) {

        redis.setKey("hb:kit:" + player.getUniqueId().toString(), kit.getInternalName());

    }

    public Kit getSavedKit(Player player) {

        String key = "hb:kit:" + player.getUniqueId().toString();
        if (!redis.exists(key))
            return defaultKit;

        String result = redis.getKey(key);
        for (Kit k : kits)
            if (k.getInternalName().equals(result))
                return k;

        return defaultKit;

    }

    public Kit getLocalKit(Player player) {

        if (!playerKits.containsKey(player)) {

            setKit(player, defaultKit, false);
            return defaultKit;

        } else {

            return playerKits.get(player);

        }

    }

    // Scoreboards
    public enum ScoreboardUpdateAction {
        CREATE, UPDATE, BEGONETHOT
    }

    public void updateTags(ScoreboardUpdateAction action) {

        for (Player p : gameLobby.getPlayers()) {

            setTags(p, getTeamPrefixes().get(p), getTeamColours().get(p), action);

        }

    }

    public void setTags(Player player, String prefix, NamedTextColor color, ScoreboardUpdateAction action) {

        if (prefix == null)
            prefix = "";
        if (color == null)
            color = NamedTextColor.WHITE;

        for (Scoreboard s : getScoreboards().values()) {

            org.bukkit.scoreboard.Scoreboard sc = s.getHolder().getScoreboard();

            String teamName = "APL" + player.getEntityId();
            if (sc.getTeam(teamName) == null) {

                sc.registerNewTeam(teamName);

            }

            Team team = sc.getTeam(teamName);
            team.prefix(Message.legacySerializerAnyCase(prefix));
            team.color(color);
            if (player == herobrine && shardCount == 3)
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            else if (player == herobrine)
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            else
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

            switch (action) {

                case CREATE:
                    team.addEntry(player.getName());
                    break;
                case UPDATE:
                    team.unregister();
                    sc.registerNewTeam(teamName);
                    team = sc.getTeam(teamName);
                    team.prefix(Message.legacySerializerAnyCase(prefix));
                    team.color(color);
                    if (player == herobrine && shardCount == 3)
                        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
                    else if (player == herobrine)
                        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                    else
                        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
                    team.addEntry(player.getName());
                    break;
                case BEGONETHOT:
                    team.unregister();
                    break;

            }

            getTeamPrefixes().remove(player);
            getTeamColours().remove(player);

            getTeamPrefixes().put(player, prefix);
            getTeamColours().put(player, color);

        }

    }

}
