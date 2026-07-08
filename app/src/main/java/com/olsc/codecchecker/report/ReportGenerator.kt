package com.olsc.codecchecker.report

import android.content.ContentValues
import android.content.Context
import android.media.MediaFormat
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.olsc.codecchecker.collector.CodecInfoCollector
import com.olsc.codecchecker.collector.DecoderStressTester
import com.olsc.codecchecker.collector.DeviceInfoCollector
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 报告生成与导出
 * 生成完整的设备检测报告（HTML 格式），导出到 /sdcard/CodecCheckerReport/
 */
object ReportGenerator {

    private const val TAG = "ReportGenerator"
    private const val REPORT_DIR = "CodecCheckerReport"

    data class FullReport(
        val deviceInfo: DeviceInfoCollector.DeviceInfo,
        val codecSummary: CodecInfoCollector.CodecSummary,
        val stressSummary: DecoderStressTester.StressSummary,
        val testTime: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    )

    /**
     * 生成 HTML 报告
     */
    fun generateHtml(report: FullReport): String {
        val sb = StringBuilder()
        val di = report.deviceInfo
        val cs = report.codecSummary
        val ss = report.stressSummary

        sb.appendLine("""<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>设备视频能力检测报告</title>
<style>
body { font-family: -apple-system, 'Segoe UI', Roboto, sans-serif; background: #0d0d0d; color: #e0e0e0; margin: 0; padding: 20px; }
h1 { color: #d4af37; border-bottom: 2px solid #d4af37; padding-bottom: 8px; }
h2 { color: #e8c84a; margin-top: 28px; border-left: 4px solid #d4af37; padding-left: 12px; }
h3 { color: #c0c0c0; }
table { width: 100%; border-collapse: collapse; margin: 12px 0; background: #1a1a1a; border-radius: 8px; overflow: hidden; }
th, td { padding: 10px 14px; text-align: left; border-bottom: 1px solid #333; }
th { background: #2a2a2a; color: #d4af37; font-weight: 600; }
tr:hover { background: #282828; }
.codec-ok { color: #4caf50; }
.codec-fail { color: #f44336; }
.codec-hw { color: #66bbf0; font-weight: bold; }
.codec-sw { color: #b0b0b0; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 0.85em; margin: 1px; }
.badge-green { background: #1b5e20; color: #a5d6a7; }
.badge-red { background: #5e1b1b; color: #ef9a9a; }
.badge-blue { background: #1a3a5e; color: #90caf9; }
.footer { margin-top: 40px; padding: 16px; background: #1a1a1a; border-radius: 8px; text-align: center; color: #888; font-size: 0.9em; }
.section { background: #141414; border-radius: 8px; padding: 16px; margin: 16px 0; }
</style>
</head>
<body>
<h1>📱 设备视频能力检测报告</h1>
<p>检测时间: ${report.testTime}</p>""")

        // ========== 设备信息 ==========
        sb.appendLine("""<h2>1️⃣ 设备信息</h2>
<table>
<tr><th>字段</th><th>值</th></tr>
<tr><td>制造商</td><td>${escHtml(di.manufacturer)}</td></tr>
<tr><td>型号</td><td>${escHtml(di.model)}</td></tr>
<tr><td>品牌</td><td>${escHtml(di.brand)}</td></tr>
<tr><td>硬件平台</td><td>${escHtml(di.hardware)}</td></tr>
<tr><td>Android 版本</td><td>${escHtml(di.androidVersion)}</td></tr>
<tr><td>内核版本</td><td>${escHtml(di.kernelVersion)}</td></tr>
<tr><td>构建指纹</td><td style="font-size:0.85em;word-break:break-all;">${escHtml(di.fingerprint)}</td></tr>
</table>""")

        // ========== CPU 信息 ==========
        val cpu = di.cpu
        sb.appendLine("""<h2>2️⃣ CPU 信息</h2>
<table>
<tr><th>字段</th><th>值</th></tr>
<tr><td>架构</td><td>${escHtml(cpu.architecture)}</td></tr>
<tr><td>核心数</td><td>$cpu.cores</td></tr>
<tr><td>最大频率</td><td>${escHtml(cpu.maxFrequency)}</td></tr>
<tr><td>最小频率</td><td>${escHtml(cpu.minFrequency)}</td></tr>
<tr><td>CPU 特性</td><td style="word-break:break-all;">${escHtml(cpu.features)}</td></tr>
""")
        if (cpu.implementer.isNotEmpty()) {
            sb.appendLine("<tr><td>实现商</td><td>${cpu.implementer} (${decodeCpuImplementer(cpu.implementer)})</td></tr>")
        }
        if (cpu.part.isNotEmpty()) {
            sb.appendLine("<tr><td>CPU Part</td><td>${cpu.part}</td></tr>")
        }
        sb.appendLine("</table>")

        // ========== 内存 ==========
        val mem = di.memory
        sb.appendLine("""<h2>3️⃣ 内存信息</h2>
<table>
<tr><th>字段</th><th>值</th></tr>
<tr><td>总 RAM</td><td>${escHtml(mem.totalRam)}</td></tr>
<tr><td>可用 RAM</td><td>${escHtml(mem.availableRam)}</td></tr>
<tr><td>Swap</td><td>${escHtml(mem.swapTotal)}</td></tr>
</table>""")

        // ========== 解码器总览 ==========
        sb.appendLine("""<h2>4️⃣ 解码器总览</h2>
<table>
<tr><th>指标</th><th>数量</th></tr>
<tr><td>硬件解码器</td><td>${cs.hardwareDecoders.size}</td></tr>
<tr><td>软件解码器</td><td>${cs.softwareDecoders.size}</td></tr>
<tr><td>硬件编码器</td><td>${cs.hardwareEncoders.size}</td></tr>
<tr><td>软件编码器</td><td>${cs.softwareEncoders.size}</td></tr>
<tr><td>编码器总数</td><td>${cs.totalCodecs}</td></tr>
</table>""")

        // ========== 支持的视频格式 ==========
        sb.appendLine("<h2>5️⃣ 视频格式支持</h2>")
        val videoMimes = cs.supportedMimeTypes.filter { it.startsWith("video/") }.sorted()
        sb.appendLine("<table><tr><th>MIME 类型</th><th>格式名称</th><th>状态</th></tr>")
        val allCheckMimes = CodecInfoCollector.KNOWN_VIDEO_MIMES.distinct()
        for (mime in allCheckMimes) {
            val supported = mime in videoMimes
            val name = mimeToFriendlyName(mime)
            val badge = if (supported) "<span class=\"badge badge-green\">✔ 支持</span>"
            else "<span class=\"badge badge-red\">✘ 不支持</span>"
            sb.appendLine("<tr><td>$mime</td><td>$name</td><td>$badge</td></tr>")
        }

        // 其他支持的但不在列表中的视频格式
        val extraVideoMimes = videoMimes - allCheckMimes.toSet()
        if (extraVideoMimes.isNotEmpty()) {
            for (mime in extraVideoMimes) {
                sb.appendLine("<tr><td>$mime</td><td>${mimeToFriendlyName(mime)}</td><td><span class=\"badge badge-blue\">✔ 支持</span></td></tr>")
            }
        }
        sb.appendLine("</table>")

        // ========== 音频格式支持 ==========
        val audioMimes = cs.supportedMimeTypes.filter { it.startsWith("audio/") }.sorted()
        sb.appendLine("<h2>6️⃣ 音频格式支持</h2>")
        sb.appendLine("<table><tr><th>MIME 类型</th><th>格式名称</th><th>状态</th></tr>")
        for (mime in CodecInfoCollector.KNOWN_AUDIO_MIMES) {
            val supported = mime in audioMimes
            val name = mimeToFriendlyName(mime)
            val badge = if (supported) "<span class=\"badge badge-green\">✔ 支持</span>"
            else "<span class=\"badge badge-red\">✘ 不支持</span>"
            sb.appendLine("<tr><td>$mime</td><td>$name</td><td>$badge</td></tr>")
        }
        for (mime in audioMimes - CodecInfoCollector.KNOWN_AUDIO_MIMES.toSet()) {
            sb.appendLine("<tr><td>$mime</td><td>${mimeToFriendlyName(mime)}</td><td><span class=\"badge badge-blue\">✔ 支持</span></td></tr>")
        }
        sb.appendLine("</table>")

        // ========== 硬件解码器详情 ==========
        if (cs.hardwareDecoders.isNotEmpty()) {
            sb.appendLine("<h2>7️⃣ 硬件解码器详情</h2>")
            for (codec in cs.hardwareDecoders) {
                val profiles = if (codec.supportedProfiles.isNotEmpty())
                    codec.supportedProfiles.joinToString(", ") else "通用"
                sb.appendLine("""<div class="section">
<h3>${escHtml(codec.name)}</h3>
<table>
<tr><td>MIME 类型</td><td>${codec.mimeType}</td></tr>
<tr><td>支持 Profile/Level</td><td>${escHtml(profiles)}</td></tr>
<tr><td>最大分辨率</td><td>${if (codec.maxResolution.isNotEmpty()) codec.maxResolution else "未获取"}</td></tr>
<tr><td>颜色格式</td><td>${codec.colorFormats.joinToString(", ")}</td></tr>
</table></div>""")
            }
        }

        // ========== 性能测试结果 ==========
        sb.appendLine("<h2>8️⃣ 解码性能测试</h2>")
        sb.appendLine("<p style='color:#888;font-size:0.9em;'>数据来源于芯片硬件报告（VideoCapabilities.areSizeAndRateSupported），反映真实解码能力</p>")
        sb.appendLine("<h3>硬件解码能力矩阵</h3>")
        sb.appendLine("<table><tr><th>格式</th><th>1080p30</th><th>1080p60</th><th>4K30</th><th>4K60</th><th>8K30</th><th>8K60</th><th>启动耗时</th></tr>")

        data class Fmt(val label: String, val res: DecoderStressTester.DecodeTestResult?)
        for (f in listOf(
            Fmt("H.264", ss.h264Result), Fmt("HEVC", ss.hevcResult),
            Fmt("VP9", ss.vp9Result), Fmt("AV1", ss.av1Result)
        )) {
            val canDecode = f.res?.canDecode == true
            val timeStr = if (canDecode && f.res!!.initTimeMs >= 0) "${f.res.initTimeMs}ms" else "—"
            sb.append("<tr><td><b>${f.label}</b></td>")

            // 解析详细支持情况
            val hwStr = f.res?.hwSupported ?: ""
            for (point in listOf("1080p30", "1080p60", "4K30", "4K60", "8K30", "8K60")) {
                val supported = hwStr.contains("$point:✔")
                sb.append("<td>${if (supported) "<span class='codec-ok'>✔</span>" else "<span class='codec-fail'>✘</span>"}</td>")
            }
            val status = if (canDecode) "<span class=\"codec-ok\">✔</span>" else "<span class=\"codec-fail\">✘</span>"
            sb.appendLine("<td>$timeStr</td><td>$status</td></tr>")
        }
        sb.appendLine("</table>")

        if (ss.otherHwDecoders.isNotEmpty()) {
            sb.appendLine("<h3>其他硬件解码器</h3>")
            sb.appendLine("<table><tr><th>名称</th><th>格式</th><th>状态</th></tr>")
            for (r in ss.otherHwDecoders) {
                val status = if (r.canDecode) "<span class=\"codec-ok\">✔</span>" else "<span class=\"codec-fail\">✘</span>"
                sb.appendLine("<tr><td>${escHtml(r.codecName.take(40))}</td><td>${r.mimeType}</td><td>$status</td></tr>")
            }
            sb.appendLine("</table>")
        }

        // ========== 并发测试 ==========
        sb.appendLine("<h2>9️⃣ 并发解码能力</h2>")
        sb.appendLine("<table><tr><th>格式</th><th>并发路数</th></tr>")
        sb.appendLine("<tr><td>H.264 硬件解码器</td><td><b>${ss.concurrentH264}</b> 路</td></tr>")
        sb.appendLine("<tr><td>HEVC 硬件解码器</td><td><b>${ss.concurrentHevc}</b> 路</td></tr>")
        sb.appendLine("</table>")

        // ========== 结论 ==========
        sb.appendLine("<h2>🔟 检测结论</h2>")
        sb.append("<div class=\"section\">")
        sb.append(generateConclusion(report))
        sb.appendLine("</div>")

        sb.appendLine("""<div class="footer">
生成于 ${report.testTime} · CodecChecker v1.0
</div>
</body>
</html>""")

        return sb.toString()
    }

