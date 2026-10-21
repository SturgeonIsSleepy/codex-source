# 上传与更新约定

## 上传前

先读 `catalog.json`，判断内容属于现有条目还是新条目。现有工具在原目录更新，不创建 `xxx-new`、`xxx-final`、日期备份或 ZIP 副本。新条目使用小写字母、数字和连字符组成的唯一名称。

## 新条目的最小内容

在 `tools/<name>/`、`projects/<name>/` 或 `assets/<name>/` 下准备：

- `README.md`：用途、依赖、最短运行命令、限制。
- `VERSION`：当前语义版本，例如 `1.0.0`。
- 必要源码或小型静态资源；测试与 Skill 跟着对应条目走。

在 `catalog.json` 的 `entries` 中登记唯一 `id`、`path`、`version`、`description` 和 `entrypoint`，并同步根 README 索引。`entrypoint` 是相对条目目录的实际文件路径。版本更新时同时修改 VERSION 与索引。

## 不放进 Git 的内容

不要上传运行状态、日志、依赖目录、SDK、AVD、Android 镜像、APK、环境变量文件或下载/构建生成的压缩包。源码目录中的打包产物会与源码版本产生歧义；分发压缩包应放在对应工具的 GitHub Release，以 `<tool>-v<version>` 标识。单文件超过 5 MiB 默认由检查拒绝；有明确业务需要时同步修改规则并在提交中说明原因。

`.gitignore` 避免常见误提交，CI 检查已跟踪文件，防止强制添加绕过忽略规则。CI 是检查，不等于分支保护；当前未配置强制合并保护。

## 检查与提交

```sh
git add <本次准备提交的路径>
python3 .github/scripts/check_layout.py
python3 -m unittest discover -s .github/scripts/tests -v
python3 -m unittest discover -s tools/android-cloud/tests -v
```

提交只包含本次条目和必要索引/文档更新，提交说明写清用途与验证。不要覆盖其他工具或强制推送。可复用 URL 的目录保持稳定，固定版本链接使用完整 commit SHA。
