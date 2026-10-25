# ePlayer 开发入口

先阅读根 AGENTS.md、../../docs/EPLAYER-HANDOFF.md、README.md 和 docs/DEVELOPMENT-PLAN.md。分支协作，保留本地 agent 的实机验收记录；云端模拟器通过不代表实机通过。

## 当前源码

- MainActivity.kt：Compose 宿主和通知返回 Intent。
- app/EPlayerApp.kt：导航、首页、曲库、音乐源和设置入口。
- app/PlayerScreen.kt：独立的播放布局、元数据和操作回调，不持有解码器。
- app/SettingsScreen.kt：设置和定时入口；计时任务由服务持有，页面只显示剩余时间。
- core/playback/：播放请求、定时和焦点所有权策略，不依赖 Compose。
- app/EPlayerViewModel.kt：MediaController 状态投影、曲库与下载操作。
- app/CloudViewModel.kt、MusicRepository.kt、SourceStore.kt：云端页面状态、响应映射与模块请求。
- service/MusicService.kt、RustAudioFocus.kt：唯一前台会话、通知、队列和固定 Player 的 Rust 焦点；ExoPlayer 保持自己的焦点管理。
- engine/：Media3/Rust 引擎、DSP、实时能量读数；保留 cp.player JNI 命名空间。
- provider/：JNI/binary/HTTP 模块生命周期与协议兼容。
- ui/artwork/：纯画布、共用修复人物和连续动作，不创建隐藏播放/音量控件。不要重新生成整脸帧造成线稿漂移。

能力边界和迁移缺口见 docs/CORE-BOUNDARIES.md；不能把旧类还存在当成功能已经接通。

## 构建与测试

JDK 21，SDK Platform 37.0，Build Tools 36.0.0；targetSdk 36。正常包 app.eplayer.music，自用包 app.eplayer.music.internal。三 ABI 为 arm64-v8a、armeabi-v7a、x86_64；USB 独占默认关闭。

先运行 tools/prepare-local-signing.sh；私钥不提交。已有三 ABI JNI 可按交接说明恢复，未改 Rust 时用 -PwithRust=false。

```sh
./gradlew assembleStandardDebug assembleStandardDebugAndroidTest -PwithRust=false
./gradlew testStandardFastReleaseUnitTest assembleStandardFastRelease assembleStandardFastReleaseAndroidTest -PwithRust=false -PtestBuildType=fastRelease
./gradlew assembleStandardRelease -PwithRust=false
```

测试源码在 app/src/androidTest。eplayer-preview 工作流先构建，再在 Android 16 加速模拟器验证普通版，保留单独的结果和组件渲染；不自动登录真实账号，不把自用模块嵌入普通版。不要反复强制完整 speed 预编译，不把大构建和慢速本地模拟器抢占同一资源。

做播放器或 UI 改动要执行相关构建和检查；纯交接文档变更无需安装设备。提交前运行根目录检查及相关测试，及时 commit/push。Release 的公开发布需遵守实际审批结果，已有被拒绝交付不能绕过审批重传。

CI 的完整 Instrumentation 使用非调试 fastRelease。R8 release 另做构建和无 Instrumentation 的冷启动检查：测试框架共享 Kotlin/Compose 类，不能把这些类被 R8 删除后的测试运行器启动失败当作应用功能验收。不要为测试框架盲目扩大生产 keep 规则，也不能把 fastRelease 结果说成 R8 全功能通过。

开发分支完整功能检查使用普通/自用非调试 fastRelease 构建；main、release/*、手动触发另外构建 R8 并做启动 smoke。结果元数据单独记录未请求、未执行、通过、失败、超时和中断，不能把跳过 R8 写成通过。
