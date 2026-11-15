
package jhu.clueless.server.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public enum Card {

    // Suspects
    SCARLET(CardType.SUSPECT),
    MUSTARD(CardType.SUSPECT),
    WHITE(CardType.SUSPECT),
    GREEN(CardType.SUSPECT),
    PEACOCK(CardType.SUSPECT),
    PLUM(CardType.SUSPECT),

    // Weapons
    CANDLESTICK(CardType.WEAPON),
    KNIFE(CardType.WEAPON),
    LEAD_PIPE(CardType.WEAPON),
    REVOLVER(CardType.WEAPON),
    ROPE(CardType.WEAPON),
    WRENCH(CardType.WEAPON),

    // Rooms
    KITCHEN(CardType.ROOM),
    BALLROOM(CardType.ROOM),
    CONSERVATORY(CardType.ROOM),
    DINING_ROOM(CardType.ROOM),
    BILLIARD_ROOM(CardType.ROOM),
    LIBRARY(CardType.ROOM),
    LOUNGE(CardType.ROOM),
    HALL(CardType.ROOM),
    STUDY(CardType.ROOM);

    private final CardType type;

    Card(CardType type) {
        this.type = type;
    }

    public CardType getType() {
        return type;
    }

    public enum CardType {
        SUSPECT,
        WEAPON,
        ROOM
    }

    public static Card getRandomCard(CardType type) {
        Card[] cards = java.util.Arrays.stream(values())
            .filter(card -> card.getType() == type)
            .toArray(Card[]::new);

        return cards[new Random().nextInt(cards.length)];
    }

    public static Solution createRandomSolution() {
        return new Solution(
            Character.valueOf(getRandomCard(CardType.SUSPECT).name()),
            Weapon.valueOf(getRandomCard(CardType.WEAPON).name()),
            Room.valueOf(getRandomCard(CardType.ROOM).name())
        );
    }

    public record Solution(
        Character character,
        Weapon weapon,
        Room room
    ) {}

    public static List<List<Card>> dealCards(
        int numberOfPlayers,
        Solution solution
    ) {
        List<Card> deck = new ArrayList<>(List.of(values()));

        deck.remove(Card.valueOf(solution.character().name()));
        deck.remove(Card.valueOf(solution.weapon().name()));
        deck.remove(Card.valueOf(solution.room().name()));

        Collections.shuffle(deck);

        List<List<Card>> hands = new ArrayList<>();

        for (int i = 0; i < numberOfPlayers; i++) {
            hands.add(new ArrayList<>());
        }

        for (int i = 0; i < deck.size(); i++) {
            hands.get(i % numberOfPlayers).add(deck.get(i));
        }

        return hands;
    }
}
