# codex-source

供 remote cloud 项目复用的小工具与资源。每项内容有独立目录，通过 [catalog.json](catalog.json) 查找；后续上传请遵循 [上传约定](docs/CONTRIBUTING.md) 和 [AGENTS.md](AGENTS.md)。

| 工具 | 用途 | 入口 |
| --- | --- | --- |
| [Android Cloud](tools/android-cloud/) | Android 16 一键配置、自动选择 KVM 或软件模拟、后台启动 | [说明](tools/android-cloud/README.md) · [Skill](tools/android-cloud/SKILL.md) · [部署脚本](tools/android-cloud/android-cloud) |
| [ePlayer](projects/eplayer/) | 0.4.0 音乐核心与手绘交互重构、普通版/自用版隔离 | [交接与验收](docs/EPLAYER-HANDOFF.md) · [源码](projects/eplayer/) · [0.3.1 预览包](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/eplayer-v0.3.1) |
| [Witch Player](projects/witch-player/) | 1.8 独立双曲播放器与角色动画参考 | [说明](projects/witch-player/README.md) · [历史产物](https://github.com/SturgeonIsSleepy/codex-source/releases/tag/witch-player-v1.8.0) |

## Android 16：其他 cloud 会话直接用

```sh
mkdir -p "$HOME/.local/bin"
curl --fail --silent --show-error --location \
  https://raw.githubusercontent.com/SturgeonIsSleepy/codex-source/main/tools/android-cloud/android-cloud \
  --output "$HOME/.local/bin/android-cloud"
python3 "$HOME/.local/bin/android-cloud" up --accept-licenses
```

此操作接受 Android SDK 许可。配置完成后返回 `starting`，模拟器继续在后台启动，用户可先做其他操作；不会等待 Android 开机。`starting` 不表示系统已就绪。需要使用时再执行 `python3 "$HOME/.local/bin/android-cloud" status`。

给助手的提示词：

> 从 SturgeonIsSleepy/codex-source 的 tools/android-cloud/android-cloud 下载部署脚本并用 python3 执行 up --accept-licenses；配置完成后让它后台启动并结束，不等开机、不轮询、不截图，不必读取源码。

`main` 获取维护中的最新版本；需要固定行为时，将下载 URL 中的 `main` 换成经过验证的完整 commit SHA。工具版本记录在各自的 `VERSION` 文件中。新环境首次下载 SDK 与镜像仍需要时间，复用持久缓存可避免重复下载。后台进程的生命周期取决于平台是否保留运行环境。

## 仓库结构

- `tools/<name>/`：可复用工具、它的说明、Skill 与测试。
- `projects/<name>/`：未来的独立项目，新增前先登记索引。
- `assets/<name>/`：未来的小型静态资源，新增前先登记索引。
- `docs/`：跨工具的仓库约定。
- `.github/`：目录和文件检查。

SDK、AVD、日志、缓存、APK 和大体积打包产物不放进源码仓库。具体规则与检查方法见上传约定。
