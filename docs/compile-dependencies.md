# 附属任务编译依赖

这些依赖用于后续编写女仆任务适配，使用 Minecraft 1.21.1 / NeoForge 版本，与当前 Earth2 Reborn 开发环境一致。

| 模组 | 固定版本 | 发布记录 |
| --- | --- | --- |
| Kaleidoscope Cookery | 1.5.0-neoforge+mc1.21.1 | [Modrinth](https://modrinth.com/mod/kaleidoscope-cookery/version/v62omIkI) |
| Kaleidoscope Tavern | 1.2.0-neoforge+mc1.21.1 | [Modrinth](https://modrinth.com/mod/kaleidoscopetavern/version/W9ILsQt7) |
| MaidUseHandCrank | 1.6.2 | [Modrinth](https://modrinth.com/mod/maidusehandcrank/version/Qu15bii2) |
| Ecliptic Seasons | 0.15.0-rc-3-1 | [Modrinth](https://modrinth.com/mod/ecliptic-seasons/version/p7byuyzM) |
| Ecliptic Seasons: MultiMod Patch | 0.32.1 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ecliptic-seasons-multimod-patch/files/8813347) |
| Create（MUHC 必需前置） | 6.0.10 | [Modrinth](https://modrinth.com/mod/create/version/UjX6dr61) |

`gradle.properties` 固定发布 ID；`build.gradle` 使用 `compatCompileOnly` 配置扩展 `compileOnly`。不依赖本机整合包路径，不加入默认运行依赖，不打包进本模组，也不新增安装时的强制依赖声明。

普通 Java 编译不会自动读取模组 JAR 内的嵌套 JAR，因此 `extractCompatApis` 将其中以下接口库提取到 `build/compat-api`，仅加入编译路径，并在 IDE 同步时执行：

- Create 内附 Registrate `MC1.21-1.3.0+67`。
- Create 内附 Flywheel `1.0.6`。
- Create 内附 Ponder `1.0.82+mc1.21.1`。
- MultiMod Patch 内附 MixinSquared `0.3.7-beta.1`。

必需前置车万女仆，以及 Minecraft、NeoForge 已由现有基础依赖提供，不重复声明。根据所选版本的发布元数据和 JAR 依赖声明，厨房、酒馆、节气没有其他必装独立模组；JEI、KubeJS、Create Connected 等可选联动不属于本次前置范围。

既有 `muhcTestJar` / `createTestJar` 可选测试配置继续保留：仅显式提供这两个参数时，才将指定本地 JAR 加入测试运行环境。本次只准备编译依赖，不新增任务适配行为。
