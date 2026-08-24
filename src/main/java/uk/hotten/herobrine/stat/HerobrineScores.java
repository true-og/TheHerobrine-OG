package uk.hotten.herobrine.stat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import uk.hotten.herobrine.data.SqlManager;
import uk.hotten.herobrine.utils.Console;

// Plugin-wide points cache behind the hb_score and hb_rank placeholders.
// Lobbies write through here, so a value is current the moment it changes.
public class HerobrineScores {

    private static final Map<UUID, Integer> scores = new ConcurrentHashMap<>();

    private static JavaPlugin plugin;
    private static volatile UUID topPlayer;
    private static volatile int topPoints;

    // Seeds the cache for everyone already online; the SQL manager must be ready.
    public static void init(JavaPlugin owner) {

        plugin = owner;
        refreshTopPlayer();
        for (Player player : Bukkit.getOnlinePlayers())
            load(player.getUniqueId());

    }

    public static int get(UUID uuid) {

        return scores.getOrDefault(uuid, 0);

    }

    // Persisted total, as read from or written to hb_stat.
    public static void set(UUID uuid, int points) {

        scores.put(uuid, Math.max(points, 0));
        bumpTop(uuid);

    }

    // Live in-round increment, mirrored from the points tracker.
    public static void add(UUID uuid, int by) {

        scores.merge(uuid, by, Integer::sum);
        bumpTop(uuid);

    }

    public static void forget(UUID uuid) {

        scores.remove(uuid);

    }

    // Same rule as StatManager: Death Bringer is reserved for the top player.
    public static GameRank rankOf(UUID uuid) {

        int points = get(uuid);
        if (uuid.equals(topPlayer) && points >= GameRank.topPlayerGate())
            return GameRank.DEATHBRINGER;

        return GameRank.findRank(points);

    }

    public static UUID getTopPlayer() {

        return topPlayer;

    }

    // Async read of a player's persisted points; a lobby check() may already be
    // ahead.
    public static void load(UUID uuid) {

        if (plugin == null || SqlManager.get() == null)
            return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            int points = queryPoints(uuid);
            if (points < 0)
                return;

            scores.putIfAbsent(uuid, points);
            bumpTop(uuid);

        });

    }

    // Async refresh of the highest scorer after stats are pushed.
    public static void refreshTopPlayer() {

        if (plugin == null || SqlManager.get() == null)
            return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, HerobrineScores::queryTopPlayer);

    }

    private static void bumpTop(UUID uuid) {

        int points = get(uuid);
        if (uuid.equals(topPlayer) || points > topPoints) {

            topPlayer = uuid;
            topPoints = points;

        }

    }

    private static int queryPoints(UUID uuid) {

        try (Connection connection = SqlManager.get().createConnection()) {

            PreparedStatement statement = connection.prepareStatement("SELECT points FROM hb_stat WHERE uuid=?");
            statement.setString(1, uuid.toString());
            ResultSet rs = statement.executeQuery();

            return rs.next() ? rs.getInt("points") : 0;

        } catch (Exception e) {

            Console.error("Failed to load points for " + uuid + ": " + e.getMessage());
            return -1;

        }

    }

    private static void queryTopPlayer() {

        try (Connection connection = SqlManager.get().createConnection()) {

            PreparedStatement statement = connection
                    .prepareStatement("SELECT uuid, points FROM hb_stat ORDER BY points DESC LIMIT 1;");
            ResultSet rs = statement.executeQuery();

            if (!rs.next())
                return;

            UUID uuid = UUID.fromString(rs.getString("uuid"));
            int points = rs.getInt("points");
            if (points >= topPoints) {

                topPlayer = uuid;
                topPoints = points;

            }

        } catch (Exception e) {

            Console.error("Failed to load the top player: " + e.getMessage());

        }

    }

}
