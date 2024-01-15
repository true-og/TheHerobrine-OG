package uk.hotten.herobrine.lobby;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import lombok.Getter;
import me.tigerhix.lib.scoreboard.type.Scoreboard;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import uk.hotten.herobrine.data.RedisManager;
import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.game.runnables.MapVotingRunnable;
import uk.hotten.herobrine.lobby.data.LobbyConfig;
import uk.hotten.herobrine.stat.StatManager;
import uk.hotten.herobrine.utils.Console;
import uk.hotten.herobrine.utils.GameState;
import uk.hotten.herobrine.world.WorldManager;

import java.util.ArrayList;

public class GameLobby {

    private JavaPlugin plugin;

    @Getter private LobbyConfig lobbyConfig;
    @Getter private String lobbyId;

    @Getter private GameManager gameManager;
    @Getter private StatManager statManager;
    @Getter private WorldManager worldManager;

    private ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
    private PacketAdapter enderEyeSfxFix;

    @Getter private ArrayList<Player> players;

    public GameLobby(JavaPlugin plugin, LobbyConfig lobbyConfig, String lobbyId) {
        this.plugin = plugin;
        this.lobbyConfig = lobbyConfig;
        this.lobbyId = lobbyId;
    }

    public void initialize() {
        Console.info("Initializing lobby " + lobbyId);
        worldManager = new WorldManager(plugin, this);
        gameManager = new GameManager(plugin, this, RedisManager.getInstance(), protocolManager);
        statManager = new StatManager(plugin, this);
        players = new ArrayList<>();

        // Stops the eye of ender break SFX from the Notch's Wisdom and Totem of Healing abilities
        enderEyeSfxFix = new PacketAdapter(plugin, PacketType.Play.Server.NAMED_SOUND_EFFECT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!gameManager.getSurvivors().contains(event.getPlayer()) && gameManager.getHerobrine() != event.getPlayer())
                    return;

                Sound effectId = event.getPacket().getSoundEffects().readSafely(0);
                if (effectId == Sound.ENTITY_ENDER_EYE_DEATH) {
                    event.setCancelled(true);
                }
            }
        };

        protocolManager.addPacketListener(enderEyeSfxFix);

        // Start it here so game manager isn't null.
        new MapVotingRunnable(this).runTaskTimerAsynchronously(plugin, 0, 20);

        Console.info("Lobby " + lobbyId + " is ready.");
    }

    public void shutdown(boolean removeSelf, boolean recreate) {
        Console.info("Lobby " + lobbyId + " is shutting down...");
        HandlerList.unregisterAll(gameManager.getGmListener());
        HandlerList.unregisterAll(worldManager);
        gameManager.setGameStateSilently(GameState.DEAD);
        gameManager.updateTags(GameManager.ScoreboardUpdateAction.BEGONETHOT);
        gameManager.getScoreboards().values().forEach(Scoreboard::deactivate);
        gameManager.getScoreboards().clear();
        gameManager.voidKits();
        statManager.stopTracking();
        worldManager.clean();
        worldManager.cleanHub();
        if (removeSelf)
            LobbyManager.getInstance().removeLobby(lobbyId);
        Console.info("Lobby " + lobbyId + " has shutdown.");

        if (recreate)
            LobbyManager.getInstance().createLobby(lobbyConfig);
    }
}