
package jhu.clueless.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jhu.clueless.server.MatchmakingService;
import jhu.clueless.server.MatchmakingService.QueueEntry;

@RestController
public class GameController {

    private final MatchmakingService matchmaking;

    public GameController(MatchmakingService matchmaking) {
        this.matchmaking = matchmaking;
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
}
