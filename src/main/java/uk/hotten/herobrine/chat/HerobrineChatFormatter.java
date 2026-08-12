package uk.hotten.herobrine.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextDecoration;
import net.trueog.utilitiesog.UtilitiesOG;
import nl.skbotnl.chatog.api.WorldChatFormatter;
import org.bukkit.entity.Player;
import uk.hotten.herobrine.game.GameManager;
import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.lobby.LobbyManager;
import uk.hotten.herobrine.stat.GameRank;
import uk.hotten.herobrine.stat.StatManager;
import uk.hotten.herobrine.utils.GameState;

// Renders lobby chat the way TheHerobrine always has, now that Chat-OG owns delivery and the
// Discord relay. Chat-OG hands us the lobby id, which is the same id LobbyManager keys lobbies by.
public class HerobrineChatFormatter implements WorldChatFormatter {

    @Override
    public Component format(Player sender, Component message, String worldName, String lobbyId) {

        LobbyManager lobbyManager = LobbyManager.getInstance();
        if (lobbyManager == null)
            return null;

        GameLobby lobby = lobbyManager.getLobby(lobbyId);
        if (lobby == null)
            return null;

        GameManager gameManager = lobby.getGameManager();
        StatManager statManager = lobby.getStatManager();
        if (gameManager == null || statManager == null)
            return null;

        String prefix = buildPrefix(sender, gameManager, statManager);

        // No prefix means the lobby is booting or shutting down, so drop the message as
        // before.
        if (prefix == null)
            return Component.empty();

        // The message is already sanitised by Chat-OG, so it is composed with rather
        // than re-parsed.
        Component body = gameManager.isHerobrine(sender) ? message.decorate(TextDecoration.BOLD) : message;

        // Expand audience-aware placeholders (LuckPerms prefixes, etc.) using the
        // sender as the audience so both in-game and Discord relays receive the
        // same union/rank/name/suffix content. Chat-OG adds the [HB1 - Lobby]
        // tag for Discord itself, so do not add it here.
        Component prefixComp = UtilitiesOG.trueogExpand(prefix, sender);

        // Add an explicit badge so Herobrine is visible in relays like Discord even
        // if some expansions are stripped downstream.
        Component herobrineBadge = null;
        if (gameManager.isHerobrine(sender)) {
            herobrineBadge = UtilitiesOG.trueogExpand("&4[HEROBRINE] &r", sender);
        }

        // Only show the caret when the player display name placeholder is present
        // in the prefix. Build a caret component that explicitly disables bold so
        // it never becomes bold even if the message body is decorated.
        if (prefix.contains("<player_display_name>")) {

            Component caretComp = UtilitiesOG.trueogExpand(getCaretColor(sender) + "> &r", sender)
                    .decoration(TextDecoration.BOLD, false);
            if (herobrineBadge != null)
                return Component.join(JoinConfiguration.noSeparators(), prefixComp, herobrineBadge, caretComp, body);
            return Component.join(JoinConfiguration.noSeparators(), prefixComp, caretComp, body);

        }

        if (herobrineBadge != null)
            return Component.join(JoinConfiguration.noSeparators(), prefixComp, herobrineBadge, body);

        return Component.join(JoinConfiguration.noSeparators(), prefixComp, body);

    }

    private String buildPrefix(Player player, GameManager gameManager, StatManager statManager) {

        GameRank rank = statManager.getGameRank(player.getUniqueId());
        Integer points = statManager.getPoints().get(player.getUniqueId());
        int score = points != null ? points : 0;

        // Include the SimpleClans union bracket tag placeholder and the LuckPerms-aware
        // player display placeholder so both union and colored player name render.
        String name = "<simpleclans_union_bracket_tag><player_display_name><luckperms_suffix> ";
        String nameNoLeading = "<simpleclans_union_bracket_tag><player_display_name><luckperms_suffix>";

        GameState gameState = gameManager.getGameState();

        if (gameState == GameState.WAITING || gameState == GameState.STARTING)
            return "&e" + score + "&8 ▏ " + rank.getDisplay() + " " + name;

        if (gameState != GameState.LIVE && gameState != GameState.ENDING)
            return null;

        if (gameManager.isHerobrine(player))
            return "&4THE HEROBRINE &8| &r&l" + name;

        if (gameManager.isSurvivor(player))
            return rank.getDisplay() + " " + name;

        return "&e" + score + "&8 ▍ &4DEAD &8▏ " + name;

    }

    // TODO: Expose PlayerUtils.getMessageColor in Chat-OG as a supported API.
    private String getCaretColor(Player player) {

        try {

            // Use Chat-OG's PlayerUtils.getMessageColor when available
            net.kyori.adventure.text.format.TextColor color = nl.skbotnl.chatog.util.PlayerUtils.INSTANCE
                    .getMessageColor(player.getUniqueId());
            if (color != null) {

                if (color.equals(net.kyori.adventure.text.format.NamedTextColor.WHITE)) {

                    return "&f";

                } else if (color.equals(net.kyori.adventure.text.format.NamedTextColor.GRAY)) {

                    return "&7";

                }

            }

        } catch (Throwable ignored) {

            // Chat-OG not present or error — fall back to dark gray
        }

        return "&8";

    }

}
