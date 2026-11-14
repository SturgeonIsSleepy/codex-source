# 存储与恢复

应用源码保存在本仓库的 `codex/eplayer-power` 分支，安装包及脱敏验收记录保存在 GitHub Releases。0.6.11 应用源码为 `6f9c28ee6423b52a310392ea0303de1bd07f70ed`；后续纯说明提交不改变该安装包。

原签名密钥单独存入拥有者的 GitHub 私有备份仓库，并已读回确认内容一致。恢复构建时将密钥放回 `projects/eplayer/debug.keystore`，沿用项目构建配置；不要生成新密钥替代，否则无法覆盖升级。原始音源 ZIP 可从已签名 APK 的 `assets/bundled-provider.zip` 原样取回。

本地项目仅保留最新 APK。源码工作副本、旧安装包、构建输出、历史临时脚本与测试素材在确认远端保存后清理。真实账号、VPN 配置、手机日志、缓存和模拟器用户磁盘不上传公开仓库；模拟器运行依赖留在 SDK 中。

## 保留的模拟器方案

- Android 17 / API 37.0，Google APIs，x86_64，至少 4 GiB RAM。
- KernelSU v3.3.1，使用与官方镜像内核匹配的 LKM 和单独的已修补 ramdisk；保持官方系统镜像不变。
- 禁用 Quick Boot、RAM 快照及 `QuickbootFileBacked`，避免每次启动额外生成约 4 GiB 的 RAM 后备文件；仅保留一个用户磁盘。此配置已冷启动验证。
- 启动必须携带 `-ramdisk` 参数。只在配置中写 root 或仅成功安装管理器，都不能代替实际 `su -c id` 验证。

恢复时安装官方 `system-images;android-37.0;google_apis;x86_64`，创建 `EPlayer17KSU`，使用 [avd-kernelsu-x86_64](https://github.com/leemikepop/avd-kernelsu-x86_64) 发布的 `avd-ksu.py`：

```text
python avd-ksu.py patch --avd EPlayer17KSU --variant kernelsu --version v3.3.1 --allow-shell --out ramdisk-ksu.img
```

安装并打开同版本 KernelSU 管理器，关闭模拟器后使用同一 ramdisk 冷启动。Windows 可使用 [启动脚本](../tools/start-rooted-avd.ps1)，它默认把 AVD 放在 SDK 的 `avd` 下、ramdisk 放在 `kernelsu/api37` 下；也可传入参数。第三方工具对其下载文件的完整性校验保留；ePlayer 自行提交的 APK 不计算 SHA256、不生成校验文件。

参考：[Android 官方命令行启动说明](https://developer.android.com/studio/run/emulator-commandline)、[KernelSU x86_64 支持说明](https://kernelsu.org/guide/x86_64-support)。

## 已验证的保留环境

Android 17 / API 37 / x86_64，内核 `6.12.81-android16-6-g4f69fc7b210c-ab16167562`。KernelSU 管理器显示 `Working / LKM`，`ksud 3.3.1 (uapi: 5)`，Shell 执行结果：

```text
uid=0(root) gid=0(root) groups=0(root) context=u:r:ksu:s0
```

共完成三次冷启动；最后一次禁用 RAM 后备文件后仍能启动、取得 KernelSU root 并打开 ePlayer 0.6.11，未重新生成 `ram.img`。最终关机保存用户磁盘，启动器继续携带修补后的 ramdisk；不把 AVD 定义存在冒称验证成功。

历史工程私有归档为 141 份脚本/说明、5 张主题参考图及一份归档说明，共147项；用户已明确授权该具体备份及验证后的清理。签名密钥单独备份且读回一致。原始手机日志、截图、账号/VPN数据和模拟器用户磁盘均未上传。
