package uk.hotten.herobrine.commands;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import uk.hotten.herobrine.lobby.LobbyManager;

public class DeleteLobbyCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 1) {

            return LobbyManager.getInstance().getLobbyIds();

        }

        return null;

    }

}
