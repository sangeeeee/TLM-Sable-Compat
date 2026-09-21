# 0.0.2-beta：结构 Home 后魂符释放异常

## 日志证据

读取的是 `E:/Program Files/Earth2 Reborn/.minecraft/versions/Earth2 Reborn/logs/` 中的 `latest.log` 与 `debug.log`，文件最后更新时间为 2026-09-21 14:32:29。

`debug.log:70228`，14:29:40.887，MUHC 输出的实际搜索参数为：

```text
Home center: (2.04810255E7, 86.5, 2.04871765E7), radius: 11
Maid position: (-135.3355791179539, 271.86926885346224, -30.64683195781662), radius: 4
```

前者是 Sable 的结构存储区坐标，后者是原世界坐标。MUHC 1.6.2 的 `UseHandCrank.findCrankHandle` 直接把女仆的限制中心与实体世界位置交给 `getCrankInDoubleCircleUnion`。

该方法原实现取两个圆的共同包围矩形，再遍历矩形内所有区块。按日志参数计算为 `1,280,074 × 1,280,453 = 1,639,074,593,522` 个区块。即使查询的是空 POI 列也会触发存储读取，正常服务器无法在合理时间内完成。

`latest.log:10650`、`:14132`、`:20884` 等重复线程记录显示服务器卡在同一调用链：

```text
UseHandCrank.findCrankHandle
  -> getCrankInDoubleCircleUnion / lambda
  -> PoiManager.getInChunk
  -> SectionStorage.readColumn
  -> CompletableFuture.join
```

ModernFix watchdog 在 14:31:02、14:31:44、14:32:26 报告同一 tick 已耗时约 82、124、166 秒。这是主线程长期无法结束当前 tick 的直接证据。日志没有提供女仆被删除或死亡的证据；后续魂符释放“消失”与客户端仍能操作、服务端却无法处理交互的现象一致，不能据此断言存档中的女仆数据已丢失。

## 原因与修复

兼容模组将 Home 改为结构局部坐标后，未适配 MUHC 仍要求世界坐标的查询入口，触发了此次问题。MUHC 虽然已有 Sable 适配，但其距离计算只投影候选曲柄，不投影传入的 Home 中心。

本次增加可选的 `HandCrankSearchMixin`：

- 进入曲柄搜索时，将 Home 中心转换为当前世界位置，使 POI 范围和距离过滤使用一致坐标。
- 通过 `PoiSearch` 分别遍历两个中心附近的区块，合并去重。即使合法的普通世界 Home 距离女仆很远，也不再遍历中间空白区域。
- 没装 MUHC 时，伪目标 Mixin 自动跳过，不增加强制依赖，也不修改通用 POI 管理器。

保留女仆原模组与 Sable 的魂符释放流程。核对整合包女仆 snapshot JAR，相关 `ItemSmartSlab`、`AbstractStoreMaidItem` 类与开发依赖的字节码一致。Sable 在 `ServerLevel.addFreshEntity` 中已经将结构地址转换为世界位置；额外做一次转换反而会错误。

女仆原模组的 `SlabClickEvent` 会在魂符收纳前主动关闭 Home。本次测试确认兼容绑定引用仍保存，但释放后不擅自重新打开 Home。

## 验证与更新

- 基础环境：13 项 GameTest 全部通过，包含实际魂符收纳、移动结构后释放以及新魂符释放。
- 加载整合包的 MUHC 1.6.2 与 Create 6.0.10：14 项 GameTest 全部通过，包含真实曲柄 POI 查询和完整工作点选择方法。
- `build` 成功，输出 `build/libs/tlm_sablecompat-neoforge-1.21.1-0.0.2-beta.jar`。

可选依赖测试命令（路径替换为本机 JAR）：

```text
gradlew runGameTestServer -PmuhcTestJar="path/to/MaidUseHandCrank.jar" -PcreateTestJar="path/to/create.jar"
```

退出游戏后，用新版替换 `mods` 中的 `0.0.1-beta`，不要同时保留两版，再重启游戏。此次没有修改 Earth2 Reborn 的模组文件或存档，也没有在完整整合包客户端中实测修复后的表现。
