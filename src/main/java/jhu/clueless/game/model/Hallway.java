package jhu.clueless.game.model;

import java.util.List;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Hallway {
  KITCHEN_BALLROOM(Room.KITCHEN, Room.BALLROOM),
  BALLROOM_CONSERVATORY(Room.BALLROOM, Room.CONSERVATORY),
  DINING_BILLIARD(Room.DINING_ROOM, Room.BILLIARD_ROOM),
  BILLIARD_LIBRARY(Room.BILLIARD_ROOM, Room.LIBRARY),
  LOUNGE_HALL(Room.LOUNGE, Room.HALL),
  HALL_STUDY(Room.HALL, Room.STUDY),
  KITCHEN_DINING(Room.KITCHEN, Room.DINING_ROOM),
  BALLROOM_BILLIARD(Room.BALLROOM, Room.BILLIARD_ROOM),
  CONSERVATORY_LIBRARY(Room.CONSERVATORY, Room.LIBRARY),
  DINING_LOUNGE(Room.DINING_ROOM, Room.LOUNGE),
  BILLIARD_HALL(Room.BILLIARD_ROOM, Room.HALL),
  LIBRARY_STUDY(Room.LIBRARY, Room.STUDY);

  public final Room first;
  public final Room second;

  public boolean connects(Room room) {
    return first == room || second == room;
  }

  public Room otherRoom(Room room) {
    if (first == room)
      return second;
    if (second == room)
      return first;
    throw new IllegalArgumentException("Room is not connected to hallway");
  }

  public List<Room> rooms() {
    return List.of(first, second);
  }
}
