package org.PFWs.PythonE.python;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.graalvm.polyglot.Value;

import java.util.List;

public final class PyCommandRegistry {

    private final PyEngine engine;

    PyCommandRegistry(PyEngine engine) {
        this.engine = engine;
    }

    void register(PyPlugin owner, String name, String description, String permission, List<String> aliases, Value fn) {
        if (Bukkit.getCommandMap().getCommand(name) != null) {
            engine.logWarn("Command '/" + name + "' is already registered, skipping");
            return;
        }
        Command command = new Command(name, description == null ? "" : description, "/" + name, aliases) {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                engine.withPlugin(owner, () -> engine.invoke(fn, sender, args));
                return true;
            }
        };
        if (permission != null && !permission.isEmpty()) {
            command.setPermission(permission);
        }
        if (Bukkit.getCommandMap().register("paperpython", command) && owner != null) {
            owner.addCommand(command);
        }
    }

    void unregisterAll(PyPlugin owner) {
        for (Command command : owner.commands()) {
            command.unregister(Bukkit.getCommandMap());
        }
    }
}
