package uk.hotten.herobrine.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.utils.GameState;
import uk.hotten.herobrine.utils.Message;

public class SetHerobrineCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Message.format(ChatColor.RED + "You are unable to use this command."));
            return true;
        }

        GameManager gm;
        GameLobby gl = LobbyManager.getInstance().getLobby((Player) sender);
        if (gl == null) {
            sender.sendMessage(Message.format(ChatColor.RED + "You must be in a lobby to do this."));
            return true;
        }

        gm = gl.getGameManager();

        if (gm.getGameState() != GameState.WAITING && gm.getGameState() != GameState.STARTING) {
            sender.sendMessage(Message.format(ChatColor.RED + "You cannot run this command right now."));
            return true;
        }

        if (args == null || args.length == 0) {
            if (gm.getPassUser() == null)
                sender.sendMessage(Message.format(ChatColor.RED + "Correct Usage: /setherobrine <player>"));
            else {
                sender.sendMessage(Message.format(ChatColor.YELLOW + gm.getPassUser().getName() + ChatColor.RESET + " will no-longer be Herobrine."));
                gm.setPassUser(null);
            }
            return true;
        }

        Player player = Bukkit.getPlayer(args[0]);

        if (player == null) {
            sender.sendMessage(Message.format(ChatColor.RED + args[0] + " is not online."));
            return true;
        }

        if (!gm.getGameLobby().getPlayers().contains(player)) {
            sender.sendMessage(Message.format(ChatColor.RED + player.getName() + " is not in your lobby."));
            return true;
        }

        gm.setPassUser(player);
        sender.sendMessage(Message.format(ChatColor.YELLOW + player.getName() + ChatColor.RESET + " will be Herobrine."));
        return true;
    }
}
