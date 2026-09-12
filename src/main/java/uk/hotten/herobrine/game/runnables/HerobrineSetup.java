package uk.hotten.herobrine.game.runnables;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.utils.PlayerUtil;

// Intro titles for the Herobrine, spaced out with scheduled tasks on the main thread.
public class HerobrineSetup extends BukkitRunnable {

    private final Player player;
    private final JavaPlugin plugin;

    public HerobrineSetup(Player player, JavaPlugin plugin) {

        this.player = player;
        this.plugin = plugin;

    }

    @Override
    public void run() {

        Message.send(player, Message.format("&aYou are &c&lTHE HEROBRINE! &k###&r"));
        Message.send(player, Message.format("&7Destroy all survivors to take over the WORLD!"));

        String[][] titles = { { "&bWelcome to the Herobrine", "&eYou are &cTHE HEROBRINE" },
                { "&bBeware the Survivors!", "&eThey want to take you down" },
                { "&bThey want the shards", "&eand make you weaker" },
                { "&bShards spawn randomly", "&eUse your compass to guard them" },
                { "&bStop the captures to win", "&eUse your special items to help" } };
        long[] delays = { 0L, 80L, 140L, 200L, 260L };

        for (int i = 0; i < titles.length; i++) {

            final String[] title = titles[i];
            Bukkit.getScheduler().runTaskLater(plugin, () -> {

                if (player.isOnline())
                    PlayerUtil.sendTitle(player, title[0], title[1], 500, 4000, 500);

            }, delays[i]);

        }

    }

}
