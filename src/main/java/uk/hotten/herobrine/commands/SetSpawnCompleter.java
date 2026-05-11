package uk.hotten.herobrine.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class SetSpawnCompleter implements TabCompleter {

    private static final List<String> TYPES = Arrays.asList("survivor", "herobrine", "alter", "shard", "shardmin",
            "shardmax");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 1) {

            String prefix = args[0].toLowerCase();
            return TYPES.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());

        }

        if (args.length == 2 && args[0].equalsIgnoreCase("shard"))
            return Arrays.asList("append", "1", "2", "3", "4", "5");

        return Collections.emptyList();

    }

}
