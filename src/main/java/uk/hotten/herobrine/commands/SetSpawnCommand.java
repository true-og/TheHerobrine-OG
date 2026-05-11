package uk.hotten.herobrine.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.world.MapSetupWizard;
import uk.hotten.herobrine.world.data.Datapoint;
import uk.hotten.herobrine.world.data.DatapointType;
import uk.hotten.herobrine.world.data.MapData;

public class SetSpawnCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {

            Message.send(sender, Message.format("&cYou must be in-game to set spawn points."));
            return true;

        }

        Player player = (Player) sender;
        JavaPlugin plugin = JavaPlugin.getPlugin(uk.hotten.herobrine.HerobrinePluginOG.class);

        if (args == null || args.length == 0) {

            sendUsage(player);
            MapSetupWizard.sendForCurrentWorld(plugin, player);
            return true;

        }

        ShardBound shardBound = parseShardBound(args[0]);
        DatapointType type = shardBound == null ? parseType(args[0]) : null;
        if (type == null && shardBound == null) {

            sendUsage(player);
            return true;

        }

        Integer shardIndex = null;
        String mapNameOverride = null;

        if (type == DatapointType.SHARD_SPAWN) {

            // /hbsetspawn shard [index] [mapName]
            if (args.length >= 2 && !args[1].equalsIgnoreCase("append")) {

                try {

                    shardIndex = Integer.parseInt(args[1]);

                } catch (NumberFormatException ex) {

                    Message.send(player, Message.format("&cShard index must be an integer (or 'append')."));
                    return true;

                }

            }

            if (args.length >= 3)
                mapNameOverride = args[2];

        } else {

            // /hbsetspawn <type> [mapName]
            if (args.length >= 2)
                mapNameOverride = args[1];

        }

        File baseDir = MapSetupWizard.resolveBaseDir(plugin);

        String mapName = mapNameOverride != null ? mapNameOverride : MapSetupWizard.deriveMapName(player, baseDir);
        if (mapName == null) {

            Message.send(player,
                    Message.format("&cCould not derive a map name from world '" + player.getWorld().getName()
                            + "'. Pass it explicitly: /hbsetspawn " + args[0].toLowerCase()
                            + (type == DatapointType.SHARD_SPAWN ? " <index|append>" : "") + " <mapName>"));
            return true;

        }

        File mapDir = new File(baseDir, mapName);
        if (!mapDir.exists() || !mapDir.isDirectory()) {

            Message.send(player, Message.format("&cMap folder does not exist: &e" + mapDir.getPath()
                    + "&c -- create it (a copy of the world) first."));
            return true;

        }

        File mapdataFile = new File(mapDir, "mapdata.yaml");

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        MapData data;
        if (mapdataFile.exists()) {

            try {

                data = mapper.readValue(mapdataFile, MapData.class);
                if (data == null)
                    data = new MapData();

            } catch (Exception e) {

                Message.send(player, Message.format("&cFailed to parse existing mapdata.yaml: " + e.getMessage()));
                e.printStackTrace();
                return true;

            }

        } else {

            data = new MapData();

        }

        if (data.getName() == null || data.getName().isBlank())
            data.setName(mapName);
        if (data.getBuilder() == null || data.getBuilder().isBlank())
            data.setBuilder("Unknown");
        if (data.getDatapoints() == null)
            data.setDatapoints(new ArrayList<>());

        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();

        if (shardBound != null) {

            if (shardBound == ShardBound.MIN) {

                data.setShardMin(y);
                Message.send(player, Message.format("&aSet &bshardMin&a for &b" + mapName + "&a to Y=&e" + y
                        + "&a (shards that fall" + " below this Y will be destroyed)."));

            } else {

                data.setShardMax(y);
                Message.send(player, Message.format("&aSet &bshardMax&a for &b" + mapName + "&a to Y=&e" + y
                        + "&a (shards that rise" + " above this Y will be destroyed)."));

            }

            if (!Double.isNaN(data.getShardMin()) && !Double.isNaN(data.getShardMax())
                    && data.getShardMin() >= data.getShardMax())
            {

                Message.send(player,
                        Message.format("&eWarning: shardMin (&f" + ((long) data.getShardMin()) + "&e) is not below"
                                + " shardMax (&f" + ((long) data.getShardMax()) + "&e); shards will be destroyed"
                                + " on every check. Update one of them."));

            }

        } else if (type == DatapointType.SHARD_SPAWN) {

            // Collect current shard points so we can address them by index.
            ArrayList<Datapoint> shards = new ArrayList<>();
            for (Datapoint dp : data.getDatapoints())
                if (dp.getType() == DatapointType.SHARD_SPAWN)
                    shards.add(dp);

            if (shardIndex == null) {

                data.getDatapoints().add(new Datapoint(DatapointType.SHARD_SPAWN, x, y, z));
                Message.send(player, Message.format("&aAdded shard spawn #" + (shards.size() + 1) + " for &b" + mapName
                        + "&a at &e" + x + "," + y + "," + z));

            } else {

                if (shardIndex < 1 || shardIndex > shards.size() + 1) {

                    Message.send(player, Message.format("&cShard index must be between 1 and " + (shards.size() + 1)
                            + " (inclusive). Use 'append' to add a new one."));
                    return true;

                }

                if (shardIndex == shards.size() + 1) {

                    data.getDatapoints().add(new Datapoint(DatapointType.SHARD_SPAWN, x, y, z));
                    Message.send(player, Message.format("&aAdded shard spawn #" + shardIndex + " for &b" + mapName
                            + "&a at &e" + x + "," + y + "," + z));

                } else {

                    Datapoint target = shards.get(shardIndex - 1);
                    target.setX(x);
                    target.setY(y);
                    target.setZ(z);
                    Message.send(player, Message.format("&aUpdated shard spawn #" + shardIndex + " for &b" + mapName
                            + "&a to &e" + x + "," + y + "," + z));

                }

            }

        } else {

            // Single-instance datapoints: replace existing or add.
            Datapoint existing = null;
            for (Datapoint dp : data.getDatapoints()) {

                if (dp.getType() == type) {

                    existing = dp;
                    break;

                }

            }

            if (existing != null) {

                existing.setX(x);
                existing.setY(y);
                existing.setZ(z);

            } else {

                data.getDatapoints().add(new Datapoint(type, x, y, z));

            }

            Message.send(player, Message
                    .format("&aSet &b" + type.name() + "&a for &b" + mapName + "&a to &e" + x + "," + y + "," + z));

        }

        try {

            mapper.writerWithDefaultPrettyPrinter().writeValue(mapdataFile, data);

        } catch (Exception e) {

            Message.send(player,
                    Message.format("&cFailed to write mapdata.yaml: " + e.getMessage() + " (changes lost)"));
            e.printStackTrace();
            return true;

        }

        Message.send(player, Message.format("&7Wrote &e" + mapdataFile.getPath()
                + "&7. Restart the lobby (or /hbreloadconfigs after rebuild) for changes to take effect."));
        MapSetupWizard.sendProgress(player, mapName, data);
        return true;

    }

    private enum ShardBound {
        MIN, MAX
    }

    private static ShardBound parseShardBound(String raw) {

        switch (raw.toLowerCase(Locale.ROOT)) {

            case "shardmin":
            case "shard_min":
            case "shard-min":
            case "minshard":
            case "min":
                return ShardBound.MIN;
            case "shardmax":
            case "shard_max":
            case "shard-max":
            case "maxshard":
            case "max":
                return ShardBound.MAX;
            default:
                return null;

        }

    }

    private static DatapointType parseType(String raw) {

        switch (raw.toLowerCase(Locale.ROOT)) {

            case "survivor":
            case "survivors":
            case "survivor_spawn":
                return DatapointType.SURVIVOR_SPAWN;
            case "herobrine":
            case "hb":
            case "herobrine_spawn":
                return DatapointType.HEROBRINE_SPAWN;
            case "alter":
            case "altar":
                return DatapointType.ALTER;
            case "shard":
            case "shards":
            case "shard_spawn":
                return DatapointType.SHARD_SPAWN;
            default:
                return null;

        }

    }

    private static void sendUsage(Player player) {

        Message.send(player, Message.format(
                "&6/hbsetspawn <survivor|herobrine|alter|shard|shardmin|shardmax>" + " [shardIndex|append] [mapName]"));
        Message.send(player, Message.format("&7Stand at the location, then run the command."));
        Message.send(player, Message.format("&7shardmin / shardmax use your current Y as the lower / upper bound a"
                + " shard may reach before being destroyed."));
        Message.send(player, Message.format(
                "&7mapName is auto-derived from your world (e.g. 'HB1-Ancient_Plateau' -> 'Ancient_Plateau')."));

    }

}
