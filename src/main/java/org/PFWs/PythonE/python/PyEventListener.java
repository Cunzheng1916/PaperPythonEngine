package org.PFWs.PythonE.python;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PyEventListener implements Listener {

    public record HandlerEntry(Class<? extends Event> eventClass, Value handler, EventPriority priority, PyPlugin owner) {
    }

    private final PyEngine engine;
    private final Map<Class<? extends Event>, List<HandlerEntry>> handlers = new ConcurrentHashMap<>();

    PyEventListener(PyEngine engine) {
        this.engine = engine;
    }

    void register(Class<? extends Event> eventClass, EventPriority priority, Value handler, PyPlugin owner) {
        HandlerEntry entry = new HandlerEntry(eventClass, handler, priority, owner);
        List<HandlerEntry> list = handlers.computeIfAbsent(eventClass, k -> new ArrayList<>());
        synchronized (list) {
            int idx = 0;
            while (idx < list.size() && list.get(idx).priority().compareTo(priority) <= 0) {
                idx++;
            }
            list.add(idx, entry);
        }
        if (owner != null) {
            owner.addEventRegistration(entry);
        }
    }

    void unregister(PyPlugin owner) {
        for (HandlerEntry entry : owner.eventRegistrations()) {
            List<HandlerEntry> list = handlers.get(entry.eventClass());
            if (list != null) {
                synchronized (list) {
                    list.remove(entry);
                }
            }
        }
    }

    void dispatch(Event event) {
        Class<?> type = event.getClass();
        while (type != null && Event.class.isAssignableFrom(type)) {
            List<HandlerEntry> list = handlers.get(type);
            if (list != null) {
                List<HandlerEntry> snapshot;
                synchronized (list) {
                    snapshot = new ArrayList<>(list);
                }
                for (HandlerEntry entry : snapshot) {
                    engine.withPlugin(entry.owner(), () -> engine.invoke(entry.handler(), event));
                }
            }
            type = type.getSuperclass();
        }
    }
}
