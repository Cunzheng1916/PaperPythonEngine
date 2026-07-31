import sys
import java

try:
    _bridge = sys.modules["__main__"]._ppe_bridge
except AttributeError:
    raise RuntimeError("ppe can only be used inside PaperPythonEngine")


def _owner(fn):
    return getattr(fn, "__module__", "")


# =====================================================================
# 基础
# =====================================================================

def jclass(name):
    return java.type(name)


def info(message):
    _bridge.logInfo(str(message))


def warn(message):
    _bridge.logWarn(str(message))


def error(message):
    _bridge.logError(str(message))


def data_path():
    module = sys._getframe(1).f_globals.get("__name__", "")
    return _bridge.dataPath(module)


# =====================================================================
# 服务端便捷函数
# =====================================================================

def broadcast(message):
    java.type("org.bukkit.Bukkit").broadcastMessage(str(message))


def get_player(name):
    p = java.type("org.bukkit.Bukkit").getPlayer(name)
    return Player(p) if p is not None else None


def online_players():
    return [Player(p) for p in java.type("org.bukkit.Bukkit").getOnlinePlayers()]


def run_command(command):
    return java.type("org.bukkit.Bukkit").dispatchCommand(
        java.type("org.bukkit.Bukkit").getConsoleSender(), command)


def get_entity(world, uuid):
    """按 UUID 在世界中查找实体（原生 Entity 或 None）"""
    return world.getEntity(uuid)


def remove_entity(entity):
    """移除一个实体（怪物、掉落物等）"""
    if entity is not None:
        entity.remove()


def get_killer(entity):
    """获取击杀某实体（怪物/玩家）的玩家；无人击杀返回 None"""
    if entity is None:
        return None
    killer = entity.getKiller()
    return Player(killer) if killer is not None else None


def is_player(obj):
    """判断一个对象是不是玩家"""
    return obj is not None and java.instanceof(obj, java.type("org.bukkit.entity.Player"))


# =====================================================================
# 世界
# =====================================================================

def get_world(name):
    """按名字取世界（原生 World 或 None）"""
    return java.type("org.bukkit.Bukkit").getWorld(name)


def worlds():
    """返回所有世界（原生 World 列表）"""
    return list(java.type("org.bukkit.Bukkit").getWorlds())


# =====================================================================
# 粒子 / 音效
# =====================================================================

def particle(location, name, count=1, ox=0.0, oy=0.0, oz=0.0, extra=0.0):
    """在位置生成粒子。location 可为友好 Location 或原生 Location。
    name 用大写，如 'FLAME'；ox/oy/oz 为散布偏移，extra 为速度/额外参数。"""
    from org.bukkit import Particle
    raw = location.raw if isinstance(location, Location) else location
    p = Particle.valueOf(str(name).upper())
    raw.getWorld().spawnParticle(p, raw, int(count), float(ox), float(oy), float(oz), float(extra))


def play_sound(who, sound, volume=1.0, pitch=1.0):
    """播放音效：传玩家播放给他，传位置则全服在该位置播放。sound 用大写，如 'ENTITY_PLAYER_LEVELUP'。"""
    from org.bukkit import Sound
    s = Sound.valueOf(str(sound).upper())
    if isinstance(who, Player):
        raw = who.location().raw
    else:
        raw = who.raw if isinstance(who, Location) else who
    raw.getWorld().playSound(raw, s, float(volume), float(pitch))


# =====================================================================
# 物品
# =====================================================================

def item(material, name=None, lore=None, amount=1):
    """构造一个 ItemStack。material 用大写，如 'DIAMOND'；可加显示名与说明。"""
    from org.bukkit import Material
    from org.bukkit.inventory import ItemStack
    if isinstance(material, str):
        material = Material.valueOf(material.upper())
    stack = ItemStack(material, int(amount))
    if name or lore:
        meta = stack.getItemMeta()
        if name:
            meta.setDisplayName(str(name))
        if lore:
            from java.util import ArrayList
            lst = ArrayList()
            for line in lore:
                lst.add(str(line))
            meta.setLore(lst)
        stack.setItemMeta(meta)
    return stack


