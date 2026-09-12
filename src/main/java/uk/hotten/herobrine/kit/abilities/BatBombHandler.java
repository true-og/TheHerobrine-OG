package uk.hotten.herobrine.kit.abilities;

import java.util.ArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitRunnable;
import uk.hotten.herobrine.game.GameManager;

// Scheduled a second after the throw, on the main thread: the coal becomes a
// swarm of bats, and two seconds later the bats explode.
public class BatBombHandler extends BukkitRunnable {

    private Item coal;
    private ArrayList<Entity> bats;
    private GameManager gm;

    public BatBombHandler(Item coal, GameManager gm) {

        this.coal = coal;
        bats = new ArrayList<>();
        this.gm = gm;

    }

    @Override
    public void run() {

        if (!coal.isValid())
            return;

        Location loc = coal.getLocation();
        coal.remove();
        for (int i = 0; i < 15; i++) {

            bats.add(loc.getWorld().spawnEntity(loc, EntityType.BAT));

        }

        Bukkit.getScheduler().runTaskLater(gm.getPlugin(), () -> {

            for (Entity bat : bats) {

                if (!bat.isValid())
                    continue;
                Location batLoc = bat.getLocation();
                bat.remove();
                batLoc.getWorld().createExplosion(batLoc, 3f, false, false);

            }

        }, 40L);

    }

}
