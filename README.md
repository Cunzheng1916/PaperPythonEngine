# PaperPythonEngine

一个基于 **Paper** 的 Python 插件引擎。服务端管理员只需把引擎 jar 放入插件文件夹，再把 Python 插件（`.zip` 或文件夹）丢进指定目录即可运行，无需安装任何额外组件。

内置 **GraalPy**（Python 3.12），打包进单个 jar，离线可用。Python 插件可以直接访问完整的 Bukkit/Paper API。

引擎提供**两套 API**：
- **新手友好 API**（推荐初学者）：纯 Python 惯用写法，无装饰器 `@`、无 lambda、无 Java 导入，`register_*` 函数注册命令和事件。
- **进阶 API**：装饰器 + 直接调用 Bukkit Java 类，适合熟悉 Java 或需要底层控制的高级用法。

两套 API 完全兼容，可混用。

同时支持**轮子（扩展库）**：把公共代码抽成 `.py`/`.zip` 放进 `PythonEngine_ex/`，插件用 `ppe.require()` 复用，代码与插件彻底分离。详见 [扩展与复用](#扩展与复用轮子--附属插件)；服主部署见 [服主 / 管理员使用指南](#服主--管理员使用指南)，引擎开发见 [开发本插件（引擎）](#开发本插件引擎)。

---

## 目录

- [快速开始](#快速开始)
- [插件结构](#插件结构)
- [新手友好 API](#新手友好-api)
- [命令参数解析](#命令参数解析)
- [进阶 API](#进阶-api)
- [生命周期](#生命周期)
- [完整示例](#完整示例)
- [扩展与复用（轮子 / 附属插件）](#扩展与复用轮子--附属插件)
- [四种配合的完整示例（新手格式）](#四种配合的完整示例新手格式)
- [服主 / 管理员使用指南](#服主--管理员使用指南)
- [控制命令](#控制命令)
- [测试与调试](#测试与调试)
- [开发本插件（引擎）](#开发本插件引擎)
- [附录：ppe 全部函数速查表](#附录ppe-全部函数速查表)
- [注意事项](#注意事项)

---

## 快速开始

1. 把 `PaperPythonEngine-1.0.0.jar` 放入服务器的 `plugins/` 文件夹。
2. 启动服务器，引擎会自动创建目录结构。
3. 把 Python 插件放入 `plugins/PaperPythonEngine/PythonPlugin/`（支持 `.zip` 文件或直接放文件夹）。
4. 重启服务器，或执行 `/pyreload` 热重载。

目录结构：

```
plugins/
├── PaperPythonEngine.jar
└── PaperPythonEngine/
    ├── PythonPlugin/          ← 把 Python 插件放这里（zip 或文件夹）
    │   └── myplugin/
    │       ├── plugin.py      ← 入口文件（必须存在）
    │       ├── commands.py    ← 可选的分层模块
    │       └── events.py
    ├── PythonEngine_ex/       ← 把"轮子"（可复用的扩展库 .py / .zip）放这里
    └── cache/                 ← zip 解压缓存（自动管理）
```

> zip 和文件夹二选一：zip 适合分发，文件夹适合本地开发（改完 `/pyreload` 立即生效，无需打包）。

---

## 插件结构

每个插件必须有 `plugin.py`（入口）。它被加载为独立模块，模块级 `on_enable` / `on_disable` 作为生命周期钩子。

### 单文件插件

```
myplugin.zip        (或 myplugin/ 文件夹)
└── plugin.py
```

### 多文件分层插件

`plugin.py` 通过普通 `import` 引入同目录下的分层模块：

```
myplugin/
├── plugin.py       # 入口：import commands / events，生命周期，调度
├── commands.py     # 所有命令
└── events.py       # 所有事件
```

各模块里的命令 / 事件会自动归属到本插件（卸载 / 重载时精确清理）。不同插件的同名模块互不干扰。

---

## 新手友好 API

无需装饰器、无需 lambda、无需导入任何 Java 类。建议开头写 `from ppe import *`。

### 注册命令

**Python 简化形式**（新手友好）：

```python
from ppe import *

def hello(player, args):
    """命令处理函数：玩家输入 /hello 时被调用
    player：执行命令的玩家（友好的 Player 对象；控制台/命令方块则是 Sender）
    args：参数列表（list），例如输入 /hello steve 时 args = ["steve"]
    """
    player.send("你好！")
    player.send("你输入了 " + str(len(args)) + " 个参数")   # len(args) 统计参数个数

register_command(hello)                                  # 命令名 = 函数名（hello）
register_command(hello, permission="hello.use")          # 加权限：没有该权限的玩家无法使用
register_command(hello, name="hi", description="打招呼",
                 permission="hello.use", aliases=("h",)) # 自定义命令名 / 描述 / 别名
```

**Java 形式**（进阶 · 装饰器 + 原生对象）：

```python
from ppe import command

@command                                  # 命令名 = 函数名 hello
def hello(sender, args):
    # sender：原生 CommandSender，用 Java 方法名 sendMessage
    sender.sendMessage("你好！")

@command("hi", permission="hello.use", aliases=("h",))   # 自定义名 / 权限 / 别名
def hi(sender, args):
    sender.sendMessage("你好！")
```

- 处理函数签名：`func(player, args)`
  - `player`：友好的玩家对象（控制台/命令方块则是 `sender`，也有 `send` 方法）
  - `args`：参数列表，例如 `/give diamond 64` 时 `args = ["diamond", "64"]`

### 命令参数解析

`args` 永远是**字符串列表**。下面是几个最常用的解析套路（可直接照抄）：

**Python 简化形式**（新手友好）：

```python
def setmoney(player, args):
    if len(args) < 2:
        player.send("用法：/setmoney <玩家名> <金额>")
        return
    try:
        amount = float(args[1])          # 例如 "100" -> 100.0
    except ValueError:
        player.send("金额必须是数字，例如 100 或 12.5")
        return
    target = get_player(args[0])         # 友好函数：按名字找在线玩家
    if target is None:
        player.send("找不到玩家 " + args[0])
        return
    player.send("目标：" + target.name() + "，金额：" + str(amount))

register_command(setmoney, name="setmoney", permission="admin.money")


def broadcast_cmd(sender, args):
    message = " ".join(args)             # 把多个参数拼成一句完整的话
    if not message:
        sender.send("用法：/broadcast <消息>")
        return
    broadcast(message)

register_command(broadcast_cmd, name="broadcast")
```

**Java 形式**（进阶 · 同样的套路）：

```python
from ppe import command
from org.bukkit import Bukkit

@command("setmoney", permission="admin.money")
def setmoney(sender, args):
    if len(args) < 2:
        sender.sendMessage("用法：/setmoney <玩家名> <金额>")   # 原生方法名 sendMessage
        return
    try:
        amount = float(args[1])          # args 可当列表用
    except ValueError:
        sender.sendMessage("金额必须是数字，例如 100 或 12.5")
        return
    target = Bukkit.getPlayer(args[0])   # Java 静态方法取玩家
    if target is None:
        sender.sendMessage("找不到玩家 " + args[0])
        return
    sender.sendMessage("目标：" + target.getName() + "，金额：" + str(amount))


@command("broadcast")
def broadcast_cmd(sender, args):
    message = " ".join(args)
    if not message:
        sender.sendMessage("用法：/broadcast <消息>")
        return
    Bukkit.broadcastMessage(message)     # Java 静态方法全服广播
```

要点：
- `len(args)` 检查参数个数，`args[0]` / `args[-1]` 取参数，`" ".join(args)` 拼整句。
- 数字要用 `int()` / `float()` 转换并捕获 `ValueError`，否则玩家输入非数字会报错。
- 用 `get_player(name)` 按名字找在线玩家，找不到返回 `None`，要先判断再使用。
- 参数不足或非法时，提示 `用法：/命令 <参数>` 并 `return`，是常见且友好的做法。

### 注册事件

**Python 简化形式**（新手友好）：

```python
def on_join(player):
    # 玩家加入服务器时触发（不可取消）
    # 参数：player —— 刚加入的玩家（友好 Player 对象）
    player.send("欢迎，" + player.name() + "！")

register_event("player_join", on_join)   # 事件名 + 处理函数


def on_chat(player, message):
    # 玩家聊天时触发（可取消）
    # 参数：player —— 说话的玩家；message —— 聊天内容（字符串）
    if "作弊" in message:
        player.send("请不要讨论作弊。")
        return False          # 返回 False 取消事件（这条消息不会发出）

register_event("player_chat", on_chat)
```

**Java 形式**（进阶 · 传 Java 事件类 + 原生事件对象）：

```python
from ppe import on_event
from org.bukkit.event.player import PlayerJoinEvent, AsyncPlayerChatEvent

@on_event(PlayerJoinEvent)                # 直接传 Java 事件类
def on_join(event):
    player = event.getPlayer()            # 原生 Player，用 Java 方法名
    player.sendMessage("欢迎，" + player.getName() + "！")


@on_event(AsyncPlayerChatEvent)
def on_chat(event):
    if "作弊" in event.getMessage():
        event.getPlayer().sendMessage("请不要讨论作弊。")
        event.setCancelled(True)          # Java 方式取消事件
```

可用的事件名（`event_name` → 触发时机 / 处理函数参数 / 可取消）：

| 事件名 | 触发时机 | 参数 | 返回 False 取消 |
|---|---|---|---|
| `player_join` | 玩家加入 | `player` | 否 |
| `player_quit` | 玩家退出 | `player` | 否 |
| `player_chat` | 玩家聊天 | `player, message` | 是 |
| `player_command` | 玩家执行命令 | `player, command` | 是 |
| `player_move` | 玩家移动 | `player, from_location, to_location` | 是 |
| `player_death` | 玩家死亡 | `player, killer, message` | 是 |
| `player_damage` | 玩家受伤 | `player, cause, damage` | 是 |
| `player_respawn` | 玩家重生 | `player, location` | 否 |
| `block_break` | 破坏方块 | `player, block, location` | 是 |
| `block_place` | 放置方块 | `player, block, location` | 是 |

优先级可指定：`register_event("player_join", on_join, priority="HIGH")`，可选 `LOWEST / LOW / NORMAL / HIGH / HIGHEST / MONITOR`。

### 定时任务

**Python 简化形式**（新手友好 · 以秒为单位）：

```python
from ppe import after, every, cancel

def do_once():
    # after/every 定时后调用的函数（注意只传函数名，不带括号）
    broadcast("1 秒后执行")

after(1.0, do_once)            # 延迟 1 秒执行一次 do_once

task = every(5.0, do_once)     # 每 5 秒执行一次，返回任务 ID（int）
cancel(task)                   # 用任务 ID 取消该定时任务
```

**Java 形式**（进阶 · 以 tick 为单位，1 秒 = 20 tick）：

```python
from ppe import schedule, schedule_repeating, cancel_task

schedule(20, do_once)                    # 延迟 20 tick（1 秒）执行一次

task = schedule_repeating(100, 100, do_once)   # 每 100 tick（5 秒）执行一次
cancel_task(task)                        # 取消任务
```

### 玩家对象 `player`

命令处理函数收到的 `player` 是一个友好的 `Player` 包装对象（当发送者是控制台/命令方块时，收到的是 `Sender` 对象，同样有 `send` 等方法）：

| 方法 | 说明 |
|---|---|
| `player.send("文字")` | 发消息 |
| `player.name()` | 玩家名 |
| `player.health()` / `player.set_health(20)` / `player.heal()` | 生命值 |
| `player.food_level()` / `player.feed()` | 饥饿值 |
| `player.fly(True)` / `player.allow_flight()` / `player.is_flying()` | 飞行 |
| `player.teleport(x, y, z)` | 传送 |
| `player.location()` | 当前位置（`Location`） |
| `player.give("DIAMOND", 64)` | 给物品（材质名用大写） |
| `player.clear_inventory()` | 清空背包 |
| `player.kick("理由")` | 踢出 |
| `player.send_title("主标题", "副标题")` | 标题 |
| `player.send_actionbar("文字")` | 快捷栏消息 |
| `player.game_mode()` / `player.set_game_mode("CREATIVE")` | 游戏模式 |
| `player.has_permission("节点")` | 权限检查 |
| `player.is_player()` | 是否是玩家（否则是控制台） |
| `player.is_online()` / `player.is_op()` | 在线 / OP 判断 |

所有方法均为小写 `snake_case`。需要底层能力时用 `player.raw` 直接访问原生 Bukkit `Player` 对象（Java 风格方法照样能调）。

**两种写法对照**：

```python
# ---------- Python 简化形式 ----------
player.heal()                      # 回满血
player.feed()                      # 吃饱
player.fly(True)                   # 开启飞行
player.send("治疗完成！")            # 发消息
player.teleport(100, 64, -200)     # 传送到坐标
player.give("DIAMOND", 16)         # 给 16 个钻石

# ---------- Java 形式（player.raw 或直接 Java） ----------
sender.setHealth(sender.getMaxHealth())     # 回满血
sender.setFoodLevel(20)                     # 吃饱
sender.setAllowFlight(True)                 # 开启飞行
sender.sendMessage("治疗完成！")             # 发消息
sender.teleport(Location(world, 100, 64, -200))   # 传送到坐标（需要构造 Location）
sender.getInventory().addItem(ItemStack(Material.DIAMOND, 16))   # 给钻石
```

### 位置与方块对象

| 对象 | 常用成员 | 说明 |
|---|---|---|
| `Location` | `.x` `.y` `.z` `.world` `.yaw` `.pitch` | 坐标；`str(loc)` 可友好显示 |
| `Block` | `.type_name()` `.location()` | 方块；`.raw` 是原生 Bukkit Block |
| `Sender` | `.send()` `.name()` `.has_permission()` | 命令发送者（含控制台） |

### 服务端便捷函数

**Python 简化形式**（新手友好）：

```python
broadcast("全服公告")
get_player("Steve")        # 返回 Player 或 None
online_players()           # 返回在线玩家列表（Player）
run_command("say hello")   # 以控制台执行命令
```

**Java 形式**（进阶 · 直接调 Bukkit 静态方法）：

```python
from org.bukkit import Bukkit

Bukkit.broadcastMessage("全服公告")                        # 全服广播
Bukkit.getPlayer("Steve")                                  # 按名取玩家（可能为 None）
list(Bukkit.getOnlinePlayers())                            # 在线玩家集合（可遍历）
Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "say hello")   # 以控制台执行命令
```

### 日志与数据

```python
info("信息")   # 输出 [FW_PaperPythonEngine][插件名] 信息
warn("警告")
error("错误")

data_path()    # 当前插件的专属数据目录（自动创建）
```

> 说明：`info/warn/error` 与 `data_path()` 是引擎提供的**简化封装**，Python 形式即可，没有额外的 Java 形式。
> 底层对应：日志走 `[FW_PaperPythonEngine]` 前缀；数据目录为 `plugins/PaperPythonEngine/data/<插件名>/`。

---

## 进阶 API

进阶写法用 `@command` / `@on_event` 装饰器，处理函数收到的是**原生** Bukkit 对象（`CommandSender` / `Event`），调用 Java 风格方法。与新手写法一一对应，可混用。

### 装饰器风格

```python
# ---------- Java 形式（装饰器 + 原生对象） ----------
from ppe import command, on_event
from org.bukkit.event.player import PlayerJoinEvent

@command("hello", permission="hello.use")
def hello(sender, args):
    sender.sendMessage("你好！")          # 原生 CommandSender，Java 方法名

@on_event(PlayerJoinEvent, priority="HIGH")
def on_join(event):
    event.getPlayer().sendMessage("欢迎！")   # 原生事件对象，getXxx()

# ---------- 同样的功能，Python 简化形式 ----------
from ppe import register_command, register_event

def hello(player, args):
    player.send("你好！")                  # 友好 Player，send()

register_command(hello, permission="hello.use")

def on_join(player):
    player.send("欢迎！")

register_event("player_join", on_join, priority="HIGH")
```

### 直接访问 Java / Bukkit

```python
# ---------- Java 形式 ----------
from org.bukkit import Bukkit
from org.bukkit.entity import Player
import java

for p in Bukkit.getOnlinePlayers():        # Java 集合可遍历
    Bukkit.broadcastMessage(p.getName())

if java.instanceof(sender, Player):        # 判断对象是不是玩家
    sender.setHealth(sender.getMaxHealth())

# ---------- Python 简化形式 ----------
from ppe import online_players, broadcast, get_player

for p in online_players():
    broadcast(p.name())

player = get_player("Steve")               # 按名取玩家，可能是 None
```

也可以 `from ppe import jclass; Bukkit = jclass("org.bukkit.Bukkit")`。

### tick 单位的调度

```python
# ---------- Java 形式（tick 单位，1 秒 = 20 tick） ----------
from ppe import schedule, schedule_repeating, cancel_task

schedule(20, do_once)                                  # 1 秒后执行一次
task = schedule_repeating(20, 100, do_once)            # 先等 1 秒，之后每 5 秒
cancel_task(task)                                      # 取消任务

# ---------- Python 简化形式（秒单位） ----------
from ppe import after, every, cancel

after(1.0, do_once)                                    # 1 秒后执行一次
task = every(5.0, do_once)                             # 每 5 秒一次
cancel(task)
```

---

## 生命周期

在 `plugin.py` 模块级定义，由引擎按函数名自动识别（**两种写法共用**，无论命令/事件用哪种风格）：

| 钩子 | 时机 |
|---|---|
| `on_enable()` | 插件加载完成后调用 |
| `on_disable()` | 插件卸载 / 服务器关闭 / `/pyreload` 时调用 |

```python
def on_enable():
    # 插件加载成功后执行：初始化数据、开定时任务等
    info("我的插件已启用")
    after(2.0, welcome_task)   # 2 秒后调用 welcome_task 一次

def on_disable():
    # 卸载时执行：存档、取消定时任务、清理等
    info("我的插件已关闭")
```

---

## 完整示例

### 1. hello（新手友好 · 单文件）

`hello/plugin.py`：

```python
# 一次性导入全部 ppe 功能：命令 / 事件 / 调度 / 广播 / 玩家对象等
from ppe import *


# ---------------- 生命周期 ----------------

def on_enable():
    # 插件加载成功后执行一次（初始化、开定时任务等）
    info("hello 插件已启用！")
    after(2.0, welcome_task)     # 2 秒后调用 welcome_task 一次（注意只传函数名，不加括号）


def welcome_task():
    # after 定时后调用的函数
    broadcast("大家好！这是用 PaperPythonEngine 写的第一个插件。")


def on_disable():
    # 插件卸载 / 服务器关闭 / /pyreload 时执行（清理资源、存档等）
    info("hello 插件已关闭。")


# ---------------- 命令 ----------------

def hello(player, args):
    # /hello 命令的处理函数
    # player：执行者（友好 Player 对象），args：参数列表
    player.send("你好！这是 /hello 命令。")
    player.heal()    # 友好方法：回满血
    player.feed()    # 友好方法：吃饱

    # ---- 参数解析示例 ----
    if args:                          # 有参数：/hello 小明
        player.send("收到参数：" + " ".join(args))
    else:                             # 无参数
        player.send("你可以这样用：/hello 你的名字")


register_command(hello, permission="hello.use")   # 命令名=hello，需权限 hello.use


# ---------------- 事件 ----------------

def on_join(player):
    # 玩家加入服务器时触发，player 是刚进入的玩家
    player.send("欢迎回来，" + player.name() + "！")
    info(player.name() + " 加入了服务器")


register_event("player_join", on_join)


def on_chat(player, message):
    # 玩家聊天时触发；返回 False 可以取消这次聊天
    if "作弊" in message:
        player.send("请不要讨论作弊。")
        return False          # 取消事件（这条消息不会发出去）


register_event("player_chat", on_chat)
```

### 2. essentials（新手友好 · 多文件分层）

```
essentials/
├── plugin.py      # 入口 + 生命周期 + 配置加载
├── core.py        # JSON 配置（config.json）+ 消息前缀
├── commands.py    # /heal /feed /fly /sethome /home /broadcast
└── events.py      # 加入欢迎、聊天过滤
```

`commands.py` 片段：

```python
# commands.py —— 命令模块

from ppe import register_command
from org.bukkit import NamespacedKey
from org.bukkit.persistence import PersistentDataType

from core import fmt   # 自己的工具：给消息加插件前缀

# NamespacedKey("命名空间", "键")：用于给玩家"打标签"存数据（PDC，跨重启保留）
HOME_KEY = NamespacedKey("essentials", "home")


def heal(player, args):
    # /heal：回满血
    player.heal()                  # 友好 Player 方法：回满血
    player.feed()                  # 友好 Player 方法：吃饱
    player.send(fmt("已恢复满血！"))  # fmt() 自动加前缀


register_command(heal)   # 命令名 = 函数名 heal，即 /heal


def sethome(player, args):
    # /sethome：把当前坐标存到玩家身上（PDC）
    loc = player.location()   # 友好 Location 对象，含 .x .y .z .yaw .pitch .world
    # 把坐标拼成字符串："世界名,x,y,z,视角,俯仰"
    data = "{},{},{},{},{},{}".format(
        loc.world.getName(), loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
    # player.raw 是原生 Bukkit Player，用它的持久化容器存字符串
    player.raw.getPersistentDataContainer().set(HOME_KEY, PersistentDataType.STRING, data)
    player.send(fmt("家已设置！"))


register_command(sethome, name="sethome")   # 函数名是 sethome，命令名也想用 /sethome，显式指定


def home(player, args):
    # /home：读回存的坐标并传送
    container = player.raw.getPersistentDataContainer()
    # 判断是否存过家（避免读到空数据报错）
    if not container.has(HOME_KEY, PersistentDataType.STRING):
        player.send(fmt("你还没有设置家，先用 /sethome 吧。"))
        return
    parts = container.get(HOME_KEY, PersistentDataType.STRING).split(",")
    # 参数解析：把字符串坐标转回数字并传送到指定 x, y, z
    player.teleport(float(parts[1]), float(parts[2]), float(parts[3]))
    player.send(fmt("已传送回家！"))


register_command(home)


def broadcast_cmd(sender, args):
    # /broadcast <消息>：sender 可能是玩家也可能是控制台，都用 .send()
    if not args:
        sender.send("用法：/broadcast <消息>")
        return
    message = " ".join(args)   # 参数解析：把多个参数拼成一句完整消息
    from ppe import broadcast
    broadcast(fmt(message))


register_command(broadcast_cmd, name="broadcast", permission="essentials.broadcast")
```

### 3. duel（小游戏 · 进阶）

```
duel/
├── plugin.py      # 入口 + 关闭时结束对局
├── core.py        # 竞技场数据（出生点/半径）持久化
├── match.py       # Match 状态机：开局/限时/判负/结束
├── commands.py    # /duel <玩家> /duel accept /duel leave /duel setspawn1|2|setradius
└── events.py      # 死亡判负、退出判负、离开场地取消、屏蔽场外伤害
```

完整代码见 `run/plugins/PaperPythonEngine/PythonPlugin/duel/`。

`match.py` 核心（对局状态机）：

```python
# match.py —— 一场单挑对局的逻辑

from ppe import schedule, schedule_repeating, cancel_task, info
from org.bukkit import Bukkit
from core import STATE


class Match:
    """一场对局：开局 -> 每秒检查 -> 判出胜负 -> 结束"""

    def __init__(self, p1, p2):
        self.p1 = p1                    # 玩家 A
        self.p2 = p2                    # 玩家 B
        self.tick_task = None           # 每秒检查任务 ID
        self.timeout_task = None        # 超时任务 ID
        self.done = False               # 是否已结束（防止重复结束）

    def start(self):
        # 开局：传送双方到竞技场，清背包、回血、发装备
        self.p1.teleport(...)           # 传送到 1 号出生点
        self.p2.teleport(...)           # 传送到 2 号出生点
        for p in (self.p1, self.p2):
            p.getInventory().clear()    # 清空背包
            p.setHealth(p.getMaxHealth())
        STATE["active"] = self          # 标记"正在进行对局"
        # 每秒执行一次 tick()（20 tick = 1 秒）
        self.tick_task = schedule_repeating(20, 20, self.tick)
        # 120 秒后强制结束（超时判负）
        self.timeout_task = schedule(120 * 20, self.on_timeout)

    def tick(self):
        # 每秒检查：有人离线 / 死亡 / 跑出场地就判负
        if self.done:
            return
        for p in (self.p1, self.p2):
            if not p.isOnline() or p.isDead():
                self.end(self.other(p))      # 判对方获胜
                return
            if _is_outside(p):               # 跑出竞技场半径
                self.end(self.other(p))
                return

    def on_timeout(self):
        # 超时：血量高的一方获胜
        if self.done:
            return
        self.end(self.p1 if self.p1.getHealth() >= self.p2.getHealth() else self.p2)

    def end(self, winner):
        # 结束：广播结果、取消所有定时任务、清状态
        if self.done:
            return
        self.done = True
        STATE["active"] = None
        cancel_task(self.tick_task)          # 取消每秒检查
        cancel_task(self.timeout_task)       # 取消超时任务
        Bukkit.broadcastMessage("[Duel] " + winner.getName() + " 获胜！")
        info("对局结束，胜者：" + winner.getName())

    def other(self, p):
        # 返回对手：是 A 就返回 B，是 B 就返回 A
        return self.p2 if p is self.p1 else self.p1
```

`commands.py` 参数解析（`/duel` 子命令）：

```python
def duel(player, args):
    # /duel <玩家> 或 /duel accept 或 /duel leave 或 /duel setspawn1 ...
    if not args:
        player.send("用法：/duel <玩家> | /duel accept | /duel leave")
        return
    action = args[0].lower()          # 取第一个参数并转小写，用于分支判断
    if action == "accept":
        accept_duel(player)           # /duel accept
    elif action == "leave":
        leave_duel(player)            # /duel leave
    else:
        challenge(player, args[0])    # /duel <玩家名>：args[0] 就是对手名字

register_command(duel, name="duel", permission="duel.use")
```

### 4. menu（箱子菜单 · 新手友好）

```
menu/
├── plugin.py      # 用法示例：构建菜单 + /menu 命令
└── gui.py         # 可复用的 Menu 类（内部用原生事件处理点击）
```

`gui.py` 核心：

```python
from ppe import wrap_sender, on_event
from org.bukkit import Bukkit, Material
from org.bukkit.inventory import ItemStack
from org.bukkit.event.inventory import InventoryClickEvent


class Menu:
    """基础箱子菜单：往格子里放物品并绑定点击动作，open() 打开。"""

    def __init__(self, title, rows=3):
        self.title = title
        self.size = rows * 9                                  # 每行 9 格
        self.inventory = Bukkit.createInventory(None, self.size, title)
        self.actions = {}                                     # 格子号 -> 点击回调
        _all_menus.append(self)

    def set_item(self, slot, material, name=None, lore=None, amount=1, action=None):
        self.inventory.setItem(slot, _item(material, name, lore, amount))
        if action:
            self.actions[slot] = action

    def fill_empty(self, material, name=None):
        # 用填充物铺满空格子（常用玻璃片做底板）
        for slot in range(self.size):
            if self.inventory.getItem(slot) is None:
                self.inventory.setItem(slot, _item(material, name))

    def open(self, player):
        player.openInventory(self.inventory)                  # 给玩家打开菜单

    def _on_click(self, event):
        if event.getInventory() is not self.inventory:
            return
        event.setCancelled(True)                              # 取消点击，防止拿走物品
        if event.getClickedInventory() is not self.inventory:
            return                                            # 点在自己背包里则不触发
        action = self.actions.get(event.getSlot())
        if action:
            action(wrap_sender(event.getWhoClicked()))        # 调用该格子的回调


@on_event(InventoryClickEvent, priority="HIGH")               # 原生事件，全局只注册一次
def _dispatch_click(event):
    for menu in _all_menus:
        menu._on_click(event)
```

`plugin.py` 用法：

```python
from ppe import *
from gui import Menu

main_menu = None

def on_enable():
    global main_menu
    main_menu = build_main_menu()
    info("menu 插件已启用！")

def build_main_menu():
    menu = Menu("主菜单", rows=3)          # 3 行 = 27 格
    # set_item(格子号, 材质, 名字, 说明, 数量, 点击回调)
    menu.set_item(10, "DIAMOND", name="治疗", lore=("恢复满血", "点击使用"), action=do_heal)
    menu.set_item(12, "ENDER_PEARL", name="传送到出生点", action=do_spawn)
    menu.set_item(14, "DIAMOND_ORE", name="领取钻石", action=do_give)
    menu.set_item(16, "BARRIER", name="关闭菜单", action=close_menu)
    menu.fill_empty("GRAY_STAINED_GLASS_PANE", name=" ")     # 铺底板
    return menu

def do_heal(player):
    player.heal()
    player.send("已治疗！")

def do_spawn(player):
    player.teleport(player.raw.getWorld().getSpawnLocation())
    player.send("已传送到出生点！")

def do_give(player):
    player.give("DIAMOND", 16)
    player.send("已领取 16 个钻石！")

def close_menu(player):
    player.closeInventory()
    player.send("菜单已关闭。")

def menu_cmd(player, args):
    main_menu.open(player)

register_command(menu_cmd, name="menu", description="打开主菜单")
```

> 玩法：玩家输入 `/menu` 打开 3 行箱子菜单，点格子触发对应动作（治疗 / 传送 / 领钻石 / 关闭），点击一律被取消（防拿走物品）。`Menu` 类封装了原生 `InventoryClickEvent`，使用者只需 `set_item(action=...)`。

---

## 扩展与复用（轮子 / 附属插件）

### 轮子（`PythonEngine_ex`）——把公共代码抽成库

**轮子（wheel）** 是可以被多个插件复用的 Python **库**，**不是插件**：

- 没有 `plugin.py`、没有 `on_enable/on_disable` 生命周期；
- 只提供函数与类，不注册命令/事件（由插件去接线）；
- 放在 `plugins/PaperPythonEngine/PythonEngine_ex/` 下，支持 `.py` 或 `.zip`；
- 插件**只能**通过 `ppe.require("轮子名")` 使用它，**不能直接 `import`**（引擎已强制隔离，`import` 会报 `ModuleNotFoundError`）。

```
plugins/PaperPythonEngine/
├── PythonPlugin/       ← 插件（应用）
└── PythonEngine_ex/    ← 轮子（库）
    ├── msg.py          ← 单个 .py 轮子
    └── util.zip        ← 打包成 zip 的轮子（zip 根目录需含 util.py 或 __init__.py）
```

**写轮子的两种形式**（与写插件一样，两种 API 可混用）：

```python
# 形式一：用 ppe 简化函数
from ppe import broadcast, online_players

def announce(text):
    broadcast("【公告】" + text)
    for p in online_players():
        p.send_title("公告", text)


# 形式二：直接调 Bukkit / Paper API
from org.bukkit import Bukkit

def broadcast_raw(text):
    Bukkit.broadcastMessage(text)
```

**插件里使用轮子**：

```python
from ppe import require

msg = require("msg")        # 导入 .py 轮子（不要直接 import msg）
util = require("util")      # .zip 轮子同样按名导入

def hello(player, args):
    msg.announce("轮子公告")
    player.send("3 + 5 = " + str(util.add(3, 5)))
```

**更完整的造轮子示例**（类 + 常量 + 两种 API 混用）：

```python
# EconomyWheel.py —— 一个"经济"轮子
from ppe import data_path, get_player
import json, os

BALANCES_FILE = os.path.join(data_path(), "balances.json")   # data_path 自动指向引擎数据目录


class Economy:
    """给插件用的经济库：提供余额存取。"""

    def __init__(self):
        self._data = {}
        self._load()

    def _load(self):
        try:
            with open(BALANCES_FILE, encoding="utf-8") as f:
                self._data = json.load(f)
        except Exception:
            self._data = {}

    def _save(self):
        with open(BALANCES_FILE, "w", encoding="utf-8") as f:
            json.dump(self._data, f, ensure_ascii=False, indent=2)

    def balance(self, name):
        return self._data.get(name, 0)

    def add(self, name, amount):
        self._data[name] = self.balance(name) + amount
        self._save()

    def take(self, name, amount):
        self._data[name] = self.balance(name) - amount
        self._save()


economy = Economy()   # 轮子加载时就初始化，插件 require 后直接用
```

插件里这样用：

```python
from ppe import require
eco = require("EconomyWheel")       # 轮子名 = 文件名（去掉 .py）

def pay(player, args):
    eco.economy.add(player.name(), 100)          # 调轮子里的实例
    player.send("已获得 100，余额：" + str(eco.economy.balance(player.name())))
```

**如何打包成 `.zip` 轮子**：

```
wheel.zip 的根目录必须包含：<轮子名>.py  或  __init__.py
```

- 右键文件夹压缩成 zip（或 `Compress-Archive -Path util.py -DestinationPath util.zip`）；
- zip 内可以有多个 `.py`（作为该轮子的子模块），只要根目录有入口文件即可；
- 放入 `PythonEngine_ex/` 后 `/pyreload` 即可被 `ppe.require("轮子名")` 加载。

**轮子还是附属插件？**

| 场景 | 用哪种 |
|---|---|
| 多个插件共享的通用代码（消息、经济、工具） | **轮子** ✅ |
| 插件 A 是插件 B 的"扩展 / 附属"，强依赖 B 的能力 | 附属插件（`dependencies` + `ppe.require`） |
| 只是想用别的插件现成功能，非强绑定 | 优先轮子 |

> 原则：**轮子装库、插件装业务**。宁可多拆几个轮子，也不要让插件互相 `require` 纠缠。

### 附属插件（插件依赖插件）

插件 A 作为插件 B 的附属时，A **只能**通过 `ppe.require("B")` 使用 B，不能直接 `import B`。
为保证 B 先加载，在 A 的 `plugin.py` **顶部**声明依赖：

```python
# A 插件（B 的附属）
dependencies = ["B"]          # 引擎会先加载 B，再加载 A

from ppe import require
B = require("B")              # 通过 ppe 获取 B 插件导出的模块

def hello(player, args):
    B.some_function(player)   # 调用 B 的功能

register_command(hello)
```

> **轮子与插件的边界**：推荐把公共代码放进**轮子**而不是让插件互相依赖——轮子与插件彻底分离，一个插件不应直接调用另一个插件的内部实现。只有"附属 / 扩展"语义明确时才用插件依赖。
> 引擎按 `dependencies` 自动调整加载顺序（先依赖、后被依赖），遇到循环依赖会跳过并告警。
>
> **缺失时的表现**：若 `ppe.require("X")` 找不到轮子/插件，会抛 `ImportError: unknown wheel or plugin: X`，该插件将**加载失败**；若声明的 `dependencies` 缺失，控制台会输出警告 `Plugin 'A' depends on missing wheel/plugin 'X'`。

---

## 四种配合的完整示例（新手格式）

从"最简单"到"最复杂"，按需选用。所有代码均为**新手友好写法**（`from ppe import *`），完整文件在 `run/plugins/PaperPythonEngine/` 下（`PythonPlugin/` 放插件、`PythonEngine_ex/` 放轮子）。

### ① 单插件：不需要任何外部代码

`single/plugin.py` —— 玩家加入欢迎 + `/ping` + 每 30 秒定时公告。

```python
from ppe import *


def on_enable():
    info("single 插件已启用！")
    every(30.0, periodic_broadcast)   # 每 30 秒定时公告


def periodic_broadcast():
    broadcast("欢迎来玩！这是 single 插件的定时公告。")


def ping(player, args):
    # /ping
    player.send("Pong！")

register_command(ping)


def on_join(player):
    # 玩家加入欢迎
    player.send("欢迎，" + player.name() + "！")

register_event("player_join", on_join)


def on_disable():
    info("single 插件已关闭。")
```

### ② 需要轮子：公共逻辑抽进 `PythonEngine_ex`

`PythonEngine_ex/giftbox.py`（轮子）—— 发物品 + 记录礼包：

```python
from ppe import data_path, info
import json
import os

LOG = os.path.join(data_path(), "gift_log.json")


def give(player, material, amount=1):
    player.give(material, amount)


def log_gift(sender_name, receiver_name, item, amount):
    records = []
    try:
        with open(LOG, encoding="utf-8") as f:
            records = json.load(f)
    except Exception:
        records = []
    records.append({"from": sender_name, "to": receiver_name, "item": item, "amount": amount})
    with open(LOG, "w", encoding="utf-8") as f:
        json.dump(records, f, ensure_ascii=False, indent=2)
    info(sender_name + " 送给了 " + receiver_name + " " + str(amount) + " 个 " + item)
```

`gift/plugin.py`（插件，用 `ppe.require("giftbox")`）—— `/gift <玩家名> <物品> [数量]`：

```python
from ppe import *


giftbox = require("giftbox")   # 导入轮子（不能直接 import giftbox）


def send_gift(player, args):
    if len(args) < 2:
        player.send("用法：/gift <玩家名> <物品> [数量]")
        return
    target = get_player(args[0])
    if target is None:
        player.send("找不到玩家 " + args[0])
        return
    item = args[1]
    amount = int(args[2]) if len(args) >= 3 else 1
    giftbox.give(player, item, amount)          # 发给自己
    giftbox.give(target, item, amount)          # 发给目标
    giftbox.log_gift(player.name(), target.name(), item, amount)
    player.send("已把 " + str(amount) + " 个 " + item + " 送给 " + target.name())


register_command(send_gift, name="gift")
```

### ③ 需要依赖：附属插件

`core/plugin.py`（被依赖的基础插件，提供等级能力）+ `coreext/plugin.py`（附属插件，扩展 `/level`）：

```python
# coreext/plugin.py —— 附属插件
dependencies = ["core"]          # 引擎会先加载 core，再加载本插件

from ppe import *

core = require("core")           # 通过 ppe 导入插件 core（不能直接 import core）


def level(player, args):
    # /level
    player.send("你的等级：" + str(core.get_level(player)))

register_command(level)
```

### ④ 轮子 + 依赖都要

`vip/plugin.py` —— 用 `msg` 轮子发公告，依赖 `core` 插件查/设等级：

```python
# vip/plugin.py
dependencies = ["core"]          # 先加载 core

from ppe import *

msg = require("msg")             # ① 使用轮子 msg（发公告）
core = require("core")           # ② 使用依赖插件 core（等级）


def vip(player, args):
    # /vip <玩家名>
    if not args:
        player.send("用法：/vip <玩家名>")
        return
    target = get_player(args[0])
    if target is None:
        player.send("找不到玩家 " + args[0])
        return
    core.set_level(target, 2)                    # 用依赖插件 core
    msg.announce(target.name() + " 成为 VIP（等级 2）！")   # 用轮子 msg
    player.send("已设置完成。")


register_command(vip, name="vip", permission="admin.vip")
```

> 四种场景已在服务端验证通过：`single`（单插件）、`gift`（轮子 giftbox）、`coreext`（依赖 core）、`vip`（msg 轮子 + core 依赖）全部正常加载运行。

---

## 服主 / 管理员使用指南

本节面向**服务器管理员**：只需要"装上就能跑"，不用写代码。

### 一、安装引擎

1. 把 `PaperPythonEngine-1.0.0.jar` 放进服务器的 `plugins/` 文件夹。
2. 启动服务器，引擎会自动创建 `plugins/PaperPythonEngine/` 目录结构。
3. 若服务器已在运行，需**重启**（插件 jar 在启动时加载）。

### 二、放入插件与轮子

| 内容 | 放到 | 格式 |
|---|---|---|
| Python 插件 | `plugins/PaperPythonEngine/PythonPlugin/` | `.zip` 或**文件夹** |
| 轮子（扩展库） | `plugins/PaperPythonEngine/PythonEngine_ex/` | `.py` 或 `.zip` |

- 插件必须有 `plugin.py`；轮子根目录需含 `<轮子名>.py` 或 `__init__.py`。
- 放好后执行 `/pyreload` 即可生效，无需重启（文件夹型插件改完文件直接 `/pyreload`）。

### 三、控制命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/pyreload` | `paperpython.reload` | 重载所有 Python 插件与轮子（卸载 → 重新加载） |
| `/pyplist` | `paperpython.list` | 列出所有 Python 插件及状态（失败的标 `[FAILED]`） |

- 控制台（服务器命令行）默认可执行；给玩家/管理员开权限请配合权限插件（如 LuckPerms）授权 `paperpython.reload` / `paperpython.list`，或直接加入 `ops.json`。

### 四、数据与日志

- 插件数据目录：`plugins/PaperPythonEngine/data/<插件名>/`（`ppe.data_path()` 指向这里）。
- 引擎日志前缀：`[FW_PaperPythonEngine][插件名]`，写进 `logs/latest.log`。

### 五、常见问题

| 现象 | 处理 |
|---|---|
| 插件没生效 | 确认在 `PythonPlugin/` 下、有 `plugin.py`；执行 `/pyplist` 看是否 `[FAILED]`；看日志的 `Failed to load` |
| 说缺轮子/插件 | 日志提示 `unknown wheel or plugin: X` → 把对应的轮子/插件放进目录后 `/pyreload` |
| 控制台中文乱码（Windows） | 启动参数加 `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` |
| 修改后没变化 | 确保执行了 `/pyreload`；zip 型插件修改后需重新打包 |

---

## 控制命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/pyreload` | `paperpython.reload` | 重载所有 Python 插件（卸载 → 重新加载） |
| `/pyplist` | `paperpython.list` | 列出所有 Python 插件及状态 |

---

## 测试与调试

本节面向**开发 Python 插件 / 轮子**的开发者。

### 快速起服测试

```bash
gradlew runServer
```

`run-paper` 会自动下载 Paper 1.21.8、把构建好的引擎 jar 装入 `run/plugins/` 并启动服务器。测试用的插件与轮子放在：

```
run/plugins/PaperPythonEngine/PythonPlugin/     ← 插件
run/plugins/PaperPythonEngine/PythonEngine_ex/  ← 轮子
```

### 开发循环（改 → 重载 → 看日志）

1. 新建/修改 `PythonPlugin/<名字>/plugin.py`（文件夹型插件**无需打包**）。
2. 在服务器控制台输入 `/pyreload` 热重载。
3. 看日志确认：`[FW_PaperPythonEngine] Loaded <名字>`；失败会打印 `Failed to load <名字>` 与 `ImportError`/堆栈。

### 常用调试手段

- `/pyplist` —— 查看插件加载状态（`[FAILED]` 表示加载失败）。
- `logs/latest.log` —— 引擎与插件日志都在这里。
- `ppe.info("...")` —— 在代码里打点，输出 `[FW_PaperPythonEngine][插件名] ...`。
- 故意制造错误验证：写一行 `1 / 0` 或 `require("不存在的轮子")`，观察错误输出是否符合预期。
- 测试轮子：写个临时插件 `require("你的轮子")` 并调用函数，`/pyreload` 后看效果，测完删掉。

### 常见坑

- 改动源码后构建产物没更新 → 强制重建：`gradlew shadowJar --no-build-cache --rerun-tasks`。
- 插件名与内部模块同名（如 `menu` 插件里放 `menu.py`）会 import 冲突，换个子模块名。
- Windows 控制台看中文乱码是终端显示问题，`logs/latest.log` 实际是 UTF-8。

---

## 开发本插件（引擎）

本节面向**想给 PaperPythonEngine 引擎本身做贡献**的开发者。

### 技术栈

| 项 | 版本 |
|---|---|
| Paper API | 1.21.8 |
| GraalPy（Python 3.12） | 25.0.3 |
| 构建 | Gradle 9 + Shadow（打包 GraalPy 为单个 jar） |
| Java | 21 |

### 项目结构

```
src/main/java/org/PFWs/PythonE/
├── paperS.java                 # 主插件：onEnable 启动引擎、注册 /pyreload /pyplist
└── python/
    ├── PyEngine.java           # 核心：GraalPy Context、插件/轮子加载、依赖排序、日志
    ├── PyBridge.java           # 暴露给 Python 的 host 对象（register/require/log 等）
    ├── PyEventListener.java    # 事件派发（注册 Bukkit 事件 → 调 Python 回调）
    ├── PyCommandRegistry.java  # 命令注册/卸载
    ├── PySchedulerBridge.java  # 定时任务桥接
    └── PyPlugin.java           # 单个插件的注册表（命令/事件/任务/生命周期）
src/main/resources/scripts/ppe.py   # Python API（新手 + 进阶两套）
```

### 核心机制

- **GraalPy 嵌入**：`PyEngine.createContext()` 创建 Python 3.12 上下文，开启 `EmulateJython`（支持 `from org.bukkit import ...`），打包了 `python-language` / `truffle-runtime` / `regex`。
- **插件加载**：扫描 `PythonPlugin/`（zip 或文件夹），`plugin.py` 为入口，按 `dependencies` 拓扑排序后逐个加载；入口模块加载后从 `sys.modules` 移除，实现插件间隔离。
- **轮子加载**：`PythonEngine_ex/` 中的 `.py`/`.zip` 先于插件加载，仅能经 `ppe.require()` 使用（不在可导入路径上）。
- **事件/命令/调度**：Java 侧注册 Bukkit 的 `Listener`/`Command`/`Scheduler`，触发时经 `Value.execute(...)` 回调 Python。
- **日志**：统一走 `[FW_PaperPythonEngine][插件名]` 格式。

### 构建与发布

需要 JDK 21 与 Gradle。

```bash
gradlew shadowJar        # 产物：build/libs/PaperPythonEngine-1.0.0.jar（约 83MB，含 GraalPy）
gradlew runServer        # 本地起服调试
gradlew shadowJar --no-build-cache --rerun-tasks   # 缓存异常时强制重建
```

发布流程：构建 → 把 jar 复制到测试服 `plugins/` → 重启验证 → 提交推送 GitHub（master）。

> 修改 `ppe.py` 后需重新 `shadowJar` 才生效（它作为资源打包进 jar，启动时写回 `plugins/PaperPythonEngine/ppe.py`）。

---

## 附录：ppe 全部函数速查表

`from ppe import *` 会一次性导入以下所有名称。

### 新手友好（推荐）

| 函数 | 说明 |
|---|---|
| `register_command(func, name=None, description="", permission=None, aliases=())` | 注册命令 |
| `register_event(event_name, func, priority="NORMAL")` | 注册事件（返回 `False` 可取消） |
| `after(seconds, func)` | 延迟 N 秒执行一次，返回任务 ID |
| `every(seconds, func)` | 每 N 秒重复执行，返回任务 ID |
| `cancel(task_id)` | 取消定时任务 |
| `broadcast(message)` | 全服广播 |
| `get_player(name)` | 按名取玩家，返回 `Player` 或 `None` |
| `online_players()` | 返回在线玩家列表（`Player`） |
| `run_command(command)` | 以控制台执行命令 |
| `info(message)` / `warn(message)` / `error(message)` | 日志，格式 `[FW_PaperPythonEngine][插件名] …` |
| `data_path()` | 当前插件专属数据目录（自动创建） |
| `require(name)` | 导入轮子或另一个插件（**只能**用它导入外部模块，禁止直接 `import`） |
| `wheels()` | 返回已加载的轮子名列表 |
| `plugins()` | 返回已加载的插件名列表 |
| `Player` / `Sender` / `Location` / `Block` | 友好包装类（`.raw` 为原生对象） |

### 进阶（装饰器 + 直接 Java）

| 函数 | 说明 |
|---|---|
| `@command(name=None, description="", permission=None, aliases=())` | 命令装饰器 |
| `@on_event(event_class, priority="NORMAL")` | 事件装饰器（传 Java 事件类） |
| `schedule(delay_ticks, fn)` | 延迟 N tick（1 秒 = 20 tick） |
| `schedule_repeating(delay_ticks, period_ticks, fn)` | 周期调度（tick） |
| `cancel_task(task_id)` | 取消 tick 调度任务 |
| `jclass(name)` | 按全限定名取 Java 类，如 `jclass("org.bukkit.Bukkit")` |

### 事件名一览

`player_join` / `player_quit` / `player_chat` / `player_command` / `player_move` / `player_death` / `player_damage` / `player_respawn` / `block_break` / `block_place`

详细参数与可取消性见 [注册事件](#注册事件) 表格。

---

## 注意事项

- **线程安全**：事件、命令、定时任务全部在主线程执行，可直接调用 Bukkit API。Python 内部的多线程请自行注意。
- **不要在主线程阻塞**：与 Java 插件同理，长时间 `time.sleep` 会卡住服务器。
- **插件隔离**：每个插件是独立 Python 模块，`on_disable` / 重载时会自动清理其命令、事件与定时任务。
- **轮子隔离**：`PythonEngine_ex` 中的轮子不是插件，插件只能通过 `ppe.require()` 使用；直接 `import` 轮子或其他插件会失败（引擎已从可导入模块中移除它们）。
- **性能**：事件处理器尽量轻量，`player_move` 等高频事件尤其注意。
- **中文输出**：服务器日志默认 UTF-8。在 Windows 上若控制台中文乱码，请在启动参数加 `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`。
- **API 版本**：当前基于 Paper 1.21.8 构建。
