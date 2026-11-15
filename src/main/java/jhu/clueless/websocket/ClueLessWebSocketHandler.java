
package jhu.clueless.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jhu.clueless.messaging.ClientMessageType;
import jhu.clueless.messaging.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles WebSocket connections for the Clue-Less game.
 */
@Component
public class ClueLessWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, WebSocketSession> connectedPlayers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Sends a welcome message when a client connects to the server.
     *
     * @param session the newly established WebSocket session
     * @throws IOException if sending the message fails
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {

        String query = session.getUri().getQuery();

        if (query != null && query.startsWith("token=")) {
            String token = java.net.URLDecoder.decode(
                query.substring(6),
                java.nio.charset.StandardCharsets.UTF_8
            );

            registerPlayer(token, session);
        }

        sendMessage(session, new WebSocketMessage(
            ClientMessageType.C14_WebSocket_Connection_Open,
            null
        ));
    }

    /**
     * Logs a message when a client disconnects.
     *
     * @param session the WebSocket session that was closed
     * @param status  the reason the connection was closed
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connectedPlayers.values().remove(session);
        System.out.println("Client disconnected");
    }

    public void registerPlayer(String token, WebSocketSession session) {
        connectedPlayers.put(token, session);
    }

    public void sendMessage(WebSocketSession session, WebSocketMessage message)
            throws IOException {

        String json = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(json));
    }

    public void sendToPlayer(String token, WebSocketMessage message)
            throws IOException {

        WebSocketSession session = connectedPlayers.get(token);

        if (session != null && session.isOpen()) {
            sendMessage(session, message);
        }
    }
}