package uk.hotten.herobrine.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.lobby.data.LobbyConfig;
import uk.hotten.herobrine.utils.Message;

public class CreateLobbyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args == null || args.length == 0) {

            Message.send(sender, Message.format("&cCorrect Usage: /hbcreatelobby <hub>"));
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

        String hub = args[0];

        Message.send(sender, "Creating lobby from config '" + lobbyConfig.getId() + "' with hub '" + hub + "'...");
        String lobby = LobbyManager.getInstance().createLobby(lobbyConfig, hub);
        if (lobby == null) {

            Message.send(sender, Message.format("&cFailed to create lobby, please contact your administrator."));
            return true;

        }

        // Persist this manual lobby creation so the lobby is recreated after
        // server restarts. Persist both the active-lobby entry (to remember the
        // hub template) and increment autoStartAmount so auto-start counts remain
        // consistent.
        boolean activePersisted = LobbyManager.getInstance().persistActiveLobby(lobbyConfig.getId(), hub);
        boolean countPersisted = LobbyManager.getInstance().incrementAutoStartForConfig(lobbyConfig.getId(), 1);
        if (!activePersisted || !countPersisted) {
            Message.send(sender,
                    Message.format("&eLobby created but failed to persist; it may not survive a restart."));
        }

        Message.send(sender, Message.format("&aLobby " + lobby + " created successfully."));
        return true;

    }

}
