package jhu.clueless.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Suggestion and Accusation component (SRS 2.3.2).
 * Skeletal stub: compares an accusation with the stub case file. Suggestions and the
 * disprove cycle are added in the Minimal increment.
 */
@Component
public class SuggestionAccusationManager {

    private static final Logger log = LoggerFactory.getLogger("SuggestionAccusation");

    private final GameStateManager state;

    public SuggestionAccusationManager(GameStateManager state) {
        this.state = state;
    }

    public boolean checkAccusation(String gameId, String suspect, String weapon, String room) {
        var caseFile = state.caseFile(gameId);
        boolean correct = caseFile.character().name().equals(suspect)
            && caseFile.weapon().name().equals(weapon)
            && caseFile.room().name().equals(room);
        log.info("checkAccusation({}, {}, {}, {}) -> {}", gameId, suspect, weapon, room, correct);
        return correct;
    }
}
