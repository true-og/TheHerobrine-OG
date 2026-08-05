package uk.hotten.herobrine.game;

import com.bergerkiller.bukkit.mw.MyWorlds;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.tigerhix.lib.scoreboard.ScoreboardLib;
import me.tigerhix.lib.scoreboard.common.EntryBuilder;
import me.tigerhix.lib.scoreboard.type.Entry;
import me.tigerhix.lib.scoreboard.type.ScoreboardHandler;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import uk.hotten.herobrine.HerobrinePluginOG;
import uk.hotten.herobrine.compat.IllegalStackCompat;
import uk.hotten.herobrine.kit.KitGui;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.utils.GameState;
import uk.hotten.herobrine.utils.HerobrineSkin;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.utils.PlayerUtil;
import uk.hotten.herobrine.utils.ShardState;
import uk.hotten.herobrine.utils.WinType;
import uk.hotten.herobrine.world.WorldManager;

public class GMListener implements Listener {

    private GameManager gameManager;
    private GameLobby gameLobby;
    private ArrayList<Player> kitCooldown = new ArrayList<>();
    private Set<UUID> returningToMainOnLogin = ConcurrentHashMap.newKeySet();
    private Map<UUID, Integer> suppressedHerobrineKnockback = new HashMap<>();

    public GMListener(GameManager gm, GameLobby gl) {

        this.gameManager = gm;
        this.gameLobby = gl;

    }

    private boolean isLobbyWorld(String worldName) {

        return worldName != null && worldName.startsWith(gameLobby.getLobbyId());

    }

    private boolean isHubWorld(String worldName) {

        return worldName != null && worldName.equals(gameLobby.getLobbyId() + "-hub");

    }

    private boolean isGameWorld(String worldName) {

        return gameLobby.getWorldManager().getGameWorld() != null
                && gameLobby.getWorldManager().getGameWorld().getName().equals(worldName);

    }

    private boolean hasLobbyPlayer(Player player) {

        return gameLobby.getPlayers().stream().anyMatch(p -> p.getUniqueId().equals(player.getUniqueId()));

    }

    private void addLobbyPlayer(Player player) {

        gameLobby.getPlayers().removeIf(p -> p.getUniqueId().equals(player.getUniqueId()));
        gameLobby.getPlayers().add(player);

    }

    private void removeLobbyPlayer(Player player) {

        gameLobby.getPlayers().removeIf(p -> p.getUniqueId().equals(player.getUniqueId()));

    }

    private void removeScoreboard(Player player) {

        for (Player scored : new ArrayList<>(gameManager.getScoreboards().keySet())) {

            if (!scored.getUniqueId().equals(player.getUniqueId()))
                continue;
            gameManager.getScoreboards().get(scored).deactivate();
            gameManager.getScoreboards().remove(scored);

        }

    }

    private void ensureScoreboard(Player player) {

        for (Player scored : new ArrayList<>(gameManager.getScoreboards().keySet())) {

            if (!scored.getUniqueId().equals(player.getUniqueId()))
                continue;
            if (scored == player)
                return;
            gameManager.getScoreboards().get(scored).deactivate();
            gameManager.getScoreboards().remove(scored);

        }

        gameManager.getScoreboards().put(player,
                ScoreboardLib.createScoreboard(player).setHandler(new ScoreboardHandler()
                {

                    @Override
                    public String getTitle(Player player) {

                        return "&e&lYour Stats";

                    }

                    @Override
                    public List<Entry> getEntries(Player player) {

                        return new EntryBuilder()
                                .next("&bPoints: &r" + gameLobby.getStatManager().getPoints().get(player.getUniqueId()))
                                .next("&bCaptures: &r"
                                        + gameLobby.getStatManager().getCaptures().get(player.getUniqueId()))
                                .next("&bKills: &r" + gameLobby.getStatManager().getKills().get(player.getUniqueId()))
                                .next("&bDeaths: &r" + gameLobby.getStatManager().getDeaths().get(player.getUniqueId()))
                                .build();

                    }

                }).setUpdateInterval(20));
        gameManager.getScoreboards().get(player).activate();

    }

