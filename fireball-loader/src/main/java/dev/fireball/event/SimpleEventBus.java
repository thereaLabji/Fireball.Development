package dev.fireball.event;

import dev.fireball.api.event.Event;
import dev.fireball.api.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe event bus implementation.
 */
public final class SimpleEventBus implements EventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleEventBus.class);

    private final Map<Class<? extends Event>, List<Consumer<? extends Event>>> listeners = new ConcurrentHashMap<>();

    @Override
    public <T extends Event> void register(Class<T> eventType, Consumer<T> listener) {
        if (eventType == null || listener == null) {
            throw new IllegalArgumentException("eventType and listener must not be null");
        }
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
        LOGGER.debug("Registered listener for {}", eventType.getSimpleName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> void post(T event) {
        if (event == null) {
            return;
        }
        List<Consumer<? extends Event>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Consumer<? extends Event> consumer : list) {
            try {
                ((Consumer<T>) consumer).accept(event);
            } catch (Exception e) {
                LOGGER.error("Exception while posting event {} to a listener",
                        event.getClass().getSimpleName(), e);
            }
        }
    }

    @Override
    public <T extends Event> void unregister(Class<T> eventType, Consumer<T> listener) {
        if (eventType == null || listener == null) {
            return;
        }
        List<Consumer<? extends Event>> list = listeners.get(eventType);
        if (list != null) {
            list.remove(listener);
        }
    }
}