# =====================================================================
# 实体
# =====================================================================

def spawn_entity(location, entity_type):
    """在世界中生成一个实体，返回实体（原生 Entity）。entity_type 用大写，如 'ZOMBIE'。"""
    from org.bukkit import EntityType
    raw = location.raw if isinstance(location, Location) else location
    et = EntityType.valueOf(str(entity_type).upper())
    return raw.getWorld().spawnEntity(raw, et)


# =====================================================================
# 持久数据（PDC）：在玩家/实体/世界等对象上存数据
# =====================================================================

def _pdc_types():
    from org.bukkit.persistence import PersistentDataType
    return {
        bool: PersistentDataType.BOOLEAN,
        int: PersistentDataType.INTEGER,
        float: PersistentDataType.DOUBLE,
        str: PersistentDataType.STRING,
    }


def pdc_set(obj, key, value):
    """往对象（玩家/实体/世界等）上存一个数据（str/int/float/bool）。"""
    from org.bukkit import NamespacedKey
    dt = _pdc_types().get(type(value))
    if dt is None:
        raise TypeError("不支持的 PDC 类型: " + str(type(value)))
    obj.getPersistentDataContainer().set(NamespacedKey("ppe", str(key)), dt, value)


def pdc_has(obj, key):
    """判断对象上是否存在该键的数据。"""
    from org.bukkit import NamespacedKey
    dc = obj.getPersistentDataContainer()
    nsk = NamespacedKey("ppe", str(key))
    for dt in _pdc_types().values():
        if dc.has(nsk, dt):
            return True
    return False


def pdc_get(obj, key, default=None):
    """读取对象上的数据；不存在返回 default。"""
    from org.bukkit import NamespacedKey
    dc = obj.getPersistentDataContainer()
    nsk = NamespacedKey("ppe", str(key))
    for dt in _pdc_types().values():
        if dc.has(nsk, dt):
            return dc.get(nsk, dt)
    return default


# =====================================================================
# 轮子 / 附属插件（PythonEngine_ex 中的扩展库，或另一个插件）
# =====================================================================

def require(name):
    """导入一个轮子（PythonEngine_ex 中的 .py/.zip）或另一个插件。
    外部模块只能通过本函数导入，不要直接 import。"""
    module = _bridge.require(name)
    if module is None:
        raise ImportError("unknown wheel or plugin: " + name)
    return module


def wheels():
    """返回已加载的轮子名列表。"""
    return _bridge.wheels()


def plugins():
    """返回已加载的插件名列表。"""
    return _bridge.plugins()


# =====================================================================
# 友好包装类（snake_case，另可用 .raw 访问原生 Bukkit 对象）
# =====================================================================

class Sender:
    """命令发送者（控制台 / 命令方块）"""

    def __init__(self, raw):
        self.raw = raw

    def __getattr__(self, name):
        return getattr(self.raw, name)

    def send(self, message):
        self.raw.sendMessage(str(message))

    def send_message(self, message):
        self.raw.sendMessage(str(message))

    def name(self):
        return self.raw.getName()

    def has_permission(self, node):
        return self.raw.hasPermission(node)

    def is_player(self):
        return False


