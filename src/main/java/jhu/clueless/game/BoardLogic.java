package jhu.clueless.game;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jhu.clueless.game.model.Hallway;
import jhu.clueless.game.model.Location;
import jhu.clueless.game.model.Room;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BoardLogic {

  public record RoomLocation(Room room) implements Location {}
  public record HallwayLocation(Hallway hallway) implements Location {}

  private static final Map<Room, List<Hallway>> EXITS = buildExits();
    private static final Map<Room, Room> PASSAGES = Map.of(
        Room.KITCHEN, Room.STUDY,
        Room.STUDY, Room.KITCHEN,
        Room.CONSERVATORY, Room.LOUNGE,
        Room.LOUNGE, Room.CONSERVATORY
    );

    private static Map<Room, List<Hallway>> buildExits() {
        Map<Room, List<Hallway>> exits = new EnumMap<>(Room.class);
        for (Room room : Room.values()) {
            exits.put(room, new ArrayList<>());
        }

        for (Hallway hallway : Hallway.values()) {
            exits.get(hallway.first).add(hallway);
            exits.get(hallway.second).add(hallway);
        }
        return Map.copyOf(exits);
    }

    public static List<Hallway> exitsFrom(Room room) {
        return EXITS.get(room);
    }

    public static Optional<Room> passageFrom(Room room) {
        return Optional.ofNullable(PASSAGES.get(room));
    }

    public static List<Location> destinationsFrom(
        Location current, Set<Hallway> occupiedHallways) {

        List<Location> destinations = new ArrayList<>();

        if (current instanceof RoomLocation inRoom) {
            for (Hallway hallway : exitsFrom(inRoom.room())) {
                if (!occupiedHallways.contains(hallway)) {
                    destinations.add(new HallwayLocation(hallway));
                }
            }

            passageFrom(inRoom.room())
                .map(RoomLocation::new)
                .ifPresent(destinations::add);
            return List.copyOf(destinations);
        }

        HallwayLocation hallway = (HallwayLocation) current;
        return hallway.hallway().rooms().stream()
            .map(RoomLocation::new)
            .map(Location.class::cast)
            .toList();
    }
}
