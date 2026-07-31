package uk.hotten.herobrine.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import uk.hotten.herobrine.HerobrinePluginOG;
import uk.hotten.herobrine.world.WorldManager;

public class CreateLobbyCompleter implements TabCompleter {

    // Hub templates are named <prefix><number>_Hub, e.g. HB1_Hub.
    private static final Pattern NUMBERED_HUB = Pattern.compile("^[A-Za-z]+(\\d+)_hub$", Pattern.CASE_INSENSITIVE);

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {

        List<String> completions = new ArrayList<>();
        if (args == null || args.length != 1)
            return completions;

        File baseDir = WorldManager.resolveBaseDir(JavaPlugin.getPlugin(HerobrinePluginOG.class));
        File[] templates = baseDir.listFiles(File::isDirectory);
        if (templates == null)
            return completions;

        String typed = args[0].toLowerCase();
        for (File template : templates) {

            String name = template.getName();
            if (name.toLowerCase().startsWith(typed))
                completions.add(name);

            // A numbered hub also answers to its bare number.
            Matcher matcher = NUMBERED_HUB.matcher(name);
            if (matcher.matches() && matcher.group(1).startsWith(typed))
                completions.add(matcher.group(1));

        }

        return completions;

    }

}
