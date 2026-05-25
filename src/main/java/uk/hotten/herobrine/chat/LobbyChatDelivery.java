package uk.hotten.herobrine.chat;

import org.bukkit.entity.Player;

import uk.hotten.herobrine.lobby.GameLobby;

/**
 * Delivery boundary for game-lobby chat. A future Chat-OG adapter can route
 * through a world/channel API while retaining TheHerobrine's lobby isolation
 * and game-specific formatting.
 */
public interface LobbyChatDelivery {

    void send(GameLobby lobby, Player sender, String rawMessage, String formattedMessage);

}
