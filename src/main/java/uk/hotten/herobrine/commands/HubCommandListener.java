package uk.hotten.herobrine.commands;

import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import uk.hotten.herobrine.lobby.LobbyManager;

// Claims /hub, /lobby and /spawn for lobby members and anyone standing in a
// Herobrine lobby world. TheHerobrine-OG, Splegg-OG and BuildBattle-OG all
// declare /hub, and Bukkit hands the bare label to whichever registered first,
// so without this a Herobrine player's /hub could run another minigame's
// command; /spawn from Spawn-OG would teleport them out through its own warmup.
// Everywhere else the event is left alone.
public class HubCommandListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {

        String label = commandLabel(event.getMessage());
        if (!label.equals("hub") && !label.equals("lobby") && !label.equals("spawn"))
            return;

        Player player = event.getPlayer();
        LobbyManager lm = LobbyManager.getInstance();
        if (lm == null || (lm.getLobby(player) == null && !lm.isManagedWorld(player.getWorld().getName())))
            return;

        event.setCancelled(true);
        HubCommand.handle(player);

    }

    // The bare command name in lower case, without the leading slash or a
    // plugin namespace such as theherobrine-og:hub.
    static String commandLabel(String message) {

        if (message == null || message.length() < 2 || message.charAt(0) != '/')
            return "";

        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length == 0)
            return "";

        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0)
            label = label.substring(colon + 1);

        return label;

    }

}
