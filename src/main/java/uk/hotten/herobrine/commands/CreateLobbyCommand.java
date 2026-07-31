package uk.hotten.herobrine.commands;

import java.io.File;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import uk.hotten.herobrine.HerobrinePluginOG;
import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.lobby.data.LobbyConfig;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.world.WorldManager;

public class CreateLobbyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args == null || args.length == 0) {

            Message.send(sender, Message.format("&cCorrect Usage: /hbcreatelobby <hub|number>"));
            return true;

        }

        LobbyConfig lobbyConfig = LobbyManager.getInstance().getLobbyConfig("default");
        if (lobbyConfig == null && !LobbyManager.getInstance().getLobbyConfigsIds().isEmpty()) {

            lobbyConfig = LobbyManager.getInstance()
                    .getLobbyConfig(LobbyManager.getInstance().getLobbyConfigsIds().get(0));

        }

        if (lobbyConfig == null) {

            Message.send(sender, Message.format("&cNo lobby configurations are available."));
            return true;

        }

        // Resolve the argument to a real template folder up front so '1' can stand
        // for 'HB1_Hub' and a typo is reported here instead of failing the lobby.
        File baseDir = WorldManager.resolveBaseDir(JavaPlugin.getPlugin(HerobrinePluginOG.class));
        File template = WorldManager.resolveTemplateDir(baseDir, lobbyConfig.getPrefix(), args[0]);
        if (template == null) {

            Message.send(sender, Message.format("&cNo hub template named '" + args[0] + "'."));
            Message.send(sender, Message.format("&c" + WorldManager.describeTemplates(baseDir)));
            return true;

        }

        String hub = template.getName();

        Message.send(sender, "Creating lobby from config '" + lobbyConfig.getId() + "' with hub '" + hub + "'...");
        String lobby = LobbyManager.getInstance().createLobby(lobbyConfig, hub);
        if (lobby == null) {

            Message.send(sender, Message.format("&cFailed to create lobby, please contact your administrator."));
            return true;

        }

        // Persist this manual lobby creation so it is recreated with the same hub
        // template after a restart. autoStartAmount stays untouched; it counts only
        // the lobbies the config itself asks for.
        boolean activePersisted = LobbyManager.getInstance().persistActiveLobby(lobbyConfig.getId(), hub);
        if (!activePersisted) {

            Message.send(sender,
                    Message.format("&eLobby created but failed to persist; it may not survive a restart."));

        }

        Message.send(sender, Message.format("&aLobby " + lobby + " created successfully."));
        return true;

    }

}
