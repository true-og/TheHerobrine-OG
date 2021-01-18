package moe.gabriella.herobrine.kit.abilities;

import me.gabriella.gabsgui.GUIItem;
import moe.gabriella.herobrine.game.GameManager;
import moe.gabriella.herobrine.kit.KitAbility;
import moe.gabriella.herobrine.utils.Console;
import moe.gabriella.herobrine.utils.GameState;
import moe.gabriella.herobrine.utils.PlayerUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class BlindingAbility extends KitAbility {

    int slot;
    int amount;
    Player player;

    public BlindingAbility(GameManager gm, int slot, int amount) {
        super(gm, "Charge of Blinding");
        this.slot = slot;
        this.amount = amount;
    }

    @Override
    public void apply(Player player) {
        this.player = player;
        GUIItem charge = new GUIItem(Material.GOLD_NUGGET).displayName(ChatColor.GOLD + "Charge of " + ChatColor.BOLD + "Blinding!").amount(amount);
        if (slot == -1)
            player.getInventory().addItem(charge.build());
        else
            player.getInventory().setItem(slot, charge.build());
    }

    @EventHandler
    public void use(PlayerInteractEvent event) {
        if (gm.getGameState() != GameState.LIVE)
            return;

        Player player = event.getPlayer();
        Location l = player.getLocation();

        if (this.player != player)
            return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            if (player.getInventory().getItemInMainHand().getType() == Material.GOLD_NUGGET) {
                Item nugget = player.getWorld().dropItem(l, new ItemStack(Material.GOLD_NUGGET));
                nugget.setVelocity(l.getDirection().normalize().multiply(2f));
                new BlindingHandler(nugget).runTaskAsynchronously(gm.getPlugin());
                PlayerUtil.removeAmountOfItem(player, player.getInventory().getItemInMainHand(), 1);
            }
        }
    }
}
