package jhu.clueless.server;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class GameTurnManager {

    private final Map<String, TurnState> games = new ConcurrentHashMap<>();

    public synchronized void start(String gameId, List<String> players) {
        if (games.putIfAbsent(gameId, new TurnState(players)) != null) {
            throw new IllegalStateException("Turns already initialized for game: " + gameId);
        }
    }

    public synchronized String activePlayer(String gameId) {
        return requireState(gameId).activePlayer();
    }

    public synchronized int turnNumber(String gameId) {
        return requireState(gameId).turnNumber;
    }

    public synchronized boolean isAlive(String gameId, String playerId) {
        return !requireState(gameId).eliminated.contains(playerId);
    }

    public synchronized List<String> playersAfter(String gameId, String playerId) {
        TurnState state = requireState(gameId);
        int start = state.players.indexOf(playerId);
        if (start < 0) {
            throw new IllegalStateException("Player is not in this game.");
        }
        return java.util.stream.IntStream.range(1, state.players.size())
            .mapToObj(offset -> state.players.get((start + offset) % state.players.size()))
            .filter(id -> !state.eliminated.contains(id))
            .toList();
    }

    public synchronized void requireActiveAction(String gameId, String playerId) {
        TurnState state = requireState(gameId);
        if (state.finished) {
            throw new IllegalStateException("Game is over.");
        }
        if (!state.activePlayer().equals(playerId)) {
            throw new IllegalStateException("It is not this player's turn.");
        }
        if (state.awaitingSuggestion) {
            throw new IllegalStateException("A suggestion is still being resolved.");
        }
    }

    public synchronized void markMoved(String gameId, String playerId) {
        requireActiveAction(gameId, playerId);
        TurnState state = requireState(gameId);
        if (state.moved) {
            throw new IllegalStateException("The player has already moved this turn.");
        }
        state.moved = true;
    }

    public synchronized void beginSuggestion(String gameId, String playerId) {
        requireActiveAction(gameId, playerId);
        requireState(gameId).awaitingSuggestion = true;
    }

    public synchronized String finishSuggestion(String gameId) {
        TurnState state = requireState(gameId);
        if (!state.awaitingSuggestion) {
            throw new IllegalStateException("No suggestion is being resolved.");
        }
        state.awaitingSuggestion = false;
        return advance(state);
    }

    public synchronized String eliminateActivePlayer(String gameId, String playerId) {
        requireActiveAction(gameId, playerId);
        TurnState state = requireState(gameId);
        state.eliminated.add(playerId);
        if (state.players.size() - state.eliminated.size() == 1) {
            state.finished = true;
            return state.players.stream().filter(id -> !state.eliminated.contains(id)).findFirst().orElseThrow();
        }
        return advance(state);
    }

    public synchronized void finishWithWinner(String gameId, String winnerId) {
        TurnState state = requireState(gameId);
        if (state.finished) {
            throw new IllegalStateException("Game is already over.");
        }
        if (state.eliminated.contains(winnerId)) {
            throw new IllegalStateException("An eliminated player cannot win.");
        }
        state.finished = true;
    }

    public synchronized boolean isFinished(String gameId) {
        return requireState(gameId).finished;
    }

    private String advance(TurnState state) {
        for (int offset = 1; offset <= state.players.size(); offset++) {
            int candidate = (state.currentIndex + offset) % state.players.size();
            String playerId = state.players.get(candidate);
            if (!state.eliminated.contains(playerId)) {
                state.currentIndex = candidate;
                state.turnNumber++;
                state.moved = false;
                return playerId;
            }
        }
        throw new IllegalStateException("No active players remain.");
    }

    private TurnState requireState(String gameId) {
        TurnState state = games.get(gameId);
        if (state == null) {
            throw new IllegalStateException("Game turns have not started.");
        }
        return state;
    }

    private static final class TurnState {
        private final List<String> players;
        private final Set<String> eliminated = new HashSet<>();
        private int currentIndex;
        private int turnNumber = 1;
        private boolean moved;
        private boolean awaitingSuggestion;
        private boolean finished;

        private TurnState(List<String> players) {
            this.players = List.copyOf(players);
        }

        private String activePlayer() {
            return players.get(currentIndex);
        }
    }
}
