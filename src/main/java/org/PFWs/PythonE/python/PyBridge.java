package org.PFWs.PythonE.python;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;

public final class PyBridge {

    private final PyEngine engine;

    PyBridge(PyEngine engine) {
        this.engine = engine;
    }

    public void registerEvent(Value eventClass, String priority, Value handler, String ownerModule) {
        try {
            Object host;
            try {
                host = eventClass.asHostObject();
            } catch (Throwable ignored) {
                host = eventClass.as(Class.class);
            }
            if (!(host instanceof Class<?> clazz)) {
                engine.logWarn("register_event: expected a Java class, got something else");
                return;
            }
            if (!Event.class.isAssignableFrom(clazz)) {
                engine.logWarn("register_event: " + clazz.getName() + " is not a Bukkit Event");
                return;
            }
            EventPriority eventPriority = EventPriority.NORMAL;
            if (priority != null) {
                try {
                    eventPriority = EventPriority.valueOf(priority.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                }
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType = (Class<? extends Event>) clazz;
            engine.registerEvent(eventType, eventPriority, handler, ownerModule);
        } catch (Throwable t) {
            engine.logError("Failed to register event handler", t);
        }
    }

    public void registerCommand(String name, String description, String permission, Value aliases, Value handler, String ownerModule) {
        List<String> aliasList = new ArrayList<>();
        if (aliases != null && aliases.hasArrayElements()) {
            for (long i = 0; i < aliases.getArraySize(); i++) {
                Value element = aliases.getArrayElement(i);
                if (element.isString()) {
                    aliasList.add(element.asString());
                }
            }
        }
        engine.registerCommand(ownerModule, name, description, permission, aliasList, handler);
    }

    public long schedule(long delayTicks, Value handler, String ownerModule) {
        return engine.schedule(delayTicks, handler, ownerModule);
    }

    public long scheduleRepeating(long delayTicks, long periodTicks, Value handler, String ownerModule) {
        return engine.scheduleRepeating(delayTicks, periodTicks, handler, ownerModule);
    }

    public void cancelTask(long taskId) {
        engine.cancelTask(taskId);
    }

    public void logInfo(String message) {
        engine.logInfo(message);
    }

    public void logWarn(String message) {
        engine.logWarn(message);
    }

    public void logError(String message) {
        engine.logError(message);
    }

    public String dataPath(String moduleName) {
        return engine.dataPath(moduleName);
    }

    public Value require(String name) {
        return engine.require(name);
    }

    public java.util.List<String> wheels() {
        return engine.wheelNames();
    }

    public java.util.List<String> plugins() {
        return engine.pluginNames();
    }
}
