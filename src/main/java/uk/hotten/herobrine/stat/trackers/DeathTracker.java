package uk.hotten.herobrine.stat.trackers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.stat.StatManager;
import uk.hotten.herobrine.stat.StatTracker;
import uk.hotten.herobrine.utils.GameState;

public class DeathTracker extends StatTracker {

    private GameLobby gameLobby;

    public DeathTracker(StatManager sm, GameLobby gameLobby) {

        super(sm, "Deaths", "deaths", "How many times you died!");
        this.gameLobby = gameLobby;

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void death(PlayerDeathEvent event) {

        if (!gameLobby.ownsWorld(event.getEntity().getWorld()))
            return;

        Player player = event.getEntity();

        GameManager gm = gameLobby.getGameManager();
        if (gm.getGameState() != GameState.LIVE)
            return;

        if (gm.isSurvivor(player) || gm.isHerobrine(player))
            increment(player.getUniqueId(), 1);

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {

        if (!gameLobby.ownsWorld(event.getPlayer().getWorld()))
            return;

        Player player = event.getPlayer();

        GameManager gm = gameLobby.getGameManager();
        if (gm.getGameState() != GameState.LIVE)
            return;

        if (gm.isSurvivor(player) || gm.isHerobrine(player))
            increment(player.getUniqueId(), 1);

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeaveWorld(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();

        GameManager gm = gameLobby.getGameManager();
        if (gm.getGameState() != GameState.LIVE)
            return;

        boolean leftLobby = !gameLobby.ownsWorld(player.getWorld());
        boolean leftGameWorld = gameLobby.getWorldManager().getGameWorld() != null
                && event.getFrom().equals(gameLobby.getWorldManager().getGameWorld());
        if (!leftLobby && !leftGameWorld)
            return;

        if (gm.isSurvivor(player) || gm.isHerobrine(player))
            increment(player.getUniqueId(), 1);

    }

}
