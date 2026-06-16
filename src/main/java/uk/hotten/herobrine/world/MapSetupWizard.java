package uk.hotten.herobrine.world;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.world.data.Datapoint;
import uk.hotten.herobrine.world.data.DatapointType;
import uk.hotten.herobrine.world.data.MapData;

public class MapSetupWizard {

    public static final String SETSPAWN_PERMISSION = "theherobrine.command.setspawn";

    // Staff-only gate for the /hbwizard map-setup walkthrough.
    public static final String WIZARD_PERMISSION = "theherobrine.command.wizard";

    public static final int MIN_SHARD_SPAWNS = 3;

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    static {

        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    }

    public static void sendForCurrentWorld(JavaPlugin plugin, Player player) {

        final File baseDir = resolveBaseDir(plugin);
        final String mapName = deriveMapName(player, baseDir);

        if (mapName == null)
            return;

        final MapData data = loadMapData(baseDir, mapName);
        sendProgress(player, mapName, data);

    }

    public static void sendProgress(Player player, String mapName, MapData data) {

        final List<String> missing = getMissingSteps(data);
        final int shardCount = countDatapoints(data, DatapointType.SHARD_SPAWN);

        Message.send(player, Message.format("&6Map setup: &b" + mapName));
        sendStep(player, "1", "survivor spawn", DatapointType.SURVIVOR_SPAWN, data, "/hbsetspawn survivor");
        sendStep(player, "2", "herobrine spawn", DatapointType.HEROBRINE_SPAWN, data, "/hbsetspawn herobrine");
        sendStep(player, "3", "alter", DatapointType.ALTER, data, "/hbsetspawn alter");

        if (shardCount >= MIN_SHARD_SPAWNS)
            Message.send(player, Message.format("&a✔ &f4 shards &7(" + shardCount + ")"));
        else
            Message.send(player, Message
                    .format("&c✗ &f4 shards &7(" + shardCount + "/" + MIN_SHARD_SPAWNS + ") &8» &e/hbsetspawn shard"));

        if (!Double.isNaN(data.getShardMin()))
            Message.send(player, Message.format("&a✔ &f5 shard min &7Y=" + ((long) data.getShardMin())));
        else
            Message.send(player, Message.format("&c✗ &f5 shard min &8» &e/hbsetspawn shardmin"));

        if (!Double.isNaN(data.getShardMax()))
            Message.send(player, Message.format("&a✔ &f6 shard max &7Y=" + ((long) data.getShardMax())));
        else
            Message.send(player, Message.format("&c✗ &f6 shard max &8» &e/hbsetspawn shardmax"));

        if (missing.isEmpty())
            Message.send(player, Message.format("&aDone. &e/hbreloadconfigs &ato apply."));
        else
            Message.send(player, Message.format("&eNext: &e" + missing.get(0)));

    }

    public static File resolveBaseDir(JavaPlugin plugin) {

        final String fileBase = plugin.getConfig().getString("mapBase");
        if (fileBase == null || fileBase.isBlank())
            return plugin.getServer().getWorldContainer();

        final File file = new File(fileBase);
        if (file.isAbsolute())
            return file;

        return new File(plugin.getServer().getWorldContainer(), fileBase);

    }

    public static String deriveMapName(Player player, File baseDir) {

        final String worldName = player.getWorld().getName();

        if (worldName.equalsIgnoreCase("hub") || worldName.toLowerCase().endsWith("-hub"))
            return null;

        final File direct = new File(baseDir, worldName);
        if (isMapDirectory(direct))
            return worldName;

        final int dash = worldName.indexOf('-');
        if (dash > 0 && dash < worldName.length() - 1) {

            final String stripped = worldName.substring(dash + 1);
            if (stripped.equalsIgnoreCase("hub"))
                return null;

            final File guess = new File(baseDir, stripped);
            if (isMapDirectory(guess))
                return stripped;

        }

        return null;

    }

    private static MapData loadMapData(File baseDir, String mapName) {

        final File mapdataFile = new File(new File(baseDir, mapName), "mapdata.yaml");

        if (!mapdataFile.exists())
            return blankMapData(mapName);

        try {

            final MapData data = MAPPER.readValue(mapdataFile, MapData.class);
            if (data != null)
                return data;

        } catch (Exception e) {

            return blankMapData(mapName);

        }

        return blankMapData(mapName);

    }

    private static MapData blankMapData(String mapName) {

        final MapData blank = new MapData();
        blank.setName(mapName);
        blank.setBuilder("Unknown");
        blank.setDatapoints(new ArrayList<>());
        return blank;

    }

    private static List<String> getMissingSteps(MapData data) {

        final List<String> missing = new ArrayList<>();

        if (!hasDatapoint(data, DatapointType.SURVIVOR_SPAWN))
            missing.add("/hbsetspawn survivor");
        if (!hasDatapoint(data, DatapointType.HEROBRINE_SPAWN))
            missing.add("/hbsetspawn herobrine");
        if (!hasDatapoint(data, DatapointType.ALTER))
            missing.add("/hbsetspawn alter");
        if (countDatapoints(data, DatapointType.SHARD_SPAWN) < MIN_SHARD_SPAWNS)
            missing.add("/hbsetspawn shard");
        if (Double.isNaN(data.getShardMin()))
            missing.add("/hbsetspawn shardmin");
        if (Double.isNaN(data.getShardMax()))
            missing.add("/hbsetspawn shardmax");

        return missing;

    }

    private static void sendStep(Player player, String number, String label, DatapointType type, MapData data,
            String command)
    {

        if (hasDatapoint(data, type)) {

            Message.send(player, Message.format("&a✔ &f" + number + " " + label));
            return;

        }

        Message.send(player, Message.format("&c✗ &f" + number + " " + label + " &8» &e" + command));

    }

    private static boolean hasDatapoint(MapData data, DatapointType type) {

        return countDatapoints(data, type) > 0;

    }

    private static int countDatapoints(MapData data, DatapointType type) {

        if (data == null || data.getDatapoints() == null)
            return 0;

        int count = 0;
        for (Datapoint datapoint : data.getDatapoints()) {

            if (datapoint != null && datapoint.getType() == type)
                count++;

        }

        return count;

    }

    private static boolean isMapDirectory(File directory) {

        return directory.exists() && directory.isDirectory() && !"hub".equalsIgnoreCase(directory.getName());

    }

}
