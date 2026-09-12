package uk.hotten.herobrine.game.runnables;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.utils.PlayerUtil;
import uk.hotten.herobrine.world.WorldManager;

// Runs on the main thread; the delayed second half is scheduled rather than slept.
public class CaptureSequence extends BukkitRunnable {

    private Player player;
    private GameManager gm;
    private WorldManager wm;

    public CaptureSequence(Player player, GameManager gm, WorldManager wm) {

        this.player = player;
        this.gm = gm;
        this.wm = wm;

    }

    @Override
    public void run() {

        Location l = wm.alter;

        PlayerUtil.broadcastTitle(gm.getGameLobby(), "&b&lShard Captured", "&eby &l" + player.getName(), 500, 3000,
                1000);
        if (gm.getShardCount() == 3)
            Message.broadcast(gm.getGameLobby(), Message.format("&c&lHerobrine &eis now &aVisible!"));
        else
            Message.broadcast(gm.getGameLobby(), Message.format("&b" + gm.getShardCount() + "&7/3 Shards Captured!"));
        PlayerUtil.playSoundAt(l, Sound.BLOCK_PORTAL_TRAVEL, 1f, 1f);
        PlayerUtil.playSoundAt(l, Sound.ENTITY_WITHER_DEATH, 0.5f, 1f);
        PlayerUtil.broadcastSound(gm.getGameLobby(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0f);

        Bukkit.getScheduler().runTaskLater(gm.getPlugin(), () -> {

            if (l == null || l.getWorld() == null)
                return;

            PlayerUtil.playSoundAt(l, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            l.getWorld().strikeLightningEffect(l);

        }, 80L);

    }

}
