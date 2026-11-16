package jhu.clueless.server;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jhu.clueless.game.model.Card;
import jhu.clueless.game.model.Character;
import jhu.clueless.game.model.Room;
import jhu.clueless.game.model.Weapon;

/**
 * Suggestion and Accusation component (SRS 2.3.2).
 * Compares accusations with the case file and finds cards that can disprove suggestions.
 */
@Component
public class SuggestionAccusationManager {

    private static final Logger log = LoggerFactory.getLogger("SuggestionAccusation");

    private final GameStateManager state;

    public SuggestionAccusationManager(GameStateManager state) {
        this.state = state;
    }

    public boolean checkAccusation(String gameId, String suspect, String weapon, String room) {
        Character character = Character.valueOf(suspect);
        Weapon weaponCard = Weapon.valueOf(weapon);
        Room roomCard = Room.valueOf(room);
        var caseFile = state.caseFile(gameId);
        boolean correct = caseFile.character() == character
            && caseFile.weapon() == weaponCard
            && caseFile.room() == roomCard;
        log.info("checkAccusation({}, {}, {}, {}) -> {}", gameId, suspect, weapon, room, correct);
        return correct;
    }

    public List<Card> matchingCards(List<Card> hand, String suspect, String weapon, String room) {
        Card suspectCard = Card.valueOf(Character.valueOf(suspect).name());
        Card weaponCard = Card.valueOf(Weapon.valueOf(weapon).name());
        Card roomCard = Card.valueOf(Room.valueOf(room).name());
        return hand.stream()
            .filter(card -> card == suspectCard || card == weaponCard || card == roomCard)
            .toList();
    }
}
