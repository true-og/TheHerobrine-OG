package uk.hotten.herobrine.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import uk.hotten.herobrine.HerobrinePluginOG;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.world.MapSetupWizard;

public class HbWizardCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {

            Message.send(sender, Message.format("&cYou must be in-game to use the map setup wizard."));
            return true;

        }

        if (!player.hasPermission(MapSetupWizard.WIZARD_PERMISSION)) {

            Message.send(player, Message.format("&cYou don't have permission to use the map setup wizard."));
            return true;

        }

        JavaPlugin plugin = JavaPlugin.getPlugin(HerobrinePluginOG.class);
        MapSetupWizard.sendForCurrentWorld(plugin, player);
        return true;

    }

}
