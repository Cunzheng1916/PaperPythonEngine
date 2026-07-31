package org.PFWs.PythonE.python;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PyEngine {

    private final JavaPlugin plugin;
    private final Path engineDir;
    private final Path pluginsDir;
    private final Path wheelsDir;
    private final Path cacheDir;

    private Context context;
    private PyPlugin currentPlugin;
    private final Map<String, PyPlugin> plugins = new LinkedHashMap<>();
    private final Map<String, Value> wheels = new LinkedHashMap<>();
    private final Set<Class<? extends Event>> registeredEventClasses = new HashSet<>();
    private final Set<String> usedModuleNames = new HashSet<>();

    private final PyEventListener listener = new PyEventListener(this);
    private final PySchedulerBridge scheduler = new PySchedulerBridge(this);
    private final PyCommandRegistry commands = new PyCommandRegistry(this);
    private final PyBridge bridge = new PyBridge(this);

    public PyEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.engineDir = plugin.getDataFolder().toPath();
        this.pluginsDir = engineDir.resolve("PythonPlugin");
        this.wheelsDir = engineDir.resolve("PythonEngine_ex");
        this.cacheDir = engineDir.resolve("cache");
    }

    public void start() {
        try {
            Files.createDirectories(pluginsDir);
            Files.createDirectories(wheelsDir);
            Files.createDirectories(cacheDir);
            writePpeModule();
        } catch (IOException e) {
            logError("Failed to prepare directories", e);
        }
        reload();
    }

    public void shutdown() {
        unloadAll();
        scheduler.shutdownAll();
        closeContext();
    }

    public void reload() {
        unloadAll();
        scheduler.shutdownAll();
        closeContext();
        context = createContext();
        wheels.clear();
        loadWheels();
        loadAll();
    }

    public String listPlugins() {
        StringBuilder sb = new StringBuilder("Python plugins (").append(plugins.size()).append("):");
        for (PyPlugin p : plugins.values()) {
            sb.append("\n- ").append(p.name()).append(p.failed() ? " [FAILED]" : "");
        }
        return sb.toString();
    }

    private void writePpeModule() throws IOException {
        try (InputStream in = plugin.getResource("scripts/ppe.py")) {
            if (in == null) {
                throw new IOException("scripts/ppe.py resource not found");
            }
            Files.copy(in, engineDir.resolve("ppe.py"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Context createContext() {
        Context ctx = Context.newBuilder("python")
                .allowAllAccess(true)
                .option("python.PosixModuleBackend", "java")
                .option("python.EmulateJython", "true")
                .option("python.NoAsyncActions", "true")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        ctx.getBindings("python").putMember("_ppe_bridge", bridge);
        eval(ctx, "import sys\n"
                + "sys.path.insert(0, " + pyStr(engineDir.toString()) + ")\n"
                + "sys.path.insert(0, " + pyStr(pluginsDir.toString()) + ")");
        return ctx;
    }

    private void closeContext() {
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                logError("Failed to close Python context", e);
            }
            context = null;
        }
    }

    // ---------- 轮子（PythonEngine_ex，只作为库被 ppe.require 导入） ----------

    private void loadWheels() {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> stream = Files.list(wheelsDir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                if (name.startsWith(".") || name.equals("__pycache__")) {
                    return false;
                }
                if (Files.isDirectory(p)) {
                    return false;
                }
                String lower = name.toLowerCase();
                return lower.endsWith(".py") || lower.endsWith(".zip");
            }).sorted().forEach(sources::add);
        } catch (IOException e) {
            logError("Failed to list " + wheelsDir, e);
            return;
        }
        for (Path source : sources) {
            loadWheel(source);
        }
        logInfo("Loaded " + wheels.size() + " python wheel(s): " + String.join(", ", wheels.keySet()));
    }

    private void loadWheel(Path source) {
        String fileName = source.getFileName().toString();
        String lower = fileName.toLowerCase();
        String name;
        Path dir;
        Path entry;
        if (lower.endsWith(".zip")) {
            name = fileName.substring(0, fileName.length() - 4);
            dir = cacheDir.resolve("wheels").resolve(name);
            try {
                Files.createDirectories(dir);
                extractZip(source, dir);
            } catch (IOException e) {
                logError("Failed to extract wheel " + name, e);
                return;
            }
            Path byName = dir.resolve(name + ".py");
            Path init = dir.resolve("__init__.py");
            if (Files.isRegularFile(byName)) {
                entry = byName;
            } else if (Files.isRegularFile(init)) {
                entry = init;
            } else {
                logError("Wheel " + name + " (zip) must contain " + name + ".py or __init__.py at its root");
                return;
            }
        } else {
            name = fileName.substring(0, fileName.length() - 3);
            dir = source.getParent();
            entry = source;
        }
        String moduleName = sanitizeName(name, "wheel");
        try {
            eval(context, "import sys\nsys.path.insert(0, " + pyStr(dir.toString()) + ")");
            String code = "import importlib.util, sys\n"
                    + "spec = importlib.util.spec_from_file_location(" + pyStr(moduleName) + ", " + pyStr(entry.toString()) + ")\n"
                    + "mod = importlib.util.module_from_spec(spec)\n"
                    + "sys.modules[" + pyStr(moduleName) + "] = mod\n"
                    + "spec.loader.exec_module(mod)\n"
                    + "sys.path.remove(" + pyStr(dir.toString()) + ")";
            context.eval("python", code);
            Value module = context.eval("python", "sys.modules.pop(" + pyStr(moduleName) + ", None)");
            purgeSubmodules(moduleName, dir, true);
            wheels.put(name, module);
            logInfo("Loaded wheel " + name);
        } catch (PolyglotException e) {
            logError("Failed to load wheel " + name, e);
        }
    }

    Value require(String name) {
        if (name == null) {
            return null;
        }
        Value wheel = wheels.get(name);
        if (wheel != null) {
            return wheel;
        }
        PyPlugin p = plugins.get(name);
        if (p == null) {
            for (PyPlugin x : plugins.values()) {
                if (x.moduleName().equals(name)) {
                    p = x;
                    break;
                }
            }
        }
        return p != null ? p.module() : null;
    }

    java.util.List<String> wheelNames() {
        return new ArrayList<>(wheels.keySet());
    }

    java.util.List<String> pluginNames() {
        List<String> names = new ArrayList<>();
        for (PyPlugin p : plugins.values()) {
            names.add(p.name());
        }
        return names;
    }

    private void loadAll() {
        Map<String, Path> sourcesByName = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                if (name.startsWith(".") || name.equals("__pycache__")) {
                    return false;
                }
                if (Files.isDirectory(p)) {
                    return true;
                }
                return Files.isRegularFile(p) && name.toLowerCase().endsWith(".zip");
            }).sorted().forEach(p -> sourcesByName.put(pluginNameOf(p), p));
        } catch (IOException e) {
            logError("Failed to list " + pluginsDir, e);
            return;
        }
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : sourcesByName.entrySet()) {
            dependencies.put(entry.getKey(), readDependencies(entry.getValue()));
        }
        for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
            for (String dep : entry.getValue()) {
                if (!sourcesByName.containsKey(dep) && !wheels.containsKey(dep)) {
                    logWarn("Plugin '" + entry.getKey() + "' depends on missing wheel/plugin '" + dep + "'");
                }
            }
        }
        List<String> order = dependencyOrder(sourcesByName.keySet(), dependencies);
        for (String name : order) {
            loadPlugin(sourcesByName.get(name));
        }
        logInfo("Loaded " + plugins.size() + " python plugin(s)");
    }

    private static String pluginNameOf(Path source) {
        String name = source.getFileName().toString();
        if (Files.isRegularFile(source) && name.toLowerCase().endsWith(".zip")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private List<String> readDependencies(Path source) {
        List<String> deps = new ArrayList<>();
        try {
            Path entry = source.resolve("plugin.py");
            if (Files.isRegularFile(source) && source.getFileName().toString().toLowerCase().endsWith(".zip")) {
                return deps;
            }
            if (!Files.isRegularFile(entry)) {
                return deps;
            }
            String text = new String(Files.readAllBytes(entry), StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?m)^\\s*dependencies\\s*=\\s*\\[([^\\]]*)\\]")
                    .matcher(text);
            if (m.find()) {
                java.util.regex.Matcher sm = java.util.regex.Pattern
                        .compile("\"([^\"]*)\"|'([^']*)'")
                        .matcher(m.group(1));
                while (sm.find()) {
                    String dep = sm.group(1) != null ? sm.group(1) : sm.group(2);
                    if (dep != null && !dep.isEmpty()) {
                        deps.add(dep);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return deps;
    }

    private static List<String> dependencyOrder(Set<String> names, Map<String, List<String>> dependencies) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (String name : names) {
            inDegree.put(name, 0);
            outgoing.put(name, new ArrayList<>());
        }
        for (String name : names) {
            for (String dep : dependencies.getOrDefault(name, List.of())) {
                if (names.contains(dep)) {
                    outgoing.get(dep).add(name);
                    inDegree.put(name, inDegree.get(name) + 1);
                }
            }
        }
        java.util.PriorityQueue<String> ready = new java.util.PriorityQueue<>();
        for (String name : names) {
            if (inDegree.get(name) == 0) {
                ready.add(name);
            }
        }
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String current = ready.poll();
            order.add(current);
            for (String next : outgoing.get(current)) {
                int deg = inDegree.get(next) - 1;
                inDegree.put(next, deg);
                if (deg == 0) {
                    ready.add(next);
                }
            }
        }
        if (order.size() < names.size()) {
            List<String> rest = new ArrayList<>(names);
            rest.removeAll(order);
            Collections.sort(rest);
            order.addAll(rest);
        }
        return order;
    }

    private void loadPlugin(Path source) {
        boolean isZip = Files.isRegularFile(source) && source.getFileName().toString().toLowerCase().endsWith(".zip");
        String name;
        Path dest;
        if (isZip) {
            name = source.getFileName().toString();
            name = name.substring(0, name.length() - 4);
            dest = cacheDir.resolve(name);
            try {
                Files.createDirectories(dest);
                extractZip(source, dest);
            } catch (IOException e) {
                logError("Failed to extract " + name, e);
                return;
            }
        } else {
            name = source.getFileName().toString();
            dest = source;
        }
        String moduleName = makeModuleName(name);
        Path entry = dest.resolve("plugin.py");
        if (!Files.isRegularFile(entry)) {
            logError(name + " is missing plugin.py");
            return;
        }
        try {
            eval(context, "import sys\nsys.path.insert(0, " + pyStr(dest.toString()) + ")");
            PyPlugin p = new PyPlugin(name, moduleName, source, dest, null, null);
            currentPlugin = p;
            try {
                String code = "import importlib.util, sys\n"
                        + "spec = importlib.util.spec_from_file_location(" + pyStr(moduleName) + ", " + pyStr(entry.toString()) + ")\n"
                        + "mod = importlib.util.module_from_spec(spec)\n"
                        + "sys.modules[" + pyStr(moduleName) + "] = mod\n"
                        + "spec.loader.exec_module(mod)";
                context.eval("python", code);

                Value module = context.eval("python", "sys.modules[" + pyStr(moduleName) + "]");
                p.setModule(module);
                p.setHooks(
                        module.hasMember("on_enable") ? module.getMember("on_enable") : null,
                        module.hasMember("on_disable") ? module.getMember("on_disable") : null
                );
            } finally {
                currentPlugin = null;
                purgeSubmodules(moduleName, dest, true);
            }

            plugins.put(name, p);
            if (p.onEnable() != null) {
                String pluginName = name;
                withPlugin(p, () -> {
                    try {
                        p.onEnable().execute();
                    } catch (PolyglotException e) {
                        p.markFailed();
                        logError("on_enable failed for " + pluginName, e);
                    }
                });
            }
            logInfo("Loaded " + name);
        } catch (PolyglotException e) {
            logError("Failed to load " + name, e);
        }
    }

    private void purgeSubmodules(String entryModule, Path pluginDir, boolean removeEntry) {
        String code = "import sys, os\n"
                + "_dir = os.path.normcase(os.path.abspath(" + pyStr(pluginDir.toString()) + ")) + os.sep\n"
                + "_entry = " + pyStr(entryModule) + "\n"
                + "for _n, _m in list(sys.modules.items()):\n"
                + "    _f = getattr(_m, '__file__', None)\n"
                + "    if _f:\n"
                + "        _nf = os.path.normcase(os.path.abspath(_f))\n"
                + "        if _nf.startswith(_dir) and (" + (removeEntry ? "True" : "_n != _entry") + "):\n"
                + "            sys.modules.pop(_n, None)";
        try {
            if (context != null) {
                context.eval("python", code);
            }
        } catch (PolyglotException ignored) {
        }
    }

    private void unloadAll() {
        for (PyPlugin p : new ArrayList<>(plugins.values())) {
            unload(p);
        }
        plugins.clear();
        usedModuleNames.clear();
        registeredEventClasses.clear();
    }

    private void unload(PyPlugin p) {
        if (p.onDisable() != null) {
            try {
                p.onDisable().execute();
            } catch (PolyglotException e) {
                logError("on_disable failed for " + p.name(), e);
            }
        }
        commands.unregisterAll(p);
        listener.unregister(p);
        scheduler.cancelAll(p);
        if (context != null) {
            try {
                context.eval("python", "import sys\nsys.modules.pop(" + pyStr(p.moduleName()) + ", None)");
            } catch (PolyglotException ignored) {
            }
        }
        logInfo("Unloaded " + p.name());
    }

    void registerEvent(Class<? extends Event> eventClass, EventPriority priority, Value handler, String ownerModule) {
        PyPlugin owner = currentPlugin != null ? currentPlugin : resolvePlugin(ownerModule);
        listener.register(eventClass, priority, handler, owner);
        if (registeredEventClasses.add(eventClass)) {
            try {
                Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.NORMAL,
                        (l, event) -> listener.dispatch(event), plugin);
            } catch (Throwable t) {
                registeredEventClasses.remove(eventClass);
                logError("Failed to register listener for " + eventClass.getName(), t);
            }
        }
    }

    void registerCommand(String ownerModule, String name, String description, String permission, List<String> aliases, Value handler) {
        commands.register(currentPlugin != null ? currentPlugin : resolvePlugin(ownerModule), name, description, permission, aliases, handler);
    }

    long schedule(long delayTicks, Value handler, String ownerModule) {
        return scheduler.schedule(delayTicks, handler, currentPlugin != null ? currentPlugin : resolvePlugin(ownerModule));
    }

    long scheduleRepeating(long delayTicks, long periodTicks, Value handler, String ownerModule) {
        return scheduler.scheduleRepeating(delayTicks, periodTicks, handler, currentPlugin != null ? currentPlugin : resolvePlugin(ownerModule));
    }

    void cancelTask(long taskId) {
        scheduler.cancel(taskId);
    }

    String dataPath(String moduleName) {
        String name = currentPlugin != null ? currentPlugin.name() : (moduleName == null || moduleName.isEmpty() ? "default" : moduleName);
        Path dir = engineDir.resolve("data").resolve(name);
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir.toString();
    }

    void invoke(Value fn, Object... args) {
        if (fn == null || fn.isNull()) {
            return;
        }
        try {
            fn.execute(args);
        } catch (PolyglotException e) {
            logError("Python callback error", e);
        } catch (Throwable t) {
            logError("Python callback error", t);
        }
    }

    private PyPlugin resolvePlugin(String ownerModule) {
        if (ownerModule == null) {
            return null;
        }
        for (PyPlugin p : plugins.values()) {
            if (p.moduleName().equals(ownerModule)) {
                return p;
            }
        }
        return null;
    }

    private String makeModuleName(String name) {
        String base = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (base.isEmpty()) {
            base = "plugin";
        }
        if (Character.isDigit(base.charAt(0))) {
            base = "_" + base;
        }
        String candidate = base;
        int i = 2;
        while (usedModuleNames.contains(candidate)) {
            candidate = base + "_" + i++;
        }
        usedModuleNames.add(candidate);
        return candidate;
    }

    private static String sanitizeName(String name, String fallback) {
        String base = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (base.isEmpty()) {
            base = fallback;
        }
        if (Character.isDigit(base.charAt(0))) {
            base = "_" + base;
        }
        return base;
    }

    private static void extractZip(Path zip, Path dest) throws IOException {
        if (Files.exists(dest)) {
            try (Stream<Path> stream = Files.walk(dest)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
        Files.createDirectories(dest);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest)) {
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        os.write(buf, 0, n);
                    }
                }
            }
        }
    }

    private static String pyStr(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r") + "'";
    }

    private void eval(Context ctx, String code) {
        try {
            ctx.eval("python", code);
        } catch (PolyglotException e) {
            logError("Python eval error", e);
        }
    }

    void withPlugin(PyPlugin owner, Runnable action) {
        PyPlugin previous = currentPlugin;
        currentPlugin = owner;
        try {
            action.run();
        } finally {
            currentPlugin = previous;
        }
    }

    void logInfo(String message) {
        Bukkit.getConsoleSender().sendMessage(formatLog(currentPlugin, message));
    }

    void logWarn(String message) {
        Bukkit.getConsoleSender().sendMessage(formatLog(currentPlugin, message));
    }

    void logError(String message) {
        Bukkit.getConsoleSender().sendMessage(formatLog(currentPlugin, message));
    }

    void logError(String message, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        Bukkit.getConsoleSender().sendMessage(formatLog(currentPlugin, message));
        for (String line : sw.toString().split("\\r?\\n")) {
            Bukkit.getConsoleSender().sendMessage(line);
        }
    }

    private static String formatLog(PyPlugin owner, String message) {
        return owner != null ? "[FW_PaperPythonEngine][" + owner.name() + "] " + message : "[FW_PaperPythonEngine] " + message;
    }

    JavaPlugin plugin() {
        return plugin;
    }
}
