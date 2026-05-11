package uk.hotten.herobrine.world;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.world.data.Datapoint;
import uk.hotten.herobrine.world.data.DatapointType;
import uk.hotten.herobrine.world.data.MapData;

public class MapSetupWizard implements Listener {

    public static final String SETSPAWN_PERMISSION = "theherobrine.command.setspawn";

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    static {

        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    }

    private final JavaPlugin plugin;

    public MapSetupWizard(JavaPlugin plugin) {

        this.plugin = plugin;

    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {

        final Player player = event.getPlayer();

        if (!player.hasPermission(SETSPAWN_PERMISSION))
            return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> sendForCurrentWorld(plugin, player), 1L);

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

        Message.send(player, Message.format("&6Map setup wizard: &b" + mapName));
        Message.send(player, Message.format("&7Walk to each location and run the command shown."));
        sendStep(player, "1", "survivor spawn", DatapointType.SURVIVOR_SPAWN, data, "/hbsetspawn survivor",
                "where survivors start");
        sendStep(player, "2", "herobrine spawn", DatapointType.HEROBRINE_SPAWN, data, "/hbsetspawn herobrine",
                "where Herobrine starts");
        sendStep(player, "3", "alter", DatapointType.ALTER, data, "/hbsetspawn alter",
                "the enchanting-table capture point");

        if (shardCount > 0) {

            Message.send(player, Message.format("&a[done] &f4. shard spawns &7(" + shardCount
                    + " set; run &e/hbsetspawn shard&7 again to add more)"));

        } else {

            Message.send(player, Message.format("&c[missing] &f4. shard spawn &7- run &e/hbsetspawn shard"
                    + " &7at every shard spawn location; at least one is required"));

        }

        if (missing.isEmpty()) {

            Message.send(player, Message.format("&aSetup complete. Run &e/hbreloadconfigs&a, then start a test game."));
            return;

        }

        Message.send(player, Message.format("&eNext step: &f" + missing.get(0)));

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
            return new MapData(mapName, "Unknown", -100, 1000, new ArrayList<>());

        try {

            final MapData data = MAPPER.readValue(mapdataFile, MapData.class);
            if (data != null)
                return data;

        } catch (Exception e) {

            return new MapData(mapName, "Unknown", -100, 1000, new ArrayList<>());

        }

        return new MapData(mapName, "Unknown", -100, 1000, new ArrayList<>());

    }

    private static List<String> getMissingSteps(MapData data) {

        final List<String> missing = new ArrayList<>();

        if (!hasDatapoint(data, DatapointType.SURVIVOR_SPAWN))
            missing.add("stand at the survivor start and run /hbsetspawn survivor");
        if (!hasDatapoint(data, DatapointType.HEROBRINE_SPAWN))
            missing.add("stand at the Herobrine start and run /hbsetspawn herobrine");
        if (!hasDatapoint(data, DatapointType.ALTER))
            missing.add("stand at the shard turn-in alter and run /hbsetspawn alter");
        if (!hasDatapoint(data, DatapointType.SHARD_SPAWN))
            missing.add("stand at a shard spawn and run /hbsetspawn shard");

        return missing;

    }

    private static void sendStep(Player player, String number, String label, DatapointType type, MapData data,
            String command, String description)
    {

        if (hasDatapoint(data, type)) {

            Message.send(player, Message.format("&a[done] &f" + number + ". " + label + " &7- " + description));
            return;

        }

        Message.send(player, Message.format(
                "&c[missing] &f" + number + ". " + label + " &7- run &e" + command + " &7(" + description + ")"));

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
