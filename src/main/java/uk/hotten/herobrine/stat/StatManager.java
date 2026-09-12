package uk.hotten.herobrine.stat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import uk.hotten.herobrine.data.SqlManager;
import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.stat.trackers.CaptureTracker;
import uk.hotten.herobrine.stat.trackers.DeathTracker;
import uk.hotten.herobrine.stat.trackers.KillsTracker;
import uk.hotten.herobrine.stat.trackers.PointsTracker;
import uk.hotten.herobrine.utils.Console;

// Every database round trip runs off the main thread on one connection per
// operation. Callers see defaults until a read lands, then the real values.
public class StatManager {

    @Getter
    private JavaPlugin plugin;

    @Getter
    private GameLobby gameLobby;

    private GameManager gm;

    @Getter
    private StatTracker pointsTracker;

    @Getter
    private Map<UUID, Integer> points;

    @Getter
    private Map<UUID, Integer> captures;

    @Getter
    private Map<UUID, Integer> kills;

    @Getter
    private Map<UUID, Integer> deaths;

    @Getter
    Map<UUID, GameRank> gameRanks;

    private volatile String highestPlayerUUID;

    public StatManager(JavaPlugin plugin, GameLobby gameLobby) {

        Console.info(gameLobby, "Loading Stat Manager...");
        this.plugin = plugin;
        this.gameLobby = gameLobby;
        this.gm = gameLobby.getGameManager();

        gm.setStatTrackers(
                new StatTracker[]
                { new PointsTracker(this), new CaptureTracker(this, gameLobby), new KillsTracker(this, gameLobby),
                        new DeathTracker(this, gameLobby) });

        for (StatTracker tracker : gm.getStatTrackers()) {

            if (tracker.getInternalName().equals("points")) {

                pointsTracker = tracker;
                break;

            }

        }

        points = new java.util.concurrent.ConcurrentHashMap<>();
        captures = new java.util.concurrent.ConcurrentHashMap<>();
        kills = new java.util.concurrent.ConcurrentHashMap<>();
        deaths = new java.util.concurrent.ConcurrentHashMap<>();
        gameRanks = new java.util.concurrent.ConcurrentHashMap<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            highestPlayerUUID = queryHighestPlayer();
            if (highestPlayerUUID == null)
                Console.error(gameLobby, "Failed to get UUID of highest player.");
            else
                Console.debug(gameLobby, "UUID of highest player is " + highestPlayerUUID);

        });

        Console.info(gameLobby, "Stat Manager is ready!");

    }

    public void startTracking() {

        for (StatTracker tracker : gm.getStatTrackers()) {

            tracker.start();

        }

    }

    public void stopTracking() {

        for (StatTracker tracker : gm.getStatTrackers()) {

            tracker.stop();

        }

    }

    // Snapshots the round's counters on the main thread, then writes them out on
    // one connection off it.
    public void push() {

        Console.info(gameLobby, "Pushing stats...");

        final Map<StatTracker, Map<UUID, Integer>> snapshot = new HashMap<>();
        for (StatTracker tracker : gm.getStatTrackers()) {

            snapshot.put(tracker, new HashMap<>(tracker.stat));
            tracker.reset();

        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            try (Connection connection = SqlManager.get().createConnection()) {

                for (Map.Entry<StatTracker, Map<UUID, Integer>> trackerEntry : snapshot.entrySet()) {

                    StatTracker tracker = trackerEntry.getKey();
                    for (Map.Entry<UUID, Integer> entry : trackerEntry.getValue().entrySet()) {

                        UUID uuid = entry.getKey();
                        int stat = entry.getValue();

                        int curr = getCurrentStat(connection, uuid, tracker.getInternalName());
                        if (curr == -1) {

                            Console.error(gameLobby, "Error pushing stat, previous was -1 for " + uuid + "!");
                            continue;

                        }

                        setStat(connection, uuid, tracker.getInternalName(), curr + stat);
                        if (tracker == pointsTracker)
                            HerobrineScores.set(uuid, curr + stat);

                    }

                }

            } catch (Exception e) {

                Console.error(gameLobby, "Failed to push stats: " + e.getMessage());
                e.printStackTrace();

            }

            HerobrineScores.refreshTopPlayer();
            Console.info(gameLobby, "Stats pushed!");

        });

    }

    private String queryHighestPlayer() {

        try (Connection connection = SqlManager.get().createConnection()) {

            PreparedStatement statement = connection
                    .prepareStatement("SELECT UUID FROM hb_stat ORDER BY points DESC LIMIT 1;");
            ResultSet rs = statement.executeQuery();

            return rs.next() ? rs.getString("uuid") : null;

        } catch (Exception e) {

            e.printStackTrace();
            return null;

        }

    }

    private void setStat(Connection connection, UUID uuid, String name, int value) throws Exception {

        PreparedStatement statement = connection.prepareStatement("UPDATE hb_stat SET " + name + "=? WHERE `uuid`=?");
        statement.setInt(1, value);
        statement.setString(2, uuid.toString());
        statement.executeUpdate();

    }

    private int getCurrentStat(Connection connection, UUID uuid, String stat) throws Exception {

        PreparedStatement statement = connection.prepareStatement("SELECT " + stat + " FROM hb_stat WHERE uuid=?");
        statement.setString(1, uuid.toString());
        ResultSet rs = statement.executeQuery();

        return rs.next() ? rs.getInt(stat) : -1;

    }

    private boolean exists(Connection connection, UUID uuid) throws Exception {

        PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM hb_stat WHERE uuid=?");
        statement.setString(1, uuid.toString());
        return statement.executeQuery().next();

    }

    private void create(Connection connection, UUID uuid) throws Exception {

        PreparedStatement statement = connection.prepareStatement("INSERT INTO hb_stat (uuid) VALUE (?)");
        statement.setString(1, uuid.toString());
        statement.executeUpdate();

    }

    // Seeds zeroes so chat and scoreboards render at once, then loads the real
    // totals off the main thread.
    public void check(UUID uuid) {

        points.putIfAbsent(uuid, 0);
        captures.putIfAbsent(uuid, 0);
        kills.putIfAbsent(uuid, 0);
        deaths.putIfAbsent(uuid, 0);
        gameRanks.putIfAbsent(uuid, GameRank.findRank(HerobrineScores.get(uuid)));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            try (Connection connection = SqlManager.get().createConnection()) {

                if (!exists(connection, uuid))
                    create(connection, uuid);

                int loadedPoints = Math.max(0, getCurrentStat(connection, uuid, "points"));
                points.put(uuid, loadedPoints);
                captures.put(uuid, Math.max(0, getCurrentStat(connection, uuid, "captures")));
                kills.put(uuid, Math.max(0, getCurrentStat(connection, uuid, "kills")));
                deaths.put(uuid, Math.max(0, getCurrentStat(connection, uuid, "deaths")));
                HerobrineScores.set(uuid, loadedPoints);

                if (uuid.toString().equals(highestPlayerUUID) && loadedPoints >= GameRank.topPlayerGate())
                    gameRanks.put(uuid, GameRank.DEATHBRINGER);
                else
                    gameRanks.put(uuid, GameRank.findRank(loadedPoints));

            } catch (Exception e) {

                Console.error(gameLobby, "Failed to load stats for " + uuid + ": " + e.getMessage());
                e.printStackTrace();

            }

        });

    }

    public GameRank getGameRank(UUID uuid) {

        return gameRanks.getOrDefault(uuid, GameRank.SPIRIT);

    }

}
