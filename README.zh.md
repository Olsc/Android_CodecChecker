<div align="center">

# 📱 Android CodecChecker

**设备视频编解码能力检测工具**

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)](https://www.android.com)
[![Min SDK](https://img.shields.io/badge/minSdk-24-brightgreen)](app/build.gradle.kts)
[![Target SDK](https://img.shields.io/badge/targetSdk-36-blue)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow)](#许可证)

[English](./README.md)

</div>

## 项目简介

Android CodecChecker 是一款 Android 设备诊断工具，用于检测设备的硬件/软件视频编解码器支持情况并评估解码性能。它可生成全面的设备检测报告（HTML + TXT），适用于设备兼容性测试、视频应用开发调试及硬件评估等场景。

## 功能特性

| 类别 | 功能 |
|------|------|
| **设备信息** | 制造商、型号、硬件平台、Android 版本、内核版本、构建指纹 |
| **CPU 检测** | 架构、核心数、频率范围、特性（NEON 等）、实现商/部件解码 |
| **内存信息** | 总 RAM、可用 RAM、Swap 大小 |
| **编解码器扫描** | 完整 `MediaCodecList` 扫描，硬件/软件分类，编码器/解码器分离 |
| **视频格式** | H.264/AVC、H.265/HEVC、VP8、VP9、AV1、MPEG-4、VC-1、Dolby Vision、MPEG-2、H.263 |
| **音频格式** | AAC、MP3、AC-3、E-AC-3、FLAC、Opus、Vorbis、WMA、AMR、TrueHD、DTS |
| **性能测试** | 解码器创建耗时、基础解码验证、分辨率/帧率能力矩阵（1080p→8K） |
| **并发测试** | H.264 与 HEVC 最大并发硬件解码路数（多实例压力测试） |
| **评分系统** | 基于硬解能力、并发能力、分辨率、内存的综合评分 0–100 |
| **报告导出** | 深色主题 HTML 完整报告 + 纯文本摘要 → `/sdcard/CodecCheckerReport/` |

## 快速开始

### 源码构建

```bash
# 克隆项目
git clone https://github.com/your-org/Android_CodecChecker.git
cd Android_CodecChecker

# 构建 Debug APK（需要 Android SDK）
./gradlew assembleDebug

# APK 位置：
#   app/build/outputs/apk/debug/app-debug.apk
```

**构建要求：**
- Android Studio Hedgehog（2023.1.1）或更新版本
- JDK 17+
- Android SDK platform 37

### 安装运行

1. 构建 APK 后通过 Android Studio 安装，或运行：
   ```bash
   ./gradlew installDebug
   ```
2. 打开应用 → 点击 **▶ 开始测试**
3. 5 步检测将自动运行：
   - 设备与 CPU 信息 → 编解码器概览 → 格式支持 → 解码器性能 → 检测结论
4. 点击 **📤 导出报告** 保存完整报告

## 报告输出

报告保存到设备：

```
/sdcard/CodecCheckerReport/
├── codec_report_20260708_143025.html    ← 完整 HTML 报告（深色主题）
└── codec_report_20260708_143025.txt     ← 纯文本摘要
```

> **文件访问说明：**
> - **Android 10+**：通过 MediaStore 写入 Downloads 集合，可通过下载应用 / Files by Google 访问
> - **Android 9 及以下**：直接文件写入需要 `WRITE_EXTERNAL_STORAGE` 权限
> - 可前往设置中授予**「所有文件管理权限」**以通过文件管理器直接浏览

### HTML 报告章节

| # | 章节 | 内容 |
|---|------|------|
| 1 | 设备信息 | 制造商、型号、硬件、Android/内核版本、指纹 |
| 2 | CPU 信息 | 架构、核心数、频率、特性、实现商 |
| 3 | 内存信息 | 总 RAM、可用 RAM、Swap |
| 4 | 解码器总览 | 硬件/软件解码器、编码器数量 |
| 5 | 视频格式支持 | 常见视频 MIME 类型支持矩阵 |
| 6 | 音频格式支持 | 常见音频 MIME 类型支持矩阵 |
| 7 | 硬件解码器详情 | 各编码器的 Profile、分辨率、颜色格式 |
| 8 | 解码性能测试 | 分辨率能力矩阵（1080p30→8K60）+ 启动耗时 |
| 9 | 并发解码能力 | H.264 与 HEVC 最大并发路数 |
| 10 | 检测结论 | 摘要 + 综合评分 + 评级 |

## 架构

```
app/
├── build.gradle.kts                         # 模块构建配置
└── src/main/
    ├── AndroidManifest.xml                  # 权限与 Activity 声明
    ├── res/
    │   ├── values/strings.xml               # 英文字符串
    │   ├── values-zh/strings.xml            # 中文字符串
    │   └── values/themes.xml                # 深色主题
    └── java/com/olsc/codecchecker/
        ├── MainActivity.kt                  # 主界面（纯代码，无 XML 布局）
        ├── collector/
        │   ├── DeviceInfoCollector.kt       # 设备/CPU/内存信息收集
        │   ├── CodecInfoCollector.kt        # 编解码器枚举与分类
        │   └── DecoderStressTester.kt       # 解码器性能与并发测试
        └── report/
            └── ReportGenerator.kt           # HTML + TXT 报告生成与导出
```

### 检测流程

```
开始 → 设备信息（CPU/内存/SOC）→ 编解码器扫描（MediaCodecList）
→ 格式支持检查 → 解码器压力测试（初始化和并发）
→ 结论（评分 + 评级）→ 显示结果 / 导出报告
```

## 技术栈

| 组件 | 库/工具 |
|------|---------|
| 语言 | Kotlin |
| 界面 | 纯代码（无 XML），深色主题 |
| 异步 | Kotlin 协程 |
| 编解码 API | `android.media.MediaCodecList`、`MediaCodec`、`MediaCodecInfo` |
| 导出 (API 29+) | `MediaStore.Downloads` |
| 导出 (API 28-) | 直接文件 I/O |
| Min SDK / Target | 24 / 36 |
| 构建 | AGP 9.2.1, Kotlin DSL |

## 多语言

| 语言 | 文件 |
|------|------|
| 简体中文（默认） | `res/values-zh/strings.xml` |
| English | `res/values/strings.xml` |

系统会根据设备语言自动加载对应语言的字符串。

## 截图

> *截图即将添加 — 欢迎贡献！*

<details>
<summary>HTML 报告主题预览</summary>

HTML 报告采用自定义深色主题：

- 金色标题强调色（`#d4af37`）
- 深色背景上的等宽数据表格
- 彩色状态徽章（绿色 ✅ / 红色 ❌ / 蓝色 ℹ️）
- 响应式布局，适配移动端浏览

</details>

## 许可证

```
MIT License

版权所有 (c) 2026 OLSC

特此免费授予任何获得本软件及关联文档文件（以下简称"软件"）副本的人
不受限制地处理本软件的权利，包括但不限于使用、复制、修改、合并、发布、
分发、再许可和/或出售本软件副本的权利，并允许获得本软件的人在满足
以下条件的情况下这样做...
```

---

<div align="center">

为 Android 开发者和设备测试者打造 ❤️

</div>
