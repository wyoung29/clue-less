package jhu.clueless.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jhu.clueless.game.BoardLogic;
import jhu.clueless.game.BoardLogic.HallwayLocation;
import jhu.clueless.game.BoardLogic.RoomLocation;
import jhu.clueless.game.model.Card;
import jhu.clueless.game.model.Hallway;
import jhu.clueless.game.model.Location;
import jhu.clueless.game.model.Room;
import jhu.clueless.messaging.BroadcastMessageType;
import jhu.clueless.messaging.ClientMessageType;
import jhu.clueless.messaging.MessageEnvelope;
import jhu.clueless.messaging.MessagePayloads.AccusationRequest;
import jhu.clueless.messaging.MessagePayloads.MoveRequest;
import jhu.clueless.messaging.MessagePayloads.PlayerMovedPayload;
import jhu.clueless.messaging.UnicastMessageType;
import jhu.clueless.websocket.ClueLessWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Server Communication component (SRS 2.3.2).
 * Receives each client request (C#) from the REST controller, calls the other server
 * components, and sends the resulting messages to clients over WebSocket:
 * unicast (U#) to one player, broadcast (B#) to everyone in the game.
 */
@Component
@Slf4j
@RequiredArgsConstructor  
public class ServerCommunication {


    private final GameSessionManager sessions;
    private final GameLogic logic;
    private final GameStateManager state;
    private final SuggestionAccusationManager suggestions;
    private final ClueLessWebSocketHandler sockets;
    private final ObjectMapper mapper;

    // ---------------------------------------------------------------- lobby

    /** C1: returns gameId, playerId, token. */
    public Map<String, Object> createGame(String username) {
        received(ClientMessageType.C1_CREATE_GAME, null, username);
        String gameId = sessions.createGame();
        String[] ids = sessions.addPlayer(gameId, username);
        return Map.of("gameId", gameId, "playerId", ids[0], "token", ids[1]);
    }

    /** C2 -> B1 */
    public Map<String, Object> joinGame(String gameId, String username) {
        received(ClientMessageType.C2_JOIN_GAME, gameId, username);

        if (state.isInitialized(gameId)) {
            throw new IllegalStateException("Game has already started.");
        }
        
        int currentCount = sessions.players(gameId).size();
        if (currentCount >= 6) {
            throw new IllegalStateException("Game is full. Maximum of 6 players allowed.");
        }

        String[] ids = sessions.addPlayer(gameId, username);
        broadcast(gameId, BroadcastMessageType.B1_PLAYER_JOINED, Map.of(
            "playerId", ids[0], "username", username, "playerCount", sessions.players(gameId).size()));
        return Map.of("gameId", gameId, "playerId", ids[0], "token", ids[1]);
    }   

    /** C4 -> B4, U1 (each player) */
    public void startGame(String gameId, String playerId) {
        received(ClientMessageType.C4_START_GAME, gameId, playerId);
        
        logic.validateAction(playerId, "C4_START_GAME");
        
        int playerCount = sessions.players(gameId).size();
        if (playerCount < 3) {
            throw new IllegalStateException("A minimum of 3 players is required to start.");
        }

        Map<String, Location> startingPositions = new LinkedHashMap<>();
        Map<String, String> visiblePositions = new LinkedHashMap<>();
        for (int i = 0; i < playerCount; i++) {
            String player = sessions.players(gameId).get(i);
            String character = sessions.character(player).name();
            startingPositions.put(character, new HallwayLocation(Hallway.values()[i]));
            visiblePositions.put(
                character,
                toLocationName(startingPositions.get(character))
            );
        }
        state.initializeGame(gameId, startingPositions);

        Card.Solution solution = state.caseFile(gameId);
        log.info("DEBUG game {} solution: character={}, weapon={}, room={}",
            gameId, solution.character(), solution.weapon(), solution.room());
        sessions.dealCards(gameId, solution);
        broadcast(gameId, BroadcastMessageType.B4_GAME_STARTED, Map.of(
            "players", sessions.players(gameId),
            "positions", visiblePositions
        ));
        for (String p : sessions.players(gameId)) {
            sendTo(gameId, p, UnicastMessageType.U1_HAND_DEALT, Map.of("cards", sessions.hand(p)));
        }
    }

    // ---------------------------------------------------------------- game play

    /** C6 -> B6 */
    public void move(String gameId, String playerId, MoveRequest req) {
        received(ClientMessageType.C6_MOVE, gameId, playerId);
        logic.validateAction(playerId, "C6_MOVE");
        String character = sessions.character(playerId).name();
        Location currentLocation = state.getLocationOfPlayer(gameId, character);
        Location newLocation = getLocationFromRequest(req.to());

        List<Location> allowedMoves = BoardLogic.destinationsFrom(currentLocation, state.getOccupiedHallways(gameId));
        if (!allowedMoves.contains(newLocation)) {
            throw new IllegalStateException(String.format("Invalid move from %s to %s", currentLocation, newLocation));
        }

        state.updateLocation(gameId, character, newLocation);
        broadcast(
            gameId, 
            BroadcastMessageType.B6_PLAYER_MOVED, 
            new PlayerMovedPayload(
                character,
                toLocationName(currentLocation),
                toLocationName(newLocation),
                determineRoute(currentLocation, newLocation)
            )
        );
    }

    public List<String> availableMoves(String gameId, String playerId) {
        if (!sessions.players(gameId).contains(playerId)) {
            throw new IllegalStateException("Player is not in this game.");
        }

        String character = sessions.character(playerId).name();
        Location currentLocation = state.getLocationOfPlayer(gameId, character);
        return BoardLogic.destinationsFrom(currentLocation, state.getOccupiedHallways(gameId))
            .stream()
            .map(this::toLocationName)
            .toList();
    }

    /** C9 -> B12, U5 (accuser only) */
    public void makeAccusation(String gameId, String playerId, AccusationRequest req) {
        received(ClientMessageType.C9_MAKE_ACCUSATION, gameId, playerId);
        boolean correct = suggestions.checkAccusation(gameId, req.suspect(), req.weapon(), req.room());
        broadcast(gameId, BroadcastMessageType.B12_ACCUSATION_MADE, Map.of(
            "accuser", playerId, "suspect", req.suspect(), "weapon", req.weapon(), "room", req.room()));
        Card.Solution solution = state.caseFile(gameId);
        Map<String, Object> solutionPayload = Map.of(
            "suspect", solution.character().name(),
            "weapon", solution.weapon().name(),
            "room", solution.room().name());
        sendTo(gameId, playerId, UnicastMessageType.U5_ACCUSATION_RESULT,
            Map.of("correct", correct, "solution", solutionPayload));
    }

    private Location getLocationFromRequest(String newLocation) {
        if (List.of(Room.values()).stream().anyMatch(room -> room.name().equalsIgnoreCase(newLocation))) {
            return new RoomLocation(Room.valueOf(newLocation));
        } else if (List.of(Hallway.values()).stream().anyMatch(hallway -> hallway.name().equalsIgnoreCase(newLocation))) {
            return new HallwayLocation(Hallway.valueOf(newLocation));
        } else {
            throw new IllegalArgumentException("Unknown value for location: " + newLocation);
        }
    }

    private String determineRoute(Location from, Location to) {
        return from instanceof HallwayLocation  || to instanceof HallwayLocation ? "HALLWAY" : "PASSAGE";
    }

    private String toLocationName(Location location) {
        if (location instanceof RoomLocation roomLocation) {
            return roomLocation.room().name();
        }
        if (location instanceof HallwayLocation hallwayLocation) {
            return hallwayLocation.hallway().name();
        }
        throw new IllegalArgumentException("Unsupported board location: " + location);
    }

    // ---------------------------------------------------------------- outbound

    private void sendTo(String gameId, String playerId, UnicastMessageType type, Object payload) {
        MessageEnvelope env = new MessageEnvelope(type.name(), gameId, null, toMap(payload));
        log.info("[Server -> {}] {} {}", playerId, type, env.payload());
        sockets.sendToPlayer(gameId, playerId, toJson(env));
    }

    private void broadcast(String gameId, BroadcastMessageType type, Object payload) {
        MessageEnvelope env = new MessageEnvelope(type.name(), gameId, state.nextSeq(gameId), toMap(payload));
        log.info("[Server -> ALL] {} seq={} {}", type, env.seq(), env.payload());
        sockets.broadcast(gameId, toJson(env));
    }

    private void received(ClientMessageType type, String gameId, String from) {
        log.info("[{} -> Server] {} game={}", from, type, gameId);
    }

    private Map<String, Object> toMap(Object payload) {
        return mapper.convertValue(payload, new TypeReference<Map<String, Object>>() {});
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
