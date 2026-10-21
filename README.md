# 知微浏览器 · Zhiwei Browser

> 一款轻量、智能、纯粹的 Android 浏览器——WebView 内核 + Jetpack Compose，内置 AI 助手。

[![Build Release APK](https://github.com/mingxiozsg-star/zhiwei-browser/actions/workflows/build-release.yml/badge.svg)](https://github.com/mingxiozsg-star/zhiwei-browser/actions/workflows/build-release.yml)
[![Release](https://img.shields.io/badge/version-1.1-blue)](https://github.com/mingxiozsg-star/zhiwei-browser/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

正式版仅 **1.5 MB**，无广告 SDK、无埋点、无后台服务。

---

## ✨ 功能特性

### 🌐 浏览体验
- **多标签管理** —— 标签页横滑切换、一键关闭
- **书签 / 历史 / 快捷方式** —— 本地持久化，永不云端上传
- **广告拦截** —— 内置规则过滤常见广告域名
- **桌面模式 / 深色模式** —— 网页强制深色，护眼浏览
- **全屏视频** —— 自动进入/退出沉浸模式
- **自定义壁纸** —— 主页支持系统壁纸或图库自定义，磨砂搜索框悬浮其上
- **自动翻译** —— 通过 Google 翻译代理整页翻译（英→中），链接跳转持续生效

### 🤖 AI 助手
- **网页总结** —— 一键提炼当前页面核心内容
- **AI 对话** —— 支持多轮对话、页内上下文提问
- **附件支持** —— 📎 图片（多模态识图）与文本文档随问题发送
- **多厂商接入** —— 内置 7 家预设（DeepSeek / OpenAI / Kimi / 智谱 / 通义 / 中转站 / 自定义），一键切换自动填充 API Base 与默认模型，**API Key 完全由用户自持**
- **身份锁定** —— 提示词级身份固化，AI 始终以「知微」身份应答

### 🎨 交互细节
- 品牌渐变开屏动画 + Logo 弹性缩放
- 三页首次引导（功能介绍 → AI 介绍 → AI 配置），带页面切换动画
- 全局方向感知转场：前进右入、返回左出
- 底部五键工具栏（← → ⌂ ☰ ↻），触控区加大

## 📦 下载

前往 [Actions](../../actions) 页面，取最新一次成功构建的 `zhiwei-release-apk` 产物；
或给仓库打 tag 触发自动发布到 [Releases](../../releases)。

> ⚠️ 正式签名与早期 debug 版签名不同，升级前需卸载旧版（书签等本地数据将清空）。

## 🛠️ 自行构建

### 环境要求
- JDK 17+
- Android SDK（compileSdk 35）

### Debug 构建
```bash
./gradlew assembleDebug
```

### Release 构建（需签名）
在项目根目录创建 `keystore.properties`（该文件已被 .gitignore 排除）：
```properties
storeFile=zhiwei-release.jks
storePassword=你的密钥库密码
keyAlias=你的别名
keyPassword=你的密钥密码
```
然后：
```bash
./gradlew assembleRelease
```

### CI 构建（推荐）
推送至 `main` 分支即自动构建 Release APK。需在仓库 Secrets 中配置：

| Secret | 说明 |
|---|---|
| `ZW_KEYSTORE_BASE64` | 签名密钥库的 Base64 编码 |
| `ZW_STORE_PASS` | 密钥库密码 |
| `ZW_KEY_ALIAS` | 密钥别名 |
| `ZW_KEY_PASS` | 密钥密码 |

## 🏗️ 技术栈

| 层 | 技术 |
|---|---|
| UI | Jetpack Compose + Material 3（BOM 2026.01.01） |
| 内核 | Android WebView（androidx.webkit 1.12.1） |
| 语言 | Kotlin 2.4.21 |
| 构建 | Gradle 9.8.2 + AGP 9.4.1 |
| 网络 | OkHttp（OpenAI 兼容 API 协议） |
| 加固 | R8 全量混淆 + 资源压缩 + arm64-v8a 单架构 |

## 📁 项目结构

```
├── app/src/main/java/com/java/myapplication/
│   ├── MainActivity.kt          # 启动路由（splash→onboarding→browser）+ 转场动画
│   ├── BrowserViewModel.kt      # 多标签/WebView 状态管理
│   ├── BrowserSettings.kt       # 设置持久化 + AI 厂商预设
│   ├── ai/AiClient.kt           # AI 请求层（文本/多模态/身份锁定）
│   └── ui/                      # Compose 界面（浏览器/设置/书签/AI面板/引导页）
├── .github/workflows/           # CI：Release 自动构建
└── tools/                       # ARM64 aapt2（本地 ARM64 环境构建用）
```

## 🔒 隐私

- 无账号体系、无统计埋点、无第三方广告 SDK
- 书签、历史、设置全部存储于本地
- AI 请求直连用户自配置的 API 端点，不经过任何中间服务器

## 📄 许可

[MIT License](LICENSE)