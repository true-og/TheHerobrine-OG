package uk.hotten.herobrine.commands;

import java.util.Arrays;
import java.util.Locale;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import uk.hotten.herobrine.lobby.LobbyManager;

// Routes /v and /vote to Herobrine voting in lobby worlds only.
public class VoteCommandListener implements Listener {

    private final VoteCommand voteCommand = new VoteCommand();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {

        String message = event.getMessage();
        if (message == null || message.length() < 2 || message.charAt(0) != '/')
            return;

        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty())
            return;

        // Drop any namespace prefix (e.g. plugin:v) before matching.
        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0)
            label = label.substring(colon + 1);

        if (!label.equals("v") && !label.equals("vote"))
            return;

        Player player = event.getPlayer();
        LobbyManager lm = LobbyManager.getInstance();

        // Only claim the command inside a Herobrine lobby world; defer elsewhere.
        if (lm == null || !lm.isManagedWorld(player.getWorld().getName()))
            return;

        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
        voteCommand.onCommand(player, null, label, args);
        event.setCancelled(true);

    }

}
