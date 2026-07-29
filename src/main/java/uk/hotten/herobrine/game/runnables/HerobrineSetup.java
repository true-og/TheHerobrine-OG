package uk.hotten.herobrine.game.runnables;

import java.util.concurrent.TimeUnit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.utils.PlayerUtil;

public class HerobrineSetup extends BukkitRunnable {

    private Player player;

    public HerobrineSetup(Player player) {

        this.player = player;

    }

    @Override
    public void run() {

        try {

            Message.send(player, Message.format("&aYou are &c&lTHE HEROBRINE! &k###&r"));
            Message.send(player, Message.format("&7Destroy all survivors to take over the WORLD!"));

            PlayerUtil.sendTitle(player, "&bWelcome to the Herobrine", "&eYou are &cTHE HEROBRINE", 500, 4000, 500);
            TimeUnit.SECONDS.sleep(4);
            PlayerUtil.sendTitle(player, "&bBeware the Survivors!", "&eThey want to take you down", 500, 4000, 500);
            TimeUnit.SECONDS.sleep(3);
            PlayerUtil.sendTitle(player, "&bThey want the shards", "&eand make you weaker", 500, 4000, 500);
            TimeUnit.SECONDS.sleep(3);
            PlayerUtil.sendTitle(player, "&bShards spawn randomly", "&eUse your compass to guard them", 500, 4000, 500);
            TimeUnit.SECONDS.sleep(3);
            PlayerUtil.sendTitle(player, "&bStop the captures to win", "&eUse your special items to help", 500, 4000,
                    500);

        } catch (Exception e) {

            e.printStackTrace();
            Message.send(player, Message.format("&cError displaying your titles!"));

        }

    }

}
