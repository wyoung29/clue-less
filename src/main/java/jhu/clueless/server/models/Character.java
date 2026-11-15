package jhu.clueless.server.models;

public enum Character {

    SCARLET("Miss Scarlet", Room.HALL),
    MUSTARD("Colonel Mustard", Room.LOUNGE),
    WHITE("Mrs. White", Room.KITCHEN),
    GREEN("Mr. Green", Room.CONSERVATORY),
    PEACOCK("Mrs. Peacock", Room.BILLIARD_ROOM),
    PLUM("Professor Plum", Room.LIBRARY);

    private final String name;
    private final Room location;

    Character(String name, Room location) {
        this.name = name;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public Room getLocation() {
        return location;
    }
}