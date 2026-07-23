package uk.hotten.herobrine.sign;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.Getter;
import uk.hotten.herobrine.utils.Console;

public class JoinSignManager {

    @Getter
    private static JoinSignManager instance;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, JoinSign> signs;

    public JoinSignManager(JavaPlugin plugin) {

        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "joinsigns.yaml");
        this.signs = new HashMap<>();
        instance = this;
        if (!file.exists())
            plugin.saveResource("joinsigns.yaml", false);
        load();

    }

    public synchronized void load() {

        signs.clear();
        if (!file.exists()) {

            Console.info("No joinsigns.yaml found; starting with empty registry.");
            return;

        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("signs");
        if (section == null)
            return;

        for (String key : section.getKeys(false)) {

            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null)
                continue;
            String world = s.getString("world");
            int x = s.getInt("x");
            int y = s.getInt("y");
            int z = s.getInt("z");
            String configId = s.getString("config");
            if (world == null || configId == null) {

                Console.error("Skipping malformed join sign entry '" + key + "' in joinsigns.yaml");
                continue;

            }

            JoinSign js = new JoinSign(world, x, y, z, configId);
            signs.put(js.key(), js);

        }

        Console.info("Loaded " + signs.size() + " join sign(s).");

    }

    public synchronized void save() {

        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (JoinSign js : signs.values()) {

            String path = "signs.sign" + i;
            yaml.set(path + ".world", js.getWorldName());
            yaml.set(path + ".x", js.getX());
            yaml.set(path + ".y", js.getY());
            yaml.set(path + ".z", js.getZ());
            yaml.set(path + ".config", js.getLobbyConfigId());
            i++;

        }

        try {

            if (!plugin.getDataFolder().exists())
                plugin.getDataFolder().mkdirs();
            yaml.save(file);

        } catch (Exception e) {

            Console.error("Failed to save join signs: " + e.getMessage());
            e.printStackTrace();

        }

    }

    public synchronized JoinSign get(Location loc) {

        if (loc == null || loc.getWorld() == null)
            return null;
        return signs.get(JoinSign.keyOf(loc));

    }

    public synchronized boolean isJoinSign(Location loc) {

        return get(loc) != null;

    }

    public synchronized void register(JoinSign js) {

        signs.put(js.key(), js);
        save();

    }

    public synchronized JoinSign unregister(Location loc) {

        if (loc == null || loc.getWorld() == null)
            return null;
        JoinSign removed = signs.remove(JoinSign.keyOf(loc));
        if (removed != null)
            save();
        return removed;

    }

    public synchronized Collection<JoinSign> all() {

        return Collections.unmodifiableCollection(new ArrayList<>(signs.values()));

    }

    public synchronized List<JoinSign> forConfig(String configId) {

        List<JoinSign> out = new ArrayList<>();
        for (JoinSign js : signs.values())
            if (js.getLobbyConfigId().equals(configId))
                out.add(js);
        return out;

    }

}
