# PaperPythonEngine 的 Python API（ppe）
#
# 设计原则：
#   1. Python 惯用化：把 Paper API 转换成 python 常用写法（snake_case、列表、字符串材质名…）
#   2. 只做基础操作：消息 / 命令 / 事件 / 定时 / 玩家 / 世界 / 物品 / 实体 / 粒子音效 / 数据
#      复杂系统（菜单、计分板、BossBar 等）请做成轮子或前置插件
#   3. 对象归一化：所有函数接受"原生或包装"对象，内部统一处理
#   4. 报错清晰：错误信息说明"哪里错了 + 怎么改"
#   5. 兼容：register_* 与 @装饰器、after/schedule 等均保留

import sys
import os
import json
import java

try:
    _bridge = sys.modules["__main__"]._ppe_bridge
except AttributeError:
    raise RuntimeError("ppe 只能在 PaperPythonEngine 中使用")


def _owner(fn):
    return getattr(fn, "__module__", "")


# =====================================================================
# 内部工具
# =====================================================================

def _as_raw(obj):
    """把友好包装对象转成原生 Bukkit 对象；原生对象原样返回。"""
    if isinstance(obj, (Player, Sender, Location, Block)):
        return obj.raw
    return obj


def _enum(value, enum_class, label, example):
    """把字符串转成 Java 枚举，失败时给出清晰报错。"""
    try:
        return enum_class.valueOf(str(value).upper())
    except BaseException:
        raise ValueError("未知" + label + ": '" + str(value) + "'，请用大写名称，如 '" + example + "'")


def _material(value):
    from org.bukkit import Material
    if isinstance(value, Material):
        return value
    try:
        return Material.valueOf(str(value).upper())
    except BaseException:
        raise ValueError("未知物品: '" + str(value) + "'，请用大写材质名，如 'DIAMOND'")


def _safe_filename(name):
    """校验数据文件名，防止目录逃逸。"""
    if not isinstance(name, str) or not name:
        raise ValueError("文件名不合法: '" + str(name) + "'，不能为空")
    if name.startswith(".") or ".." in name or "/" in name or "\\" in name:
        raise ValueError("文件名不合法: '" + name + "'，只允许使用字母/数字/下划线/连字符/点，且不能包含路径")
    return name


# =====================================================================
# 基础
# =====================================================================

def jclass(name):
    """按全限定名取 Java 类，如 jclass("org.bukkit.Bukkit")。"""
    return java.type(name)


def info(message):
    _bridge.logInfo(str(message))


def warn(message):
    _bridge.logWarn(str(message))


def error(message):
    _bridge.logError(str(message))


def data_path():
    """返回当前插件的专属数据目录（自动创建）。
    删除插件不会删除该目录，数据会保留。"""
    module = sys._getframe(1).f_globals.get("__name__", "")
    return _bridge.dataPath(module)


# =====================================================================
# 玩家
# =====================================================================

def get_player(name):
    """按名字取在线玩家，返回 Player 或 None。"""
    p = java.type("org.bukkit.Bukkit").getPlayer(name)
    return Player(p) if p is not None else None


def online_players():
    """返回所有在线玩家（Player 列表）。"""
    return [Player(p) for p in java.type("org.bukkit.Bukkit").getOnlinePlayers()]


def is_player(obj):
    """判断一个对象是不是玩家。"""
    return obj is not None and java.instanceof(_as_raw(obj), java.type("org.bukkit.entity.Player"))


def broadcast(message):
    """全服广播。"""
    java.type("org.bukkit.Bukkit").broadcastMessage(str(message))


def run_command(command):
    """以控制台执行命令，返回是否成功。"""
    return java.type("org.bukkit.Bukkit").dispatchCommand(
        java.type("org.bukkit.Bukkit").getConsoleSender(), command)


# =====================================================================
# 世界
# =====================================================================

def get_world(name):
    """按名字取世界（原生 World 或 None）。"""
    return java.type("org.bukkit.Bukkit").getWorld(name)


def worlds():
    """返回所有世界（原生 World 列表）。"""
    return list(java.type("org.bukkit.Bukkit").getWorlds())


# =====================================================================
# 物品
# =====================================================================

def item(material, name=None, lore=None, amount=1):
    """构造一个 ItemStack。
    material 可用大写字符串（如 'DIAMOND'）或 Material；可加显示名与说明。"""
    from org.bukkit.inventory import ItemStack
    stack = ItemStack(_material(material), int(amount))
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
    """在世界中生成一个实体，返回原生 Entity。
    entity_type 用大写，如 'ZOMBIE'。"""
    from org.bukkit import EntityType
    raw = _as_raw(location)
    et = _enum(entity_type, EntityType, "实体类型", "ZOMBIE")
    return raw.getWorld().spawnEntity(raw, et)


