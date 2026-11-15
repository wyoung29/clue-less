package jhu.clueless.messaging;

public record WebSocketMessage(
    Enum<?> type,
    Object payload
) {}