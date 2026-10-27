# 核心边界与能力核对

当前为 0.3.2 组合候选，尚未允许合入 main 或发布。0.3.1 是已发布基线；0.3.2 分项设备回归通过，但组合检查已暴露间歇性的 legacy Stop，新增框架正向测试的音频启动准备也需验证。以下编号阶段保留当时结果，不能将其“待执行”视为当前状态；详细提交、运行与失败见 VALIDATION-2026-10-25.md。

缓存已独立为进程所有，声音手记支持手动频率/增益/Q 和 AutoEQ 导入、取消、非法值拒绝。普通系统路由保留，Android 独占 USB 桥接已去除；真实外接设备路由仍由本地 agent 验证。账号、歌单及歌词的独立 PR11 不计入此组合候选。

本轮基线：`888f1a4ad397213115ebabf39a33bf1705854367`。这是代码依赖核对，不是新增的功能验收通过记录。

## 边界

| 层 | 入口 | 责任与限制 |
| --- | --- | --- |
| Android 播放会话 | `service/MusicService.kt`、`RustAudioFocus.kt` | 唯一 MediaSession、前台通知、队列恢复、引擎切换、系统音频焦点与 noisy 广播；不能依赖 Compose 生命周期维持播放 |
| 音频实现 | `engine/CPPlayerManager.kt`、`FlickPlayer.kt`、`RustEngine.kt` | Media3 与 Rust 的播放、解码、DSP 适配；保持 JNI 名称和普通系统路由，默认不编译 USB 独占 |
| 音乐源协议 | `provider/`、`app/SourceStore.kt`、`MusicRepository.kt` | 模块生命周期、统一请求、平台响应映射；不能放到 UI 组件中调用或保存账号数据 |
| 曲库与下载 | `app/LibraryStore.kt`、`Downloads.kt` | 导入校验、原子保存、手机扫描、系统 DownloadManager、失败重试；后台传输由系统持有 |
| 队列与界面状态 | `QueueStore`、`EPlayerViewModel`、`CloudViewModel` | 会话状态投影、页面可见时进度更新、异步操作；避免每帧写盘或后台 UI 轮询 |
| 播放呈现 | `app/PlayerScreen.kt` | 布局、元数据、进度拖动和操作回调；不持有解码器、不直接解析源、不自行维护第二套队列 |
| 插画渲染 | `ui/artwork/` | 共用人物底图、固定眼睛纹理、阻尼动作；只读音频能量，不能申请麦克风或接管音频输出 |

## 不能把“源码还在”写成“完整功能已经保留”

- 搜索、推荐、账号、歌单、收藏、评论、下载已经有新页面或调用路径，但真实源、真实账号及故障切换仍需本地 agent 验证。
- 均衡器：ExoPlayer 按实际 audioSession 能力应用设置；Rust 手动参数/AutoEQ 已接新页面，跨采样率重建输出后重新应用 DSP，已有真实幅度红绿证据。系统引擎不展示其不支持的频率/Q。该通过不等于间歇性 Stop 已修复。
- `CrossfadeManager` 的旧双播放器实现仍在目录中，当前 `MusicService` 已是单播放器，没有直接使用该类。不要为了“精简”先删源码，也不要继续声称新会话已支持全部旧淡入淡出操作；应先决定并恢复对外能力。
- 定时停止由 `PlaybackSleepTimer` 和 MediaSession 持有，自适应设置区可开启、调整、取消；0.3.1 合成音频后台检查已通过。缓存管理入口和淡入淡出仍未恢复，不把已接 DSP 与未接能力混为一谈。
- 新的普通版/自用版必须分别验证模块边界；普通版的自动测试不得依赖或上传自用版模块及真实登录凭据。

## 后续顺序

先保持可复核的 MediaSession 播放基线；用独立的播放页完成横竖屏和大字体适配。随后统一暂停/恢复命令语义，修复缓冲期无法暂停的问题，并让关闭角色动画仍使用相同修复底图。再逐项恢复缺失的控制入口与自动检查，最后按真实依赖删除已经替代且无必要调用的旧层。不得用一轮大规模删除代替功能迁移。

