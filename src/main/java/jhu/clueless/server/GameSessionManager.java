package jhu.clueless.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jhu.clueless.game.model.Character;
import jhu.clueless.game.model.Card;
import jhu.clueless.game.model.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Game Session Management component (SRS 2.3.2).
 * Skeletal stub: keeps just enough (games, players, tokens) to address messages.
 * Characters are handed out in join order until character selection is built.
 */
@Component
@Slf4j
public class GameSessionManager {

    private static final List<Character> CHARACTERS = List.of(Character.values());

    private final Map<String, List<String>> playersByGame = new ConcurrentHashMap<>();
    private final Map<String, String> playerByToken = new ConcurrentHashMap<>();
    private final Map<String, Player> playersById = new ConcurrentHashMap<>();

    public String createGame() {
        String gameId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        playersByGame.put(gameId, new CopyOnWriteArrayList<>());
        log.info("createGame() -> {}", gameId);
        return gameId;
    }

    /** Returns {playerId, token}. */
    public String[] addPlayer(String gameId, String username) {
        List<String> players = playersByGame.get(gameId);
        String playerId = "P-" + UUID.randomUUID().toString().substring(0, 4);
        String token = UUID.randomUUID().toString();
        Character character = CHARACTERS.get(players.size() % CHARACTERS.size());
        playersById.put(playerId, new Player(playerId, character, List.of()));
        players.add(playerId);
        playerByToken.put(token, playerId);
        log.info("addPlayer({}, {}) -> {} as {}", gameId, username, playerId, character);
        return new String[] {playerId, token};
    }

    public boolean gameExists(String gameId) {
        return playersByGame.containsKey(gameId);
    }

    public String playerForToken(String token) {
        return token == null ? null : playerByToken.get(token);
    }

    public List<String> players(String gameId) {
        return playersByGame.getOrDefault(gameId, List.of());
    }

    public synchronized void dealCards(String gameId, Card.Solution solution) {
        List<String> playerIds = players(gameId);
        if (playerIds.stream().map(playersById::get).anyMatch(player -> !player.hand().isEmpty())) {
            return;
        }

        List<List<Card>> hands = Card.dealCards(playerIds.size(), solution);
        for (int i = 0; i < playerIds.size(); i++) {
            String playerId = playerIds.get(i);
            Player player = playersById.get(playerId);
            playersById.put(playerId, new Player(playerId, player.character(), List.copyOf(hands.get(i))));
        }
    }

    public List<Card> hand(String playerId) {
        Player player = playersById.get(playerId);
        return player == null ? List.of() : player.hand();
    }

    public Character character(String playerId) {
        Player player = playersById.get(playerId);
        return player == null ? null : player.character();
    }
}
