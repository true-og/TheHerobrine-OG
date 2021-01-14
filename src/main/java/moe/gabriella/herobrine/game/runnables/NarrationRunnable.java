package moe.gabriella.herobrine.game.runnables;

import moe.gabriella.herobrine.game.GameManager;
import moe.gabriella.herobrine.utils.GameState;
import moe.gabriella.herobrine.utils.PlayerUtil;
import moe.gabriella.herobrine.utils.ShardState;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class NarrationRunnable extends BukkitRunnable {

    public static double timer = 0;
    GameManager gm = GameManager.getInstance();

    @Override
    public void run() {
        if (gm.getGameState() != GameState.LIVE) {
            cancel();
            return;
        }

        if (gm.getShardCount() == 0 && gm.getShardState() == ShardState.WAITING) // I dunno i think it looks better without this at the start, and i think this was a bug on the hive too
            return;

        if (timer > 10)
            timer = 0;

        switch (gm.getShardState()) {
            case WAITING: {
                if (firstMsg())
                    all(ChatColor.AQUA + "Shard " + (gm.getShardCount() + 1) + " shall be summoned soon!");
                else
                    separate(ChatColor.RED + "BEWARE: The Herobrine is still a cloud of smoke!", ChatColor.AQUA + "Try eliminate all the Survivors");
                break;
            }
            case SPAWNED: {
                all(ChatColor.AQUA + "Use your compass to find the shard!");
                break;
            }
            case CARRYING: {
                if (firstMsg())
                    all("" + ChatColor.GREEN + ChatColor.BOLD + gm.getShardCarrier().getName() + ChatColor.DARK_AQUA + " has the shard!");
                else
                    separate(ChatColor.GREEN + "Protect your shard carrier", ChatColor.AQUA + "Kill the shard carrier first!");
                break;
            }
            case INACTIVE: {
                if (gm.getShardCount() != 3)
                    return;

                separate(ChatColor.RED + "Kill the Herobrine!", ChatColor.AQUA + "You are now visible!");
                break;
            }
        }
        timer += 0.5;
    }

    private boolean firstMsg() {
        return timer > 5;
    }

    private void all(String msg) {
        PlayerUtil.broadcastActionbar(msg);
    }

    private void separate(String survivors, String herobrine) {
        for (Player p : gm.getSurvivors())
            PlayerUtil.sendActionbar(p, survivors);
        PlayerUtil.sendActionbar(gm.getHerobrine(), herobrine);
    }
}
