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

// /hub and /lobby. Sends the player back where they came from, or to main
// spawn; leaving the lobby world is what removes them from the lobby.
// HubCommandListener routes /hub, /lobby and /spawn here for anyone inside a
// Herobrine lobby, whichever plugin owns the bare label.
public class HubCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {

            Message.send(sender, Message.format("&cYou are unable to use this command."));
            return true;

        }

        handle(player);
        return true;

    }

    public static void handle(Player player) {

        LobbyManager lm = LobbyManager.getInstance();
        if (lm != null && lm.hasPreJoinLocation(player.getUniqueId())) {

            if (!lm.returnPlayer(player, null)) {

                Message.send(player, Message.format("&cUnable to return you to your previous location."));
                return;

            }

            Message.send(player, Message.format("&aReturned to your previous location."));
            return;

        }

        // MyWorlds' main world spawn is what Spawn-OG's /setspawn writes, so this
        // lands on the server spawn.
        World mainWorld = MyWorlds.getMainWorld();
        if (mainWorld == null)
            mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null && !Bukkit.getWorlds().isEmpty())
            mainWorld = Bukkit.getWorlds().get(0);

        if (mainWorld == null) {

            Message.send(player, Message.format("&cNo main world is available."));
            return;

        }

        Location destination = mainWorld.getSpawnLocation();
        if (!player.teleport(destination)) {

            Message.send(player, Message.format("&cUnable to return you to the hub."));
            return;

        }

        Message.send(player, Message.format("&aReturned to the hub."));

    }

}
