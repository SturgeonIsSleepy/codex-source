# Android 16 / 17 remote cloud 快速部署

面向与当前环境相同的 Linux x86_64、Python 3、JDK 17+ 环境。自动使用 KVM；没有 KVM 时自动软件模拟。使用官方 AOSP 标准镜像，不含 Google Play 服务：Android 16 为 API 36（`android-36`），Android 17 为 API 37（实际 SDK 包名 `android-37.0`）。

从本仓库获取工具目录后，在仓库根目录运行：

```sh
python3 tools/android-cloud/android-cloud up --android 17 --accept-licenses
```

新增 `--android 17` 选择 Android 17；省略此选项仍为 Android 16，兼容已有脚本。首次安装官方 SDK、模拟器和对应镜像；后续复用缓存。参数表示接受 Android SDK 许可：https://developer.android.com/studio/terms 。无需 Docker、root 或构建 APK。

**默认后台启动：配置完成并拉起进程就返回，不等 Android 开机。** 输出示例：

```json
{"status":"starting","serial":"emulator-5556","api":37,"mode":"software"}
```

给其他会话的一句话：

> 运行 `python3 tools/android-cloud/android-cloud up --android 17 --accept-licenses`；返回 starting 后立即结束，不等启动、不轮询、不截图。失败只查看 logs。

需要使用时再检查：

```sh
python3 tools/android-cloud/android-cloud status --android 17
python3 tools/android-cloud/android-cloud adb --android 17 shell getprop ro.build.version.release
python3 tools/android-cloud/android-cloud adb --android 17 exec-out screencap -p > screen.png
python3 tools/android-cloud/android-cloud logs --android 17
python3 tools/android-cloud/android-cloud down --android 17
```

`starting` 仅表示已发起后台启动；`ready` 才表示 ADB 可用、所选版本 API 匹配（16→36、17→37）且系统完成启动。需要主动等待时用 `up --android 17 --wait`，软件模式默认等待上限 900 秒，可用 `--timeout` 调整。无 KVM 时 Android 开机需要数分钟，工具不把等待放进默认流程。

可选运行 `sh tools/android-cloud/install.sh`，安装成 `~/.local/bin/android-cloud`。工具包带 `SKILL.md`，可整体安装到客户端支持的技能目录，让后续助手使用简短指令操作。

## 缓存与环境预装

默认缓存目录 `~/.cache/android-cloud` 包含 SDK、镜像、AVD、日志和状态，同一环境中的其他项目可复用。也可设置 `ANDROID_CLOUD_HOME=/persistent/android-cloud`。

基础镜像或环境初始化阶段可运行 `android-cloud setup --android 17 --accept-licenses`，只准备文件、不启动模拟器。保留缓存后，其他会话只需执行 `up --android 17`。没有持久卷或预装时，新机器仍需首次下载；本工具不修改其他 cloud 会话的全局配置。

首次安装建议至少 6 GiB 可用磁盘、4 GiB 可用内存。默认 2048 MiB、2 核、480×800、无窗口、无音频、不保存快照。可用 `--memory 3072 --port 5558` 调整，先停止已有实例。需要独立实例时设置不同缓存目录和端口。

## 网络与维护

保留平台代理和 CA 信任。宿主下载使用平台 HTTPS 代理，sdkmanager 和模拟器使用平台 HTTP 代理；客体证书信任需按平台要求另行配置。不关闭宿主下载的 TLS 校验，不绕过网络策略。

固定 Google command-line tools 23.0（使用独立版本目录，不受旧版 latest 缓存影响） 并校验官方 SHA-1；模拟器与镜像由官方 sdkmanager 管理。若日志报告缺少动态库，请在基础镜像补齐对应依赖；常见 libnss3、libpulse0、libx11-6、libgl1。

可选 `connect HOST:PORT --android 17 --accept-licenses` 连接已有、经授权的私有 Android 17 ADB 端点，不会远程创建虚拟机；此模式的 `down` 只断开连接。默认不提供 web/VNC 页面，也不公开 ADB 端口。

## 只下载单个脚本，减少会话输入

无需上传 ZIP、克隆整仓库或读取源码：

```sh
mkdir -p "$HOME/.local/bin"
curl -fLsS https://raw.githubusercontent.com/SturgeonIsSleepy/codex-source/main/tools/android-cloud/android-cloud -o "$HOME/.local/bin/android-cloud"
python3 "$HOME/.local/bin/android-cloud" up --android 17 --accept-licenses
```

固定版本时，将 URL 中的 main 换为完整 commit SHA。目录维护规则见仓库根目录 AGENTS.md 与 docs/CONTRIBUTING.md。


## 16 / 17 的状态隔离

两个版本共享 SDK 下载、ADB 和 AVDS 目录；AVD 名称、状态文件、日志和默认端口分开：

| Android | AVD | 默认端口 | 状态 | 日志 |
| --- | --- | --- | --- | --- |
| 16（默认） | cloud_android_16 | 5554 | state.json | android-cloud.log |
| 17 | cloud_android_17 | 5556 | state-17.json | android-cloud-17.log |

所有命令（包括 `status`、`logs`、`down`、`adb`）均需用同一个 `--android 17` 选择 17。`--android` 可放在子命令前或后；`adb` 必须放在要转发的 ADB 参数前，例如 `adb --android 17 shell getprop`。旧的 16 缓存和状态路径保持不变；两个版本共用安装锁，防止同时修改 SDK。

`setup --android 17` 只安装文件和创建 AVD，不启动模拟器。配置验证不代表已验证 Android 开机；`up` 后台启动也不保证平台在结束会话后继续保留进程。
