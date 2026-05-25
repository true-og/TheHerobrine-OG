package uk.hotten.herobrine.chat;

import org.bukkit.entity.Player;

import uk.hotten.herobrine.lobby.GameLobby;
import uk.hotten.herobrine.utils.Message;

public class LocalLobbyChatDelivery implements LobbyChatDelivery {

    @Override
    public void send(GameLobby lobby, Player sender, String rawMessage, String formattedMessage) {

        Message.broadcast(lobby, formattedMessage);

    }

}