## 焦点所有权补充

Rust 焦点请求绑定固定 Player，关闭旧所有者后忽略排队回调；ExoPlayer 继续使用自身焦点管理，不另建第二套请求。临时失焦可恢复原播放请求（包括缓冲期），永久失焦、定时停止与 noisy 暂停取消恢复意图。重复的临时失焦不能覆盖首次恢复意图。切引擎与服务结束显式释放请求。

四项纯 JVM 策略回归通过；这不是系统电话、蓝牙、耳机等实机路由验证。双引擎真实 WAV 播放/暂停/拖动、切回与定时保留已加入集成检查，待新提交 CI。尚未验证所有外部控制器在失焦期间的交互，不据此声称焦点验收完成。

第七阶段修复 `FlickPlayer.handleSetMediaItems` 忽略 `startPositionMs`：未启动时保留位置，播放时按 Play→Seek 顺序发送；暂停时跨曲 seek 与移除当前曲使用同一待播放状态，不启动旧解码。旧 native 事件在待播放阶段不能覆盖新位置，进度轮询也遵守 seek 后的短暂旧值保护。新增读取原生实际进度的 12 秒断点检查，待对应设备结果；网络流的未缓存远端断点、快速反复切换与跨进程恢复仍未验收。

## 已剥离的旧插画 UI

`WitchPlayerView` 的全部生产调用均使用 artworkOnly；播放页已由 Compose 提供队列、歌词、进度和播放控制。因此移除 View 内六个隐藏 Button/SeekBar、系统音量管理器、旧曲名/时长/控件绘制及 Controls 回调，接口缩为 `setPlayback(playing, requested, position)` 和动画开关。画布只加载天空与共用修复人物，不再解码 865×1280 的旧参考图或加载旧 UI 字体。保留参考素材及许可；未改人物、皮肤修复、眼睛或骨架资源。

现有像素/动作测试继续覆盖同一画布，更新其调用入口；本地编译通过，设备像素与视觉结果待新 CI。旧隐藏音量条本来未在新页面展示，普通 Android 音量键及系统路由保持原路径。`CrossfadeManager` 仍只有自身定义、没有新会话调用，但它代表尚未恢复的对外能力；在实现替代前不删除。

第七阶段还将通知返回检查改为打开系统通知面板、按歌曲标题找到可点击卡片并点击，保存 `visual-check/notification-controls.png`；若系统未暴露可点击卡片则失败，不用直接启动 Activity 或发送 PendingIntent 代替通过。该新交互尚未设备运行。

## 当前阻塞：Rust 真播放

CI 40124903082 的纯画布/布局等 24 项通过，但完整流程切 Rust 后未进入有效播放，1 失败、1 真实登录条件跳过；没有 Rust 播放/拖动/切回通过证据。第七阶段起始进度修复不能视为已解决这个未知阻塞。第八阶段将非缓冲原生错误及 JNI Play/Seek 拒绝回传 Media3，补初始化/传输和失败状态诊断，先定位再继续 DSP 入口，不能仅靠延长超时或删除检查过关。

## 第九阶段：已定位时长适配缺口

CI 40126620638 的原生解码日志显示两首合成音频推进到曲终，而 MediaController.duration 始终 TIME_UNSET。源码不存在 Progress 事件产生点，适配层时长只在 Progress 处理器更新，原有轮询仅更新位置。采样现已送入同一处理器，既更新时长也更新进度状态流和幅度；采样仍为 500ms，JNI 与 UI 状态线程分开，不增加后台 UI 轮询。状态修正后恢复进度任务，暂停/曲终取消它。

切引擎现在复制循环、随机和软件音量；Rust 也向 Media3 报告自己的音量，避免切回时丢失。曲终不被后续 stopped 事件降成 IDLE，支持一次点击重播；加入真实原生配置保持、曲终稳定/重播、幅度和错误恢复检查。以上本地构建通过，但尚待新 CI，不能宣称完整验收通过。

