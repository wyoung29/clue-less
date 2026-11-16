package jhu.clueless.server;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Game Logic component (SRS 2.3.2).
 * Skeletal stub: every action is accepted. Rule validation is added in the Minimal increment.
 */
@Component
@Slf4j
public class GameLogic {

    public boolean validateAction(String playerId, String type) {
        log.info("validateAction({}, {}) -> true (stub)", playerId, type);
        return true;
    }
}
