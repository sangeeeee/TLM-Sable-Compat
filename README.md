# TLM Sable Compat

为 [车万女仆（Touhou Little Maid）](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid) 与 [Sable](https://www.curseforge.com/minecraft/mc-mods/sable) 提供兼容支持的 Minecraft NeoForge 模组。

当前版本：`0.0.1-beta`  
支持环境：Minecraft `1.21.1` / NeoForge `21.1.248+`

## 安装

将本模组、Touhou Little Maid `1.5.3+` 和 Sable `2.0.5+` 放入同一个 NeoForge 客户端与服务端的 `mods` 文件夹。

## 第一阶段功能

- 跟随以 Sable 当前跟踪的主人所在结构为准。只在该结构寻找安全落点；主人飞越其他结构不会选中它们。
- 同结构使用局部坐标寻路；跨结构直接尝试安全传送。主人离开结构后，附近没有普通世界落点时，女仆保留当前结构上的其他移动目标。
- GUI Home 与河童罗盘活动区域记录结构身份和局部坐标，随结构移动。罗盘限制为同维度、同一普通世界或同一结构；结构绑定检查空间与最近活动点 32 格距离。
- 结构分裂时按实际搬移的锚点迁移记录；各活动区分属不同结构则整组永久失效。正常卸载只暂停，移除或收纳则使旧绑定永久失效。
- 大幅倾斜时暂停结构内寻路，恢复姿态后继续。传送同时检查原世界和其他结构的碰撞。

具体规则、测试范围与手动验证步骤见 [第一阶段兼容说明](docs/compatibility-stage-one.md)。目前验证的依赖版本为女仆 `1.5.3`、Sable `2.0.5`；版本声明允许更新版本，但尚未逐一验证。

## 构建

使用 Java 21 运行：

```shell
./gradlew build
```

构建会自动下载所需模组依赖。输出文件位于 `build/libs/tlm_sablecompat-neoforge-1.21.1-0.0.1-beta.jar`。

运行使用实际 Sable 和女仆模组的服务器集成测试：

```shell
./gradlew runGameTestServer
```

测试只在该开发启动配置中注册，不会在正常游戏启动时自动运行。

---

An add-on providing compatibility between [Touhou Little Maid](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid) and [Sable](https://www.curseforge.com/minecraft/mc-mods/sable) for Minecraft NeoForge.

Current version: `0.0.1-beta`  
Supported environment: Minecraft `1.21.1` / NeoForge `21.1.248+`

## Installation

Install this mod, Touhou Little Maid `1.5.3+`, and Sable `2.0.5+` in the `mods` folder on both the NeoForge client and server.

## Building

Run with Java 21:

```shell
./gradlew build
```

The required mod dependencies are downloaded automatically. The output is `build/libs/tlm_sablecompat-neoforge-1.21.1-0.0.1-beta.jar`.
