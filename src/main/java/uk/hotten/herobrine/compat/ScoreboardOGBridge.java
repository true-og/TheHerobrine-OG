package uk.hotten.herobrine.compat;

import java.util.List;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import plugin.api.ScoreboardOGAPI;
import plugin.api.SidebarProvider;

// Soft bridge to Scoreboard-OG. The nested Hook is the only class that touches the
// API, and it is loaded only after the plugin is confirmed present and enabled.
public final class ScoreboardOGBridge {

    private static final String OWNER = "TheHerobrine-OG";
    private static Boolean available;

    private ScoreboardOGBridge() {

    }

    public static boolean isAvailable() {

        if (available == null) {

            boolean present = Bukkit.getPluginManager().isPluginEnabled("Scoreboard-OG");
            available = present && Hook.ready();

        }

        return available;

    }

    public static void claim(Player player, Function<Player, Component> title,
            Function<Player, List<Component>> lines)
    {

        if (isAvailable())
            Hook.claim(player, title, lines);

    }

    public static void release(Player player) {

        if (isAvailable())
            Hook.release(player);

    }

    public static void releaseAll() {

        if (isAvailable())
            Hook.releaseAll();

    }

    private static final class Hook {

        static boolean ready() {

            try {

                return ScoreboardOGAPI.isAvailable();

            } catch (NoClassDefFoundError error) {

                // An older Scoreboard-OG without the API; fall back to the local boards.
                return false;

            }

        }

        static void claim(Player player, Function<Player, Component> title, Function<Player, List<Component>> lines) {

            ScoreboardOGAPI.claim(player, OWNER, new SidebarProvider() {

                @Override
                public Component title(Player viewer) {

                    return title.apply(viewer);

                }

                @Override
                public List<Component> lines(Player viewer) {

                    return lines.apply(viewer);

                }

            });

        }

        static void release(Player player) {

            ScoreboardOGAPI.release(player, OWNER);

        }

        static void releaseAll() {

            ScoreboardOGAPI.releaseAll(OWNER);

        }

    }

}
