package org.PFWs.PythonE.python;

import org.bukkit.command.Command;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PyPlugin {

    private final String name;
    private final String moduleName;
    private final Path sourceZip;
    private final Path dir;
    private Value onEnable;
    private Value onDisable;
    private boolean failed;

    private final List<Command> commands = new ArrayList<>();
    private final List<PyEventListener.HandlerEntry> eventRegistrations = new ArrayList<>();
    private final Set<Integer> taskIds = new HashSet<>();

    PyPlugin(String name, String moduleName, Path sourceZip, Path dir, Value onEnable, Value onDisable) {
        this.name = name;
        this.moduleName = moduleName;
        this.sourceZip = sourceZip;
        this.dir = dir;
        this.onEnable = onEnable;
        this.onDisable = onDisable;
    }

    public String name() {
        return name;
    }

    public String moduleName() {
        return moduleName;
    }

    public Path sourceZip() {
        return sourceZip;
    }

    public Path dir() {
        return dir;
    }

    public Value onEnable() {
        return onEnable;
    }

    public Value onDisable() {
        return onDisable;
    }

    void setHooks(Value onEnable, Value onDisable) {
        this.onEnable = onEnable;
        this.onDisable = onDisable;
    }

    public boolean failed() {
        return failed;
    }

    void markFailed() {
        this.failed = true;
    }

    public List<Command> commands() {
        return commands;
    }

    void addCommand(Command command) {
        commands.add(command);
    }

    public List<PyEventListener.HandlerEntry> eventRegistrations() {
        return eventRegistrations;
    }

    void addEventRegistration(PyEventListener.HandlerEntry entry) {
        eventRegistrations.add(entry);
    }

    public Set<Integer> taskIds() {
        return taskIds;
    }

    void addTask(int taskId) {
        taskIds.add(taskId);
    }

    void removeTask(int taskId) {
        taskIds.remove(taskId);
    }

    void clearTasks() {
        taskIds.clear();
    }
}