第九阶段设备检查已走通有效时长、原生实际断点、幅度、暂停/拖动和曲终/重播；整体仍因错误后未 prepare 的恢复检查失败。第十阶段在原生 Play 创建输出后应用保存的软件音量，逻辑音量用 volatile 传递至 JNI 线程，并验证真实渲染 RMS 的静音与恢复。恢复顺序与界面一致，需 prepare；新设备结果未取得前不标全部通过。

## 声音设置接续（第十一/十二阶段）

SoundSettingsStore 是偏好到界面状态的应用层适配；声音编辑组件不调用 JNI 或音频效果器。服务/引擎负责应用，Rust 串行处理，Exo 按实际 audioSession 能力公开状态。显式 releasePlayer 清理系统效果器；跨淡入淡出双播放器时按会话释放，不执行全局 releaseAll。新编辑页有 EQ、Rust 空间、系统环绕/低音/响度；前置放大旧方法目前只是字段赋值，不对外显示为已生效能力。AutoEQ/频率/Q 编辑及淡入淡出仍需后续恢复。

新增真实合成音频 EQ 增强/关闭测试和大字 UI 回归已经编译，设备结果待 CI 回写。不要以“界面出现开关”或源码存在代替渲染音频验证。

## 第十四阶段：旧外观配置剥离

全项目 Kotlin/Java/Rust/XML 及测试检索确认，PureBlackMode、ThemeMode、FollowCoverApp/Mini/Player、UseFluidBackground、FontRoundness、HideNavbarOnScroll、WavyProgress 的 get/save 共 18 个访问器均无调用方；移除相应 9 个常量及未使用的 createCustomColorScheme 适配。没有清除已存偏好，不触碰音乐源协议/JNI/音频焦点/普通系统 USB 路由、字体资产和许可。19 项 JVM 与主程序/设备测试包本地构建通过；删除后的设备回归仍待最新 CI，不等同于已整体验收。

## 缓存入口审计（尚未接 UI）

此段记录最初审计：当时 Exo 使用服务持有的 SimpleCache，旧 clearAudioCache 会 release 后递归删目录。现在已改为进程持有的 PlaybackCache（cache/media）并删除该未调用清理方法；本地/导入文件走 DefaultDataSource，远端首次 open 才建立缓存。既有 CacheDataSource 可持有缓存引用，仍不能接 release/delete 清理按钮。CacheManager.clearAll 将 SEARCH_HISTORY 和 LOCAL_SONGS 等持久数据也列入缓存类型，亦不能直接接“清缓存”。恢复入口前应按数据职责拆分：保留历史、账号、曲库与下载，使用会话命令序列化播放缓存清理并验证后续可继续播放。当前没有暴露或执行清理，不删除用户数据。

## 第十五阶段（独立分支进行中）

系统音效能力值移入 core/audio/AudioEffectCapabilities；应用状态与呈现仅传纯数据，不引用 Exo 管理器的嵌套类型。返回设置时取消未完成的 AutoEQ 读取/预览，防止离开声音页后弹出迟到的结果。既有 19 项 JVM、debug 主程序/设备测试包构建通过；尚未取得本阶段设备结果，b34e679 的通过记录仅对应第十四阶段。后续缓存操作必须以服务和存储职责明确为前提，不调用旧 release/delete 清理。

第十六阶段缓存边界已改为 core/playback/PlaybackCache → SimpleCache，DataSourceFactoryProvider → PlaybackCache，MusicService → Player。不再 engine → MusicService → engine 循环引用；只持有 Application 上下文。存储路径与 256MiB LRU 保持已有行为，原无调用方的释放/删目录方法移除，不声称已有缓存清理能力。真实断网后命中缓存的新增回归已编译，设备待执行。用户导入、下载及历史不由该对象清理。

