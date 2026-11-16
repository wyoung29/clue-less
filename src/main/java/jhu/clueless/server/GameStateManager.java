package jhu.clueless.server;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import jhu.clueless.game.BoardLogic;
import jhu.clueless.game.model.Hallway;
import jhu.clueless.game.model.Location;
import jhu.clueless.game.model.Card;
import lombok.extern.slf4j.Slf4j;

/**
 * Game State Management component (SRS 2.3.2).
 * Tracks token locations, broadcast sequence numbers, and each game's solution.
 */
@Component
@Slf4j 
public class GameStateManager {

    private final Map<String, AtomicLong> seqByGame = new ConcurrentHashMap<>();
    private final Map<String, BoardState> boardLocation = new ConcurrentHashMap<>();
    private final Map<String, Card.Solution> caseFiles = new ConcurrentHashMap<>();

    /** Broadcasts are numbered 1, 2, 3, ... per game (SRS 5.1). */
    public long nextSeq(String gameId) {
        return seqByGame.computeIfAbsent(gameId, g -> new AtomicLong()).incrementAndGet();
    }

    public void initializeGame(String gameId, Map<String, Location> startingLocations) {
        log.info("Initializing Board {} with {} players...", gameId, startingLocations.size());

        BoardState board = new BoardState(startingLocations);

        if (boardLocation.putIfAbsent(gameId, board) != null) {
            throw new IllegalStateException("Game already initialized: " + gameId);
        }
    }

    public boolean isInitialized(String gameId) {
        return boardLocation.containsKey(gameId);
    }

    public Location getLocationOfPlayer(String gameId, String player) {
        BoardState board = requireBoard(gameId);
        return board.locations.get(player);
    }

    // Returns the hallways that are currently occupied by players in a game.
    public Set<Hallway> getOccupiedHallways(String gameId) {
        BoardState board = requireBoard(gameId);
        Set<Hallway> occupied = EnumSet.noneOf(Hallway.class);
        for (Location loc : board.locations.values()) {
            if (loc instanceof BoardLogic.HallwayLocation hallway) {
                occupied.add(hallway.hallway());
            }
        }
        return occupied;
    }

    public void updateLocation(String gameId, String player, Location newLocation) {
        BoardState board = requireBoard(gameId);

        if (!board.locations.containsKey(player)) {
            throw new IllegalStateException("Player not in game: " + player);
        }

        Location oldLocation = board.locations.put(player, newLocation);
        log.info("updateLocation({}, {}: {} -> {})", gameId, player, oldLocation, newLocation);
    }

    public Card.Solution caseFile(String gameId) {
        return caseFiles.computeIfAbsent(gameId, id -> {
            Card.Solution solution = Card.createRandomSolution();
            log.info("caseFile({}) initialized", id);
            return solution;
        });
    }

    private BoardState requireBoard(String gameId) {
        Optional<BoardState> board = Optional.ofNullable(boardLocation.get(gameId));
        if (board.isEmpty()) {
            throw new IllegalStateException("Game not initialized: " + gameId);
        }
        return board.get();
    }

    private static final class BoardState {
        private final Map<String, Location> locations;

        private BoardState(Map<String, Location> startingPlaces) {
            this.locations = new HashMap<>(startingPlaces);
        }
    }
}
