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
import uk.hotten.herobrine.stat.StatManager;
import uk.hotten.herobrine.utils.GameState;
import uk.hotten.herobrine.utils.Message;

// One player's lobby sidebar plus the Bukkit board that carries nametag teams.
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

        this.fallback = ScoreboardLib.createScoreboard(player).setHandler(statsHandler()).setUpdateInterval(20);
        this.fallback.activate();
        this.teamBoard = fallback.getHolder().getScoreboard();

    }

    public org.bukkit.scoreboard.Scoreboard getTeamBoard() {

        return teamBoard;

    }

    // Switches from the lobby stats card to the live round card.
    public void showGame() {

        gameMode = true;
        if (fallback != null)
            fallback.setHandler(gameHandler());

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

        return Message.legacySerializerAnyCase(inRound() ? "&cThe&lHerobrine!" : "&e&lYour Stats");

    }

    private List<Component> lines(Player viewer) {

        List<Component> out = new ArrayList<>();
        for (String line : inRound() ? gameLines() : statsLines(viewer.getUniqueId()))
            out.add(Message.legacySerializerAnyCase(line));
        return out;

    }

    private List<String> statsLines(UUID uuid) {

        StatManager stats = gm.getGameLobby().getStatManager();
        return List.of("&bPoints: &r" + stats.getPoints().getOrDefault(uuid, 0),
                "&bCaptures: &r" + stats.getCaptures().getOrDefault(uuid, 0),
                "&bKills: &r" + stats.getKills().getOrDefault(uuid, 0),
                "&bDeaths: &r" + stats.getDeaths().getOrDefault(uuid, 0));

    }

    private List<String> gameLines() {

        return List.of("", "&a✦ Shard Count", gm.getShardCount() + "/3", "", "&a❂ Survivors",
                String.valueOf(gm.getSurvivors().size()), "", "&8--------------", "&b" + gm.getNetworkWeb());

    }

    private ScoreboardHandler statsHandler() {

        return new ScoreboardHandler() {

            @Override
            public String getTitle(Player viewer) {

                return "&e&lYour Stats";

            }

            @Override
            public List<Entry> getEntries(Player viewer) {

                EntryBuilder builder = new EntryBuilder();
                for (String line : statsLines(viewer.getUniqueId()))
                    builder.next(line);
                return builder.build();

            }

        };

    }

    private ScoreboardHandler gameHandler() {

        return new ScoreboardHandler() {

            @Override
            public String getTitle(Player viewer) {

                return "&cThe&lHerobrine!";

            }

            @Override
            public List<Entry> getEntries(Player viewer) {

                EntryBuilder builder = new EntryBuilder();
                for (String line : gameLines()) {

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
