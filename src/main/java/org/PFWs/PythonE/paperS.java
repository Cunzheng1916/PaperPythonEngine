package org.PFWs.PythonE;

import org.PFWs.PythonE.python.PyEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class paperS extends JavaPlugin {

    private PyEngine engine;

    @Override
    public void onEnable() {
        engine = new PyEngine(this);
        engine.start();
        registerControlCommands();
    }

    @Override
    public void onDisable() {
        if (engine != null) {
            engine.shutdown();
            engine = null;
        }
    }

    private void registerControlCommands() {
        Command reload = new Command("pyreload") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                engine.reload();
                sender.sendMessage("Python plugins reloaded.");
                return true;
            }
        };
        reload.setPermission("paperpython.reload");
        getServer().getCommandMap().register("paperpython", reload);

        Command list = new Command("pyplist", "", "/pyplist", java.util.List.of("pylist")) {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                sender.sendMessage(engine.listPlugins());
                return true;
            }
        };
        list.setPermission("paperpython.list");
        getServer().getCommandMap().register("paperpython", list);
    }
}
