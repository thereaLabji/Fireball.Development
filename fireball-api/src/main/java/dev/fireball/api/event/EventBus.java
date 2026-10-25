package dev.fireball.api.event;

import java.util.function.Consumer;

/**
 * Simple event bus for mod communication.
 */
public interface EventBus {
    /**
     * Register a listener for a specific event type.
     */
    <T extends Event> void register(Class<T> eventType, Consumer<T> listener);

    /**
     * Post an event to all registered listeners.
     */
    <T extends Event> void post(T event);

    /**
     * Unregister a previously registered listener.
     */
    <T extends Event> void unregister(Class<T> eventType, Consumer<T> listener);
}
