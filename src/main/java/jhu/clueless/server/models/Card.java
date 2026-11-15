package jhu.clueless.server.models;

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
}