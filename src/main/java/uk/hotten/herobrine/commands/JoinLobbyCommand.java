package uk.hotten.herobrine.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.lobby.LobbyManager.JoinResult;
import uk.hotten.herobrine.utils.Message;

public class JoinLobbyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {

            Message.send(sender, Message.format("&cYou are unable to use this command."));
            return true;

        }

        Player player = (Player) sender;
        LobbyManager lm = LobbyManager.getInstance();

        if (args == null || args.length == 0) {

            lm.sendLobbyMessage(player);
            return true;

        }

        GameLobby gl = lm.getLobby(args[0]);
        if (gl == null) {

            Message.send(player, Message.format("&a" + args[0] + " does not exist."));
            return true;

        }

        JoinResult result = lm.attemptJoin(player, gl);
        switch (result) {

            case OK -> Message.send(player, Message.format("&aJoining " + gl.getLobbyId() + "..."));
            case UNAVAILABLE_STATE -> Message.send(player, Message.format("&cThis lobby cannot be joined right now."));
            case FULL -> Message.send(player, Message.format("&cThis lobby is full."));
            case ALREADY_IN -> Message.send(player, Message.format("&cYou're already in this lobby!"));
            case NO_HUB -> Message.send(player, Message.format("&cThis lobby is unavailable right now."));
            case UNKNOWN_LOBBY -> Message.send(player, Message.format("&cThat lobby no longer exists."));

        }

        return true;

    }

}
