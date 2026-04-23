package uk.hotten.herobrine.lobby;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.mw.MyWorlds;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.Getter;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.lobby.data.LobbyConfig;
import uk.hotten.herobrine.utils.Console;
import uk.hotten.herobrine.utils.GameState;
import uk.hotten.herobrine.utils.Message;

public class LobbyManager {

    private static final String[] MY_WORLDS_PLUGIN_NAMES = { "MyWorlds", "My_Worlds" };

    private final JavaPlugin plugin;

    @Getter
    private static LobbyManager instance;

    @Getter
    private MyWorlds myWorlds;

    private final HashMap<String, LobbyConfig> lobbyConfigs;
    private final HashMap<String, GameLobby> gameLobbies;

    public LobbyManager(JavaPlugin plugin) {

        this.plugin = plugin;
        instance = this;

        lobbyConfigs = new HashMap<>();
        gameLobbies = new HashMap<>();

        hookMyWorlds();

        try {

            checkAndLoadConfigs(true);

        } catch (Exception e) {

            Console.error("Failed to load config files.");
            e.printStackTrace();

        }

    }

    private void hookMyWorlds() {

        for (String pluginName : MY_WORLDS_PLUGIN_NAMES) {

            Plugin mwPlugin = Bukkit.getServer().getPluginManager().getPlugin(pluginName);
            if (mwPlugin == null || !mwPlugin.isEnabled())
                continue;

            if (mwPlugin instanceof MyWorlds) {

                myWorlds = (MyWorlds) mwPlugin;
                Console.info("Hooked world plugin: " + mwPlugin.getName());
                return;

            }

            Console.error("Detected '" + pluginName + "' but it was not a MyWorlds implementation.");
            myWorlds = null;
            return;

        }

        Console.error("Neither MyWorlds nor My_Worlds is installed or enabled.");
        myWorlds = null;

    }

