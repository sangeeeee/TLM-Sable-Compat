# TLM Sable Compat

[English](#english) | 中文

为 [车万女仆（Touhou Little Maid）](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid) 与 [Sable](https://www.curseforge.com/minecraft/mc-mods/sable) 提供兼容支持的 Minecraft NeoForge 模组。

Sable 将飞艇等可移动物理结构保存在独立空间中。方块使用结构内部坐标，玩家和女仆实体则显示在普通世界坐标中。没有兼容时，女仆可能朝错误方向寻路、找不到随船移动的 Home 和工作方块，或把另一个空间中的目标当成附近目标。本模组让跟随、Home、工作、休闲、睡眠和通勤使用正确的空间与坐标。

当前版本：`0.0.1-beta`<br>
支持环境：Minecraft `1.21.1`、NeoForge `21.1.248+`<br>
已验证：Touhou Little Maid `1.5.3`、Sable `2.0.5`

## 安装

在客户端和服务端安装本模组、Touhou Little Maid `1.5.3+` 和 Sable `2.0.5+`。附属任务兼容是可选的；只在使用对应功能时安装该附属及其前置。可选模组没有打包进本模组，也不是强制依赖。

本项目仍处于开发阶段。更高依赖版本可能可以加载，但上面的组合才是当前实际验证组合。当前实现按首次实现设计，不提供旧版配置、Home 或存档格式迁移。

## 基本规则

### 跟随主人

本模组完全采用 Sable 当前跟踪结果判断主人属于普通世界还是某个结构，不通过“主人附近有什么结构”自行猜测。

- 双方都在普通世界时，保留车万女仆原本的跟随规则。
- 双方在同一结构时，女仆使用结构内部坐标正常步行寻路。
- 主人从结构 A 到结构 B，或在普通世界与结构之间切换时，女仆在目标空间寻找主人附近的安全落点并传送，不计算跨结构步行路线。
- 主人只是飞过或靠近飞艇，但未被 Sable 跟踪到该结构时，该飞艇不会成为跟随目标。
- 主人飞离高空结构且附近没有安全落点时，女仆留在原结构等待并定期重试，同时可以继续合法的本地活动。
- 女仆意外掉出目标结构时，会尝试传送回合法安全位置。

安全落点必须属于目标空间，有可靠地面和足够的身体空间，并避开流体、世界边界及其他结构的碰撞。坐下、睡觉、拴绳、乘坐和 Home 模式仍会限制跟随。

### Home

在女仆 GUI 中开启 Home，会把女仆当前位置设为简单 Home。在 Sable 结构上设置时，保存结构身份、局部位置和支撑锚点，因此 Home 会随结构平移和旋转。

开启 Home 后，女仆不再跟随主人。工作、休闲、休息和返回行为以 Home 所在结构及活动范围为准，不会为了工作离开该结构；意外离开时会尝试返回。

结构正常卸载时，Home 暂停并在重新加载后恢复。结构被移除或收纳、Home 锚点消失，或结构分裂导致活动锚点进入不同空间时，绑定失效，需要重新设置。结构倾斜超过当前步行能力时，相关 AI 暂停，姿态恢复后继续。当前不支持侧墙或天花板行走。

### 河童的罗盘

罗盘可以为女仆配置随日程切换的三个区域：

1. 手持罗盘依次右击方块，记录工作、休闲、休息区域。
2. 可以只记录一到两个区域；缺失区域沿用车万女仆原本的回退规则。
3. 手持已记录的罗盘右击自己拥有的女仆，将区域绑定给她。
4. 潜行右击方块会清空罗盘记录。
5. 潜行右击女仆会清除三活动区。在结构上开启 Home 时，会保留或重建女仆当前位置的简单 Home。

合法用法：

- 所有区域位于同一维度，并且全部属于同一普通世界空间或同一个 Sable 结构。
- 相邻两次记录不超过 64 格。
- 涉及结构时，绑定时女仆必须被 Sable 跟踪到该结构，并且距最近记录点不超过 32 格。
- 工作方块、床、休闲方块以及附属任务的箱子或设备位于当前日程区域和所选结构内，并有合法交互位置。

不合法用法：

- 把普通世界与结构、两个不同结构或不同维度的区域记录在同一罗盘中。
- 已记录三个区域后继续添加，或让相邻记录点相距超过 64 格。
- 女仆在普通世界、另一个结构或距离过远时绑定结构罗盘。
- 使用已卸载、已移除、已收纳、过度倾斜、锚点丢失或因分裂失效的结构记录。
- 手动给附属任务绑定范围外或其他结构上的工作方块、酒桶或箱子，试图绕过 Home/罗盘范围。

非法记录或绑定会显示具体失败原因，并保留女仆原来的有效配置。成功绑定后，女仆得到独立副本；以后修改原罗盘不会改变已经绑定的女仆。若结构分裂后任意区域与其他区域不再属于同一结构，整组绑定永久失效。

## 工作、休闲与睡眠

- 方块工作只选择当前结构和活动区内、具有可达交互位置的目标。
- 实体工作保留原任务搜索范围，但只接近同结构内可达的实体。
- 近战不会为了攻击离开结构；远程武器可以从结构上攻击射程和视线内的结构外目标。
- 床和车万女仆自带的休闲方块可以随结构移动；女仆按日程在工作、休闲和休息区域之间通勤。
- 同结构优先步行，没有完整路径时才尝试安全传送。轻微障碍会触发低频局部绕行。
- 其他独立物理结构堆在甲板上仍可能阻挡寻路；本模组不会建立跨多个物理结构的联合路径。
- 女仆可以站在结构上钓普通世界中的水，但不支持跨两个结构钓鱼，也不支持站在普通世界钓结构内部的水。原有椅子或船要求仍保留。

## 当前兼容范围

车万女仆本体已适配跟随、GUI Home、河童罗盘、公共方块与实体工作、日程通勤、床、休闲方块、战斗、物品拾取和钓鱼。拾取范围包含比女仆脚部高一格的掉落物。

已专门适配的可选模组：

| 模组 | 当前支持内容 |
| --- | --- |
| [MaidUseHandCrank](https://modrinth.com/mod/maidusehandcrank) | 结构上的机械动力手摇曲柄搜索、寻路和工作；修复结构 Home 坐标混用导致的服务器卡顿 |
| [Kaleidoscope Compat](https://www.curseforge.com/minecraft/mc-mods/kaleidoscope-compat) + [Kaleidoscope Cookery](https://modrinth.com/mod/kaleidoscope-cookery) | 菜板与石磨；包括高处菜板、邻近一格交互和中断后继续加工 |
| Kaleidoscope Compat + [Kaleidoscope Tavern](https://modrinth.com/mod/kaleidoscopetavern) | 果盆投料与压榨 |
| [Maidsoul Kitchen](https://modrinth.com/mod/maidsoul-kitchen) | 熔炉、扩展瓜类、浆果、果树、扩展动物喂养及公共烹饪流程 |
| Maidsoul Kitchen + [Farmer's Delight](https://modrinth.com/mod/farmers-delight) | 烹饪锅、砧板和煎锅 |
| Maidsoul Kitchen + [Barbeque's Delight](https://www.curseforge.com/minecraft/mc-mods/barbeques-delight) | 串签工作盆与烧烤架 |
| Maidsoul Kitchen + [Ecliptic Seasons](https://modrinth.com/mod/ecliptic-seasons) | 节气农场任务 |
| [Ecliptic Seasons: MultiMod Patch](https://www.curseforge.com/minecraft/mc-mods/ecliptic-seasons-multimod-patch) | 节气扫雪任务 |
| [Maid Tavern](https://modrinth.com/mod/maid-tavern) | 葡萄收获；酿酒的箱子取料、酒桶投料、放瓶接酒、收瓶、成品和副产物入库 |

公共适配也可能让其他附属任务直接可用，但只有上表任务完成了源码核对和专门服务器测试。详细清单见 [附属任务兼容](docs/addon-task-compatibility.md) 和 [Maid Tavern 兼容](docs/maid-tavern-compatibility.md)。

## 未来计划

以下模组已作为仅编译依赖加入开发环境，方便分析和后续实现，但当前版本尚未宣称兼容：

- [Maid Restaurant](https://modrinth.com/mod/maid-restaurant)
- [Maid Useful Tasks](https://www.curseforge.com/minecraft/mc-mods/maid-useful-tasks)
- [Touhou Little Maid: Maid Manager](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid-maid-manager)
- [Maid Storage Manager](https://www.curseforge.com/minecraft/mc-mods/maid-storage-manager)
- [Maid Please Help Me Forge](https://www.curseforge.com/minecraft/mc-mods/maid-please-help-me-forge)
- [Maid Restaurant Storage](https://www.curseforge.com/minecraft/mc-mods/maid-restaurant-storage)

这些依赖不会进入成品 JAR，也不要求玩家安装。TerraFirmaCraft 和 Patchouli 只是 Maid Please Help Me Forge 的开发前置，不代表当前已经完成对应兼容。固定开发版本见 [编译依赖表](docs/compile-dependencies.md)。

## 已知边界

- 不支持侧翻或倒置结构上的步行、工作和睡眠。
- 不进行跨结构桥接寻路，也不把玩家附近但未被 Sable 跟踪的结构作为候选。
- 结构分裂后，附属模组自己保存的方块地址可能需要用该附属的工具重新绑定。
- 通用收纳处理依赖 Sable 的标准移除流程；绕过该流程的收纳模组可能需要专门兼容。
- 自动测试验证服务器逻辑，不等同于完整整合包客户端、长时间驾驶和显示动画测试。

完整规则见 [寻路与 Home](docs/compatibility-stage-one.md) 和 [工作、休闲、休息与通勤](docs/work-rest-compatibility.md)。

## 构建与测试

使用 Java 21：

```shell
./gradlew build
./gradlew runGameTestServer
```

成品位于 `build/libs/tlm_sablecompat-neoforge-1.21.1-0.0.1-beta.jar`。

---

<a id="english"></a>

# English

TLM Sable Compat is a Minecraft NeoForge add-on for [Touhou Little Maid](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid) and [Sable](https://www.curseforge.com/minecraft/mc-mods/sable).

Sable stores moving physical structures such as airships in separate spaces. Blocks use structure-local coordinates while players and maids appear at projected world coordinates. Without an adapter, a maid may walk in the wrong direction, lose a moving Home or workstation, or treat a target in another space as nearby. This mod makes following, Home, work, leisure, sleep, and commuting use the correct space and coordinates.

Current version: `0.0.1-beta`<br>
Supported environment: Minecraft `1.21.1`, NeoForge `21.1.248+`<br>
Tested with: Touhou Little Maid `1.5.3`, Sable `2.0.5`

## Installation

Install TLM Sable Compat, Touhou Little Maid `1.5.3+`, and Sable `2.0.5+` on both client and server. Task add-ons are optional; install an add-on and its prerequisites only when using that integration. Optional mods are not bundled or required to load this mod.

This project is still in development. Later dependency versions may load, but only the versions above are currently tested. Migration from earlier configuration, Home, or save formats is not provided.

## Core rules

### Following the owner

Sable's current tracking result is the sole authority for whether the owner belongs to the normal world or a physical structure. Nearby ships are never guessed as targets.

- In the normal world, Touhou Little Maid's original following rules remain in effect.
- On the same structure, the maid walks and pathfinds in local coordinates.
- Between structures, or between a structure and the normal world, the maid searches for a safe landing near the owner in the destination space and teleports. No cross-structure walking route is calculated.
- Flying over or near an airship does not select it; Sable must actually track the owner on it.
- If the owner flies away from a high structure and no safe world landing exists, the maid stays on the previous structure, continues valid local activity, and retries later.
- A maid that accidentally leaves the selected structure tries to return to a safe local position.

Landings require reliable support, enough body space, and no fluid, world-border, or other-structure collision. Sitting, sleeping, leashes, vehicles, and Home mode continue to restrict following.

### Home

Enabling Home in the maid GUI creates a simple Home at her current position. On a Sable structure, the structure identity, local position, and support anchor are stored, so Home moves and rotates with the structure.

While Home is enabled, the maid no longer follows her owner. Work, leisure, sleep, and return behavior stay within the Home structure and active area. A normally unloaded structure only suspends Home. Removing or storing it, losing the anchor, or splitting activity anchors across different spaces invalidates the binding. AI pauses beyond the supported tilt angle and resumes when the pose is usable. Wall and ceiling walking are unsupported.

### Kappa Compass

The compass assigns up to three schedule areas:

1. Right-click blocks in this order: work, leisure, sleep.
2. One or two areas are allowed; missing areas use Touhou Little Maid's fallback rules.
3. Right-click a maid you own with the recorded compass to bind it.
4. Sneak-right-click a block to clear the compass.
5. Sneak-right-click a maid to clear the schedule areas. With Home enabled on a structure, a simple Home at the maid's current position is kept or rebuilt.

Valid use:

- All areas are in one dimension and the same normal-world space or the same Sable structure.
- Consecutive recorded points are at most 64 blocks apart.
- For a structure binding, Sable currently tracks the maid on that structure and she is within 32 blocks of the nearest point.
- Workstations, beds, leisure blocks, and add-on devices are inside the active area and selected structure, with a valid interaction position.

Invalid use:

- Mixing the normal world with a structure, two structures, or different dimensions.
- Recording a fourth area or placing consecutive points more than 64 blocks apart.
- Binding a structure compass while the maid is in the normal world, on another structure, or too far away.
- Using records from an unloaded, removed, stored, excessively tilted, split, or anchorless structure.
- Binding an add-on workstation, barrel, or chest outside the Home/Compass area or on another structure.

Invalid operations report a specific reason and preserve the previous valid configuration. A successful bind gives the maid an independent copy, so later compass edits do not affect her. If a split puts any recorded area on another structure, the entire group becomes permanently invalid.

## Work, leisure, and sleep

- Block tasks select only reachable interaction positions in the selected structure and active area.
- Entity tasks keep their original search radius but approach only reachable entities on the same structure.
- Melee tasks do not leave the structure. Ranged weapons may attack off-structure targets within range and line of sight.
- Beds and built-in leisure blocks move with the structure; maids commute between their schedule areas.
- Walking is preferred within one structure. Safe teleportation is used when no complete local path exists. Small obstructions can trigger a low-frequency detour.
- Independent physical objects may still block deck paths. No route is built across several structures.
- A maid on a structure may fish in normal-world water. Fishing between two structures, or from the normal world into structure-local water, is unsupported. The original chair or boat requirement remains.

## Current compatibility

Touhou Little Maid support covers following, GUI Home, Kappa Compass areas, common block and entity work, schedule commuting, beds, leisure blocks, combat, item pickup, and fishing. Pickup also includes drops one block above the maid's feet.

Specifically supported optional mods:

| Mod | Supported features |
| --- | --- |
| [MaidUseHandCrank](https://modrinth.com/mod/maidusehandcrank) | Create hand-crank search, navigation, and work; fixes a server stall caused by mixed Home/world coordinates |
| [Kaleidoscope Compat](https://www.curseforge.com/minecraft/mc-mods/kaleidoscope-compat) + [Kaleidoscope Cookery](https://modrinth.com/mod/kaleidoscope-cookery) | Chopping board and millstone, including raised boards, neighboring interaction, and interrupted cutting |
| Kaleidoscope Compat + [Kaleidoscope Tavern](https://modrinth.com/mod/kaleidoscopetavern) | Pressing tub loading and pressing |
| [Maidsoul Kitchen](https://modrinth.com/mod/maidsoul-kitchen) | Furnace, extended melon, berry and fruit farming, extended animal feeding, and shared cooking behavior |
| Maidsoul Kitchen + [Farmer's Delight](https://modrinth.com/mod/farmers-delight) | Cooking pot, cutting board, and skillet |
| Maidsoul Kitchen + [Barbeque's Delight](https://www.curseforge.com/minecraft/mc-mods/barbeques-delight) | Skewer basin and grill |
| Maidsoul Kitchen + [Ecliptic Seasons](https://modrinth.com/mod/ecliptic-seasons) | Seasonal farm task |
| [Ecliptic Seasons: MultiMod Patch](https://www.curseforge.com/minecraft/mc-mods/ecliptic-seasons-multimod-patch) | Seasonal snow-clearing task |
| [Maid Tavern](https://modrinth.com/mod/maid-tavern) | Grape harvesting; brewing storage, barrel loading, bottle filling/collection, and result/byproduct storage |

Common adapters may help other tasks, but only those listed above have dedicated source review and server tests. See [add-on task compatibility](docs/addon-task-compatibility.md) and [Maid Tavern compatibility](docs/maid-tavern-compatibility.md).

## Planned compatibility

These mods are compile-only development dependencies for analysis and future implementation. The current release does not claim compatibility with them:

- [Maid Restaurant](https://modrinth.com/mod/maid-restaurant)
- [Maid Useful Tasks](https://www.curseforge.com/minecraft/mc-mods/maid-useful-tasks)
- [Touhou Little Maid: Maid Manager](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid-maid-manager)
- [Maid Storage Manager](https://www.curseforge.com/minecraft/mc-mods/maid-storage-manager)
- [Maid Please Help Me Forge](https://www.curseforge.com/minecraft/mc-mods/maid-please-help-me-forge)
- [Maid Restaurant Storage](https://www.curseforge.com/minecraft/mc-mods/maid-restaurant-storage)

They are not shipped in the output JAR and players are not required to install them. TerraFirmaCraft and Patchouli are development prerequisites of Maid Please Help Me Forge; their compile-classpath presence does not mean compatibility is complete. See the [pinned compile dependency list](docs/compile-dependencies.md).

## Known limits

- Walking, working, and sleeping on sideways or inverted structures are unsupported.
- No path is built across structures, and a nearby structure not selected by Sable is never considered.
- After a split, block addresses stored by an add-on may need to be selected again with that add-on's tool.
- Generic stored-structure handling relies on Sable's standard removal lifecycle. A mod that bypasses it may need dedicated support.
- Automated tests cover server logic, not full modpack client behavior, long-duration piloting, or rendering.

See [pathfinding and Home](docs/compatibility-stage-one.md) and [work, leisure, sleep, and commuting](docs/work-rest-compatibility.md) for full rules.

## Build and test

Use Java 21:

```shell
./gradlew build
./gradlew runGameTestServer
```

The output is `build/libs/tlm_sablecompat-neoforge-1.21.1-0.0.1-beta.jar`.
