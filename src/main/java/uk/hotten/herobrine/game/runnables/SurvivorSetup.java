package uk.hotten.herobrine.game.runnables;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.utils.PlayerUtil;

// Intro titles for a survivor, spaced out with scheduled tasks on the main thread.
public class SurvivorSetup extends BukkitRunnable {

    private final Player player;
    private final JavaPlugin plugin;

    public SurvivorSetup(Player player, JavaPlugin plugin) {

        this.player = player;
        this.plugin = plugin;

    }

    @Override
    public void run() {

        Message.send(player, Message.format("&aYou are a &lSurvivor&r&a."));
        Message.send(player, Message.format("&7Collect shards and return them to the alter to weaken Herobrine!"));

        String[][] titles = { { "&bWelcome to the Herobrine!", "&eYou are a &aSURVIVOR" },
                { "&bBeware &cThe Herobrine", "&eFor now he is just a smoke cloud" },
                { "&bCapture the shards", "&eand make the &cHerobrine &eweaker" },
                { "&bShards spawn randomly", "&eUse your compass to find them" },
                { "&bWeaken the &cHerobrine", "&eyou need to capture shards" } };
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
