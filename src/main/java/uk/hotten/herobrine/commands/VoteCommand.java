package uk.hotten.herobrine.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.world.WorldManager;

public class VoteCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Message.format(ChatColor.RED + "You are unable to use this command."));
            return true;
        }

        GameManager gm;
        WorldManager wm;
        GameLobby gl = LobbyManager.getInstance().getLobby((Player) sender);
        if (gl == null) {
            sender.sendMessage(Message.format(ChatColor.RED + "You must be in a lobby to do this."));
            return true;
        }

        gm = gl.getGameManager();
        wm = gl.getWorldManager();

        Player player = (Player) sender;

        if (!wm.isVotingRunning()) {
            player.sendMessage(Message.format(ChatColor.RED + "You cannot run this command right now."));
            return true;
        }

        if (args == null || args.length == 0) {
            wm.sendVotingMessage(player);
            return true;
        }

        int map;
        try {
            map = Integer.parseInt(args[0]);
        } catch (Exception e) {
            player.sendMessage(Message.format(ChatColor.RED + "Correct Usage: /vote <map number>"));
            wm.sendVotingMessage(player);
            return true;
        }

        if (!wm.getVotingMaps().containsKey(map)) {
            player.sendMessage(Message.format(ChatColor.RED + "Invalid map!"));
            wm.sendVotingMessage(player);
            return true;
        }

        if (wm.getPlayerVotes().get(player) == map) {
            player.sendMessage(Message.format(ChatColor.RED + "You have already voted for this map!"));
        } else {
            if (wm.getPlayerVotes().get(player) != 0) {
                wm.getVotingMaps().get(wm.getPlayerVotes().get(player)).decrementVotes();
            }
            wm.getPlayerVotes().remove(player);
            wm.getPlayerVotes().put(player, map);
            wm.getVotingMaps().get(map).incrementVotes();
            player.sendMessage(Message.format(ChatColor.GOLD + "Vote received. " + ChatColor.AQUA + wm.getVotingMaps().get(map).getMapData().getName() + ChatColor.GOLD + " now has " + ChatColor.AQUA + wm.getVotingMaps().get(map).getVotes() + ChatColor.GOLD + " votes."));
        }

        return true;
    }
}
