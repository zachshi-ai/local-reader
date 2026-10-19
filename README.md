# 本地阅读（LocalReader）

一个只在本地跑的 HTML / Markdown 阅读器。手机系统默认把 `.html` 关联到文本查看器、又不给选浏览器时，用它直接打开本地文件。

## 功能

- 通过系统文件选择器打开本地文件（`text/html`、`text/markdown`、`text/plain`、`application/xhtml+xml`）
- 支持作为文件打开方式被调用（`ACTION_VIEW`），可注册为 `.html` / `.md` 的默认应用
- Markdown 在本地渲染成排版页面，不依赖任何网络资源
- 页内链接、外部链接分开处理：外部链接交给系统浏览器
- 无网络权限，文件内容不出设备

## 结构

```
proj/src/com/localreader/MainActivity.java   全部逻辑（Activity + WebView）
proj/res/                                    布局、图标、配色
proj/AndroidManifest.xml                     intent-filter 与权限声明
```

产物 `LocalReader-v1.0.apk`（minSdk 24，targetSdk 34），同目录 `本地阅读APK下载.html` 是给手机直接下载安装用的落地页。

## 构建

```bash
export ANDROID_HOME=/path/to/android-sdk
# 资源编译 → javac → d8 → 打包对齐 → 用你自己的 keystore 签名
```

仓库不含签名文件，签名口令请自备。

## 许可

MIT
