package org.PFWs.PythonE.python;

import org.bukkit.Bukkit;
import org.graalvm.polyglot.Value;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PySchedulerBridge {

    private final PyEngine engine;
    private final Map<Integer, PyPlugin> tasksById = new ConcurrentHashMap<>();

    PySchedulerBridge(PyEngine engine) {
        this.engine = engine;
    }

    long schedule(long delayTicks, Value fn, PyPlugin owner) {
        int[] holder = new int[1];
        org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskLater(engine.plugin(), () -> {
            tasksById.remove(holder[0]);
            engine.withPlugin(owner, () -> engine.invoke(fn));
        }, Math.max(1, delayTicks));
        holder[0] = task.getTaskId();
        track(task.getTaskId(), owner);
        return task.getTaskId();
    }

    long scheduleRepeating(long delayTicks, long periodTicks, Value fn, PyPlugin owner) {
        org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskTimer(engine.plugin(), () ->
                engine.withPlugin(owner, () -> engine.invoke(fn)), Math.max(1, delayTicks), Math.max(1, periodTicks));
        track(task.getTaskId(), owner);
        return task.getTaskId();
    }

    void cancel(long taskId) {
        Bukkit.getScheduler().cancelTask((int) taskId);
        PyPlugin owner = tasksById.remove((int) taskId);
        if (owner != null) {
            owner.removeTask((int) taskId);
        }
    }

    void cancelAll(PyPlugin owner) {
        for (Integer taskId : new HashSet<>(owner.taskIds())) {
            Bukkit.getScheduler().cancelTask(taskId);
            tasksById.remove(taskId);
        }
        owner.clearTasks();
    }

    void shutdownAll() {
        for (Integer taskId : new HashSet<>(tasksById.keySet())) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        tasksById.clear();
    }

    private void track(int taskId, PyPlugin owner) {
        if (owner == null) {
            return;
        }
        tasksById.put(taskId, owner);
        owner.addTask(taskId);
    }
}
