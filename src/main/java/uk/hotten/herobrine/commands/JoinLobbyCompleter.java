package uk.hotten.herobrine.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import uk.hotten.herobrine.lobby.LobbyManager;

public class JoinLobbyCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length != 1)
            return null;

        // Bukkit does not filter what a TabCompleter returns, so match here.
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        for (String id : LobbyManager.getInstance().getLobbyIds())
            if (id.toLowerCase(Locale.ROOT).startsWith(prefix))
                completions.add(id);

        return completions;

    }

}
