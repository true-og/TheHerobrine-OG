package uk.hotten.herobrine.lobby.data;

import lombok.Getter;

public class LobbyConfig {

    @Getter
    private String id;

    @Getter
    private String prefix;

    @Getter
    private int minPlayers;

    @Getter
    private int maxPlayers;

    @Getter
    private int startTime;

    @Getter
    private boolean allowOverfill;

    @Getter
    private int votingMaps;

    @Getter
    private int endVotingAt;

    @Getter
    private int autoStartAmount;

    @Getter
    private String hub;

    // Hub template folder auto-started lobbies copy their hub world from.
    public String getHubTemplate() {

        return hub == null || hub.isBlank() ? "hub" : hub;

    }

    public LobbyConfig() {

    }

}
