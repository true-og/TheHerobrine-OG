package uk.hotten.herobrine.sign;

import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.lobby.LobbyManager.JoinResult;
import uk.hotten.herobrine.lobby.data.LobbyConfig;
import uk.hotten.herobrine.utils.Console;
import uk.hotten.herobrine.utils.Message;

public class JoinSignListener implements Listener {

    public static final String HEADER = "[Herobrine]";
    public static final String PERM_CREATE = "theherobrine.signs.create";
    public static final String PERM_DESTROY = "theherobrine.signs.destroy";

    private final JavaPlugin plugin;
    private final JoinSignManager manager;

    public JoinSignListener(JavaPlugin plugin, JoinSignManager manager) {

        this.plugin = plugin;
        this.manager = manager;

    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {

        Component line0 = event.line(0);
        if (line0 == null)
            return;

        String header = PlainTextComponentSerializer.plainText().serialize(line0).trim();
        if (!header.equalsIgnoreCase(HEADER))
            return;

        Player player = event.getPlayer();
        if (!player.hasPermission(PERM_CREATE)) {

            Message.send(player, Message.format("&cYou don't have permission to create join signs."));
            event.setCancelled(true);
            return;

        }

        Component line1 = event.line(1);
        String configId = line1 == null ? "" : PlainTextComponentSerializer.plainText().serialize(line1).trim();
        if (configId.isEmpty()) {

            Message.send(player, Message.format("&cLine 2 must be the lobby config id."));
            event.setCancelled(true);
            return;

        }

        LobbyConfig cfg = LobbyManager.getInstance().getLobbyConfig(configId);
        if (cfg == null) {

            Message.send(player, Message.format("&cUnknown lobby config '" + configId + "'."));
            event.setCancelled(true);
            return;

        }

        Block block = event.getBlock();
        Location loc = block.getLocation();
        String worldName = loc.getWorld().getName();
        JoinSign js = new JoinSign(worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), configId);
        manager.register(js);

        Message.send(player, Message.format("&aJoin sign registered for config '" + configId + "'."));
        if (isEphemeralLobbyWorld(worldName))
            Message.send(player, Message.format(
                    "&eWarning: this world is managed by TheHerobrine and may be deleted on shutdown. Place join signs in a persistent hub world."));
        Console.info("Registered join sign at " + js.key() + " -> " + configId + " by " + player.getName());

        String lobbyLabel = configId;
        LobbyManager.LobbyAggregate aggregate = LobbyManager.getInstance().aggregateForConfig(configId);
        if (aggregate.displayLobbyId() != null)
            lobbyLabel = aggregate.displayLobbyId();

        // Provisional lines; updater task will overwrite shortly.
        event.line(0, Component.text("§4The Herobrine"));
        event.line(1, Component.text("§6" + lobbyLabel));
        event.line(2, Component.text("§7Loading..."));
        event.line(3, Component.text(""));

    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Block block = event.getClickedBlock();
        if (block == null)
            return;
        if (!Tag.SIGNS.isTagged(block.getType()) && !Tag.WALL_SIGNS.isTagged(block.getType()))
            return;

        JoinSign js = manager.get(block.getLocation());
        if (js == null)
            return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        LobbyManager lm = LobbyManager.getInstance();

        LobbyConfig cfg = lm.getLobbyConfig(js.getLobbyConfigId());
        if (cfg == null) {

            Message.send(player, Message.format("&cConfig '" + js.getLobbyConfigId() + "' no longer exists."));
            return;

        }

        GameLobby target = lm.pickJoinableLobbyForConfig(js.getLobbyConfigId(), player);
        if (target == null) {

            Message.send(player, Message.format("&cNo joinable lobby for '" + js.getLobbyConfigId() + "' right now."));
            return;

        }

        JoinResult result = lm.attemptJoin(player, target);
        switch (result) {

            case OK -> Message.send(player, Message.format("&aJoining " + target.getLobbyId() + "..."));
            case UNAVAILABLE_STATE -> Message.send(player, Message.format("&cThat lobby cannot be joined right now."));
            case FULL -> Message.send(player, Message.format("&cAll lobbies are full."));
            case ALREADY_IN -> Message.send(player, Message.format("&cYou're already in this lobby!"));
            case NO_HUB -> Message.send(player, Message.format("&cThat lobby is unavailable right now."));
            case UNKNOWN_LOBBY -> Message.send(player, Message.format("&cThat lobby no longer exists."));

        }

    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {

        Block block = event.getBlock();
        if (!Tag.SIGNS.isTagged(block.getType()) && !Tag.WALL_SIGNS.isTagged(block.getType()))
            return;

        JoinSign js = manager.get(block.getLocation());
        if (js == null)
            return;

        Player player = event.getPlayer();
        if (!player.hasPermission(PERM_DESTROY)) {

            event.setCancelled(true);
            Message.send(player, Message.format("&cYou don't have permission to destroy join signs."));
            return;

        }

        manager.unregister(block.getLocation());
        Message.send(player, Message.format("&aJoin sign removed."));
        Console.info("Removed join sign at " + js.key() + " by " + player.getName());

    }

    private boolean isEphemeralLobbyWorld(String worldName) {

        if (worldName == null)
            return false;
        LobbyManager lm = LobbyManager.getInstance();
        if (lm == null)
            return false;
        for (String configId : lm.getLobbyConfigsIds()) {

            LobbyConfig cfg = lm.getLobbyConfig(configId);
            if (cfg == null)
                continue;
            String prefix = cfg.getPrefix();
            if (prefix != null && !prefix.isEmpty() && worldName.startsWith(prefix))
                return true;

        }

        return false;

    }

    // Reserved hook in case sign block is needed in future logic.
    @SuppressWarnings("unused")
    private Sign asSign(Block block) {

        if (block.getState() instanceof Sign s)
            return s;
        return null;

    }

    @SuppressWarnings("unused")
    private JavaPlugin plugin() {

        return plugin;

    }

}
