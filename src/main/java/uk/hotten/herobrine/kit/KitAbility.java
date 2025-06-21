package uk.hotten.herobrine.kit;

import java.util.ArrayList;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import uk.hotten.herobrine.game.GameManager;

public abstract class KitAbility implements Listener {

    public GameManager gm;

    @Getter
    private String displayName;

    private ArrayList<Player> cooldown;

    public KitAbility(GameManager gm, String displayName) {
        this.gm = gm;
        this.displayName = displayName;
        cooldown = new ArrayList<>();
    }

    /**
     * @implNote Runs when the game starts, called from (apply in Kit)
     * @param player
     */
    public void apply(Player player) {}

    public void startCooldown(Player player) {
        cooldown.add(player);
        Bukkit.getServer().getScheduler().runTaskLater(gm.getPlugin(), () -> cooldown.remove(player), 60);
    }

    public boolean isOnCooldown(Player player) {
        return cooldown.contains(player);
    }
}