    /**
     * 生成纯文本摘要报告
     */
    fun generateTextSummary(report: FullReport): String {
        val sb = StringBuilder()
        val di = report.deviceInfo
        val cs = report.codecSummary

        sb.appendLine("=" .repeat(60))
        sb.appendLine("  设备视频能力检测报告")
        sb.appendLine("=" .repeat(60))
        sb.appendLine("检测时间: ${report.testTime}")
        sb.appendLine()
        sb.appendLine("--- 设备信息 ---")
        sb.appendLine("制造商: ${di.manufacturer}")
        sb.appendLine("型号: ${di.model}")
        sb.appendLine("硬件: ${di.hardware}")
        sb.appendLine("Android: ${di.androidVersion}")
        sb.appendLine("内核: ${di.kernelVersion}")
        sb.appendLine("CPU 架构: ${di.cpu.architecture}")
        sb.appendLine("CPU 核心: ${di.cpu.cores}")
        sb.appendLine("CPU 最大频率: ${di.cpu.maxFrequency}")
        sb.appendLine("CPU 特性: ${di.cpu.features}")
        sb.appendLine("总 RAM: ${di.memory.totalRam}")
        sb.appendLine()
        sb.appendLine("--- 解码器 ---")
        sb.appendLine("硬件解码器: ${cs.hardwareDecoders.size}")
        sb.appendLine("软件解码器: ${cs.softwareDecoders.size}")
        sb.appendLine("硬件编码器: ${cs.hardwareEncoders.size}")
        sb.appendLine("软件编码器: ${cs.softwareEncoders.size}")

        sb.appendLine()
        sb.appendLine("--- 主要视频格式支持 ---")
        for (mime in CodecInfoCollector.KNOWN_VIDEO_MIMES) {
            val supported = mime in cs.supportedMimeTypes
            sb.appendLine("  ${if (supported) "✔" else "✘"} ${mimeToFriendlyName(mime)} ($mime)")
        }

        sb.appendLine()
        sb.appendLine("--- 主要音频格式支持 ---")
        for (mime in CodecInfoCollector.KNOWN_AUDIO_MIMES) {
            val supported = mime in cs.supportedMimeTypes
            sb.appendLine("  ${if (supported) "✔" else "✘"} ${mimeToFriendlyName(mime)} ($mime)")
        }

        sb.appendLine()
        sb.appendLine("--- 检测结论 ---")
        sb.appendLine(generatePlainConclusion(report))

        sb.appendLine()
        sb.appendLine("=" .repeat(60))
        return sb.toString()
    }

