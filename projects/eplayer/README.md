# ePlayer

基于 [Aurora-Nasa-1/CPPlayer](https://github.com/Aurora-Nasa-1/CPPlayer) 的 Android 音乐播放器，统一为白纸、天空和手绘墨线风格。应用独立安装标识为 `app.eplayer.music`；保留原项目 Kotlin 包名与 JNI 名称，以免破坏原生引擎接口。

## 当前开发版本 0.3.1

普通版与自用版分离，账号、云端发现、搜索、收藏、歌单和下载流程正在迁移。功能边界与已知缺口见 [CORE-BOUNDARIES.md](docs/CORE-BOUNDARIES.md)；历史模拟器检查不能代替最新代码或实机验收。播放页采用独立组件，按宽度与方向适配，并为大字体保留滚动及可达操作区。

## 0.3.0 验证预览版

[APK、原生库及验证记录](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.3.0)。普通 fastRelease 的 Android 16 自动检查 25 通过/1 登录条件跳过；普通/自用 R8 构建、普通 R8 冷启动及确切 universal 交付 APK 安装/启动通过。完整 R8 音乐流程、实机音频和真实账号仍未验收。使用 CI 临时签名，与旧版不兼容，升级前备份本地音频及设置；该签名不保证后续预览可覆盖升级。

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
