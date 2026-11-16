package jhu.clueless.game.model;

public enum Character {

    SCARLET("Miss Scarlet"),
    MUSTARD("Colonel Mustard"),
    WHITE("Mrs. White"),
    GREEN("Mr. Green"),
    PEACOCK("Mrs. Peacock"),
    PLUM("Professor Plum");

    private final String name;

    Character(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}