    /**
     * 导出报告到 /sdcard/CodecCheckerReport/
     *
     * API 29+ 使用 MediaStore（无需运行时权限）
     * API 28-  使用直接文件访问（需要 WRITE_EXTERNAL_STORAGE）
     */
    fun exportReport(context: Context, report: FullReport): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val html = generateHtml(report)
        val txt = generateTextSummary(report)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // ========== API 29+：使用 MediaStore ==========
            val fileName = "codec_report_$timestamp"

            // 导出 HTML
            val htmlValues = ContentValues().apply {
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/$REPORT_DIR")
                put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.html")
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.MIME_TYPE, "text/html")
            }
            val htmlUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, htmlValues)
            if (htmlUri != null) {
                context.contentResolver.openOutputStream(htmlUri)?.use { os ->
                    os.write(html.toByteArray(Charsets.UTF_8))
                }
                htmlValues.clear()
                htmlValues.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(htmlUri, htmlValues, null, null)
            }

            // 导出 TXT
            val txtValues = ContentValues().apply {
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/$REPORT_DIR")
                put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.txt")
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }
            val txtUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, txtValues)
            if (txtUri != null) {
                context.contentResolver.openOutputStream(txtUri)?.use { os ->
                    os.write(txt.toByteArray(Charsets.UTF_8))
                }
                txtValues.clear()
                txtValues.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(txtUri, txtValues, null, null)
            }

            val path = "${Environment.getExternalStorageDirectory()}/$REPORT_DIR/"
            Log.i(TAG, "报告已导出到 MediaStore: $path")
            return path
        } else {
            // ========== API 28-：直接文件写入 ==========
            val reportDir = File(Environment.getExternalStorageDirectory(), REPORT_DIR)
            reportDir.mkdirs()

            val htmlFile = File(reportDir, "codec_report_$timestamp.html")
            htmlFile.writeText(html, Charsets.UTF_8)

            val txtFile = File(reportDir, "codec_report_$timestamp.txt")
            txtFile.writeText(txt, Charsets.UTF_8)

            Log.i(TAG, "报告已导出: ${reportDir.absolutePath}")
            return reportDir.absolutePath
        }
    }

    private fun generateConclusion(report: FullReport): String {
        val ss = report.stressSummary
        val di = report.deviceInfo

        val h264Ok = ss.h264Result?.canDecode == true
        val hevcOk = ss.hevcResult?.canDecode == true
        val vp9Ok = ss.vp9Result?.canDecode == true
        val av1Ok = ss.av1Result?.canDecode == true
        val bestRes = ss.highestResolution

        val sb = StringBuilder()
        sb.append("<ul>")
        sb.append("<li><b>H.264 硬解:</b> ${if (h264Ok) "✔ ${ss.h264Result?.maxResolution ?: "支持"}" else "✘ 不支持"}</li>")
        sb.append("<li><b>HEVC 硬解:</b> ${if (hevcOk) "✔ ${ss.hevcResult?.maxResolution ?: "支持"}" else "✘ 不支持"}</li>")
        sb.append("<li><b>VP9 解码:</b> ${if (vp9Ok) "✔ ${ss.vp9Result?.maxResolution ?: "支持"}" else "✘ 不支持"}</li>")
        sb.append("<li><b>AV1 解码:</b> ${if (av1Ok) "✔ ${ss.av1Result?.maxResolution ?: "支持"}" else "✘ 不支持"}</li>")
        sb.append("<li><b>最高分辨率:</b> ${bestRes.ifEmpty { "未识别" }}</li>")
        sb.append("<li><b>H.264 并发:</b> ${ss.concurrentH264} 路</li>")
        sb.append("<li><b>HEVC 并发:</b> ${ss.concurrentHevc} 路</li>")
        sb.append("<li><b>CPU 核心:</b> ${di.cpu.cores}</li>")

        var score = 0
        if (h264Ok) score += 20
        if (hevcOk) score += 20
        if (vp9Ok) score += 15
        if (av1Ok) score += 15
        if (ss.concurrentH264 >= 4) score += 8
        else if (ss.concurrentH264 >= 2) score += 4
        if (ss.concurrentHevc >= 2) score += 4
        if (bestRes.contains("4320@60")) score += 15
        else if (bestRes.contains("4320")) score += 12
        else if (bestRes.contains("2160@60")) score += 10
        else if (bestRes.contains("2160")) score += 8
        else if (bestRes.contains("1440")) score += 5
        else if (bestRes.contains("1080")) score += 3
        val ramMatch = Regex("""(\d+\.?\d*)""").find(di.memory.totalRam)
        val ramGb = ramMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        if (ramGb >= 8) score += 5
        else if (ramGb >= 4) score += 3

        sb.append("<li><b>综合评分:</b> $score/100</li>")
        sb.append("<li><b>评级:</b> ${getRating(score)}</li>")
        sb.append("</ul>")
        return sb.toString()
    }

    private fun generatePlainConclusion(report: FullReport): String {
        val ss = report.stressSummary

        val h264Ok = ss.h264Result?.canDecode == true
        val hevcOk = ss.hevcResult?.canDecode == true
        val vp9Ok = ss.vp9Result?.canDecode == true
        val av1Ok = ss.av1Result?.canDecode == true
        val bestRes = ss.highestResolution

        val sb = StringBuilder()
        sb.appendLine("H.264 硬解: ${if (h264Ok) "支持 (${ss.h264Result?.maxResolution ?: ""})" else "不支持"}")
        sb.appendLine("HEVC 硬解: ${if (hevcOk) "支持 (${ss.hevcResult?.maxResolution ?: ""})" else "不支持"}")
        sb.appendLine("VP9 解码: ${if (vp9Ok) "支持 (${ss.vp9Result?.maxResolution ?: ""})" else "不支持"}")
        sb.appendLine("AV1 解码: ${if (av1Ok) "支持 (${ss.av1Result?.maxResolution ?: ""})" else "不支持"}")
        sb.appendLine("最高分辨率: ${bestRes.ifEmpty { "未识别" }}")
        sb.appendLine("H.264 并发: ${ss.concurrentH264} 路")
        sb.appendLine("HEVC 并发: ${ss.concurrentHevc} 路")

        var score = 0
        if (h264Ok) score += 20
        if (hevcOk) score += 20
        if (vp9Ok) score += 15
        if (av1Ok) score += 15
        if (ss.concurrentH264 >= 4) score += 8
        else if (ss.concurrentH264 >= 2) score += 4
        if (ss.concurrentHevc >= 2) score += 4
        if (bestRes.contains("4320@60")) score += 15
        else if (bestRes.contains("4320")) score += 12
        else if (bestRes.contains("2160@60")) score += 10
        else if (bestRes.contains("2160")) score += 8
        else if (bestRes.contains("1440")) score += 5
        else if (bestRes.contains("1080")) score += 3

        sb.appendLine("综合评分: $score/100")
        sb.appendLine("评级: ${getRating(score)}")
        return sb.toString()
    }

    fun getRating(score: Int): String = when {
        score >= 85 -> "A: 卓越 — 设备视频解码能力极强"
        score >= 70 -> "B: 优秀 — 能流畅播放绝大部分视频"
        score >= 55 -> "C: 良好 — 日常使用无压力"
        score >= 40 -> "D: 一般 — 部分高码率视频可能卡顿"
        else -> "E: 较弱 — 视频播放能力有限"
    }

    private fun decodeCpuImplementer(impl: String): String = when (impl.trim()) {
        "0x41" -> "ARM"
        "0x42" -> "Broadcom"
        "0x43" -> "Cavium"
        "0x44" -> "DEC"
        "0x46" -> "Fujitsu"
        "0x48" -> "HiSilicon"
        "0x49" -> "Infineon"
        "0x4d" -> "Motorola/Freescale"
        "0x4e" -> "NVIDIA"
        "0x50" -> "APM"
        "0x51" -> "Qualcomm"
        "0x53" -> "Samsung"
        "0x54" -> "Texas Instruments"
        "0x56" -> "Marvell"
        "0x61" -> "Apple"
        "0x69" -> "Intel"
        "0xc0" -> "Ampere"
        else -> "未知"
    }

    fun mimeToFriendlyName(mime: String): String = when {
        mime.contains("avc") || mime.contains("h264") || mime.contains("h.264") -> "H.264/AVC"
        mime.contains("hevc") || mime.contains("h265") || mime.contains("h.265") -> "H.265/HEVC"
        mime.contains("vp9") -> "VP9"
        mime.contains("vp8") -> "VP8"
        mime.contains("av1") -> "AV1"
        mime.contains("mpeg4") -> "MPEG-4"
        mime.contains("mpeg2") -> "MPEG-2"
        mime.contains("mpeg") -> "MPEG"
        mime.contains("h263") -> "H.263"
        mime.contains("vc1") -> "VC-1"
        mime.contains("dolby") -> "Dolby Vision"
        mime.contains("aac") -> "AAC"
        mime.contains("mp3") || mime.contains("mpeg") && mime.contains("audio") -> "MP3"
        mime.contains("ac3") -> "AC-3"
        mime.contains("eac3") -> "E-AC-3"
        mime.contains("flac") -> "FLAC"
        mime.contains("opus") -> "Opus"
        mime.contains("vorbis") -> "Vorbis"
        mime.contains("wma") -> "WMA"
        mime.contains("amr") -> "AMR"
        mime.contains("truehd") -> "TrueHD"
        mime.contains("dts") -> "DTS"
        else -> mime.substringAfterLast("/").replaceFirstChar { it.uppercase() }
    }

    private fun escHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#039;")
    }
}
