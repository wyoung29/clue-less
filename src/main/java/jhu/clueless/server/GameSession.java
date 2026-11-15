package jhu.clueless.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jhu.clueless.server.models.Card;
import jhu.clueless.server.models.Character;
import jhu.clueless.server.models.Player;

public class GameSession {

    private final String gameId;
    private final String hostToken;
    private final Map<String, Player> playersByToken;
    private boolean completed;

    private GameState gameState;
    private Card.Solution solution;
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

        // Assign random characters
        List<Character> characters =
            new ArrayList<>(List.of(Character.values()));

        Collections.shuffle(characters);

        // Generate solution and deal cards
        solution = Card.createRandomSolution();
        List<List<Card>> hands = Card.dealCards(
            playersByToken.size(),
            solution
        );

        // Assign a character and hand to each player
        int index = 0;
        for (Map.Entry<String, Player> entry : playersByToken.entrySet()) {
            Player oldPlayer = entry.getValue();

            entry.setValue(new Player(
                oldPlayer.playerId(),
                characters.get(index),
                hands.get(index)
            ));

            index++;
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
        return Collections.unmodifiableMap(playersByToken);
    }

    public GameState getGameState() {
        return gameState;
    }

    public Card.Solution getSolution() {
        return solution;
    }

    public boolean isStarted() {
        return started;
    }

    public synchronized void completeGame() {
        completed = true;
    }

    public boolean isCompleted() {
        return completed;
    }

    public synchronized Player nextTurn(String currentPlayerId) {
        List<Player> players = new ArrayList<>(playersByToken.values());

        if (players.isEmpty()) {
            throw new IllegalStateException("NO_PLAYERS_IN_GAME");
        }

        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).playerId().equals(currentPlayerId)) {
                return players.get((i + 1) % players.size());
            }
        }

        throw new IllegalArgumentException("PLAYER_NOT_FOUND");
    }
}