
package jhu.clueless.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jhu.clueless.server.models.Character;
import jhu.clueless.server.models.Player;

public class GameSession {

    private final String gameId;
    private final String hostToken;
    private final Map<String, Player> playersByToken;

    private GameState gameState;
    private boolean started;

    public GameSession(
        String gameId,
        Map<String, Player> playersByToken,
        String hostToken
    ) {
        this.gameId = gameId;
        this.playersByToken = new LinkedHashMap<>(playersByToken);
        this.hostToken = hostToken;
        this.started = false;
    }

    public synchronized GameState start(String token) {

        if (!hostToken.equals(token)) {
            throw new IllegalArgumentException("ONLY_HOST_CAN_START");
        }

        if (started) {
            throw new IllegalStateException("GAME_ALREADY_STARTED");
        }

        List<Character> characters =
            new ArrayList<>(List.of(Character.values()));

        Collections.shuffle(characters);

        int index = 0;

        for (Map.Entry<String, Player> entry : playersByToken.entrySet()) {
            Player oldPlayer = entry.getValue();

            Player assignedPlayer = new Player(
                oldPlayer.playerId(),
                characters.get(index++)
            );

            entry.setValue(assignedPlayer);
        }

        gameState = new GameState(
            gameId,
            new ArrayList<>(playersByToken.values())
        );

        started = true;

        return gameState;
    }

    public synchronized void addPlayer(String token, Player player) {
        if (started) {
            throw new IllegalStateException("GAME_ALREADY_STARTED");
        }

        if (playersByToken.size() >= Character.values().length) {
            throw new IllegalStateException("GAME_FULL");
        }

        playersByToken.put(token, player);
    }

    public synchronized boolean canAcceptPlayers() {
        return !started && playersByToken.size() < Character.values().length;
    }

    public String getGameId() {
        return gameId;
    }

    public String getHostToken() {
        return hostToken;
    }

    public Map<String, Player> getPlayersByToken() {
        return Map.copyOf(playersByToken);
    }

    public GameState getGameState() {
        return gameState;
    }

    public boolean isStarted() {
        return started;
    }
}
