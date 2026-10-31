# ePlayer

基于 [Aurora-Nasa-1/CPPlayer](https://github.com/Aurora-Nasa-1/CPPlayer) 的 Android 音乐播放器，统一为白纸、天空和手绘墨线风格。应用独立安装标识为 `app.eplayer.music`；保留原项目 Kotlin 包名与 JNI 名称，以免破坏原生引擎接口。

## 当前开发版本0.5.5（组合验证中，未发布）

PR25整合新导航/播放器、纸白蓝天与夜间蓝灰、统一Medium中日字体、默认双曲导入，以及歌词/曲库/队列/统计/音乐源核心。原生适配器退出改为关闭输出线程，CI实际按当前Rust源码编译API28三ABI并核验32个JNI入口；普通Android路由保留，无USB独占。

124547db /CI41232129032：52 JVM通过，68项设备65通过/2个旧原生终态断言失败/1真实登录跳过。修订0f398714要求2秒内引擎移除、进度为空、系统无输出，取消预缓冲后旧响应仍不得恢复播放；CI41234691200实际66通过/2跳过（106.003秒）：额外的队列保护跳过使恢复检查未执行，继续修复夹具写线程清理并让CI拒绝非登录跳过，不计作全绿。SourceStore现归core/source、下载持久化归core/download，保持原账号、模块、偏好和文件数据，迁移后的最终组合设备/R8尚待。完整断点和截图证据以[当前计划](docs/DEVELOPMENT-PLAN.md)为准。

公开预览仍为0.3.2；旧失败0.5.2不发布，私有内置模块APK不公开。最终主线R8、确切原始通用包双引擎跨应用和新Release均未完成，不能用构建或分项绿色代替。

### 0.5.4数据边界（已独立验证）

agent/eplayer-data-boundaries将Track/LibraryStore/RecentStore移入core/library，QueueStore移入core/queue；服务不再经混合UI文件取得队列/最近播放，界面只消费核心数据。旧JSON/偏好键/文件名/URI与源标识保持。33 JVM及本地应用/设备包构建34秒通过；本分支51项已50通过/1登录跳过（103.113秒，CI41113754192），此版本R8尚未执行。0.5.3保留给本地PR17界面分支，不覆盖其成果。

### 历史主线0.5.2失败（保留证据，不发布）

PR22已正常合入main c17dc4a，[CI41112575118](https://github.com/SturgeonIsSleepy/codex-source/actions/runs/41112575118)两版R8构建通过，但独立51项49通过/1 EQ停止失败/1登录跳过（106.585秒），冷启动前置阻断。确切原始通用包跨应用与Release尚未执行；下面是已通过分支组合的范围。

当前agent/eplayer-052-core-integration汇合持续公开SessionPlayer、纯歌词核心/原子缓存和队列恢复索引修复，完整51项设备已通过（CI41110662240，50通过/1真实登录跳过，103.810秒），主线R8与确切包待执行；原人物/动作/Medium字体不改。主线0.5.1虽R8构建通过但独立47项失败，未发布；后续按最终0.5.2统一执行一次主线R8/全轮→确切原始包双引擎跨应用→Release，不用旧分支绿色代替组合验收。

独立结果：歌词48项47通过/1跳过（107.921秒）；会话现有47项46通过/1跳过（92.607秒）；队列三个分项通过但整轮51项仍因已知EQ Stop失败。完整失败、证据与当前断点见[实施计划](docs/DEVELOPMENT-PLAN.md)。

### 0.5.1组合验收

合入独立0.4音乐源与0.5首页/导航/歌词/音质重构，并接续0.3.3的首屏人物、焦点交接及跨曲下载所有权修复。人物、动效、中日文字体、普通/原生双引擎完整保留；普通版不附音源或歌曲录音。首页统计与继续播放、网易云二级页、独立曲库、最右侧设置及全屏歌词沿用0.5实现。当前组合CI40761643034通过47项（46通过/1登录跳过，94.027秒），主线R8与确切包待执行；变化与原验证边界见[0.5.0记录](docs/LIGHT-0.5.0.md)。

0.3.2已发布验证预览：手动频率/增益/Q与AutoEQ、换采样率后DSP恢复、联网缓冲暂停、完整队列状态交接、进程所有的延迟缓存与下载取消清理；删除无调用的USB独占桥接。源码基线113acb0，完整边界见[变更](CHANGELOG.md)和[验证索引](docs/VALIDATION-2026-10-25.md)。

[0.4.0 基础功能交付](docs/DELIVERY-0.4.0.md) 的源码、签名与验收记录保留。普通版不内置音源；自用版由本地工具嵌入用户指定模块。

## 0.3.2 验证预览版

[APK、三ABI原生库、日志和验证归档](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.3.2)。主线Android 16检查36通过/1登录条件跳过（37项，104.993秒）；普通/自用R8构建通过。未经重打包的通用R8包另通过系统/原生2项跨应用检查（55.117秒）：系统文件选择器导入、播放/暂停、12秒定位、后台与实际通知点击返回。最终该通用包安装/冷启动1749ms和发布归档通过。

原始APK：versionCode106，SHA256 `13c1401d2121e2cde477c2b3d08064366c65b34357e210253c641aa775f9a27c`，minSdk28/target36。CI临时证书与0.3.1不同，不保证覆盖升级；已有导入与设置请先备份，固定本地签名/实机登录由本地agent接续。旧[0.3.1](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.3.1)和[0.3.0](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.3.0)附件保留。

验证ZIP的index.json索引21个历史artifact和6份原始任务日志。确切R8截图/无声短片位于history/12766192574.zip的r8-check、native-motion.mp4和system-motion.mp4；主线布局图见history/12765768463.zip。失败、SDK准备阶段未执行和登录跳过保持原结论。

尚未完成：当前组合主线R8与确切包验收、实机音质/路由、真实账号、长时稳定性、淡入淡出和安全缓存管理入口。0.3.3首屏人物/跨曲下载等核心39项已有独立通过证据，不能直接计为新组合通过。

## 0.1.1 历史预览版

没有音乐源时，首页直接提供魔女之旅歌单导入与本地曲库入口；本地曲库默认显示手机音乐，不再自动跳转所有文件访问设置。在线能力仍需用户配置音乐源。本轮构建和设备验收结果见交接页。

## 功能与设计

- 以 CPPlayer 的音频与音乐源协议为核心，迁移搜索、曲库、下载、歌词、评论、队列和 MediaSession；声音手记提供按引擎能力显示的 EQ/音效和带预览的 AutoEQ 文本导入；完整文件操作、淡入淡出与缓存入口仍待逐项验收。
- 设置提供 15–120 分钟定时停止，可调整或取消；由播放服务执行，退出页面不取消，进程被系统结束后不恢复旧倒计时。
- 首页与曲库提供《魔女之旅》默认歌单：灰色のサーガ、リテラチュア。音频由用户导入，不随应用分发。
- 竖屏与横屏均使用伊蕾娜插画播放器，控件调用 CPPlayer 原有的播放服务。
- 所有表情共用同一张修复底图；只在眼睛区域移动眼睑。头发、帽子和手臂不随表情切换图层。
- 本地音频分析提供节拍与能量；ExoPlayer 与 Rust 引擎都提供实时 PCM 能量，让在线歌曲也影响摇摆幅度。没有能量数据时使用连续的缓慢摇摆，整个流程不使用麦克风。
- 全局文字使用霞鹜文楷 Medium；参考素材所含 Yuji Boku 及字体许可仍保留，插画画布不再重复加载曲名字体。
- 移除 USB 独占设置、设备接管、USB 启动过滤器与运行管理器；Rust 默认不编译 `uac2`。普通系统 USB / 耳机音频路由保留。
- 移除上游 Sentry 构建插件与上报配置。关于页面标注 ePlayer 与 CPPlayer 的关系，不自动安装上游 APK。

没有音乐源模块时，本地音乐与默认歌单仍可使用。在线搜索、账号和云端内容需要在设置中导入兼容的 CPPlayer 音乐源模块。

## 构建

需要 JDK 21、Android SDK Platform 37.0、Build Tools 36.0.0、NDK、Rust Android targets、cargo-ndk 和 CMake。

```bash
./tools/prepare-local-signing.sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --locked
./gradlew assembleFastRelease
```

`local.properties` 设置 `sdk.dir`。`ANDROID_NDK_HOME` 可以指定 NDK 目录。`assembleFastRelease` 为本地调试签名、关闭 R8 的构建。正式分发应设置自己的 release 签名。

已单独构建 JNI 后，可用 `-PwithRust=false` 跳过 Rust 增量构建。不要将缺少 JNI 的此类构建当作完整版本发布。

## 结构

- `ui/artwork/`：单一底图修复、眼睑、连续动作及纯绘制 View；不创建第二套控件或管理媒体音量。
- `app/PlayerScreen.kt`：自适应播放布局、元数据、插画与操作回调。
- `manager/WitchPlaylist.kt`：默认歌单、本地导入验证与私有音频 URI。
- `ui/theme/Theme.kt`：统一纸白、蓝天、墨色、文楷字体。
- `androidTest/.../EPlayerIntegrationTest.kt`：真实文件导入及 CPPlayer MediaSession 播放测试。

CPPlayer 原项目采用 MIT 许可，见 [LICENSE](LICENSE)。原项目说明保存在 [docs/CPPLAYER-UPSTREAM-README.md](docs/CPPLAYER-UPSTREAM-README.md)。

## 迁移交接

当前主线是本目录的 ePlayer；早期独立版见相邻的 `witch-player`。从 [交接入口](../../docs/EPLAYER-HANDOFF.md) 读取验收状态、已知问题、安装包、原生库和历史记录的下载地址。

原 CPPlayer 工作树提交为 `d196c0d`。本次迁移只补充目录登记、构建签名准备、versionCode 最低值 6 及交接说明，未改变播放器功能。源代码不携带签名私钥；`prepare-local-signing.sh` 创建本地调试密钥，新密钥不能覆盖旧密钥签名的已安装包。需要覆盖安装时自行保存原签名材料，不要提交到仓库。已有测试 APK 可直接下载。

字体为完整霞鹜文楷 Medium，覆盖中日文等曲库内容，约 24.6 MiB；仓库仅对该精确路径设 26 MiB 上限例外，许可在 assets/fonts 中。

设备检查可用 `./gradlew assembleFastRelease assembleFastReleaseAndroidTest -PtestBuildType=fastRelease`。已有三 ABI 原生库可按交接说明从 Release 恢复，使用 `-PwithRust=false`；不要把缺失 JNI 的包当完整版本。
