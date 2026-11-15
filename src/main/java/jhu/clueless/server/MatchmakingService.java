
package jhu.clueless.server;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import org.springframework.stereotype.Service;

import jhu.clueless.messaging.BroadcastMessageType;
import jhu.clueless.messaging.MessagePayloads;
import jhu.clueless.messaging.WebSocketMessage;
import jhu.clueless.server.models.Player;
import jhu.clueless.websocket.ClueLessWebSocketHandler;

@Service
public class MatchmakingService {

    private static final int MIN_PLAYERS_TO_START = 3;
    private final Queue<QueueEntry> waitingPlayers = new ArrayDeque<>();
    private final GameSessionManager sessions;
    private final ClueLessWebSocketHandler webSocketHandler;

    public MatchmakingService(
        GameSessionManager sessions, 
        ClueLessWebSocketHandler webSocketHandler
    ) {
        this.sessions = sessions;
        this.webSocketHandler = webSocketHandler;
    }

    public synchronized QueueEntry matchMaking() {

        String playerId = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        Player player = new Player(playerId, null);
        GameSession session = sessions.findAvailableSession();

        // allows the first player to start the game
        boolean isFirstInQueue = false;

        if (session == null) {

            Map<String, Player> playersByToken = new LinkedHashMap<>();
            playersByToken.put(token, player);

            session = sessions.createSession(playersByToken, token);

            isFirstInQueue = true;

        } else {

            session.addPlayer(token, player);
        }

        int playerCount = session.getPlayersByToken().size();
        String status = session.getPlayersByToken().size() >= MIN_PLAYERS_TO_START
            ? "READY"
            : "WAITING";

        // let the first player in queue know they can start the game
        if (playerCount == MIN_PLAYERS_TO_START) {
            WebSocketMessage message = new WebSocketMessage(
                BroadcastMessageType.B18_PLAYERS_READY,
                new MessagePayloads.GameReadyPayload(
                    session.getGameId(),
                    playerCount,
                    true
                )
            );

            try {
                webSocketHandler.sendToPlayer(session.getHostToken(), message);
            } catch (IOException e) {
                System.err.println("Failed to notify host: " + e.getMessage());
            }
        }

        return new QueueEntry(
            player,
            token,
            isFirstInQueue,
            session.getGameId(),
            status
        );
    }

    public synchronized int queueSize() {
        return waitingPlayers.size();
    }

    public record QueueEntry(
        Player player,
        String token,
        boolean isFirstInQueue,
        String gameId,
        String status
    ) {}
}