def remove_entity(entity):
    """移除一个实体（怪物、掉落物等）。"""
    if entity is not None:
        _as_raw(entity).remove()


def get_entity(world, uuid):
    """按 UUID 在世界中查找实体（原生 Entity 或 None）。"""
    return _as_raw(world).getEntity(uuid)


def get_killer(entity):
    """获取击杀该实体的玩家；无人击杀返回 None。"""
    if entity is None:
        return None
    killer = _as_raw(entity).getKiller()
    return Player(killer) if killer is not None else None


# =====================================================================
# 粒子 / 音效
# =====================================================================

def particle(location, name, count=1, ox=0.0, oy=0.0, oz=0.0, extra=0.0):
    """在位置生成粒子。
    name 用大写，如 'FLAME'；ox/oy/oz 为散布偏移，extra 为速度/额外参数。"""
    from org.bukkit import Particle
    raw = _as_raw(location)
    p = _enum(name, Particle, "粒子", "FLAME")
    raw.getWorld().spawnParticle(p, raw, int(count), float(ox), float(oy), float(oz), float(extra))


def play_sound(who, sound, volume=1.0, pitch=1.0):
    """播放音效：传玩家播放给他，传位置则全服在该位置播放。
    sound 用大写，如 'ENTITY_PLAYER_LEVELUP'。"""
    from org.bukkit import Sound
    s = _enum(sound, Sound, "音效", "ENTITY_PLAYER_LEVELUP")
    raw = _as_raw(who)
    if hasattr(raw, "getLocation"):
        raw = raw.getLocation()
    raw.getWorld().playSound(raw, s, float(volume), float(pitch))


# =====================================================================
# 数据（只允许保存在当前插件专属目录 data/<插件名>/）
# =====================================================================

def pdc_set(obj, key, value):
    """往对象（玩家/实体/世界等）上存一个数据（str/int/float/bool）。"""
    from org.bukkit import NamespacedKey
    dt = _pdc_types().get(type(value))
    if dt is None:
        raise TypeError("pdc_set() 不支持的类型: " + str(type(value)) + "，仅支持 str/int/float/bool")
    _as_raw(obj).getPersistentDataContainer().set(NamespacedKey("ppe", str(key)), dt, value)


def pdc_has(obj, key):
    """判断对象上是否存在该键的数据。"""
    from org.bukkit import NamespacedKey
    dc = _as_raw(obj).getPersistentDataContainer()
    nsk = NamespacedKey("ppe", str(key))
    for dt in _pdc_types().values():
        if dc.has(nsk, dt):
            return True
    return False


def pdc_get(obj, key, default=None):
    """读取对象上的数据；不存在返回 default。"""
    from org.bukkit import NamespacedKey
    dc = _as_raw(obj).getPersistentDataContainer()
    nsk = NamespacedKey("ppe", str(key))
    for dt in _pdc_types().values():
        if dc.has(nsk, dt):
            return dc.get(nsk, dt)
    return default


def _pdc_types():
    from org.bukkit.persistence import PersistentDataType
    return {
        bool: PersistentDataType.BOOLEAN,
        int: PersistentDataType.INTEGER,
        float: PersistentDataType.DOUBLE,
        str: PersistentDataType.STRING,
    }


def _data_dir():
    return data_path()


def save_json(name, data):
    """把数据保存为 JSON 文件，强制存放在当前插件的 data/ 目录。
    文件名不要带路径或扩展名（自动补 .json）。"""
    path = os.path.join(_data_dir(), _safe_filename(name) + ".json")
    try:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except OSError as e:
        raise OSError("保存数据失败 (" + path + "): " + str(e))


def load_json(name, default=None):
    """读取 JSON 数据文件；不存在返回 default。"""
    path = os.path.join(_data_dir(), _safe_filename(name) + ".json")
    if not os.path.exists(path):
        return default
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        raise ValueError("解析数据文件失败 (" + path + "): " + str(e))


def save_file(name, text):
    """把文本保存为文件（如日志），强制存放在当前插件的 data/ 目录。"""
    path = os.path.join(_data_dir(), _safe_filename(name))
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(str(text))
    except OSError as e:
        raise OSError("保存文件失败 (" + path + "): " + str(e))


def load_file(name, default=None):
    """读取文本文件；不存在返回 default。"""
    path = os.path.join(_data_dir(), _safe_filename(name))
    if not os.path.exists(path):
        return default
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError as e:
        raise OSError("读取文件失败 (" + path + "): " + str(e))


def list_data_files():
    """列出当前插件已保存的数据文件。"""
    directory = _data_dir()
    try:
        return sorted(os.listdir(directory))
    except OSError:
        return []


