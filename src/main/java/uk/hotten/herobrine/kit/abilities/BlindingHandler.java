package uk.hotten.herobrine.kit.abilities;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.utils.PlayerUtil;

// Scheduled two seconds after the throw, on the main thread.
public class BlindingHandler extends BukkitRunnable {

    private Item nugget;
    private GameManager gm;

    public BlindingHandler(Item nugget, GameManager gm) {

        this.nugget = nugget;
        this.gm = gm;

    }

    @Override
    public void run() {

        if (!nugget.isValid())
            return;

        Location loc = nugget.getLocation();
        nugget.remove();
        loc.getWorld().createExplosion(loc, 0f, false, false);
        for (Player p : gm.getSurvivors()) {

            if (PlayerUtil.getDistance(p, loc) <= 6) {

                PlayerUtil.addEffect(p, PotionEffectType.BLINDNESS, 100, 1, false, false);

            }

        }

    }

}