class Player(Sender):
    """在线玩家"""

    def is_player(self):
        return True

    def is_online(self):
        return self.raw.isOnline()

    def is_op(self):
        return self.raw.isOp()

    def health(self):
        return self.raw.getHealth()

    def max_health(self):
        return self.raw.getMaxHealth()

    def set_health(self, value):
        self.raw.setHealth(value)

    def heal(self):
        self.raw.setHealth(self.raw.getMaxHealth())

    def food_level(self):
        return self.raw.getFoodLevel()

    def feed(self):
        self.raw.setFoodLevel(20)

    def fly(self, on=True):
        self.raw.setAllowFlight(on)

    def allow_flight(self):
        return self.raw.getAllowFlight()

    def is_flying(self):
        return self.raw.isFlying()

    def set_flying(self, on):
        self.raw.setFlying(on)

    def kick(self, reason="Kicked from server"):
        self.raw.kickPlayer(reason)

    def location(self):
        return Location(self.raw.getLocation())

    def teleport(self, x, y=None, z=None, world=None):
        if y is None:
            target = x.raw if isinstance(x, Location) else x
            self.raw.teleport(target)
            return
        w = world or self.raw.getWorld()
        from org.bukkit import Location as JLocation
        self.raw.teleport(JLocation(w, float(x), float(y), float(z)))

    def give(self, material, amount=1):
        from org.bukkit import Material
        from org.bukkit.inventory import ItemStack
        if isinstance(material, str):
            try:
                material = Material.valueOf(material.upper())
            except Exception:
                raise ValueError("unknown material: " + material)
        self.raw.getInventory().addItem(ItemStack(material, amount))

    def clear_inventory(self):
        self.raw.getInventory().clear()

    def send_title(self, title, subtitle="", fade_in=10, stay=70, fade_out=20):
        self.raw.sendTitle(str(title), str(subtitle), int(fade_in), int(stay), int(fade_out))

    def send_actionbar(self, message):
        self.raw.sendActionBar(str(message))

    def game_mode(self):
        return self.raw.getGameMode().name()

    def set_game_mode(self, mode):
        from org.bukkit import GameMode
        if isinstance(mode, str):
            mode = GameMode.valueOf(mode.upper())
        self.raw.setGameMode(mode)


class Location:
    """三维坐标"""

    def __init__(self, raw):
        self.raw = raw

    def __getattr__(self, name):
        return getattr(self.raw, name)

    @property
    def x(self):
        return self.raw.getX()

    @property
    def y(self):
        return self.raw.getY()

    @property
    def z(self):
        return self.raw.getZ()

    @property
    def world(self):
        return self.raw.getWorld()

    def __str__(self):
        w = self.world
        return "Location(x={}, y={}, z={}, world={})".format(
            round(self.x, 1), round(self.y, 1), round(self.z, 1),
            w.getName() if w else None)


class Block:
    """方块"""

    def __init__(self, raw):
        self.raw = raw

    def __getattr__(self, name):
        return getattr(self.raw, name)

    def type_name(self):
        return self.raw.getType().name()

    def location(self):
        return Location(self.raw.getLocation())


def wrap_sender(sender):
    if java.instanceof(sender, java.type("org.bukkit.entity.Player")):
        return Player(sender)
    return Sender(sender)


# =====================================================================
# 命令（新手友好：不需要装饰器）
# =====================================================================

def register_command(func, name=None, description="", permission=None, aliases=(), tab_complete=None):
    def wrapper(sender, args):
        return func(wrap_sender(sender), list(args))

    def tab_wrapper(sender, args):
        if tab_complete is None:
            return None
        return list(tab_complete(wrap_sender(sender), list(args)))

    _bridge.registerCommand(name or func.__name__, description, permission, aliases, wrapper, _owner(func), tab_wrapper)
    return func


# =====================================================================
# 事件（新手友好：事件名 + 纯函数，返回 False 可取消事件）
# =====================================================================

