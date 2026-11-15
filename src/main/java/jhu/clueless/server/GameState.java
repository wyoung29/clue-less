
package jhu.clueless.server;

import java.util.List;

import jhu.clueless.server.models.Player;

public class GameState {

    private final String gameId;
    private final List<Player> players;

    public GameState(String gameId, List<Player> players) {
        this.gameId = gameId;
        this.players = List.copyOf(players);
    }

    public String getGameId() {
        return gameId;
    }

    public List<Player> getPlayers() {
        return players;
    }
}
