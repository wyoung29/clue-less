
package jhu.clueless.controller;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jhu.clueless.server.MatchmakingService;
import jhu.clueless.messaging.BroadcastMessageType;
import jhu.clueless.server.GameSession;
import jhu.clueless.server.GameSessionManager;
import jhu.clueless.server.GameState;
import jhu.clueless.server.MatchmakingService.QueueEntry;
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

        return response;
    }

    @PostMapping("/api/matchmaking/{gameId}/start")
    public GameState startGame(
        @PathVariable String gameId,
        @RequestHeader("X-Player-Token") String token
    ) {
        GameState gameState = sessions.startGame(gameId, token);

        GameSession session = sessions.getSession(gameId);

        WebSocketMessage message = new WebSocketMessage(
            BroadcastMessageType.B4_GAME_STARTED,
            gameState
        );

        for (String playerToken : session.getPlayersByToken().keySet()) {
            try {
                webSocketHandler.sendToPlayer(playerToken, message);
            } catch (IOException e) {
                System.err.println("Failed to notify player: " + e.getMessage());
            }
        }

        return gameState;
    }
}
