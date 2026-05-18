package uk.hotten.herobrine.sign;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class JoinSign {

    @Getter
    private final String worldName;

    @Getter
    private final int x;

    @Getter
    private final int y;

    @Getter
    private final int z;

    @Getter
    private final String lobbyConfigId;

    public JoinSign(String worldName, int x, int y, int z, String lobbyConfigId) {

        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.lobbyConfigId = lobbyConfigId;

    }

    public String key() {

        return worldName + ":" + x + ":" + y + ":" + z;

    }

    public static String keyOf(Location loc) {

        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();

    }

    public World resolveWorld() {

        return Bukkit.getWorld(worldName);

    }

    public Location toLocation() {

        World w = resolveWorld();
        if (w == null)
            return null;
        return new Location(w, x, y, z);

    }

}
