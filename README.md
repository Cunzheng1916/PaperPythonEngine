# PaperPythonEngine

一个基于 **Paper** 的 Python 插件引擎。服务端管理员只需把引擎 jar 放入插件文件夹，再把 Python 插件（`.zip` 或文件夹）丢进指定目录即可运行，无需安装任何额外组件。

内置 **GraalPy**（Python 3.12），打包进单个 jar，离线可用。Python 插件可以直接访问完整的 Bukkit/Paper API。

引擎提供**两套 API**：
- **新手友好 API**（推荐初学者）：纯 Python 惯用写法，无装饰器 `@`、无 lambda、无 Java 导入，`register_*` 函数注册命令和事件。
- **进阶 API**：装饰器 + 直接调用 Bukkit Java 类，适合熟悉 Java 或需要底层控制的高级用法。

两套 API 完全兼容，可混用。

---

## 目录

- [快速开始](#快速开始)
- [插件结构](#插件结构)
- [新手友好 API](#新手友好-api)
- [进阶 API](#进阶-api)
- [生命周期](#生命周期)
- [完整示例](#完整示例)
- [控制命令](#控制命令)
- [构建](#构建)
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

```python
from ppe import *

def hello(player, args):
    player.send("你好！")

register_command(hello)                      # 命令名 = 函数名（hello）
register_command(hello, permission="hello.use")   # 或指定权限
register_command(hello, name="hi", description="打招呼", permission="hello.use", aliases=("h",))
```

- 处理函数签名：`func(player, args)`
  - `player`：友好的玩家对象（控制台/命令方块则是 `sender`，也有 `send` 方法）
  - `args`：参数列表，例如 `/give diamond 64` 时 `args = ["diamond", "64"]`

### 注册事件

```python
def on_join(player):
    player.send("欢迎，" + player.name() + "！")

register_event("player_join", on_join)

def on_chat(player, message):
    if "作弊" in message:
        player.send("请不要讨论作弊。")
        return False          # 返回 False 可以取消事件

register_event("player_chat", on_chat)
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

### 定时任务（秒）

```python
from ppe import after, every, cancel

def do_once():
    broadcast("1 秒后执行")

after(1.0, do_once)                 # 延迟 1 秒执行一次

task = every(5.0, do_once)          # 每 5 秒执行一次，返回任务 ID
cancel(task)                        # 取消任务
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

### 位置与方块对象

| 对象 | 常用成员 | 说明 |
|---|---|---|
| `Location` | `.x` `.y` `.z` `.world` `.yaw` `.pitch` | 坐标；`str(loc)` 可友好显示 |
| `Block` | `.type_name()` `.location()` | 方块；`.raw` 是原生 Bukkit Block |
| `Sender` | `.send()` `.name()` `.has_permission()` | 命令发送者（含控制台） |

### 服务端便捷函数

```python
broadcast("全服公告")
get_player("Steve")        # 返回 Player 或 None
online_players()           # 返回在线玩家列表（Player）
run_command("say hello")   # 以控制台执行命令
```

### 日志与数据

```python
info("信息")   # 输出 [FW_PaperPythonEngine][插件名] 信息
warn("警告")
error("错误")

data_path()    # 当前插件的专属数据目录（自动创建）
```

---

## 进阶 API

### 装饰器风格

```python
from ppe import command, on_event, schedule

@command                        # 命令名 = 函数名
def ping(sender, args):
    sender.sendMessage("Pong!")

@command("pay", permission="money.pay", aliases=("give-money",))
def pay(sender, args):
    ...

from org.bukkit.event.player import PlayerJoinEvent

@on_event(PlayerJoinEvent, priority="HIGH")
def on_join(event):
    event.getPlayer().sendMessage("Welcome!")
```

### 直接访问 Java / Bukkit

```python
from org.bukkit import Bukkit
from org.bukkit.entity import Player
import java

players = Bukkit.getOnlinePlayers()
if java.instanceof(sender, Player):
    sender.setHealth(sender.getMaxHealth())
```

也可以 `from ppe import jclass; Bukkit = jclass("org.bukkit.Bukkit")`。

### tick 单位的调度

```python
from ppe import schedule, schedule_repeating, cancel_task

schedule(20, lambda: info("1 秒后"))        # 1 秒 = 20 tick
schedule_repeating(20, 40, lambda: info("每 2 秒"))
```

---

## 生命周期

在 `plugin.py` 模块级定义，由引擎按函数名自动识别：

| 钩子 | 时机 |
|---|---|
| `on_enable()` | 插件加载完成后调用 |
| `on_disable()` | 插件卸载 / 服务器关闭 / `/pyreload` 时调用 |

```python
def on_enable():
    info("我的插件已启用")
    after(2.0, welcome_task)

def on_disable():
    info("我的插件已关闭")
```

---

## 完整示例

### 1. hello（新手友好 · 单文件）

`hello/plugin.py`：

```python
from ppe import *


def on_enable():
    info("hello 插件已启用！")
    after(2.0, welcome_task)


def welcome_task():
    broadcast("大家好！这是用 PaperPythonEngine 写的第一个插件。")


def hello(player, args):
    player.send("你好！这是 /hello 命令。")
    player.heal()
    player.feed()


register_command(hello, permission="hello.use")


def on_join(player):
    player.send("欢迎回来，" + player.name() + "！")
    info(player.name() + " 加入了服务器")


register_event("player_join", on_join)


def on_chat(player, message):
    if "作弊" in message:
        player.send("请不要讨论作弊。")
        return False


register_event("player_chat", on_chat)


def on_disable():
    info("hello 插件已关闭。")
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
from ppe import register_command
from org.bukkit import NamespacedKey
from org.bukkit.persistence import PersistentDataType
from core import fmt

HOME_KEY = NamespacedKey("essentials", "home")


def heal(player, args):
    player.heal()
    player.send(fmt("已恢复满血！"))


register_command(heal)


def sethome(player, args):
    loc = player.location()
    data = "{},{},{},{},{},{}".format(loc.world.getName(), loc.x, loc.y, loc.z, loc.yaw, loc.pitch)
    player.raw.getPersistentDataContainer().set(HOME_KEY, PersistentDataType.STRING, data)
    player.send(fmt("家已设置！"))


register_command(sethome, name="sethome")
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

---

## 控制命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/pyreload` | `paperpython.reload` | 重载所有 Python 插件（卸载 → 重新加载） |
| `/pyplist` | `paperpython.list` | 列出所有 Python 插件及状态 |

---

## 构建

需要 JDK 21 与 Gradle。

```bash
gradlew shadowJar
```

产物：`build/libs/PaperPythonEngine-1.0.0.jar`（已包含 GraalPy，约 83MB，可直接放入 `plugins/`）。

本地起服测试：

```bash
gradlew runServer
```

> 若改动源码后构建产物没更新（Gradle 本地缓存偶发问题），强制重建：
> `gradlew shadowJar --no-build-cache --rerun-tasks`

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
- **性能**：事件处理器尽量轻量，`player_move` 等高频事件尤其注意。
- **中文输出**：服务器日志默认 UTF-8。在 Windows 上若控制台中文乱码，请在启动参数加 `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`。
- **API 版本**：当前基于 Paper 1.21.8 构建。
