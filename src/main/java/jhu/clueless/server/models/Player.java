
package jhu.clueless.server.models;

import java.util.List;

public record Player(
    String playerId,
    Character character,
    List<Card> hand
) {}
