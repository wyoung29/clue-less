
package jhu.clueless.game.model;

import java.util.List;

public record Player(
    String playerId,
    Character character,
    List<Card> hand
) {}
