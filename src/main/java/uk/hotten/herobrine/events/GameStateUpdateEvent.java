package uk.hotten.herobrine.events;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import uk.hotten.herobrine.utils.GameState;

public class GameStateUpdateEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    @Getter
    private GameState prevState;

    @Getter
    private GameState newState;

    @Getter
    private String lobbyId;

    public GameStateUpdateEvent(GameState prevState, GameState newState, String lobbyId) {

        this.prevState = prevState;
        this.newState = newState;
        this.lobbyId = lobbyId;

    }

    @Override
    public HandlerList getHandlers() {

        return handlers;

    }

    public static HandlerList getHandlerList() {

        return handlers;

    }

}
