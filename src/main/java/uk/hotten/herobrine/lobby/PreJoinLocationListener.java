package uk.hotten.herobrine.lobby;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import uk.hotten.herobrine.HerobrinePluginOG;
import uk.hotten.herobrine.utils.Console;
import uk.hotten.herobrine.utils.GameModeInventoriesGuard;

// Plugin-scoped safety net for pre-join locations. A lobby unregisters its own
// listeners before it deletes its worlds, so anything that has to outlive the
// teardown (a respawn, a relog) is handled here instead of in GMListener.
public class PreJoinLocationListener implements Listener {

    private final JavaPlugin plugin;

    public PreJoinLocationListener(JavaPlugin plugin) {

        this.plugin = plugin;

    }

    // Players reach a lobby world by more than /hbjoin -- /world tp, portals and
    // other plugins all land here, and without a recorded spot those players get
    // dumped at main world spawn when the lobby tears down.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {

        LobbyManager lm = LobbyManager.getInstance();
        if (lm == null)
            return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null)
            return;
        if (from.getWorld().equals(to.getWorld()))
            return;

        boolean fromManaged = lm.isManagedWorld(from.getWorld().getName());
        boolean toManaged = lm.isManagedWorld(to.getWorld().getName());

        GameModeInventoriesGuard guard = HerobrinePluginOG.getGmiGuard();

        if (toManaged && !fromManaged) {

            lm.savePreJoinLocation(event.getPlayer().getUniqueId(), from);
            // Before the world change: MyWorlds' gamemode restore on entry and every
            // spectator/survival flip the lobby makes must run without
            // GameModeInventories.
            if (guard != null)
                guard.suspend(event.getPlayer());
            return;

        }

        // Left a lobby world for a real one by any route, so the recorded spot has
        // served its purpose. Dropping it stops a stale spot yanking them later.
        if (fromManaged && !toManaged) {

            lm.removePreJoinLocation(event.getPlayer().getUniqueId());
            // Stays attached through this teleport's world change (MyWorlds restores
            // the real inventory and then the saved survival gamemode there).
            if (guard != null) {

                guard.suspend(event.getPlayer());
                guard.releaseAfterLeaving(event.getPlayer());

            }

        }

    }

    // A respawn is a world change without a teleport event, so the suspension
    // is handled here for both directions.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawnMonitor(PlayerRespawnEvent event) {

        LobbyManager lm = LobbyManager.getInstance();
        GameModeInventoriesGuard guard = HerobrinePluginOG.getGmiGuard();
        if (lm == null || guard == null)
            return;

        Player player = event.getPlayer();
        Location respawn = event.getRespawnLocation();
        if (respawn == null || respawn.getWorld() == null)
            return;

        boolean fromManaged = player.getWorld() != null && lm.isManagedWorld(player.getWorld().getName());
        boolean toManaged = lm.isManagedWorld(respawn.getWorld().getName());

        if (toManaged) {

            guard.suspend(player);
            return;

        }

        if (fromManaged) {

            guard.suspend(player);
            guard.releaseAfterLeaving(player);

        }

    }

    // A login inside a lobby world: MyWorlds forces the world's gamemode at join,
    // and the return teleport follows a tick later.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {

        LobbyManager lm = LobbyManager.getInstance();
        GameModeInventoriesGuard guard = HerobrinePluginOG.getGmiGuard();
        if (lm == null || guard == null || event.getResult() != PlayerLoginEvent.Result.ALLOWED)
            return;

        Player player = event.getPlayer();
        if (player.getWorld() != null && lm.isManagedWorld(player.getWorld().getName()))
            guard.suspend(player);

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {

        GameModeInventoriesGuard guard = HerobrinePluginOG.getGmiGuard();
        if (guard != null)
            guard.release(event.getPlayer());

    }

    // A player still on the death screen when their lobby world is deleted would
    // otherwise respawn at main world spawn.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {

        LobbyManager lm = LobbyManager.getInstance();
        if (lm == null)
            return;

        Player player = event.getPlayer();

        // A live lobby owns its own respawns.
        if (lm.getLobby(player) != null)
            return;

        if (!lm.hasPreJoinLocation(player.getUniqueId()))
            return;

        Location respawn = event.getRespawnLocation();
        if (respawn != null && respawn.getWorld() != null && lm.isManagedWorld(respawn.getWorld().getName()))
            return;

        Location saved = lm.resolvePreJoinLocation(player.getUniqueId());
        if (saved == null)
            return;

        event.setRespawnLocation(saved);
        lm.removePreJoinLocation(player.getUniqueId());
        Console.debug("Respawned " + player.getName() + " at their pre-join location after lobby teardown.");

    }

    // Quitting inside a lobby world leaves the player logged out in a world that
    // gets deleted, so the server drops them at main world spawn on their next
    // login. Send them back to where they were before they joined.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {

        LobbyManager lm = LobbyManager.getInstance();
        if (lm == null)
            return;

        Player player = event.getPlayer();
        if (lm.getLobby(player) != null)
            return;
        if (player.getWorld() != null && lm.isManagedWorld(player.getWorld().getName()))
            return;
        if (!lm.hasPreJoinLocation(player.getUniqueId()))
            return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            if (!player.isOnline())
                return;
            if (!lm.hasPreJoinLocation(player.getUniqueId()))
                return;
            if (lm.returnPlayer(player, null))
                Console.debug("Returned " + player.getName() + " to their pre-join location on login.");

        }, 20);

    }

}
