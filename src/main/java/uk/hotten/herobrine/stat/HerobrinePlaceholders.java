package uk.hotten.herobrine.stat;

import net.trueog.utilitiesog.UtilitiesOG;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.lobby.LobbyManager;

// Registers the hb_score, hb_rank and hb_class MiniPlaceholders through Utilities-OG.
public class HerobrinePlaceholders implements Listener {

    // Chat prefix the Herobrine wears in a live round.
    public static final String HEROBRINE_CLASS = "&4THE HEROBRINE";

    public static void register(JavaPlugin plugin) {

        UtilitiesOG.registerAudiencePlaceholder("hb_score",
                (Player player) -> String.valueOf(HerobrineScores.get(player.getUniqueId())));
        UtilitiesOG.registerAudiencePlaceholder("hb_rank",
                (Player player) -> HerobrineScores.rankOf(player.getUniqueId()).getDisplay());
        UtilitiesOG.registerAudiencePlaceholder("hb_class", HerobrinePlaceholders::herobrineClass);

        plugin.getServer().getPluginManager().registerEvents(new HerobrinePlaceholders(), plugin);

    }

    // Empty unless the player is in a lobby whose round has picked them as the
    // Herobrine.
    public static String herobrineClass(Player player) {

        LobbyManager lobbyManager = LobbyManager.getInstance();
        if (lobbyManager == null)
            return "";

        GameLobby lobby = lobbyManager.getLobby(player);
        if (lobby == null)
            return "";

        GameManager gameManager = lobby.getGameManager();
        if (gameManager == null || !gameManager.isHerobrine(player))
            return "";

        return HEROBRINE_CLASS;

    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        HerobrineScores.load(event.getPlayer().getUniqueId());

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        HerobrineScores.forget(event.getPlayer().getUniqueId());

    }

}
