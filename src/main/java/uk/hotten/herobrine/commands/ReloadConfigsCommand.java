package uk.hotten.herobrine.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.utils.Console;
import uk.hotten.herobrine.utils.Message;

public class ReloadConfigsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        Message.send(sender,
                Message.format("&eNote: Active lobby instances will be rebuilt from the refreshed configs."));
        Message.send(sender, Message.format("&eReloading lobby configurations and rebuilding active lobbies..."));
        try {

            int recreated = LobbyManager.getInstance().reloadConfigs();

            Message.send(sender,
                    Message.format("&aSuccessfully loaded " + LobbyManager.getInstance().getLobbyConfigsIds().size()
                            + " lobby configuration(s) and rebuilt " + recreated + " lobby instance(s)."));

        } catch (Exception e) {

            Message.send(sender,
                    Message.format("&cFailed to reload lobby configuration files. Please contact your administrator."));
            Console.error("Failed to reload config files.");
            e.printStackTrace();

        }

        return true;

    }

}
