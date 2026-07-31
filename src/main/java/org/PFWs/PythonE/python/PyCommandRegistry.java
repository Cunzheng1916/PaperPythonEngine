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
        Command existing = Bukkit.getCommandMap().getCommand(name);
        if (existing != null && existing.getClass().getEnclosingClass() != PyCommandRegistry.class) {
            engine.logWarn("Command '/" + name + "' already exists (not from this engine), skipping");
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
        java.util.Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        known.put(name.toLowerCase(java.util.Locale.ROOT), command);
        for (String alias : aliases) {
            known.put(alias.toLowerCase(java.util.Locale.ROOT), command);
        }
        if (owner != null) {
            owner.addCommand(command);
        }
    }

    void unregisterAll(PyPlugin owner) {
        for (Command command : owner.commands()) {
            command.unregister(Bukkit.getCommandMap());
        }
    }
}
