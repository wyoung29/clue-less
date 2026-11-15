
package jhu.clueless.controller;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jhu.clueless.server.MatchmakingService;
import jhu.clueless.messaging.BroadcastMessageType;
import jhu.clueless.messaging.MessagePayloads;
import jhu.clueless.messaging.MessagePayloads.AccusationRequest;
import jhu.clueless.server.GameSession;
import jhu.clueless.server.GameSessionManager;
import jhu.clueless.server.MatchmakingService.QueueEntry;
import jhu.clueless.server.models.Card;
import jhu.clueless.server.models.Character;
import jhu.clueless.server.models.Weapon;
import jhu.clueless.server.models.Room;
import jhu.clueless.server.models.Player;
import jhu.clueless.messaging.WebSocketMessage;
import jhu.clueless.websocket.ClueLessWebSocketHandler;

@RestController
public class GameController {

    private final MatchmakingService matchmaking;
    private final GameSessionManager sessions;
    private final ClueLessWebSocketHandler webSocketHandler;

    public GameController(
        MatchmakingService matchmaking,
        GameSessionManager sessions,
        ClueLessWebSocketHandler webSocketHandler
    ) {
        this.matchmaking = matchmaking;
        this.sessions = sessions;
        this.webSocketHandler = webSocketHandler;
    }

    @PostMapping("/api/matchmaking")
    public Map<String, Object> matchMaking() {

        QueueEntry entry = matchmaking.matchMaking();

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("token", entry.token());
        response.put("status", entry.status());
        response.put("isFirstInQueue", entry.isFirstInQueue());
        response.put("gameId", entry.gameId());
        response.put("playerId", entry.player().playerId());

        return response;
    }

    @PostMapping("/api/matchmaking/{gameId}/start")
    public Map<String, String> startGame(
        @PathVariable String gameId,
        @RequestHeader("X-Player-Token") String token
    ) {
        sessions.startGame(gameId, token);

        GameSession session = sessions.getSession(gameId);

        for (Map.Entry<String, Player> entry : session.getPlayersByToken().entrySet()) {

            String playerToken = entry.getKey();
            Player player = entry.getValue();

            try {
                // Notify player that the game started
                webSocketHandler.sendToPlayer(
                    playerToken,
                    new WebSocketMessage(
                        BroadcastMessageType.B4_GAME_STARTED,
                        Map.of("gameId", gameId)
                    )
                );

                // Privately send this player's cards
                webSocketHandler.sendToPlayer(
                    playerToken,
                    new WebSocketMessage(
                        BroadcastMessageType.B19_PLAYER_HAND_DEALT,
                        new MessagePayloads.PlayerHandPayload(
                            gameId,
                            player.character().getName(),
                            player.character().getLocation().name(),
                            player.hand().stream()
                                .map(Enum::name)
                                .toList()
                        )
                    )
                );

            } catch (IOException e) {
                System.err.println("Failed to notify player: " + e.getMessage());
            }
        }

        // First player to join gets the first turn
        Player firstPlayer = session.getPlayersByToken()
            .values()
            .iterator()
            .next();

        WebSocketMessage turnMessage = new WebSocketMessage(
            BroadcastMessageType.B5_TURN_CHANGED,
            new MessagePayloads.TurnChangedPayload(
                firstPlayer.playerId(),
                firstPlayer.character().getName()
            )
        );

        // Notify everyone whose turn it is
        for (String playerToken : session.getPlayersByToken().keySet()) {
            try {
                webSocketHandler.sendToPlayer(playerToken, turnMessage);
            } catch (IOException e) {
                System.err.println("Failed to send turn notification: " + e.getMessage());
            }
        }

        return Map.of("status", "STARTED");
    }

    @PostMapping("/api/matchmaking/{gameId}/accusation")
    public Map<String, String> makeAccusation(
        @PathVariable String gameId,
        @RequestHeader("X-Player-Token") String token,
        @RequestBody AccusationRequest request
    ) {
        GameSession session = sessions.getSession(gameId);

        if (session == null) {
            return Map.of("status", "GAME_NOT_FOUND");
        }

        if (!session.getPlayersByToken().containsKey(token)) {
            return Map.of("status", "INVALID_PLAYER");
        }

        if (!session.isStarted()) {
            return Map.of("status", "GAME_NOT_STARTED");
        }

        if (session.isCompleted()) {
            return Map.of("status", "GAME_ALREADY_COMPLETED");
        }

        try {
            Character character = Character.valueOf(request.character());
            Weapon weapon = Weapon.valueOf(request.weapon());
            Room room = Room.valueOf(request.room());

            Card.Solution solution = session.getSolution();

            boolean correct =
                solution.character() == character &&
                solution.weapon() == weapon &&
                solution.room() == room;

            Player currentPlayer = session.getPlayersByToken().get(token);

            if (correct) {

                // Mark game as completed
                session.completeGame();

                // Notify everyone that the game is over
                WebSocketMessage message = new WebSocketMessage(
                    BroadcastMessageType.B14_GAME_OVER,
                    Map.of(
                        "gameId", gameId,
                        "status", "COMPLETED",
                        "winnerPlayerId", currentPlayer.playerId()
                    )
                );

                for (String playerToken : session.getPlayersByToken().keySet()) {
                    try {
                        webSocketHandler.sendToPlayer(playerToken, message);
                    } catch (IOException e) {
                        System.err.println("Failed to notify player: " + e.getMessage());
                    }
                }

                return Map.of("status", "CORRECT");
            }

            // Incorrect accusation: privately notify the player
            //TODO: most likely nuke this user from the game
            webSocketHandler.sendToPlayer(
                token,
                new WebSocketMessage(
                    BroadcastMessageType.B20_INCORRECT_ACCUSATION,
                    Map.of("status", "INCORRECT")
                )
            );

            // Move to the next player
            Player nextPlayer = session.nextTurn(currentPlayer.playerId());

            // Notify everyone whose turn it is
            WebSocketMessage turnMessage = new WebSocketMessage(
                BroadcastMessageType.B5_TURN_CHANGED,
                new MessagePayloads.TurnChangedPayload(
                    nextPlayer.playerId(),
                    nextPlayer.character().getName()
                )
            );

            for (String playerToken : session.getPlayersByToken().keySet()) {
                try {
                    webSocketHandler.sendToPlayer(playerToken, turnMessage);
                } catch (IOException e) {
                    System.err.println("Failed to send turn notification: " + e.getMessage());
                }
            }

            return Map.of("status", "INCORRECT");

        } catch (IllegalArgumentException | NullPointerException e) {
            return Map.of("status", "INVALID_GUESS");
        } catch (IOException e) {
            return Map.of("status", "NOTIFICATION_FAILED");
        }
    }
}