    private void ensureLobbyTracking(Player player) {

        addLobbyPlayer(player);

        if (HerobrinePluginOG.hasIllegalStack())
            IllegalStackCompat.exemptPlayer(player.getUniqueId());

        gameLobby.getStatManager().check(player.getUniqueId());
        ensureScoreboard(player);

        gameManager.updateTags(GameManager.ScoreboardUpdateAction.CREATE);
        gameManager.setTags(player, null, null, GameManager.ScoreboardUpdateAction.CREATE);

    }

    @EventHandler
    public void onJoinViaWorld(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();

        if (!isLobbyWorld(player.getWorld().getName()))
            return;

        if (isLobbyWorld(event.getFrom().getName()))
            return;

        onJoinLogic(player, player.getWorld().getName());

    }

    @EventHandler
    public void onJoinViaLogin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        if (!isLobbyWorld(player.getWorld().getName()))
            return;

        returningToMainOnLogin.add(player.getUniqueId());
        if (!returnToMainWorld(player))
            returningToMainOnLogin.remove(player.getUniqueId());

    }

    private boolean returnToMainWorld(Player player) {

        LobbyManager lm = LobbyManager.getInstance();
        if (lm != null && lm.hasPreJoinLocation(player.getUniqueId())) {

            if (!lm.returnPlayer(player, null)) {

                Message.send(player, Message.format("&cUnable to return you to your previous location."));
                return false;

            }

            return true;

        }

        World mainWorld = MyWorlds.getMainWorld();
        if (mainWorld == null)
            mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null && !Bukkit.getWorlds().isEmpty())
            mainWorld = Bukkit.getWorlds().get(0);
        if (mainWorld == null) {

            Message.send(player, Message.format("&cNo main world is available."));
            return false;

        }

        if (!player.teleport(mainWorld.getSpawnLocation())) {

            Message.send(player, Message.format("&cUnable to return you to the hub."));
            return false;

        }

        return true;

    }

    private void onJoinLogic(Player player, String worldName) {

        if (!gameManager.canJoin(player)) {

            LobbyManager lm = LobbyManager.getInstance();
            World fullFallback = MyWorlds.getMainWorld();
            if (lm != null)
                lm.returnPlayer(player, fullFallback != null ? fullFallback.getSpawnLocation() : null);
            else if (fullFallback != null)
                player.teleport(fullFallback.getSpawnLocation());

            Message.send(player, Message.format("&cThis lobby is full."));
            return;

        }

        if (gameManager.getGameState() == GameState.LIVE || gameManager.getGameState() == GameState.ENDING) {

            ensureLobbyTracking(player);
            gameManager.makeSpectator(player);
            return;

        }

        if (!isHubWorld(worldName)) {

            return;

        }

        ensureLobbyTracking(player);

        Message.broadcast(gameLobby, Message.format("&b" + player.getName() + " &ehas joined!"));
        gameManager.addSurvivor(player);
        gameManager.startCheck();

        gameLobby.getWorldManager().getPlayerVotes().put(player, 0);
        gameLobby.getWorldManager().sendVotingMessage(player);
        gameManager.hubInventory(player);
        gameManager.setKit(player, gameManager.getSavedKit(player), true);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setGameMode(GameMode.SURVIVAL);
        // Always drop joining players on the lobby (hub) spawn.
        World hubWorld = gameLobby.getWorldManager().getHubWorld();
        player.teleport(hubWorld != null ? hubWorld.getSpawnLocation() : player.getWorld().getSpawnLocation());

    }

    @EventHandler
    public void onLeaveViaQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();

        if (!hasLobbyPlayer(player) && !isLobbyWorld(player.getWorld().getName()))
            return;

        onLeaveLogic(player);

    }

    @EventHandler
    public void onLeaveViaWorld(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();

        String fromWorld = event.getFrom().getName();
        String toWorld = player.getWorld().getName();

        if (!isLobbyWorld(fromWorld))
            return;

        if (returningToMainOnLogin.remove(player.getUniqueId()))
            return;

        if (isLobbyWorld(toWorld)) {

            if (gameManager.getGameState() == GameState.LIVE && isGameWorld(fromWorld))
                handleLiveRoundExit(player, true);
            return;

        }

        onLeaveLogic(player);

    }

    private void handleLiveRoundExit(Player player, boolean keepInLobby) {

        if (gameManager.getGameState() != GameState.LIVE)
            return;

        if (gameManager.isHerobrine(player)) {

            HerobrineSkin.restore(player);
            gameManager.end(WinType.SURVIVORS);
            return;

        }

        if (!gameManager.isSurvivor(player)) {

            if (keepInLobby && (gameManager.isSpectator(player) || gameManager.isDeadSurvivor(player)))
                gameManager.makeSpectator(player);
            return;

        }

        gameManager.markSurvivorDead(player);

        if (gameManager.isShardCarrier(player)) {

            gameManager.getShardHandler().drop(player.getLocation());

        }

        if (keepInLobby)
            gameManager.makeSpectator(player);

        gameManager.endCheck();

    }

    private void onLeaveLogic(Player player) {

        boolean wasLive = gameManager.getGameState() == GameState.LIVE;

        if (wasLive)
            handleLiveRoundExit(player, false);

        removeLobbyPlayer(player);

        // If the leaver is the active Herobrine, restore their skin before
        // their PlayerProfile is persisted to disk -- otherwise the override
        // sticks across reconnects, even into worlds outside this lobby.
        if (gameManager.isHerobrine(player))
            HerobrineSkin.restore(player);

        if (HerobrinePluginOG.hasIllegalStack())
            IllegalStackCompat.unexemptPlayer(player.getUniqueId());

        if (!gameManager.isSpectator(player))
            Message.broadcast(gameLobby, Message.format("&b" + player.getName() + " &ehas quit."));
        if (!wasLive)
            gameManager.removeSurvivor(player);
        gameManager.removeSpectator(player);

        removeScoreboard(player);
        gameManager.getTeamPrefixes().remove(player);
        gameManager.getTeamColours().remove(player);

        WorldManager wm = gameLobby.getWorldManager();
        if (wm.getPlayerVotes().getOrDefault(player, 0) != 0)
            wm.getVotingMaps().get(wm.getPlayerVotes().get(player)).decrementVotes();
        wm.getPlayerVotes().remove(player);

        if (gameManager.getGameState() == GameState.LIVE) {

            // If ran straight away, it still thinks THB is online if they were the quitter
            Bukkit.getServer().getScheduler().runTaskLater(gameManager.getPlugin(), gameManager::endCheck, 1);
            return;

        }

        if (gameManager.getPassUser() != null && gameManager.getPassUser().getUniqueId().equals(player.getUniqueId())) {

            gameManager.setPassUser(null);
            Message.broadcast(gameManager.getGameLobby(),
                    Message.format("&6" + player.getName() + " has left and will no-longer be Herobrine."),
                    "theherobrine.command.setherobrine");

        }

    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {

        if (!event.getEntity().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        if (!(event.getEntity() instanceof Player)) {

            event.setCancelled(true);
            return;

        }

        Player player = (Player) event.getEntity();

        if (gameManager.getGameState() != GameState.LIVE)
            return;

        event.getItem().getItemStack();
        if (event.getItem().getItemStack().getType() != Material.NETHER_STAR) {

            event.setCancelled(true);
            return;

        }

        if (!gameManager.isSurvivor(player)) {

            event.setCancelled(true);
            return;

        }

        for (Player p : gameLobby.getPlayers()) {

            if (gameManager.isHerobrine(p))
                continue;
            PlayerUtil.sendTitle(p, "&a&l" + player.getName() + "&3 has the shard!", "&eHelp them return it!", 250,
                    3000, 250);

        }

        PlayerUtil.sendTitle(gameManager.getHerobrine(), "&a&l" + player.getName() + "&3 has the shard!",
                "&eMaybe target them first", 250, 3000, 250);
        gameManager.getShardHandler().getShardTitle().remove();
        gameManager.setShardState(ShardState.CARRYING);
        gameManager.setShardCarrier(player);
        gameManager.setTags(player, "&d&lShard: ", NamedTextColor.LIGHT_PURPLE,
                GameManager.ScoreboardUpdateAction.UPDATE);
        PlayerUtil.broadcastSound(gameLobby, Sound.ENTITY_BAT_DEATH, 1f, 0f);
        PlayerUtil.addEffect(player, PotionEffectType.BLINDNESS, 100, 1, false, false);
        PlayerUtil.addEffect(player, PotionEffectType.SLOW, 600, 2, false, false);
        PlayerUtil.addEffect(player, PotionEffectType.CONFUSION, 300, 1, false, false);
        Message.send(player, Message.format("&6You have a shard! Take it to the alter (Enchanting Table)!"));

    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {

        if (!event.getPlayer().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        event.setCancelled(true);

    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {

        if (!event.getPlayer().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        Player player = event.getPlayer();

        if (gameManager.getGameState() == GameState.LIVE) {

            if ((event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_AIR)
                    && gameManager.isSpectator(player))
            {

                new SpectatorGui(gameManager.getPlugin(), player, gameManager).open(true);
                return;

            }

            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {

                if (event.getClickedBlock() == null)
                    return;
                Material m = event.getClickedBlock().getType();

                if (m == Material.ENCHANTING_TABLE)
                    event.setCancelled(true);

                if (m == Material.ENCHANTING_TABLE && gameManager.isShardCarrier(player)) {

                    event.setCancelled(true);
                    player.getInventory().getItemInMainHand();
                    if (player.getInventory().getItemInMainHand().getType() == Material.NETHER_STAR) {

                        gameManager.capture(player);

                    }

                } else if (m == Material.ITEM_FRAME)
                    event.setCancelled(true);

            }

        }

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {

            if (player.getInventory().getItemInMainHand().getType() == Material.COMPASS) {

                if (kitCooldown.contains(player))
                    return;

                if (gameManager.getGameState() == GameState.WAITING
                        || gameManager.getGameState() == GameState.STARTING)
                {

                    new KitGui(gameManager.getPlugin(), player, gameManager).open(false);

                } else if (gameManager.getGameState() == GameState.LIVE && gameManager.isSpectator(player)) {

                    new SpectatorGui(gameManager.getPlugin(), player, gameManager).open(true);

                } else {

                    return;

                }

                kitCooldown.add(player);
                Bukkit.getServer().getScheduler().runTaskLater(gameManager.getPlugin(),
                        () -> kitCooldown.remove(player), 20);

            }

        }

    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {

        if (!event.getPlayer().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        if (gameManager.isShardCarrier(event.getPlayer())) {

            for (Player p : gameLobby.getPlayers()) {

                if (p != event.getPlayer())
                    p.setCompassTarget(event.getPlayer().getLocation());
                else
                    p.setCompassTarget(gameLobby.getWorldManager().alter);

            }

        }

    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {

        if (!event.getPlayer().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
            event.setCancelled(true);

    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {

        if (!event.getPlayer().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        if (event.getBlock().getType() == Material.OAK_FENCE
                || event.getBlock().getType() == Material.NETHER_BRICK_FENCE)
        {

            event.setCancelled(false);
            return;

        }

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
            event.setCancelled(true);

    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {

        if (!event.getEntity().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        event.setFoodLevel(20);
        event.setCancelled(true);

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombatAuthorization(EntityDamageByEntityEvent event) {

        if (!event.getEntity().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        if (gameManager.getGameState() != GameState.LIVE) {

            event.setCancelled(true);
            return;

        }

        if (gameManager.isPvpProtected()) {

            event.setCancelled(true);
            return;

        }

        if (event.getEntity() instanceof Player && event.getDamager() instanceof Arrow) {

            Player player = (Player) event.getEntity();
            Player attacker = getPlayerAttacker(event);
            if (attacker == null || !(gameManager.isSurvivor(attacker) && gameManager.isHerobrine(player)))
                event.setCancelled(true);
            return;

        }

        // Hound attacks THB
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Wolf) {

            Player player = (Player) event.getEntity();
            if (!gameManager.isHerobrine(player))
                event.setCancelled(true);
            return;

        }

        // TBH attacks hound
        if (event.getEntity() instanceof Wolf && event.getDamager() instanceof Player) {

            Player player = (Player) event.getDamager();
            if (!gameManager.isHerobrine(player))
                event.setCancelled(true);
            return;

        }

        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {

            event.setCancelled(true);
            return;

        }

        Player player = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        if (gameManager.isSurvivor(attacker)) {

            if (gameManager.isSurvivor(player))
                event.setCancelled(true);

        } else if (!gameManager.isHerobrine(attacker)) {

            event.setCancelled(true);

        }

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombatDamage(EntityDamageByEntityEvent event) {

        if (!event.getEntity().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        if (event.getEntity() instanceof Player && event.getDamager() instanceof Arrow) {

            event.setDamage(4.5);
            return;

        }

        if (event.getEntity() instanceof Player && event.getDamager() instanceof Wolf) {

            event.setDamage(6);
            return;

        }

        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player))
            return;

        Player attacker = (Player) event.getDamager();
        if (gameManager.isSurvivor(attacker)) {

            double damage = gameManager.getHerobrineHitDamage(attacker.getInventory().getItemInMainHand().getType(),
                    attacker.hasPotionEffect(PotionEffectType.INCREASE_DAMAGE));
            if (damage != -1)
                event.setDamage(damage);

        } else if (gameManager.isHerobrine(attacker)) {

            double damage = gameManager.getSurvivorHitDamage(attacker.getInventory().getItemInMainHand().getType());
            if (damage != -1)
                event.setDamage(damage);

        }

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAcceptedCombatDamage(EntityDamageByEntityEvent event) {

        if (!event.getEntity().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        if (!(event.getEntity() instanceof Player) || event.getFinalDamage() <= 0)
            return;

        Player player = (Player) event.getEntity();
        Player attacker = getPlayerAttacker(event);
        if (attacker == null)
            return;

        if (gameManager.isSurvivor(attacker) && gameManager.isHerobrine(player))
            PlayerUtil.animateHbHit(gameLobby, player.getLocation());
        else if (gameManager.isHerobrine(attacker) && gameManager.isSurvivor(player))
            PlayerUtil.playSoundAt(attacker.getLocation(), Sound.ENTITY_GHAST_AMBIENT, 1f, 0f);

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHerobrineKnockback(EntityPushedByEntityAttackEvent event) {

        if (!event.getEntity().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        if (!(event.getEntity() instanceof Player))
            return;

        Player player = (Player) event.getEntity();
        if (!gameManager.isHerobrine(player) || gameManager.getShardCount() == 3)
            return;

        // Purpur owns the server-side acceleration here. KnockbackSync can still supply
        // a legacy
        // velocity packet, so retain this marker until the matching velocity event is
        // handled.
        event.setCancelled(true);

        UUID playerId = player.getUniqueId();
        int suppressionVersion = suppressedHerobrineKnockback.merge(playerId, 1, Integer::sum);
        Bukkit.getServer().getScheduler().runTaskLater(gameManager.getPlugin(), () -> {

            Integer activeVersion = suppressedHerobrineKnockback.get(playerId);
            if (activeVersion != null && activeVersion == suppressionVersion)
                suppressedHerobrineKnockback.remove(playerId);

        }, 2);

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHerobrineVelocity(PlayerVelocityEvent event) {

        if (suppressedHerobrineKnockback.containsKey(event.getPlayer().getUniqueId()))
            event.setCancelled(true);

    }

    private Player getPlayerAttacker(EntityDamageByEntityEvent event) {

        if (event.getDamager() instanceof Player)
            return (Player) event.getDamager();

        if (event.getDamager() instanceof Arrow) {

            ProjectileSource shooter = ((Arrow) event.getDamager()).getShooter();
            if (shooter instanceof Player)
                return (Player) shooter;

        }

        return null;

    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {

        if (!event.getEntity().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        if (!(event.getEntity() instanceof Player))
            return;

        Player player = (Player) event.getEntity();

        if (gameManager.getGameState() != GameState.LIVE) {

            event.setCancelled(true);
            return;

        }

        if (gameManager.isSpectator(player)) {

            event.setCancelled(true);
            return;

        }

        if (gameManager.isHerobrine(player) && event.getCause() == EntityDamageEvent.DamageCause.FALL) {

            event.setCancelled(true);
            return;

        }

        if (gameManager.isHerobrine(player)
                && (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                        || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK)
                && gameManager.getShardCount() != 3)
        {

            event.setCancelled(true);
            gameManager.getHerobrine().setFireTicks(1);
            gameManager.getHerobrine().setVisualFire(false);

        }

        if (gameManager.isSurvivor(player)) {

            if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                    || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
            {

                gameManager.getHbLastHit().add(player);
                Bukkit.getServer().getScheduler().runTaskLater(gameManager.getPlugin(),
                        () -> gameManager.getHbLastHit().remove(player), 120);

            }

        }

    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {

        if (!event.getEntity().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        Player player = event.getEntity();
        event.deathMessage(Message.legacySerializerAnyCase(""));
        event.getDrops().clear();

        // Deaths outside the live round (the ending celebration, the hub) still have
        // to force a respawn -- a player left on the death screen cannot be
        // teleported out when the lobby tears its worlds down.
        if (gameManager.getGameState() != GameState.LIVE) {

            Bukkit.getServer().getScheduler().runTaskLater(gameManager.getPlugin(), () -> {

                if (player.isOnline() && player.isDead())
                    player.spigot().respawn();

            }, 20);
            return;

        }

        Bukkit.getServer().getScheduler().runTaskLater(gameManager.getPlugin(), () -> {

            if (player.isOnline() && player.isDead())
                player.spigot().respawn();

        }, 40);

        if (gameManager.isHerobrine(player)) {

            if (player.getKiller() != null) {

                Message.broadcast(gameLobby,
                        Message.format("&b" + player.getKiller().getName() + " &ahas defeated &c&lthe HEROBRINE!"));
                gameLobby.getStatManager().getPointsTracker().increment(player.getKiller().getUniqueId(), 30);

            }

            PlayerUtil.playSoundAt(player.getLocation(), Sound.ENTITY_WITHER_HURT, 1f, 1f);
            gameManager.end(WinType.SURVIVORS);

        } else {

            gameManager.markSurvivorDead(player);
            if ((player.getKiller() != null && gameManager.isHerobrine(player.getKiller()))
                    || gameManager.getHbLastHit().contains(player))
            {

                Message.broadcast(gameLobby,
                        Message.format("&b" + player.getName() + "&e was killed by &c&lthe HEROBRINE!"));
                gameLobby.getStatManager().getPointsTracker().increment(gameManager.getHerobrine().getUniqueId(), 5);

            }

            if (gameManager.isShardCarrier(player)) {

                if (player.getLastDamageCause() != null
                        && player.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID)
                {

                    gameManager.getShardHandler().destroy();

                } else {

                    gameManager.getShardHandler().drop(player.getLocation().add(0, 1, 0));

                }

            }

            gameManager.endCheck();

        }

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {

        if (!hasLobbyPlayer(event.getPlayer()))
            return;

        if (gameManager.getGameState() == GameState.LIVE || gameManager.getGameState() == GameState.ENDING) {

            event.setRespawnLocation(gameLobby.getWorldManager().survivorSpawn);
            gameManager.makeSpectator(event.getPlayer());

        }

    }

    @EventHandler
    public void onPotion(PotionSplashEvent event) {

        if (!event.getEntity().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        for (LivingEntity e : event.getAffectedEntities()) {

            if (e instanceof Player) {

                Player player = (Player) e;
                if (gameManager.isHerobrine(player) || !gameManager.isSurvivor(player))
                    Bukkit.getServer().getScheduler().runTaskLater(gameManager.getPlugin(), () -> {

                        event.getPotion().getEffects().forEach(effect -> {

                            player.removePotionEffect(effect.getType());

                        });

                        if (gameManager.isHerobrine(player)) {

                            if (gameManager.getShardCount() != 3)
                                PlayerUtil.addEffect(player, PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false,
                                        false);
                            PlayerUtil.addEffect(player, PotionEffectType.JUMP, Integer.MAX_VALUE, 1, false, false);
                            PlayerUtil.addEffect(player, PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false);

                        }

                    }, 1);

            }

        }

    }

    @EventHandler
    public void onProjectile(ProjectileLaunchEvent event) {

        if (!event.getEntity().getWorld().getName().startsWith(gameLobby.getLobbyId()))
            return;

        if (!(event.getEntity() instanceof Arrow))
            return;

        Arrow arrow = (Arrow) event.getEntity();
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);

    }

    // Chat is Chat-OG's now: it scopes delivery to the world and mirrors it to the
    // Herobrine Discord
    // channel. HerobrineChatFormatter keeps the per game state formatting.

}
