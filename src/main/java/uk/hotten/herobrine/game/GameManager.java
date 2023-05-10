package uk.hotten.herobrine.game;

import com.comphenix.protocol.ProtocolManager;
import lombok.Getter;
import lombok.Setter;
import me.tigerhix.lib.scoreboard.common.EntryBuilder;
import me.tigerhix.lib.scoreboard.type.Entry;
import me.tigerhix.lib.scoreboard.type.Scoreboard;
import me.tigerhix.lib.scoreboard.type.ScoreboardHandler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import uk.hotten.gxui.GUIItem;
import uk.hotten.herobrine.events.GameStateUpdateEvent;
import uk.hotten.herobrine.events.ShardCaptureEvent;
import uk.hotten.herobrine.events.ShardStateUpdateEvent;
import uk.hotten.herobrine.game.runnables.*;
import uk.hotten.herobrine.kit.Kit;
import uk.hotten.herobrine.kit.abilities.BatBombAbility;
import uk.hotten.herobrine.kit.abilities.BlindingAbility;
import uk.hotten.herobrine.kit.abilities.DreamweaverAbility;
import uk.hotten.herobrine.kit.abilities.LocatorAbility;
import uk.hotten.herobrine.kit.kits.*;
import uk.hotten.herobrine.data.RedisManager;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.stat.StatManager;
import uk.hotten.herobrine.stat.StatTracker;
import uk.hotten.herobrine.utils.*;
import uk.hotten.herobrine.world.WorldManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GameManager {

    @Getter private JavaPlugin plugin;

    private WorldManager worldManager;
    private RedisManager redis;
    @Getter private ProtocolManager protocolManager;
    @Getter private GameLobby gameLobby;
    @Getter private GMListener gmListener;

    @Getter private GameState gameState;
    @Getter private ShardState shardState;

    @Getter private int requiredToStart;
    @Getter private int maxPlayers;
    @Getter private boolean allowOverfill;

    @Getter private String networkName;
    @Getter private String networkWeb;

    @Getter private Player herobrine;
    private BatBombAbility hbBatBomb;
    private DreamweaverAbility hbDream;
    private BlindingAbility hbBlinding;
    @Getter private ArrayList<Player> survivors;
    @Getter private ArrayList<Player> spectators;
    @Getter private ArrayList<Player> hbLastHit;
    @Getter @Setter private Player passUser;

    @Getter private ShardHandler shardHandler;
    @Getter @Setter private boolean shardPreviousDestroyed;
    @Getter public int shardCount;
    @Getter @Setter private Player shardCarrier;

    public int startTimer;
    public boolean stAlmost = false;
    public boolean stFull = false;
    public boolean timerPaused = false;
    private BukkitTask waitingRunnable;
    private NarrationRunnable narrationRunnable;

    @Getter private Kit[] kits;
    @Getter private Kit defaultKit;
    @Getter private HashMap<Player, Kit> playerKits;

    @Getter @Setter private StatTracker[] statTrackers;

    @Getter private HashMap<Player, Scoreboard> scoreboards;
    @Getter private HashMap<Player, String> teamPrefixes = new HashMap<>();
    @Getter private HashMap<Player, ChatColor> teamColours = new HashMap<>();
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

        requiredToStart = plugin.getConfig().getInt("minPlayers");
        maxPlayers = plugin.getConfig().getInt("maxPlayers");
        startTimer = plugin.getConfig().getInt("startTime");
        allowOverfill = plugin.getConfig().getBoolean("allowOverfill");
        networkName = plugin.getConfig().getString("networkName");
        networkWeb = plugin.getConfig().getString("networkWeb");
        boolean lockClassicKits = plugin.getConfig().getBoolean("lockClassicKits");
        boolean lockUnlockableKits = plugin.getConfig().getBoolean("lockUnlockableKits");

        shardCount = 0;
        shardPreviousDestroyed = false;

        survivors = new ArrayList<>();
        spectators = new ArrayList<>();
        hbLastHit = new ArrayList<>();

        kits = new Kit[] {
                new ArcherKit(this, lockClassicKits),
                new PriestKit(this, lockClassicKits),
                new ScoutKit(this, lockClassicKits),
                new WizardKit(this, lockClassicKits),
                new MageKit(this, lockUnlockableKits),
                new PaladinKit(this, lockUnlockableKits),
                new SorcererKit(this, lockUnlockableKits)
        };

        for (Kit k : kits) {
            if (k.getInternalName().equals("archer"))
                defaultKit = k;
        }

        playerKits = new HashMap<>();

        scoreboards = new HashMap<>();
        gameScoreboardHandler = new ScoreboardHandler() {
            @Override
            public String getTitle(Player player) {
                return "" + ChatColor.RED + "The" + ChatColor.BOLD + "Herobrine!";
            }

            @Override
            public List<Entry> getEntries(Player player) {
                return new EntryBuilder()
                        .blank()
                        .next(ChatColor.GREEN + "✦ Shard Count")
                        .next("" + shardCount + "/3")
                        .blank()
                        .next(ChatColor.GREEN + "❂ Survivors")
                        .next("" + survivors.size())
                        .blank()
                        .next("" + ChatColor.DARK_GRAY + "--------------")
                        .next("" + ChatColor.AQUA + networkWeb)
                        .build();
            }
        };

        gameLobby.getWorldManager().pickVotingMaps();

        startWaiting();
        Console.info(gameLobby, "Game Manager is ready!");
    }

    public void setGameState(GameState newState) {
        new BukkitRunnable() {
            @Override
            public void run() {
                GameState old = gameState;
                if (old == null) old = GameState.UNKNOWN;

                gameState = newState;
                Console.debug(gameLobby, "Game state updated to " + newState.toString() + "(from " + old.toString() + ")!");
                plugin.getServer().getPluginManager().callEvent(new GameStateUpdateEvent(old, newState, gameLobby.getLobbyId()));
            }
        }.runTask(plugin);
    }

    public void setShardState(ShardState newState) {
        new BukkitRunnable() {

            @Override
            public void run() {
                ShardState old = shardState;
                if (old == null) old = ShardState.UNKNOWN;

                shardState = newState;
                Console.debug(gameLobby, "Shard state updated to " + newState.toString() + "(from " + old.toString() + ")!");
                plugin.getServer().getPluginManager().callEvent(new ShardStateUpdateEvent(old, newState, gameLobby.getLobbyId()));
                if (gameState == GameState.LIVE)
                    narrationRunnable.timer = 0;
            }
        }.runTask(plugin);
    }

    public void startWaiting() {
        setGameState(GameState.WAITING);
        if (waitingRunnable != null) waitingRunnable.cancel();

        // In case the timer decreased from players leaving and a world was loaded
        Bukkit.getServer().getScheduler().runTask(plugin, () -> gameLobby.getWorldManager().clean(false));
        startTimer = plugin.getConfig().getInt("startTime");

        waitingRunnable = new WaitingRunnable(this).runTaskTimerAsynchronously(plugin, 0, 10);
    }

    public void startCheck() {
        if (getSurvivors().size() >= getRequiredToStart() && !timerPaused) {
            if (getGameState() != GameState.STARTING) {
                setGameState(GameState.STARTING);
                new StartingRunnable(this, gameLobby.getWorldManager()).runTaskTimerAsynchronously(getPlugin(), 0, 20);
            } else {
                if (getSurvivors().size() >= getMaxPlayers()-3 && !stAlmost && startTimer > 30) {
                    Message.broadcast(gameLobby, Message.format("" + ChatColor.GREEN + "We almost have a full server! Shortening timer to 30 seconds!"));
                    stAlmost = true;
                    startTimer = 30;
                } else if (getSurvivors().size() >= getMaxPlayers() && !stFull && startTimer > 10) {
                    Message.broadcast(gameLobby, Message.format("" + ChatColor.GREEN + "We have a full server! Starting in 10 seconds!"));
                    stFull = true;
                    startTimer = 10;
                }
            }
        }
    }

    public void start() {
        setGameState(GameState.LIVE);
        narrationRunnable = new NarrationRunnable(this);
        narrationRunnable.runTaskTimerAsynchronously(plugin, 0, 10); // has to run before the shardstate updates
        setShardState(ShardState.WAITING);
        gameLobby.getStatManager().startTracking();
        if (passUser != null) {
            herobrine = passUser;
            passUser = null;
        } else {
            herobrine = PlayerUtil.randomPlayer(gameLobby);
        }
        survivors.remove(herobrine);
        setupHerobrine();
        setupSurvivors();
        new HerobrineSetup(herobrine).runTaskAsynchronously(plugin);
        setTags(herobrine, "" + ChatColor.RED + ChatColor.BOLD + "HEROBRINE ", ChatColor.RED, ScoreboardUpdateAction.UPDATE);
        for (Player p : survivors) {
            setTags(p, null, ChatColor.DARK_GREEN, ScoreboardUpdateAction.UPDATE);
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
            p.setGameMode(GameMode.SURVIVAL);
        }

        updateTags(ScoreboardUpdateAction.UPDATE);
    }

    public void setupHerobrine() {
        herobrine.teleport(worldManager.herobrineSpawn);
        herobrine.setHealth(20);
        herobrine.setFoodLevel(20);

        PlayerUtil.addEffect(herobrine, PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false);
        PlayerUtil.addEffect(herobrine, PotionEffectType.JUMP, Integer.MAX_VALUE, 1, false, false);
        PlayerUtil.addEffect(herobrine, PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false);

        updateHerobrine();
    }

    public void updateHerobrine() {
        switch (shardCount) {
            case 0: {
                GUIItem item = new GUIItem(Material.STONE_AXE).displayName(ChatColor.GRAY + "The Thorbringer").unbreakable(true);
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
                GUIItem item = new GUIItem(Material.IRON_AXE).displayName(ChatColor.GRAY + "Axe of " + ChatColor.BOLD + "Deceit!").unbreakable(true);
                herobrine.getInventory().addItem(item.build());

                hbBlinding.apply(herobrine);

                giveVials(-1, 2);

                herobrine.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
                PlayerUtil.addEffect(herobrine, PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1, false, false);
                break;
            }
            case 2: {
                herobrine.getInventory().remove(Material.IRON_AXE);
                GUIItem item = new GUIItem(Material.IRON_SWORD).displayName(ChatColor.GRAY + "Sword of " + ChatColor.BOLD + "HELLBRINGING!").unbreakable(true);
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

                GUIItem item = new GUIItem(Material.IRON_SWORD).displayName(ChatColor.AQUA + "Sword of " + ChatColor.BOLD + "Chances!").unbreakable(true);
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

        pm.setDisplayName(ChatColor.GREEN + "Poisonous Vial");

        pm.setBasePotionData(new PotionData(PotionType.POISON, false, true));
        potion.setItemMeta(pm);

        if (slot == -1)
            herobrine.getInventory().addItem(potion);
        else
            herobrine.getInventory().setItem(slot, potion);
    }

    public void setupSurvivors() {
        for (Player p : survivors) {
            p.teleport(worldManager.survivorSpawn);
            p.setHealth(20);
            p.setFoodLevel(20);
            PlayerUtil.addEffect(p, PotionEffectType.BLINDNESS, 60, 1, false, false);
        }

        setupKits();
        applyKits();
    }

    public void makeSpectator(Player player) {
        player.teleport(worldManager.survivorSpawn);

        PlayerUtil.clearInventory(player);
        PlayerUtil.clearEffects(player);

        spectators.add(player);
        PlayerUtil.addEffect(player, PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false);

        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.getInventory().setItem(0, new GUIItem(Material.COMPASS).displayName(ChatColor.YELLOW + "Spectator Menu").build());

        setTags(player, null, ChatColor.GRAY, ScoreboardUpdateAction.UPDATE);
        updateTags(ScoreboardUpdateAction.UPDATE);
        scoreboards.get(player).setHandler(gameScoreboardHandler);
    }

    public void end(WinType type) {
        setGameState(GameState.ENDING);
        setShardState(ShardState.INACTIVE);
        voidKits();

        if (type == WinType.SURVIVORS) {
            PlayerUtil.broadcastTitle(gameLobby, ChatColor.GREEN + "SURVIVORS WIN!", "", 20, 60, 20);
            Message.broadcast(gameLobby, Message.format("" + ChatColor.GREEN + ChatColor.BOLD + "The Survivors " + ChatColor.YELLOW + "have defeated " + ChatColor.RED + ChatColor.BOLD + "The Herobrine!"));
            Message.broadcast(gameLobby, Message.format(type.getDesc()));
            PlayerUtil.broadcastSound(gameLobby, Sound.ENTITY_WITHER_DEATH, 1f, 1f);
            for (Player p : survivors)
                gameLobby.getStatManager().getPointsTracker().increment(p.getUniqueId(), 10);

            herobrine.getWorld().strikeLightningEffect(herobrine.getLocation().add(0, 0.5, 0));
            Bukkit.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                Location loc = herobrine.getLocation();
                for (int i = 0; i < 50; i++) {
                    try {
                        Bukkit.getServer().getScheduler().runTask(plugin, () -> PlayerUtil.spawnFirework(loc.clone().add(new Vector(Math.random()-0.5, 0, Math.random()-0.5).multiply(20)), Color.LIME));
                        TimeUnit.MILLISECONDS.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } else {
            PlayerUtil.broadcastTitle(gameLobby, ChatColor.RED + "HEROBRINE" + ChatColor.GREEN + " WINS!", "", 20, 60, 20);
            Message.broadcast(gameLobby, Message.format("" + ChatColor.RED + ChatColor.BOLD + "The Herobrine " + ChatColor.YELLOW + "has defeated all the survivors."));
            Message.broadcast(gameLobby, Message.format(type.getDesc()));
            PlayerUtil.broadcastSound(gameLobby, Sound.ENTITY_ENDER_DRAGON_HURT, 1f, 1f);
            gameLobby.getStatManager().getPointsTracker().increment(herobrine.getUniqueId(), 10);
        }

        gameLobby.getStatManager().stopTracking();
        gameLobby.getStatManager().push();
        Message.broadcast(gameLobby, Message.format(ChatColor.GRAY + "The lobby will shutdown in 15 seconds."));
        Bukkit.getServer().getScheduler().runTaskLater(plugin, () -> gameLobby.shutdown(true), 300);
    }

    public void endCheck() {
        if (gameState != GameState.LIVE)
            return;
        
        Console.debug(gameLobby, "=== END CHECK ===");
        Console.debug(gameLobby, "Survivors: " + getSurvivors().size());
        Console.debug(gameLobby, "Herobrine: " + (getHerobrine().isOnline() ? "Online" : "Offline"));
        if (getHerobrine().isOnline())
            Console.debug(gameLobby, "Herobrine World: " + getHerobrine().getWorld().getName());
        Console.debug(gameLobby, "======");

        if (getSurvivors().size() == 0) {
            end(WinType.HEROBRINE);
        } else if (!getHerobrine().getWorld().getName().startsWith(gameLobby.getLobbyId()) || !getHerobrine().isOnline()) {
            end(WinType.SURVIVORS);
        }
    }

    public void capture(Player player) {
        player.getInventory().remove(Material.NETHER_STAR);
        setTags(player, null, ChatColor.DARK_GREEN, ScoreboardUpdateAction.UPDATE);
        shardCarrier = null;
        shardCount++;
        if (shardCount == 3) {
            setShardState(ShardState.INACTIVE);
            herobrine.removePotionEffect(PotionEffectType.INVISIBILITY);
            updateTags(ScoreboardUpdateAction.UPDATE);
        }
        else
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
        kitItem.displayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Choose " + ChatColor.AQUA + ChatColor.BOLD + "Class");
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
            player.sendMessage(Message.format(ChatColor.YELLOW + "Set your class to " + kit.getDisplayName()));
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

    public void setTags(Player player, String prefix, ChatColor color, ScoreboardUpdateAction action) {
        if (prefix == null)
            prefix = "";
        if (color == null)
            color = ChatColor.WHITE;

        for (Scoreboard s : getScoreboards().values()) {
            org.bukkit.scoreboard.Scoreboard sc = s.getHolder().getScoreboard();

            String teamName = "APL" + player.getEntityId();
            if (sc.getTeam(teamName) == null) {
                sc.registerNewTeam(teamName);
            }

            Team team = sc.getTeam(teamName);
            team.setPrefix(prefix);
            team.setColor(color);
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
                    team.setPrefix(prefix);
                    team.setColor(color);
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
