package jhu.clueless.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jhu.clueless.game.BoardLogic;
import jhu.clueless.game.BoardLogic.HallwayLocation;
import jhu.clueless.game.BoardLogic.RoomLocation;
import jhu.clueless.game.model.Card;
import jhu.clueless.game.model.Character;
import jhu.clueless.game.model.Hallway;
import jhu.clueless.game.model.Location;
import jhu.clueless.game.model.Room;
import jhu.clueless.game.model.Weapon;
import jhu.clueless.messaging.BroadcastMessageType;
import jhu.clueless.messaging.ClientMessageType;
import jhu.clueless.messaging.MessageEnvelope;
import jhu.clueless.messaging.MessagePayloads.AccusationRequest;
import jhu.clueless.messaging.MessagePayloads.DisproveResponseRequest;
import jhu.clueless.messaging.MessagePayloads.MoveRequest;
import jhu.clueless.messaging.MessagePayloads.PlayerMovedPayload;
import jhu.clueless.messaging.MessagePayloads.SuggestionRequest;
import jhu.clueless.messaging.MessagePayloads.SuggestionMadePayload;
import jhu.clueless.messaging.MessagePayloads.YourTurnPayload;
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
    private final GameTurnManager turns;
    private final ClueLessWebSocketHandler sockets;
    private final ObjectMapper mapper;
    private final Map<String, PendingSuggestion> pendingSuggestions = new ConcurrentHashMap<>();

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
    public synchronized void startGame(String gameId, String playerId) {
        received(ClientMessageType.C4_START_GAME, gameId, playerId);
        if (!sessions.players(gameId).contains(playerId)) {
            throw new IllegalStateException("Player is not in this game.");
        }
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
        sessions.dealCards(gameId, solution);
        turns.start(gameId, sessions.players(gameId));
        broadcast(gameId, BroadcastMessageType.B4_GAME_STARTED, Map.of(
            "players", sessions.players(gameId),
            "positions", visiblePositions
        ));
        for (String p : sessions.players(gameId)) {
            sendTo(gameId, p, UnicastMessageType.U1_HAND_DEALT, Map.of("cards", sessions.hand(p)));
        }
        announceTurn(gameId, turns.activePlayer(gameId));
    }

    // ---------------------------------------------------------------- game play

    /** C6 -> B6 */
    public synchronized void move(String gameId, String playerId, MoveRequest req) {
        received(ClientMessageType.C6_MOVE, gameId, playerId);
        turns.requireActiveAction(gameId, playerId);
        logic.validateAction(playerId, "C6_MOVE");
        if (req == null || req.to() == null) {
            throw new IllegalArgumentException("A destination is required.");
        }
        String character = sessions.character(playerId).name();
        Location currentLocation = state.getLocationOfPlayer(gameId, character);
        Location newLocation = getLocationFromRequest(req.to());

        List<Location> allowedMoves = BoardLogic.destinationsFrom(currentLocation, state.getOccupiedHallways(gameId));
        if (!allowedMoves.contains(newLocation)) {
            throw new IllegalStateException(String.format("Invalid move from %s to %s", currentLocation, newLocation));
        }

        state.updateLocation(gameId, character, newLocation);
        turns.markMoved(gameId, playerId);
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

    /** C7 -> B7, followed by a private disprove request or B11. */
    public synchronized void makeSuggestion(String gameId, String playerId, SuggestionRequest req) {
        received(ClientMessageType.C7_MAKE_SUGGESTION, gameId, playerId);
        turns.requireActiveAction(gameId, playerId);
        if (req == null || req.suspect() == null || req.weapon() == null) {
            throw new IllegalArgumentException("A suspect and weapon are required.");
        }
        Character.valueOf(req.suspect());
        Weapon.valueOf(req.weapon());

        String character = sessions.character(playerId).name();
        Location location = state.getLocationOfPlayer(gameId, character);
        if (!(location instanceof RoomLocation roomLocation)) {
            throw new IllegalStateException("A suggestion can only be made from a room.");
        }

        String suggestionId = UUID.randomUUID().toString();
        PendingSuggestion pending = new PendingSuggestion(
            suggestionId, playerId, req.suspect(), req.weapon(), roomLocation.room().name(),
            turns.playersAfter(gameId, playerId));
        turns.beginSuggestion(gameId, playerId);
        pendingSuggestions.put(gameId, pending);
        broadcast(gameId, BroadcastMessageType.B7_SUGGESTION_MADE,
            new SuggestionMadePayload(suggestionId, playerId, req.suspect(), req.weapon(),
                roomLocation.room().name()));
        requestNextDisprover(gameId, pending);
    }

    /** C8 -> U4/B10 and turn advance. */
    public synchronized void disproveSuggestion(
            String gameId, String playerId, DisproveResponseRequest req) {
        received(ClientMessageType.C8_DISPROVE_RESPONSE, gameId, playerId);
        PendingSuggestion pending = pendingSuggestions.get(gameId);
        if (pending == null || !playerId.equals(pending.currentResponder)) {
            throw new IllegalStateException("This player has no pending disprove request.");
        }
        if (req == null || req.card() == null) {
            throw new IllegalArgumentException("A card must be selected.");
        }
        Card card;
        try {
            card = Card.valueOf(req.card());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown card: " + req.card(), e);
        }
        if (!pending.matchingCards.contains(card)) {
            throw new IllegalStateException("The selected card cannot disprove this suggestion.");
        }

        sendTo(gameId, pending.suggester, UnicastMessageType.U4_CARD_SHOWN,
            Map.of("suggestionId", pending.id, "playerId", playerId, "card", card.name()));
        broadcast(gameId, BroadcastMessageType.B10_SUGGESTION_DISPROVED,
            Map.of("suggestionId", pending.id, "suggester", pending.suggester,
                "playerId", playerId));
        pendingSuggestions.remove(gameId);
        announceTurn(gameId, turns.finishSuggestion(gameId));
    }

    public List<String> availableMoves(String gameId, String playerId) {
        if (!sessions.players(gameId).contains(playerId)) {
            throw new IllegalStateException("Player is not in this game.");
        }
        if (!turns.isAlive(gameId, playerId)) {
            throw new IllegalStateException("Eliminated players cannot move.");
        }

        String character = sessions.character(playerId).name();
        Location currentLocation = state.getLocationOfPlayer(gameId, character);
        return BoardLogic.destinationsFrom(currentLocation, state.getOccupiedHallways(gameId))
            .stream()
            .map(this::toLocationName)
            .toList();
    }

    public boolean canSuggest(String gameId, String playerId) {
        if (!sessions.players(gameId).contains(playerId)) {
            throw new IllegalStateException("Player is not in this game.");
        }
        if (!turns.isAlive(gameId, playerId)) {
            return false;
        }

        String character = sessions.character(playerId).name();
        return state.getLocationOfPlayer(gameId, character) instanceof RoomLocation;
    }

    /** C9 -> B12, U5 (accuser only) */
    public synchronized void makeAccusation(String gameId, String playerId, AccusationRequest req) {
        received(ClientMessageType.C9_MAKE_ACCUSATION, gameId, playerId);
        turns.requireActiveAction(gameId, playerId);
        if (req == null || req.suspect() == null || req.weapon() == null || req.room() == null) {
            throw new IllegalArgumentException("A suspect, weapon, and room are required.");
        }
        Character.valueOf(req.suspect());
        Weapon.valueOf(req.weapon());
        Room.valueOf(req.room());
        boolean correct = suggestions.checkAccusation(gameId, req.suspect(), req.weapon(), req.room());
        broadcast(gameId, BroadcastMessageType.B12_ACCUSATION_MADE, Map.of(
            "accuser", playerId, "suspect", req.suspect(), "weapon", req.weapon(), "room", req.room()));
        Card.Solution solution = state.caseFile(gameId);
        Map<String, Object> solutionPayload = Map.of(
            "suspect", solution.character().name(),
            "weapon", solution.weapon().name(),
            "room", solution.room().name());
        if (correct) {
            sendTo(gameId, playerId, UnicastMessageType.U5_ACCUSATION_RESULT,
                Map.of("correct", true, "solution", solutionPayload));
            turns.finishWithWinner(gameId, playerId);
            announceGameOver(gameId, playerId, solutionPayload);
        } else {
            sendTo(gameId, playerId, UnicastMessageType.U5_ACCUSATION_RESULT,
                Map.of("correct", false));
            broadcast(gameId, BroadcastMessageType.B13_PLAYER_ELIMINATED,
                Map.of("playerId", playerId, "character", sessions.character(playerId).name()));
            String nextPlayer = turns.eliminateActivePlayer(gameId, playerId);
            state.removePlayer(gameId, sessions.character(playerId).name());
            if (turns.isFinished(gameId)) {
                announceGameOver(gameId, nextPlayer, solutionPayload);
            } else {
                announceTurn(gameId, nextPlayer);
            }
        }
    }

    private void requestNextDisprover(String gameId, PendingSuggestion pending) {
        while (pending.nextResponder < pending.responders.size()) {
            String responder = pending.responders.get(pending.nextResponder++);
            broadcast(gameId, BroadcastMessageType.B8_DISPROVE_PENDING,
                Map.of("suggestionId", pending.id, "playerId", responder));
            List<Card> matchingCards = suggestions.matchingCards(
                sessions.hand(responder), pending.suspect, pending.weapon, pending.room);
            if (matchingCards.isEmpty()) {
                broadcast(gameId, BroadcastMessageType.B9_PLAYER_CANNOT_DISPROVE,
                    Map.of("suggestionId", pending.id, "playerId", responder));
                continue;
            }

            pending.currentResponder = responder;
            pending.matchingCards = matchingCards;
            sendTo(gameId, responder, UnicastMessageType.U3_DISPROVE_REQUEST,
                Map.of("suggestionId", pending.id, "suggester", pending.suggester,
                    "suspect", pending.suspect, "weapon", pending.weapon, "room", pending.room,
                    "cards", matchingCards.stream().map(Card::name).toList()));
            return;
        }

        broadcast(gameId, BroadcastMessageType.B11_SUGGESTION_NOT_DISPROVED,
            Map.of("suggestionId", pending.id, "suggester", pending.suggester));
        pendingSuggestions.remove(gameId);
        announceTurn(gameId, turns.finishSuggestion(gameId));
    }

    private void announceTurn(String gameId, String playerId) {
        String character = sessions.character(playerId).name();
        Location location = state.getLocationOfPlayer(gameId, character);
        List<String> legalMoves = BoardLogic.destinationsFrom(location, state.getOccupiedHallways(gameId))
            .stream().map(this::toLocationName).toList();
        int turn = turns.turnNumber(gameId);
        broadcast(gameId, BroadcastMessageType.B5_TURN_CHANGED,
            Map.of("playerId", playerId, "character", character, "turn", turn));
        sendTo(gameId, playerId, UnicastMessageType.U2_YOUR_TURN,
            new YourTurnPayload(turn, toLocationName(location), legalMoves, false,
                location instanceof RoomLocation, true, false));
    }

    private void announceGameOver(String gameId, String winnerId, Map<String, Object> solution) {
        broadcast(gameId, BroadcastMessageType.B14_GAME_OVER,
            Map.of("winnerId", winnerId, "winner", sessions.character(winnerId).name(),
                "solution", solution));
    }

    private static final class PendingSuggestion {
        private final String id;
        private final String suggester;
        private final String suspect;
        private final String weapon;
        private final String room;
        private final List<String> responders;
        private int nextResponder;
        private String currentResponder;
        private List<Card> matchingCards = List.of();

        private PendingSuggestion(String id, String suggester, String suspect, String weapon,
                String room, List<String> responders) {
            this.id = id;
            this.suggester = suggester;
            this.suspect = suspect;
            this.weapon = weapon;
            this.room = room;
            this.responders = responders;
        }
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
