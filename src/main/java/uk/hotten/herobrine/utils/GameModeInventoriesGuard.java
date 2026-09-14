package uk.hotten.herobrine.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

// Keeps GameModeInventories-OG out of the gamemode changes a minigame stay causes.
//
// GameModeInventories-OG swaps inventory, armour, XP and ender chest on every
// gamemode change, keyed by player and gamemode only, never by world. MyWorlds
// swaps the same data per world bundle and, on entering a world, re-applies the
// gamemode saved with that bundle. A player who was put in adventure or spectator
// inside the minigame therefore flips back to survival in the overworld right
// after MyWorlds restored their real inventory; GMI answers that flip by filing
// the real inventory and ender chest under the minigame gamemode and loading the
// empty SURVIVAL snapshot it took inside the minigame. That is a full wipe.
//
// Negating gamemodeinventories.use through a permission attachment for as long
// as the player is inside minigame territory, and for one tick after leaving it,
// keeps GMI out of every flip on both ends. gamemodeinventories.death is negated
// alongside it: a staff death inside the minigame would otherwise file the kit
// under their survival row and re-apply it on a respawn that lands outside.
// gamemodeinventories.spectator is granted so GMI's restrict_spectator cannot
// cancel the spectator mode a minigame puts eliminated players in.
// Same class in Splegg-OG, TheHerobrine-OG and BuildBattle-OG.
public final class GameModeInventoriesGuard {

    private static final String GMI_USE_PERMISSION = "gamemodeinventories.use";
    private static final String GMI_DEATH_PERMISSION = "gamemodeinventories.death";
    private static final String GMI_SPECTATOR_PERMISSION = "gamemodeinventories.spectator";

    private final Plugin plugin;
    private final Predicate<Player> insideTerritory;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public GameModeInventoriesGuard(Plugin plugin, Predicate<Player> insideTerritory) {

        this.plugin = plugin;
        this.insideTerritory = insideTerritory;

    }

    // Idempotent. Must run before the first teleport into, or gamemode change
    // inside, minigame territory.
    public void suspend(Player player) {

        if (player == null || attachments.containsKey(player.getUniqueId()))
            return;

        try {

            final PermissionAttachment attachment = player.addAttachment(plugin);
            attachment.setPermission(GMI_USE_PERMISSION, false);
            attachment.setPermission(GMI_DEATH_PERMISSION, false);
            attachment.setPermission(GMI_SPECTATOR_PERMISSION, true);
            attachments.put(player.getUniqueId(), attachment);

        } catch (IllegalArgumentException | IllegalStateException ex) {

            plugin.getLogger().warning("Could not suspend GameModeInventories for " + player.getName() + ": "
                    + ex.getMessage() + ". Their inventory is at risk on the way out of the minigame.");

        }

    }

    public boolean isSuspended(Player player) {

        return player != null && attachments.containsKey(player.getUniqueId());

    }

    public void release(Player player) {

        if (player != null)
            release(player.getUniqueId());

    }

    public void release(UUID playerId) {

        final PermissionAttachment attachment = attachments.remove(playerId);
        if (attachment == null)
            return;

        try {

            attachment.remove();

        } catch (IllegalArgumentException ignored) {

            // The player instance is already gone; the attachment died with it.

        }

    }

    // The world change that follows a teleport event, and MyWorlds' gamemode
    // restore inside it, must finish under the suspension, so the release is
    // deferred a tick. A player who is back inside territory by then (another
    // plugin bounced them straight back) keeps it.
    public void releaseAfterLeaving(Player player) {

        if (player == null)
            return;

        if (!plugin.isEnabled()) {

            release(player);
            return;

        }

        Bukkit.getScheduler().runTask(plugin, () -> {

            if (!player.isOnline()) {

                release(player.getUniqueId());
                return;

            }

            if (insideTerritory.test(player))
                return;

            release(player);

        });

    }

    // Suspends everyone already standing in territory, for /reload and for
    // players who were inside before the plugin came up.
    public void sweepOnlinePlayers() {

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (insideTerritory.test(player))
                suspend(player);

        }

    }

    public void releaseAll() {

        for (UUID playerId : new java.util.ArrayList<>(attachments.keySet()))
            release(playerId);

    }

}
