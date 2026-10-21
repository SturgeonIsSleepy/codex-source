# Android 16 remote cloud 快速部署

面向与当前环境相同的 Linux x86_64、Python 3、JDK 17+ 环境。自动使用 KVM；没有 KVM 时自动软件模拟。官方 Android 16 / API 36 AOSP 标准镜像，不含 Google Play 服务。

从本仓库获取工具目录后，在仓库根目录运行：

```sh
python3 tools/android-cloud/android-cloud up --accept-licenses
```

首次安装官方 SDK、模拟器和镜像；后续复用缓存。参数表示接受 Android SDK 许可：https://developer.android.com/studio/terms 。无需 Docker、root 或构建 APK。

**默认后台启动：配置完成并拉起进程就返回，不等 Android 开机。** 输出示例：

```json
{"status":"starting","serial":"emulator-5554","api":36,"mode":"software"}
```

给其他会话的一句话：

> 运行 android-cloud-kit/android-cloud up --accept-licenses；返回 starting 后立即结束，不等启动、不轮询、不截图。失败只查看 logs。

需要使用时再检查：

```sh
python3 tools/android-cloud/android-cloud status
python3 tools/android-cloud/android-cloud adb shell getprop ro.build.version.release
python3 tools/android-cloud/android-cloud adb exec-out screencap -p > screen.png
python3 tools/android-cloud/android-cloud logs
python3 tools/android-cloud/android-cloud down
```

`starting` 仅表示已发起后台启动；`ready` 才表示 ADB 可用、API=36 且系统完成启动。需要主动等待时用 `up --wait`，软件模式默认等待上限 900 秒，可用 `--timeout` 调整。无 KVM 时 Android 开机需要数分钟，工具不把等待放进默认流程。

可选运行 `sh tools/android-cloud/install.sh`，安装成 `~/.local/bin/android-cloud`。工具包带 `SKILL.md`，可整体安装到客户端支持的技能目录，让后续助手使用简短指令操作。

## 缓存与环境预装

默认缓存目录 `~/.cache/android-cloud` 包含 SDK、镜像、AVD、日志和状态，同一环境中的其他项目可复用。也可设置 `ANDROID_CLOUD_HOME=/persistent/android-cloud`。

基础镜像或环境初始化阶段可运行 `android-cloud setup --accept-licenses`，只准备文件、不启动模拟器。保留缓存后，其他会话只需执行 `up`。没有持久卷或预装时，新机器仍需首次下载；本工具不修改其他 cloud 会话的全局配置。

首次安装建议至少 8 GiB 可用磁盘、4 GiB 可用内存。默认 2048 MiB、2 核、480×800、无窗口、无音频、不保存快照。可用 `--memory 3072 --port 5556` 调整，先停止已有实例。需要独立实例时设置不同缓存目录和端口。

## 网络与维护

保留平台代理和 CA 信任。宿主下载使用平台 HTTPS 代理，sdkmanager 和模拟器使用平台 HTTP 代理；客体证书信任需按平台要求另行配置。不关闭宿主下载的 TLS 校验，不绕过网络策略。

固定 Google command-line tools 19.0 并校验官方 SHA-1；模拟器与镜像由官方 sdkmanager 管理。若日志报告缺少动态库，请在基础镜像补齐对应依赖；常见 libnss3、libpulse0、libx11-6、libgl1。

可选 `connect HOST:PORT --accept-licenses` 连接已有、经授权的私有 Android 16 ADB 端点，不会远程创建虚拟机；此模式的 `down` 只断开连接。默认不提供 web/VNC 页面，也不公开 ADB 端口。

## 只下载单个脚本，减少会话输入

无需上传 ZIP、克隆整仓库或读取源码：

```sh
mkdir -p "$HOME/.local/bin"
curl -fLsS https://raw.githubusercontent.com/SturgeonIsSleepy/codex-source/main/tools/android-cloud/android-cloud -o "$HOME/.local/bin/android-cloud"
python3 "$HOME/.local/bin/android-cloud" up --accept-licenses
```

固定版本时，将 URL 中的 main 换为完整 commit SHA。目录维护规则见仓库根目录 AGENTS.md 与 docs/CONTRIBUTING.md。