def clear_data():
    """清空当前插件的数据目录（谨慎使用，删除后不可恢复）。"""
    directory = _data_dir()
    for name in os.listdir(directory):
        p = os.path.join(directory, name)
        try:
            if os.path.isdir(p):
                import shutil
                shutil.rmtree(p)
            else:
                os.remove(p)
        except OSError as e:
            raise OSError("清理数据失败 (" + p + "): " + str(e))


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
        from org.bukkit.inventory import ItemStack
        self.raw.getInventory().addItem(ItemStack(_material(material), int(amount)))

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
    if java.instanceof(_as_raw(sender), java.type("org.bukkit.entity.Player")):
        return Player(_as_raw(sender))
    return Sender(_as_raw(sender))


# =====================================================================
# 命令（新手友好：register_command；进阶：@command 装饰器）
# =====================================================================

def register_command(func, name=None, description="", permission=None, aliases=(), tab_complete=None):
    """注册一个命令。命令名默认取函数名。
    tab_complete：补全函数 (sender, args) -> 建议列表。"""
    def wrapper(sender, args):
        return func(wrap_sender(sender), list(args))

    def tab_wrapper(sender, args):
        if tab_complete is None:
            return None
        return list(tab_complete(wrap_sender(sender), list(args)))

    _bridge.registerCommand(name or func.__name__, description, permission, aliases, wrapper, _owner(func), tab_wrapper)
    return func


def command(name=None, description="", permission=None, aliases=(), tab_complete=None):
    """命令装饰器。用法：
    @command("hello", permission="hello.use", tab_complete=tab_fn)
    def hello(sender, args): ...
    装饰器风格里 sender 是原生 CommandSender，args 是参数列表。"""
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


# =====================================================================
# 事件（新手友好：register_event 事件名；进阶：@on_event 事件类）
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
    """注册一个事件。事件名用友好名，如 "player_join"。
    处理函数返回 False 可取消事件（可取消的事件）。"""
    if event_name not in _EVENT_DEFS:
        raise ValueError("未知事件名: '" + str(event_name) + "'。可用: " + ", ".join(sorted(_EVENT_DEFS)))
    event_class = java.type(_EVENT_DEFS[event_name][0])

    def adapter(event):
        try:
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
        except Exception:
            import traceback
            error("事件 '" + event_name + "' 处理出错:\n" + traceback.format_exc())

    _bridge.registerEvent(event_class, priority, adapter, _owner(func))
    return func


def on_event(event_class, priority="NORMAL"):
    """事件装饰器。用法：
    @on_event(PlayerJoinEvent, priority="HIGH")
    def on_join(event): ...
    装饰器风格里 event 是原生事件对象。"""
    def deco(fn):
        _bridge.registerEvent(event_class, priority, fn, _owner(fn))
        return fn

    return deco


# =====================================================================
# 定时任务（秒为单位；另有 tick 单位版本）
# =====================================================================

def after(seconds, func):
    """延迟 N 秒执行一次，返回任务 ID。"""
    return _bridge.schedule(max(1, int(seconds * 20)), func, _owner(func))


def every(seconds, func):
    """每 N 秒重复执行，返回任务 ID。"""
    delay = max(1, int(seconds * 20))
    return _bridge.scheduleRepeating(delay, delay, func, _owner(func))


def cancel(task_id):
    """取消一个定时任务。"""
    _bridge.cancelTask(task_id)


def schedule(delay_ticks, fn):
    """延迟 N tick 执行一次（1 秒 = 20 tick），返回任务 ID。"""
    return _bridge.schedule(delay_ticks, fn, _owner(fn))


def schedule_repeating(delay_ticks, period_ticks, fn):
    """按 tick 周期重复执行，返回任务 ID。"""
    return _bridge.scheduleRepeating(delay_ticks, period_ticks, fn, _owner(fn))


def cancel_task(task_id):
    """取消一个 tick 单位的定时任务。"""
    _bridge.cancelTask(task_id)


# =====================================================================
# 轮子 / 附属插件（PythonEngine_ex 中的扩展库，或另一个插件）
# =====================================================================

def require(name):
    """导入一个轮子（PythonEngine_ex 中的 .py/.zip）或另一个插件。
    外部模块只能通过本函数导入，不要直接 import。"""
    module = _bridge.require(name)
    if module is None:
        raise ImportError("找不到轮子或插件: '" + str(name) + "'。请确认已放入 PythonEngine_ex/ 或 PythonPlugin/")
    return module


def wheels():
    """返回已加载的轮子名列表。"""
    return _bridge.wheels()


def plugins():
    """返回已加载的插件名列表。"""
    return _bridge.plugins()
