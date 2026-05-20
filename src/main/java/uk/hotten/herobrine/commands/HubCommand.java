package uk.hotten.herobrine.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.mw.MyWorlds;

import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.utils.Message;

public class HubCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {

            Message.send(sender, Message.format("&cYou are unable to use this command."));
            return true;

        }

        LobbyManager lm = LobbyManager.getInstance();
        Location savedLoc = lm != null ? lm.getAndRemovePreJoinLocation(player.getUniqueId()) : null;
        if (savedLoc != null && savedLoc.getWorld() != null) {

            if (!player.teleport(savedLoc)) {

                Message.send(player, Message.format("&cUnable to return you to your previous location."));
                return true;

            }

            Message.send(player, Message.format("&aReturned to your previous location."));
            return true;

        }

        World mainWorld = MyWorlds.getMainWorld();
        if (mainWorld == null)
            mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null && !Bukkit.getWorlds().isEmpty())
            mainWorld = Bukkit.getWorlds().get(0);

        if (mainWorld == null) {

            Message.send(player, Message.format("&cNo main world is available."));
            return true;

        }

        Location destination = mainWorld.getSpawnLocation();
        if (!player.teleport(destination)) {

            Message.send(player, Message.format("&cUnable to return you to the hub."));
            return true;

        }

        Message.send(player, Message.format("&aReturned to the hub."));
        return true;

    }

}
