# ePlayer 转移交接

更新：2026-10-24。主线为 [projects/eplayer](../projects/eplayer/)，原独立参考版为 [projects/witch-player](../projects/witch-player/)。原 ePlayer 工作树最后提交 `d196c0d`；迁移后的后续工作以本仓库提交为准。

## 先读与最新授权

1. 阅读根目录 AGENTS.md、catalog.json、本页和 [开发计划](../projects/eplayer/docs/DEVELOPMENT-PLAN.md)。
2. 用户最新要求：继续开发和适配，尽可能只保留 CPPlayer 核心代码；外观和使用逻辑全面重构，自行规划，所有阶段成果保存到本 GitHub 仓库。
3. 保留完整音乐能力、去除 USB 独占；保留默认双曲歌单和音频导入，不附录音。角色、字体和动作要求见计划。不要修改无关工具。
4. 希望每次在没有使用限额且平台仍允许执行时持续推进，达到运行时限或硬阻塞则先保存断点；不能保证平台提供无限单次运行。

## 当前成果和事实边界

已接入 CPPlayer，独立包名 `app.eplayer.music`、名称 ePlayer；`cp.player` 保留为源码/JNI 命名空间。曲库、搜索、音乐源、歌词、评论、下载、队列、双引擎仍保留；USB 独占管理器、设置、过滤器及默认 uac2 编译已移除，普通 Android 音频路由保留。

界面采用纸白、蓝天、墨线与文楷 Medium。伊蕾娜共用同一人物底图，只在眼睛区域改变眼睑；连续动作使用阻尼，避免换脸时改变头发、帽子、手和肤色。已修复 Media3 空缓冲区自复制导致的播放异常，限制实时 PCM 采样开销；本地 file/content URI 绕过网络缓存；替换录音会使播放与节奏缓存失效。

分阶段曾通过：三项插画检查、三项 PCM 检查、原生引擎初始化且无 USB 独占、音乐源 ZIP/中文搜索/地址映射。**最终完整播放、暂停/拖动、后台续播、通知返回和整页视觉验收尚未取得完整通过结果。** 旧轮次存在超时与缓冲，最终非调试测试在用户要求收尾时手动停止；日志中的 Process crashed 不能据此当作自然崩溃。不要宣称九项最终验收全部通过。

## 安装包、原生库、历史与证据

- [ePlayer 0.1.0 测试交付与证据](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.1.0)：约 90 MiB 的非调试、未 R8 缩小通用 APK，原工作树源码 ZIP、三 ABI JNI、开发补丁历史、验证归档、阶段说明和 SHA256SUMS。
- [Witch Player 1.8 及历史归档](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/witch-player-v1.8.0)：独立版 APK、此前各版产物、动作预览、原始参考图和非密钥诊断素材。

历史源码压缩包中的本地签名材料和编译目录在上传时剔除，因此可能与原本地 ZIP 校验值不同；以各 Release 的 SHA256SUMS 为准。未上传 SDK、AVD、机器身份、环境凭据或签名私钥。上传包不包含用户歌曲录音。

## 恢复开发

需要 JDK 21、Android SDK Platform 37.0、Build Tools 36.0.0；targetSdk 为 36，minSdk 为 28。Rust 完整构建需要 NDK、三个 Android targets、cargo-ndk 与 CMake。

```sh
git clone https://github.com/SturgeonIsSleepy/codex-source.git
cd codex-source/projects/eplayer
./tools/prepare-local-signing.sh
# 用 ANDROID_HOME、ANDROID_SDK_ROOT 或未提交的 local.properties 指定 SDK
./gradlew assembleFastRelease
```

若先复用已验证能加载的 JNI，可下载 `eplayer-v0.1.0-native-libs.tar.gz` 并在项目根解压（内含 app/src/main/jniLibs），然后加 `-PwithRust=false`。独立源码 ZIP 不应替代本仓库最新主线。

本地密钥重新生成后不能覆盖旧签名安装包，需自行使用同一原签名或在测试设备卸载旧包；不要上传密钥。迁移后 versionCode 下限为已发布预览的 6，避免 monorepo 较少提交造成降级。

Android 16 软件 TCG 模拟器非常慢；测试与大构建不要并行，不要反复强制完整 speed 预编译。区分模拟器超时、真实异常和人工终止。Android Cloud setup 按原约定后台启动；需要实际 UI 验收时才显式进入测试阶段。历史环境中的固定路径、端口和 timeout 属性不能直接当作新环境配置。

## 自动开发任务

用户要求每天北京时间 02:00（Asia/Shanghai）执行持续开发。2026-10-24 创建时平台返回 `too_many_active_automations`：现有任务 5/5，**因此尚未启用**。等待用户指定暂停/替换哪个任务；不能未经同意删除原任务，也不能用一个只会构建 APK 的 GitHub Actions 冒充持续开发任务。首个应执行时间取启用之后的下一次北京时间 02:00。

## 本次迁移验证

已执行目录检查、9 项目录规则测试、17 项 Android Cloud 控制器测试、构建脚本语法检查和独立版 MotionCheck。迁移未重新进行 Android UI 验收，测试 APK仍对应原工作树 d196c0d；源码迁移仅涉及签名准备、构建版本下限、目录和交接说明等调整。
