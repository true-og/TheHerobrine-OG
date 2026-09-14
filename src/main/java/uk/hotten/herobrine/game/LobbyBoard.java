package uk.hotten.herobrine.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.ScoreboardManager;

import me.tigerhix.lib.scoreboard.ScoreboardLib;
import me.tigerhix.lib.scoreboard.common.EntryBuilder;
import me.tigerhix.lib.scoreboard.type.Entry;
import me.tigerhix.lib.scoreboard.type.Scoreboard;
import me.tigerhix.lib.scoreboard.type.ScoreboardHandler;
import net.kyori.adventure.text.Component;
import uk.hotten.herobrine.compat.ScoreboardOGBridge;
import uk.hotten.herobrine.game.runnables.ShardHandler;
import uk.hotten.herobrine.stat.StatManager;
import uk.hotten.herobrine.utils.GameState;
import uk.hotten.herobrine.utils.Message;
import uk.hotten.herobrine.utils.ShardState;
import uk.hotten.herobrine.world.data.MapData;

// One player's sidebar plus the Bukkit board that carries nametag teams.
// Scoreboard-OG renders the sidebar when present; else ScoreboardLib draws it.
public class LobbyBoard {

    private final GameManager gm;
    private final Player player;
    private final org.bukkit.scoreboard.Scoreboard teamBoard;
    private final Scoreboard fallback;
    private boolean gameMode;

    public LobbyBoard(GameManager gm, Player player) {

        this.gm = gm;
        this.player = player;

        if (ScoreboardOGBridge.isAvailable()) {

            ScoreboardManager manager = Bukkit.getScoreboardManager();
            this.teamBoard = manager.getNewScoreboard();
            player.setScoreboard(teamBoard);
            this.fallback = null;
            ScoreboardOGBridge.claim(player, this::title, this::lines);
            return;

        }

        this.fallback = ScoreboardLib.createScoreboard(player).setHandler(handler()).setUpdateInterval(20);
        this.fallback.activate();
        this.teamBoard = fallback.getHolder().getScoreboard();

    }

    public org.bukkit.scoreboard.Scoreboard getTeamBoard() {

        return teamBoard;

    }

    // Switches from the lobby card to the live round card.
    public void showGame() {

        gameMode = true;

    }

    public void close() {

        if (fallback != null) {

            fallback.deactivate();
            return;

        }

        ScoreboardOGBridge.release(player);
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (player.isOnline() && manager != null && player.getScoreboard() == teamBoard)
            player.setScoreboard(manager.getMainScoreboard());

    }

    private boolean inRound() {

        GameState state = gm.getGameState();
        return gameMode || state == GameState.LIVE || state == GameState.ENDING;

    }

    private Component title(Player viewer) {

        return Message.legacySerializerAnyCase(TrueOGBoard.TITLE);

    }

    private List<Component> lines(Player viewer) {

        List<Component> out = new ArrayList<>();
        for (String line : rawLines(viewer))
            out.add(Message.legacySerializerAnyCase(line));
        return out;

    }

    // Top to bottom in the network board's layout: a blank, labelled blocks split
    // by blanks, then the site footer.
    private List<String> rawLines(Player viewer) {

        List<String> lines = new ArrayList<>();
        lines.add("");
        if (inRound())
            addGameLines(lines, viewer);
        else
            addLobbyLines(lines, viewer.getUniqueId());
        lines.add("");
        lines.add(footer());
        return lines;

    }

    // Queue card: who is here, when the round starts, and the viewer's record.
    private void addLobbyLines(List<String> lines, UUID uuid) {

        lines.add("&6Players:");
        lines.add("&f" + gm.getGameLobby().getPlayers().size() + "&7/&f" + gm.getMaxPlayers());
        lines.add("");
        lines.add("&6Starting in:");
        if (gm.getGameState() == GameState.STARTING) {

            lines.add("&f" + TrueOGBoard.clock(gm.startTimer));

        } else {

            int needed = Math.max(0, gm.getRequiredToStart() - gm.getSurvivors().size());
            lines.add(needed == 0 ? "&aReady" : "&7Need " + needed + " more");

        }

        lines.add("");
        StatManager stats = gm.getGameLobby().getStatManager();
        lines.add("&bPoints: &f" + TrueOGBoard.compact(stats.getPoints().getOrDefault(uuid, 0)));
        lines.add("&bShards: &f" + TrueOGBoard.compact(stats.getCaptures().getOrDefault(uuid, 0)));
        lines.add("&2Kills: &f" + TrueOGBoard.compact(stats.getKills().getOrDefault(uuid, 0)));
        lines.add("&4Deaths: &f" + TrueOGBoard.compact(stats.getDeaths().getOrDefault(uuid, 0)));

    }

    // Round card: the viewer's role, the map, the shard hunt and who is left.
    private void addGameLines(List<String> lines, Player viewer) {

        lines.add("&6Role:");
        lines.add(role(viewer));
        lines.add("");
        lines.add("&eMap:");
        MapData map = gm.getGameLobby().getWorldManager().getGameMapData();
        lines.add("&f" + TrueOGBoard.fit(map == null ? "???" : map.getName(), TrueOGBoard.VALUE_WIDTH));
        lines.add("");
        lines.add("&aShards: &f" + gm.getShardCount() + "/3");
        lines.add("&bShard:");
        lines.add(shardStatus());
        lines.add("");
        lines.add("&2Alive: &f" + gm.getSurvivors().size());

    }

    private String role(Player viewer) {

        if (gm.isHerobrine(viewer))
            return "&cHerobrine";
        if (gm.isSurvivor(viewer) && !gm.isDeadSurvivor(viewer) && !gm.isSpectator(viewer))
            return "&2Survivor";
        return "&7Spectator";

    }

    private String shardStatus() {

        if (gm.getGameState() == GameState.ENDING)
            return "&7Game over";

        ShardHandler handler = gm.getShardHandler();
        ShardState state = gm.getShardState();
        if (state == null)
            return "&7None";

        switch (state) {

            case WAITING:
                return handler == null ? "&7Waiting" : "&7Next in " + handler.getTimer() + "s";
            case SPAWNED:
                return handler == null ? "&eSpawned" : "&eSpawned " + TrueOGBoard.clock(handler.getDespawnTimer());
            case CARRYING:
                Player carrier = gm.getShardCarrier();
                return "&a" + TrueOGBoard.fit(carrier == null ? "Carried" : carrier.getName(), TrueOGBoard.VALUE_WIDTH);
            case INACTIVE:
                return gm.getShardCount() >= 3 ? "&aAll captured" : "&7None";
            default:
                return "&7None";

        }

    }

    private String footer() {

        String web = gm.getNetworkWeb();
        return web == null || web.isEmpty() ? TrueOGBoard.FOOTER : "&e" + TrueOGBoard.fit(web, TrueOGBoard.VALUE_WIDTH);

    }

    private ScoreboardHandler handler() {

        return new ScoreboardHandler() {

            @Override
            public String getTitle(Player viewer) {

                return TrueOGBoard.TITLE;

            }

            @Override
            public List<Entry> getEntries(Player viewer) {

                EntryBuilder builder = new EntryBuilder();
                for (String line : rawLines(viewer)) {

                    if (line.isEmpty())
                        builder.blank();
                    else
                        builder.next(line);

                }

                return builder.build();

            }

        };

    }

}
