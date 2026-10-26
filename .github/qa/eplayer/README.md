# R8 跨应用音乐验收

该验收 APK 仅使用 Android 平台测试接口，instrumentation 的目标是自身 `app.eplayer.qa`，播放器保持原始签名与字节。它在全新 Android 16 SDK 模拟器上，通过系统文件选择器导入运行时合成 WAV，分别选择系统/Rust 音频，用框架 MediaSession 检查有效时长、音频输出活跃、暂停位置、拖动、后台推进和实际通知卡片返回，并保存截图。

`.github/eplayer-blackbox.json` 固定成功主线 CI、源码和 universal R8 APK SHA256。工作流下载对应产物，编译约 30KB QA APK，再运行 KVM；不重新构建播放器。程序只对识别为 SDK emulator 的设备清理测试安装数据，不允许物理设备进入清理步骤。运行结果单独记录通过/失败/超时，不替代 fastRelease、实机音质或在线账号验收。

框架 MediaSession 的播放位置按其报告的更新时间和速度计算；此检查没有直接读取原生 PCM 或测量扬声器音质。系统/Rust 由实际 UI 选择。所有 WAV 在测试时生成，不放入 Git 或产品 APK；QA 自签名私钥仅在临时目录中使用并销毁。

本地只编译 QA：

```sh
python3 .github/scripts/build_eplayer_blackbox.py --sdk "$ANDROID_HOME" --output /tmp/eplayer-qa.apk
```

完整设备验收由 `eplayer-r8-blackbox.yml` 执行。平台 JUnit3 runner 作为 SDK shared library 使用，因此不依赖播放器中被 R8 删除或改名的 Kotlin/Compose 类。当前仅针对 API36；升级 SDK 时应实际验证该平台测试库是否仍可用。

初始清单固定已发布 0.3.0 的 a62e0fb，只用于验证新验收工具。不能把它当作正在修复的 0.3.1 已通过；新主线完成构建后需要更新清单并重新检查对应确切 APK。