    public void checkAndLoadConfigs(boolean autoStart) throws Exception {

        Console.info("Loading lobby configs...");
        lobbyConfigs.clear();

        if (!plugin.getDataFolder().exists()) {

            plugin.getDataFolder().mkdirs();

        }

        Path path = Path.of(plugin.getDataFolder() + File.separator + "lobbies");
        if (!Files.exists(path)) {

            Console.error("No lobbies file found, creating...");
            new File(path.toUri()).mkdirs();

        }

        Collection<File> files = FileUtils.listFiles(path.toFile(), new RegexFileFilter("\\w+\\.yaml$"),
                DirectoryFileFilter.DIRECTORY);

        if (files.isEmpty()) {

            Console.info("No lobby config files detected. Saving bundled default...");

            plugin.saveResource("lobbies/default.yaml", false);

            Console.info("Default config file written. Please change!!");

            files.add(new File(path + File.separator + "default.yaml"));

        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        files.forEach(file -> {

            try {

                Console.info("Loading lobby config " + file.getName());
                LobbyConfig lobbyConfig = mapper.readValue(file, LobbyConfig.class);

                lobbyConfigs.put(lobbyConfig.getId(), lobbyConfig);

                if (autoStart) {

                    for (int i = 0; i < lobbyConfig.getAutoStartAmount(); i++) {

                        createLobby(lobbyConfig);

                    }

                }

                Console.info("Successfully loaded configuration ID " + lobbyConfig.getId());

            } catch (IOException e) {

                Console.error("Failed to load config file " + file.getName());
                throw new RuntimeException(e);

            }

        });

    }

    public String createLobby(LobbyConfig lobbyConfig) {

        String lobbyId = generateLobbyId(lobbyConfig);
        if (lobbyId == null) {

            Console.error("Failed to generate lobby ID.");
            return null;

        }

        GameLobby gl = new GameLobby(plugin, lobbyConfig, lobbyId);
        if (!gl.initialize()) {

            Console.error("Failed to initialize lobby " + lobbyId + ".");
            return null;

        }

        gameLobbies.put(lobbyId, gl);
        return lobbyId;

    }

    private String generateLobbyId(LobbyConfig lobbyConfig) {

        for (int i = 1; i <= 100; i++) {

            if (gameLobbies.containsKey(lobbyConfig.getPrefix() + i))
                continue;

            return lobbyConfig.getPrefix() + i;

        }

        return null;

    }

    public GameLobby getLobby(String lobbyId) {

        return gameLobbies.getOrDefault(lobbyId, null);

    }

    public GameLobby getLobby(Player player) {

        for (Map.Entry<String, GameLobby> entry : gameLobbies.entrySet()) {

            if (entry.getValue().getPlayers().contains(player))
                return entry.getValue();

        }

        return null;

    }

    public List<String> getLobbyIds() {

        return gameLobbies.keySet().stream().toList();

    }

    public void removeLobby(String lobbyId) {

        gameLobbies.remove(lobbyId);

    }

    public LobbyConfig getLobbyConfig(String configId) {

        return lobbyConfigs.getOrDefault(configId, null);

    }

    public List<String> getLobbyConfigsIds() {

        return lobbyConfigs.keySet().stream().toList();

    }

    public void shutdown() {

        for (Map.Entry<String, GameLobby> entry : gameLobbies.entrySet()) {

            entry.getValue().shutdown(false, false);

        }

        gameLobbies.clear();

    }

    public int reloadConfigs() throws Exception {

        List<GameLobby> existingLobbies = new ArrayList<>(gameLobbies.values());

        checkAndLoadConfigs(false);

        int recreated = 0;
        for (GameLobby gameLobby : existingLobbies) {

            String configId = gameLobby.getLobbyConfig().getId();
            gameLobby.shutdown(true, false);

            LobbyConfig refreshedConfig = getLobbyConfig(configId);
            if (refreshedConfig == null) {

                Console.error("Skipping recreation of lobby " + gameLobby.getLobbyId() + " because config '" + configId
                        + "' no longer exists.");
                continue;

            }

            if (createLobby(refreshedConfig) != null)
                recreated++;

        }

        for (LobbyConfig lobbyConfig : lobbyConfigs.values()) {

            int currentCount = getLobbyCount(lobbyConfig.getId());
            while (currentCount < lobbyConfig.getAutoStartAmount()) {

                if (createLobby(lobbyConfig) == null)
                    break;
                currentCount++;
                recreated++;

            }

        }

        return recreated;

    }

    private int getLobbyCount(String configId) {

        int count = 0;
        for (GameLobby gameLobby : gameLobbies.values())
            if (gameLobby.getLobbyConfig().getId().equals(configId))
                count++;
        return count;

    }

    public void sendLobbyMessage(Player player) {

        Message.send(player, Message.format("&6Join a lobby with /hbjoin <id>."));
        Message.send(player, Message.format("&6Lobbies available to join: "));

        String fullOrOverfill = "&c&lFULL &r" + (player.hasPermission("theherobrine.overfill") ? "&a(JOIN)" : "");

        int sent = 0;
        for (Map.Entry<String, GameLobby> entry : gameLobbies.entrySet()) {

            GameLobby gameLobby = entry.getValue();
            GameManager gm = gameLobby.getGameManager();

            if (gm.getGameState() == GameState.LIVE) {

                StringBuilder sb = new StringBuilder("&b" + gameLobby.getLobbyId() + ": &e" + gm.getSurvivors().size()
                        + " remaining" + "&8 - &b&lLIVE &2(SPECTATE)");

                TextComponent textComponent = Message.legacySerializerAnyCase(Message.format(sb.toString()))
                        .clickEvent(ClickEvent.runCommand("/hbjoin " + gameLobby.getLobbyId()))
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Message
                                .legacySerializerAnyCase("&6Click here to spectate &b" + gameLobby.getLobbyId())));

                player.sendMessage(textComponent);
                sent++;
                continue;

            }

            if (gm.getGameState() != GameState.WAITING && gm.getGameState() != GameState.STARTING)
                continue;

            String startingStatus = (gm.getGameState() == GameState.WAITING ? "&e&lWAITING " : "&d&lSTARTING ") + "&r";

            StringBuilder sb = new StringBuilder("&b" + gameLobby.getLobbyId() + ": &e" + gm.getSurvivors().size() + "/"
                    + gm.getMaxPlayers() + "&7 - &r");

            if (gameLobby.getPlayers().size() < gm.getMaxPlayers())
                sb.append(startingStatus + "&a(JOIN)");
            else
                sb.append(fullOrOverfill);

            TextComponent textComponent = Message.legacySerializerAnyCase(Message.format(sb.toString()));
            if (sb.toString().contains("JOIN")) {

                textComponent = textComponent
                        .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Message.legacySerializerAnyCase("&6Click here to join &b" + gameLobby.getLobbyId())))
                        .clickEvent(ClickEvent.runCommand("/hbjoin " + gameLobby.getLobbyId()));

            } else {

                textComponent = textComponent.hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Message.legacySerializerAnyCase("&cThis lobby is full.")));

            }

            player.sendMessage(textComponent);
            sent++;

        }

        if (sent == 0) {

            Message.send(player, Message.format("&cThere are no lobbies available."));

        }

    }

}
