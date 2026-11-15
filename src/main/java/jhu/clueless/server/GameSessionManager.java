
package jhu.clueless.server;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import jhu.clueless.server.models.Player;

@Service
public class GameSessionManager {

    private final Map<String, GameSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    public GameSession createSession(
        Map<String, Player> playersByToken,
        String hostToken
    ) {
        String gameId = UUID.randomUUID().toString();

        GameSession session = new GameSession(
            gameId,
            playersByToken,
            hostToken
        );

        sessions.put(gameId, session);

        return session;
    }

    public GameSession getSession(String gameId) {
        return sessions.get(gameId);
    }

    public GameState startGame(String gameId, String token) {

        GameSession session = sessions.get(gameId);

        if (session == null) {
            throw new IllegalArgumentException("GAME_NOT_FOUND");
        }

        return session.start(token);
    }

    public GameSession findAvailableSession() {
        return sessions.values().stream()
            .filter(session -> session.canAcceptPlayers())
            .findFirst()
            .orElse(null);
    }
}
