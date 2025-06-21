package uk.hotten.herobrine.game.runnables;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.utils.GameState;

public class HerobrineSmokeRunnable extends BukkitRunnable {

    GameManager gm = GameManager.get();

    @Override
    public void run() {
        if (gm.getGameState() != GameState.LIVE) {
            cancel();
            return;
        }

        Location loc = gm.getHerobrine().getLocation().clone().add(0, 2, 0);
        loc.getWorld().playEffect(loc, Effect.SMOKE, 0);
    }
}
