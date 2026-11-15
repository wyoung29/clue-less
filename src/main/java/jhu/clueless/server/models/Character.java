
package jhu.clueless.server.models;

public enum Character {

    // all characters and starting locations (TODO: rooms for now)
    SCARLET("Miss Scarlet", "HALL"),
    MUSTARD("Colonel Mustard", "LOUNGE"),
    WHITE("Mrs. White", "KITCHEN"),
    GREEN("Mr. Green", "CONSERVATORY"),
    PEACOCK("Mrs. Peacock", "BILLIARD_ROOM"),
    PLUM("Professor Plum", "LIBRARY");

    private final String name;
    private final String location;

    Character(String name, String location) {
        this.name = name;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }
}
