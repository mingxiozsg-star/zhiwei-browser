# 知微浏览器 · Zhiwei Browser

> 一款轻量、智能、纯粹的 Android 浏览器——WebView 内核 + Jetpack Compose，内置 AI 助手。
>
> A lightweight, clean Android browser built with a WebView core and Jetpack Compose, with a built-in AI assistant.

[![Build Release APK](https://github.com/mingxiozsg-star/zhiwei-browser/actions/workflows/build-release.yml/badge.svg)](https://github.com/mingxiozsg-star/zhiwei-browser/actions/workflows/build-release.yml)
[![Release](https://img.shields.io/badge/version-1.8.3-blue)](https://github.com/mingxiozsg-star/zhiwei-browser/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

正式版仅 **1.5 MB**，无广告 SDK、无埋点、无后台服务。
Release build is just **1.5 MB** — no ad SDK, no analytics, no background service.

---

## ✨ 功能特性 · Features

### 🌐 浏览体验 · Browsing

- **多标签管理** —— 标签页横滑切换、一键关闭
- **Multi-tab management** — swipe between tabs, close them in one tap
- **书签 / 历史 / 快捷方式** —— 本地持久化，永不云端上传
- **Bookmarks / history / shortcuts** — stored locally, never uploaded to any server
- **广告拦截** —— 内置规则过滤常见广告域名
- **Ad blocking** — built-in rules filter common ad domains
- **桌面模式 / 深色模式** —— 网页强制深色，护眼浏览
- **Desktop mode / dark mode** — force dark on pages for easier reading
- **全屏视频** —— 自动进入 / 退出沉浸模式
- **Full-screen video** — enters and leaves immersive mode automatically
- **自定义壁纸** —— 主页支持系统壁纸或图库自定义，磨砂搜索框悬浮其上
- **Custom wallpaper** — use the system wallpaper or any gallery image, with a frosted search bar floating above it
- **自动翻译** —— 通过 Google 翻译代理整页翻译（英→中），链接跳转持续生效
- **Auto translate** — full-page translation through a Google Translate proxy, kept across in-page navigation

### 🤖 AI 助手 · AI Assistant

- **网页总结** —— 一键提炼当前页面核心内容
- **Page summary** — one tap to extract the key points of the current page
- **AI 对话** —— 支持多轮对话、页内上下文提问
- **AI chat** — multi-turn conversation with page context
- **附件支持** —— 📎 图片（多模态识图）与文本文档随问题发送
- **Attachments** — 📎 images (multimodal vision) and text documents sent along with the question
- **多厂商接入** —— 内置 7 家预设（DeepSeek / OpenAI / Kimi / 智谱 / 通义 / 中转站 / 自定义），一键切换自动填充 API Base 与默认模型，**API Key 完全由用户自持**
- **Multiple providers** — 7 built-in presets (DeepSeek / OpenAI / Kimi / Zhipu / Qwen / relay / custom); switching fills in the API base and default model, and **the API key stays in your hands**
- **身份锁定 + 模型可查** —— 提示词级身份固化，AI 始终以「知微」身份应答；被问到模型时如实回答当前模型，不编造其他身份
- **Locked identity, open model** — the prompt pins the persona "Zhiwei"; when asked which model is running it answers honestly instead of making things up
- **网页操作（工具调用）** —— AI 能直接读页面、打开网址、点按钮、填搜索框、滚动、返回，按用户意图自己动手，每次操作都能在对话里看到痕迹
- **Web actions (tool calling)** — the AI can read pages, open URLs, click buttons, fill inputs, scroll and go back on its own, and every action shows up in the chat
- **帮你下载应用** —— 找到下载入口后直接把文件（含 APK 安装包）加入下载队列，自动挂加速线路
- **Download on request** —— once it finds the download link, files (APKs included) go straight into the download queue with mirror acceleration
- **网页助手提示** —— 英文页先问要不要翻译、长文页问要不要总结，支持按站点免打扰
- **Assist prompts** — English pages ask before translating, long pages offer a summary, with per-site mute
- **模型可见** —— AI 面板直接显示当前所用模型
- **Model shown** — the AI panel displays the model currently in use
- **无障碍支持** —— 打开读屏后弹窗改用系统确认框、结果自动朗读、触控区不小于 48dp
- **Accessibility** — with a screen reader on, prompts use system dialogs, results are announced aloud, touch targets stay at least 48dp

### 🎨 交互细节 · Interaction Details

- 品牌渐变开屏动画 + Logo 弹性缩放
- Branded gradient splash animation with an elastic logo
- 三页首次引导（功能介绍 → AI 介绍 → AI 配置），带页面切换动画
- Three-page first-run guide (features → AI intro → AI setup) with page transitions
- 全局方向感知转场：前进右入、返回左出
- Direction-aware transitions: forward slides in from the right, back slides out to the left
- 底部五键工具栏（← → ⌂ ☰ ↻），触控区加大
- Five-key bottom toolbar (← → ⌂ ☰ ↻) with larger touch targets

## 📦 下载 · Download

前往 [Actions](../../actions) 页面，取最新一次成功构建的 `zhiwei-release-apk` 产物；或给仓库打 tag 触发自动发布到 [Releases](../../releases)。
Open [Actions](../../actions) and grab the `zhiwei-release-apk` artifact from the latest successful build, or push a tag to publish to [Releases](../../releases) automatically.

> ⚠️ 正式签名与早期 debug 版签名不同，升级前需卸载旧版（书签等本地数据将清空）。
>
> ⚠️ The release signature differs from the early debug builds, so uninstall the old version first — local data such as bookmarks will be cleared.

## 🛠️ 自行构建 · Build It Yourself

### 环境要求 · Requirements

- JDK 17+
- Android SDK（compileSdk 35）

### Debug 构建 · Debug Build

```bash
./gradlew assembleDebug
```

### Release 构建（需签名）· Release Build (Signing Required)

在项目根目录创建 `keystore.properties`（该文件已被 .gitignore 排除）：
Create `keystore.properties` in the project root (already excluded by .gitignore):

```properties
storeFile=zhiwei-release.jks
storePassword=你的密钥库密码
keyAlias=你的别名
keyPassword=你的密钥密码
```

然后 · Then:

```bash
./gradlew assembleRelease
```

### CI 构建（推荐）· CI Build (Recommended)

推送至 `main` 分支即自动构建 Release APK。需在仓库 Secrets 中配置：
Pushing to `main` builds the release APK automatically. Configure these repository secrets:

| Secret | 说明 · Description |
|---|---|
| `ZW_KEYSTORE_BASE64` | 签名密钥库的 Base64 编码<br>Base64 of the signing keystore |
| `ZW_STORE_PASS` | 密钥库密码<br>Keystore password |
| `ZW_KEY_ALIAS` | 密钥别名<br>Key alias |
| `ZW_KEY_PASS` | 密钥密码<br>Key password |

## 🏗️ 技术栈 · Tech Stack

| 层 · Layer | 技术 · Tech |
|---|---|
| UI | Jetpack Compose + Material 3（BOM 2026.01.01） |
| 内核 · Engine | Android WebView（androidx.webkit 1.12.1） |
| 语言 · Language | Kotlin 2.4.21 |
| 构建 · Build | Gradle 9.8.2 + AGP 9.4.1 |
| 网络 · Network | OkHttp（OpenAI 兼容 API 协议 · OpenAI-compatible API） |
| 加固 · Hardening | R8 全量混淆 + 资源压缩 + arm64-v8a 单架构<br>full R8 obfuscation, resource shrinking, arm64-v8a only |

## 📁 项目结构 · Project Layout

```
├── app/src/main/java/com/java/myapplication/
│   ├── MainActivity.kt          # 启动路由（splash→onboarding→browser）+ 转场动画 / startup routing + transitions
│   ├── BrowserViewModel.kt      # 多标签 / WebView 状态管理 / tabs and WebView state
│   ├── BrowserSettings.kt       # 设置持久化 + AI 厂商预设 / settings and AI provider presets
│   ├── ai/AiClient.kt           # AI 请求层（文本 / 多模态 / 身份锁定）/ AI request layer
│   └── ui/                      # Compose 界面（浏览器 / 设置 / 书签 / AI 面板 / 引导页）
│                                # Compose screens: browser, settings, bookmarks, AI panel, onboarding
├── .github/workflows/           # CI：Release 自动构建 / automated release build
└── tools/                       # ARM64 aapt2（本地 ARM64 环境构建用）/ aapt2 for local ARM64 builds
```

## 🔒 隐私 · Privacy

- 无账号体系、无统计埋点、无第三方广告 SDK
- No accounts, no analytics, no third-party ad SDK
- 书签、历史、设置全部存储于本地
- Bookmarks, history and settings stay on the device
- AI 请求直连用户自配置的 API 端点，不经过任何中间服务器
- AI requests go straight to the endpoint you configure, with no middle server

## 📄 许可 · License

[MIT License](LICENSE)
