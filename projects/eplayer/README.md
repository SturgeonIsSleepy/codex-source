# ePlayer

基于 [Aurora-Nasa-1/CPPlayer](https://github.com/Aurora-Nasa-1/CPPlayer) 的 Android 音乐播放器，采用 YouTube Music 参考布局与固定中性配色。应用独立安装标识为 `app.eplayer.music`；保留原项目 Kotlin 包名与 JNI 名称，以免破坏原生引擎接口。

## 当前开发版本 0.6.2

默认交付一个内置用户指定音源的自用通用 APK，包含 ARM64、ARMv7 与 x86_64；不再单独交付 ARM64 包。普通版仅在用户明确要求时制作。当前开发分支的构建与设备检查默认使用 internal universal，历史公开普通版发布入口保留兼容。

0.6.2 修复详情页返回的场景/滚动复用，设置按层返回；搜索改为独立页面，一次点击聚焦输入并显示键盘和联想。播放器重排为连续队列，上滑隐藏角色、切换迷你播放头，保留封面/进度控制；歌词取消封面放大，改短距离淡入上移。见 [0.6.2 记录](docs/SCENES-0.6.2.md)。

0.6.1 缩小底栏和控件、统一设置行距，设置内移除账号入口；分类列表提供本地绘制封面。模糊支持连续值与触感反馈，播放器重排为封面在上、曲名/五键控制在下；歌词通过封面放大和磨砂淡入打开。手动「下一首播放」在随机状态下仍优先播放，随机状态有高亮和状态点。见 [0.6.1 记录](docs/COMPACT-0.6.1.md)。

0.6.0 重排为首页、探索、媒体库，头像进入账号与设置。首页为听歌统计和一键点歌，支持歌手、歌单、专辑、单曲种子漫游。播放器背景由用户导入，不再内置 MTZ 图片；保留角色、动作和原字体。队列与歌单支持多选、完整全选与批量操作。见 [0.6.0 记录](docs/YTM-0.6.0.md)。

0.5.8 修复搜索返回时输入法优先收起；播放队列与歌单支持整行长按编辑/拖动，保留边缘自动滚动和排序动画；歌单详情保留可操作的悬浮播放底栏；间奏不显示翻译或音译。自有歌单排序同步服务端，他人歌单只保存本机顺序。详见 [0.5.8 记录](docs/INTERACTION-0.5.8.md)。

0.5.7 参考用户提供的 MTZ，新增可在「设置 → 画面设置 → 主题」开关的伊蕾娜粉紫配色与淡插画。设置收拢为账号、音源、播放、画面和关于；首页可直接搜索并展示结果。主题图片仅约 68 KB，不增加模糊渲染和新字体。来源与验证边界见 [0.5.7 记录](docs/THEME-0.5.7.md)。

0.5.5 默认曲库歌单页，移除双曲预设与重复喜欢入口，云盘移到底部，加入网易账号总听歌排行。根页返回直接退出并保留底栏，弹层一次关闭；队列可直接删除/拖动排序，封面与标题随切歌过渡。变化与验收见 [0.5.5 记录](docs/LIBRARY-0.5.5.md)。

0.5.6 调整歌词主次、对话留白与跟随滚动；队列编辑支持边缘自动滚动和拖动占位动画；预见式返回直接提交，画面设置增加缓存封面模糊调节。仅执行交付必要的 Release 构建与打包核验，未做设备复测。

0.5.4 加入真实词时间的 KTV 扫色、TTML 对话左右布局、间奏浮动三点和歌曲封面的缓存磨砂取色。逐行歌词保留回退，变化与验收见 [0.5.4 记录](docs/LYRICS-0.5.4.md)。

0.5.3 使用克制的魔女主题与柔和灰紫深色，将播放条接在导航底栏上，仅将人物后的蓝天换为歌曲封面，伊蕾娜在前景且身体伸出封面。页面、弹层和确认框统一接入预见式返回。变化与实际验收见 [0.5.3 记录](docs/TRAVEL-0.5.3.md)。

0.5.2 去除液态玻璃，悬浮底栏采用不透明材质，完善深色播放器和歌词配色；减少被遮挡动画、无人使用的能量采样及暂停轮询。人物、动作、字体、音质和双引擎保留，首页仍仅含统计、继续播放与网易云入口。变化与验收边界见 [0.5.2 记录](docs/POWER-DARK-0.5.2.md)，历史完整功能记录见 [0.5.0](docs/LIGHT-0.5.0.md)。

[0.4.0 基础功能交付](docs/DELIVERY-0.4.0.md) 的源码、签名与验收记录保留。普通版不内置音源；自用版由本地工具嵌入用户指定模块。

## 0.3.1 验证预览版

[APK、原生库及验证记录](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.3.1)。普通 fastRelease Android 16 检查 30 通过/1 登录条件跳过；普通/自用 R8 构建、普通 R8 冷启动和原始 universal 安装/启动通过。相同 R8 APK 的系统/原生双引擎已通过实际文件选择器导入、暂停/定位、后台播放与系统通知点击返回；截图和短时无声动作预览随验证归档提供。原生公开释放契约和实时 EQ 渲染检查通过。实机音质/路由、真实账号及长时稳定性未验收。

使用 CI 临时签名，versionCode52；与 0.3.0 证书不同。升级前备份本地音频及设置，必要时卸载旧预览；不保证后续构建可覆盖升级。旧 [0.3.0](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.3.0) 附件保留。参数频率/Q 编辑与缓存边界后续工作不属于此包。

已知调查项：后续 0.3.2 组合回归在快速切换引擎后，实时 EQ 发生间歇停止；正在 [PR #4](https://github.com/SturgeonIsSleepy/codex-source/pull/4) 定位。0.3.1 的通过记录保留其实际范围，暂不能排除同类间歇问题。

## 0.1.1 历史预览版

没有音乐源时，首页直接提供魔女之旅歌单导入与本地曲库入口；本地曲库默认显示手机音乐，不再自动跳转所有文件访问设置。在线能力仍需用户配置音乐源。本轮构建和设备验收结果见交接页。

## 功能与设计

- 以 CPPlayer 的音频与音乐源协议为核心，迁移搜索、曲库、下载、歌词、评论、队列和 MediaSession；声音手记提供按引擎能力显示的 EQ/音效和带预览的 AutoEQ 文本导入；完整文件操作、淡入淡出与缓存入口仍待逐项验收。
- 设置提供 15–120 分钟定时停止，可调整或取消；由播放服务执行，退出页面不取消，进程被系统结束后不恢复旧倒计时。
- 本地音乐支持导入与扫描；旧双曲预设入口已移除，历史用户导入迁移为普通本地音乐。
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
- `manager/WitchPlaylist.kt`：旧导入文件的 URI 兼容；新导入统一由 `app/LibraryStore.kt` 管理。
- `ui/theme/Theme.kt`：统一纸白、蓝天、墨色、文楷字体。
- `androidTest/.../EPlayerIntegrationTest.kt`：真实文件导入及 CPPlayer MediaSession 播放测试。

CPPlayer 原项目采用 MIT 许可，见 [LICENSE](LICENSE)。原项目说明保存在 [docs/CPPLAYER-UPSTREAM-README.md](docs/CPPLAYER-UPSTREAM-README.md)。

## 迁移交接

当前主线是本目录的 ePlayer；早期独立版见相邻的 `witch-player`。从 [交接入口](../../docs/EPLAYER-HANDOFF.md) 读取验收状态、已知问题、安装包、原生库和历史记录的下载地址。

原 CPPlayer 工作树提交为 `d196c0d`。本次迁移只补充目录登记、构建签名准备、versionCode 最低值 6 及交接说明，未改变播放器功能。源代码不携带签名私钥；`prepare-local-signing.sh` 创建本地调试密钥，新密钥不能覆盖旧密钥签名的已安装包。需要覆盖安装时自行保存原签名材料，不要提交到仓库。已有测试 APK 可直接下载。

字体为完整霞鹜文楷 Medium，覆盖中日文等曲库内容，约 24.6 MiB；仓库仅对该精确路径设 26 MiB 上限例外，许可在 assets/fonts 中。

设备检查可用 `./gradlew assembleFastRelease assembleFastReleaseAndroidTest -PtestBuildType=fastRelease`。已有三 ABI 原生库可按交接说明从 Release 恢复，使用 `-PwithRust=false`；不要把缺失 JNI 的包当完整版本。
