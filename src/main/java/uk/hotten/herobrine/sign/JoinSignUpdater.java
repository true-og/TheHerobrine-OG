package uk.hotten.herobrine.sign;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.text.Component;
import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.lobby.LobbyManager.LobbyAggregate;
import uk.hotten.herobrine.utils.Message;

public class JoinSignUpdater extends BukkitRunnable {

    private final JoinSignManager manager;

    public JoinSignUpdater(JoinSignManager manager) {

        this.manager = manager;

    }

    @Override
    public void run() {

        LobbyManager lm = LobbyManager.getInstance();
        if (lm == null)
            return;

        for (JoinSign js : manager.all()) {

            World world = Bukkit.getWorld(js.getWorldName());
            if (world == null)
                continue;

            // Skip when chunk unloaded so we don't force-load just to redraw a sign.
            int chunkX = js.getX() >> 4;
            int chunkZ = js.getZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ))
                continue;

            Location loc = new Location(world, js.getX(), js.getY(), js.getZ());
            Block block = loc.getBlock();
            if (!(block.getState() instanceof Sign sign))
                continue;

            LobbyAggregate agg = lm.aggregateForConfig(js.getLobbyConfigId());
            renderLines(sign, js.getLobbyConfigId(), agg);
            sign.update(false, false);

        }

    }

    private void renderLines(Sign sign, String configId, LobbyAggregate agg) {

        String lobbyLabel = agg.displayLobbyId() == null ? configId : agg.displayLobbyId();

        sign.line(0, line("&4The Herobrine"));
        sign.line(1, line("&6" + truncate(lobbyLabel, 15)));

        if (agg.totalLobbies() == 0) {

            sign.line(2, line("&8(no lobbies)"));
            sign.line(3, line(""));
            return;

        }

        String statusLine;
        String detailLine;

        if (agg.joinableLobbies() > 0) {

            int liveStarting = agg.startingCount();
            String label;
            if (liveStarting > 0)
                label = "&5&lSTARTING";
            else
                label = "&2&lJOIN";

            statusLine = label;
            detailLine = "&0" + agg.playerCount() + "&8/&0"
                    + (agg.maxPlayersPerLobby() * Math.max(1, agg.totalLobbies()));

        } else if (agg.liveCount() > 0 && agg.liveCount() == agg.totalLobbies()) {

            statusLine = "&3&lLIVE";
            detailLine = agg.currentMap() == null ? "&8in progress" : "&8" + truncate(agg.currentMap(), 15);

        } else if (agg.endingCount() > 0 && agg.liveCount() == 0 && agg.startingCount() == 0
                && agg.waitingCount() == 0)
        {

            statusLine = "&8&lENDING";
            detailLine = "&8please wait";

        } else if (agg.liveCount() > 0) {

            statusLine = "&4&lFULL";
            detailLine = "&8" + agg.liveCount() + " live";

        } else {

            statusLine = "&4&lFULL";
            detailLine = "&0" + agg.playerCount() + "&8/&0"
                    + (agg.maxPlayersPerLobby() * Math.max(1, agg.totalLobbies()));

        }

        sign.line(2, line(statusLine));
        sign.line(3, line(detailLine));

    }

    private Component line(String legacy) {

        return Message.legacySerializerAnyCase(legacy);

    }

    private String truncate(String s, int max) {

        if (s == null)
            return "";
        if (s.length() <= max)
            return s;
        return s.substring(0, max);

    }

}