_EVENT_DEFS = {
    "player_join": ("org.bukkit.event.player.PlayerJoinEvent", False),
    "player_quit": ("org.bukkit.event.player.PlayerQuitEvent", False),
    "player_chat": ("org.bukkit.event.player.AsyncPlayerChatEvent", True),
    "player_command": ("org.bukkit.event.player.PlayerCommandPreprocessEvent", True),
    "player_move": ("org.bukkit.event.player.PlayerMoveEvent", True),
    "player_death": ("org.bukkit.event.entity.PlayerDeathEvent", False),
    "player_damage": ("org.bukkit.event.entity.EntityDamageEvent", True),
    "player_respawn": ("org.bukkit.event.player.PlayerRespawnEvent", False),
    "block_break": ("org.bukkit.event.block.BlockBreakEvent", True),
    "block_place": ("org.bukkit.event.block.BlockPlaceEvent", True),
}


def register_event(event_name, func, priority="NORMAL"):
    if event_name not in _EVENT_DEFS:
        raise ValueError("unknown event name: " + event_name + ". Available: " + ", ".join(sorted(_EVENT_DEFS)))
    event_class = java.type(_EVENT_DEFS[event_name][0])

    def adapter(event):
        if event_name == "player_join":
            result = func(wrap_sender(event.getPlayer()))
        elif event_name == "player_quit":
            result = func(wrap_sender(event.getPlayer()))
        elif event_name == "player_chat":
            result = func(wrap_sender(event.getPlayer()), event.getMessage())
        elif event_name == "player_command":
            result = func(wrap_sender(event.getPlayer()), event.getMessage())
        elif event_name == "player_move":
            to = event.getTo()
            result = func(wrap_sender(event.getPlayer()), Location(event.getFrom()),
                          Location(to) if to is not None else None)
        elif event_name == "player_death":
            killer = event.getKiller()
            result = func(wrap_sender(event.getEntity()),
                          wrap_sender(killer) if killer is not None else None,
                          event.getDeathMessage())
        elif event_name == "player_damage":
            result = func(wrap_sender(event.getEntity()), event.getCause().name(), event.getDamage())
        elif event_name == "player_respawn":
            result = func(wrap_sender(event.getPlayer()), Location(event.getRespawnLocation()))
        elif event_name == "block_break":
            block = event.getBlock()
            result = func(wrap_sender(event.getPlayer()), Block(block), Location(block.getLocation()))
        elif event_name == "block_place":
            block = event.getBlock()
            result = func(wrap_sender(event.getPlayer()), Block(block), Location(block.getLocation()))
        else:
            result = func(event)
        if result is False:
            event.setCancelled(True)

    _bridge.registerEvent(event_class, priority, adapter, _owner(func))
    return func


# =====================================================================
# 定时任务（新手友好：以秒为单位，无需 lambda）
# =====================================================================

def after(seconds, func):
    return _bridge.schedule(max(1, int(seconds * 20)), func, _owner(func))


def every(seconds, func):
    delay = max(1, int(seconds * 20))
    return _bridge.scheduleRepeating(delay, delay, func, _owner(func))


def cancel(task_id):
    _bridge.cancelTask(task_id)


# =====================================================================
# 进阶：装饰器 + tick 单位（老式风格，仍可用）
# =====================================================================

def command(name=None, description="", permission=None, aliases=(), tab_complete=None):
    if callable(name):
        _bridge.registerCommand(name.__name__, "", None, (), name, _owner(name), None)
        return name

    def deco(fn):
        def tab_wrapper(sender, args):
            if tab_complete is None:
                return None
            return list(tab_complete(sender, list(args)))

        _bridge.registerCommand(name or fn.__name__, description, permission, aliases, fn, _owner(fn), tab_wrapper)
        return fn

    return deco


def on_event(event_class, priority="NORMAL"):
    def deco(fn):
        _bridge.registerEvent(event_class, priority, fn, _owner(fn))
        return fn

    return deco


def schedule(delay_ticks, fn):
    return _bridge.schedule(delay_ticks, fn, _owner(fn))


def schedule_repeating(delay_ticks, period_ticks, fn):
    return _bridge.scheduleRepeating(delay_ticks, period_ticks, fn, _owner(fn))


def cancel_task(task_id):
    _bridge.cancelTask(task_id)
