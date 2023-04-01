package uk.hotten.herobrine;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import uk.hotten.herobrine.commands.ForceStartCommand;
import uk.hotten.herobrine.commands.SetHerobrineCommand;
import uk.hotten.herobrine.data.SqlManager;
import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.data.RedisManager;
import uk.hotten.herobrine.stat.StatManager;
import uk.hotten.herobrine.utils.Console;
import uk.hotten.herobrine.world.WorldManager;
import org.bukkit.plugin.java.JavaPlugin;

public class HerobrinePluginOG extends JavaPlugin {

    @Override
    public void onEnable() {
        Console.info("Setting up The Herobrine!");

        this.saveDefaultConfig();

        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        SqlManager sqlManager = new SqlManager(this);
        RedisManager redisManager = new RedisManager(this);

        WorldManager worldManager = new WorldManager(this);
        GameManager gameManager = new GameManager(this, worldManager, redisManager, protocolManager);
        StatManager statManager = new StatManager(this, gameManager);

        getCommand("setherobrine").setExecutor(new SetHerobrineCommand());
        getCommand("forcestart").setExecutor(new ForceStartCommand());

        Console.info("The Herobrine! is ready.");
    }

    @Override
    public void onDisable() {
        WorldManager.getInstance().clean();
    }
}
