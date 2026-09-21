# 附属任务编译依赖

这些依赖用于后续编写女仆任务适配，使用 Minecraft 1.21.1 / NeoForge 版本，与当前 Earth2 Reborn 开发环境一致。

| 模组 | 固定版本 | 发布记录 |
| --- | --- | --- |
| Kaleidoscope Cookery | 1.5.0-neoforge+mc1.21.1 | [Modrinth](https://modrinth.com/mod/kaleidoscope-cookery/version/v62omIkI) |
| Kaleidoscope Tavern | 1.2.0-neoforge+mc1.21.1 | [Modrinth](https://modrinth.com/mod/kaleidoscopetavern/version/W9ILsQt7) |
| Kaleidoscope Compat（菜板、石磨、果盆任务提供者） | 2.9.7-neoforge+mc1.21.1-Patch | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/kaleidoscope-compat/files/8920105) |
| MaidUseHandCrank | 1.6.2 | [Modrinth](https://modrinth.com/mod/maidusehandcrank/version/Qu15bii2) |
| Ecliptic Seasons | 0.15.0-rc-3-1 | [Modrinth](https://modrinth.com/mod/ecliptic-seasons/version/p7byuyzM) |
| Ecliptic Seasons: MultiMod Patch | 0.32.1 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ecliptic-seasons-multimod-patch/files/8813347) |
| Create（MUHC 必需前置） | 6.0.10 | [Modrinth](https://modrinth.com/mod/create/version/UjX6dr61) |
| Maidsoul Kitchen | 1.21.1-beta-v0.1.4 | [Modrinth](https://modrinth.com/mod/maidsoul-kitchen/version/JvPRj6e1) |
| Farmer's Delight | 1.21.1-1.3.4 | [Modrinth](https://modrinth.com/mod/farmers-delight/version/XTVZDOol) |
| Barbeque's Delight | 1.3.0 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/barbeques-delight/files/8004721) |
| Maid Tavern | 1.2.0-neoforge+mc1.21.1 | [Modrinth](https://modrinth.com/mod/maid-tavern/version/L7NA2k7R) |
| Maid Restaurant | 0.2.9 | [Modrinth](https://modrinth.com/mod/maid-restaurant/version/vEgMAYm3) |
| Maid Useful Tasks | 1.4.2 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/maid-useful-tasks/files/7909120) |
| TLM Maid Manager | 1.0.3-beta2 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid-maid-manager/files/8712327) |
| Maid Storage Manager | 1.15.6 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/maid-storage-manager/files/7976865) |
| Maid Please Help Me Forge | 0.1.3 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/maid-please-help-me-forge/files/7588086) |
| Maid Restaurant Storage | 0.3.2-fix+neoforge-1.21.1 | [Modrinth](https://modrinth.com/mod/maid-restaruant-storage/version/N2ribxx0) |
| TerraFirmaCraft（Maid Please Help Me Forge 必需前置） | 4.2.10 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/terrafirmacraft/files/8831715) |
| Patchouli（TerraFirmaCraft 必需前置） | 1.21.1-93-neoforge | [Modrinth](https://modrinth.com/mod/patchouli/version/BIogJv2D) |

`gradle.properties` 固定发布 ID；`build.gradle` 使用 `compatCompileOnly` 配置扩展 `compileOnly`。不依赖本机整合包路径，不加入默认运行依赖，不打包进本模组，也不新增安装时的强制依赖声明。

普通 Java 编译不会自动读取模组 JAR 内的嵌套 JAR，因此 `extractCompatApis` 将其中以下接口库提取到 `build/compat-api`，仅加入编译路径，并在 IDE 同步时执行：

- Create 内附 Registrate `MC1.21-1.3.0+67`。
- Create 内附 Flywheel `1.0.6`。
- Create 内附 Ponder `1.0.82+mc1.21.1`。
- MultiMod Patch 内附 MixinSquared `0.3.7-beta.1`。
- Barbeque's Delight 内附 L2 Core `3.0.8+1`、L2 Modular Blocks `3.0.0+4`、L2 Serial `3.0.9+5`。

Maidsoul Kitchen 和 Barbeque's Delight 也内附 Registrate `MC1.21-1.3.0+67`，其 SHA-256 与 Create 内附副本一致；提取时按文件名去重，只加入一份。上述内嵌库已检查，没有进一步的嵌套 JAR。

Maidsoul Kitchen、Maid Tavern、Maid Restaurant、Maid Useful Tasks、TLM Maid Manager、Maid Storage Manager 和 Maid Restaurant Storage 所需的车万女仆已由基础依赖提供。Maid Tavern 与 Maid Restaurant 分别复用表中的 Kaleidoscope Tavern 与 Kaleidoscope Cookery；Maid Restaurant Storage 复用 Maid Restaurant。Barbeque's Delight 的 Farmer's Delight 前置，以及 Maid Please Help Me Forge 的 TerraFirmaCraft 与 Patchouli 前置均由本表显式提供。它们都仅用于编译。

必需前置车万女仆，以及 Minecraft、NeoForge 已由现有基础依赖提供，不重复声明。根据所选版本的发布元数据和 JAR 依赖声明，厨房、酒馆、节气没有其他必装独立模组；JEI、KubeJS、Create Connected 等可选联动不属于本次前置范围。

既有 `muhcTestJar` / `createTestJar` 可选测试配置继续保留。另可显式使用 `-PaddonTests`，将本表依赖加入开发运行环境并注册附属任务 GameTest；默认构建仍全部仅编译，不打包、不要求玩家安装。不要同时传入两个测试配置，以免加载重复模组。

Maid Manager 锚点兼容可使用 `-PmanagerTests` 单独加载管理器执行服务器测试；使用 `-PaddonTests` 时不会重复添加依赖。兼容范围见 [Maid Manager 锚点兼容](maid-manager-compatibility.md)。

Kaleidoscope Compat 的 Patch JAR 没有嵌套库，且含有 Gradle ZIP 读取器拒绝的目录条目，因此不参与 `extractCompatApis`。原始 JAR 直接用于编译及可选测试运行，Java JAR 读取器可正常读取；不重新打包第三方模组。