当前进展修正（0.3.2）：声音编辑/AutoEQ 已在 0.3.1 实现，旧段落的“待接通”仅是阶段历史；主线 331d312 的真实 EQ 与释放契约已通过。PlaybackCache 现在独立于 MediaSession 服务，实例跨服务重建保持，256MiB 流媒体缓存不涵盖用户导入/下载。PR #4 组合主线设备结果仍待执行。

## USB 独占 Android 桥接残留剥离

全项目 Kotlin/Java/设备测试检索确认无生产调用后，删除 RustEngine 内 8 个独占注册/格式/硬件音量/静音/清理/等待包装器和 12 个未使用 JNI 声明，净减 82 行。不再提供设备 fd 注册、独占锁或硬件控制的 Android 入口；仅保留 NativeEngineTest 使用的只读“无独占会话”探针与普通诊断。未改 DSD 解码/普通输出、AudioManager、音频焦点、系统 USB/耳机路由和默认无 uac2 的构建。既有原生 .so 的可选上游导出尚未删除；本阶段不宣称已重编原生库。19 JVM/主程序/设备包构建通过；新组合设备结果未执行，与 EQ 停止诊断分支分开，不因删无调用方法宣称该问题已修复。
## 流媒体缓存延迟初始化（独立性能阶段）

创建播放器/数据源不再立即扫描 SimpleCache 目录和数据库；只有打开远端流时才建立缓存数据源。DefaultDataSource 的 file/content 直接路径不会初始化流媒体缓存，既有进程缓存路径/LRU/离线命中保持。延期数据源保留传输监听、URI/响应头及关闭语义，持有 Application 上下文。新增实际文件/ContentProvider 字节读取且缓存提供器不得被调用的检查，远端测试增加 factory/createDataSource 不触碰缓存、两次 open 各请求同一缓存的断言。19 JVM 和主程序/设备包编译通过；新增设备结果未执行，不声称已有启动耗时/实机性能测量。仍不提供危险的缓存 release/delete 清理按钮。

## 下一步生命周期精简的前置条件

| 现有路径 | 已确认的边界问题 | 先补的验收与约束 |
| --- | --- | --- |
| MusicService.SET_ENGINE | 旧顺序先发布空播放器，再恢复队列；候选已调为恢复后交接 | 实际 framework 状态订阅、两次往返引擎、合法 Stop、三轮 EQ；不得全局屏蔽旧控制协议 |
| FlickPlayer.streamToRustEngine → StreamProxy | 通用 Exception 捕获包含协程取消；本地切曲未统一取消旧下载；迟到下载回调可更新当前路径 | 延迟 loopback 下载中切到本地曲，释放上游后仍须本地实际进度/时长/幅度；取消不得改当前会话为 IDLE |
| StreamProxy.streamToCache | 输出流未由 use 管理，失败/取消时可能留打开的半文件；IO read 不主动检查取消 | 完整文件仍可断网重读；取消关闭数据源和输出，仅删除自己未完成的文件，不删正在播放或用户文件 |
| FlickPlayer 的 seek/underrun 恢复 | 部分直接 RustEngine.play 绕过共同音量/DSP 初始化 | 实际恢复音频幅度/静音及采样率回归，再统一启动入口，不靠状态标志假装播放 |

这些流媒体问题来自源码审计，尚未在本阶段改产品或标红绿通过。先完成引擎交接的设备结果，再以独立阶段处理下载所有权；缓存清理和 Crossfade 不提前删除，保持能力清单与真实调用路径一致。

依赖精简预审：实际 Gradle standardDebugRuntimeClasspath 与 main/test/XML 调用搜索表明，旧 navigation-compose、media3-ui、palette-ktx、material3-window-size-class 直接声明无现存源码调用，旧 Sentry 用途的 window compileOnly 也无调用。尚未移除，需按阶段编译及设备回归确认传递依赖边界；window 本身仍被当前 Compose 链传递依赖，不能承诺完全去除或节电。Coil、ZXing、Lyricon provider 仍有实际调用，继续保留。
