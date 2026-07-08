package com.olsc.videotest

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.olsc.videotest.collector.CodecInfoCollector
import com.olsc.videotest.collector.DecoderStressTester
import com.olsc.videotest.collector.DeviceInfoCollector
import com.olsc.videotest.report.ReportGenerator
import kotlinx.coroutines.*

class VideoTestActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var contentLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStartTest: Button
    private lateinit var btnExportReport: Button
    private lateinit var statusText: TextView

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var fullReport: ReportGenerator.FullReport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF0D0D0D.toInt())
        }

        // 标题
        rootLayout.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            setTextColor(0xFFD4AF37.toInt())
            setPadding(0, 8, 0, 8)
        })

        // 子标题
        rootLayout.addView(TextView(this).apply {
            text = getString(R.string.subtitle)
            textSize = 13f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, 16)
        })

        // 状态文本
        statusText = TextView(this).apply {
            text = getString(R.string.status_ready)
            textSize = 14f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 0, 0, 8)
        }
        rootLayout.addView(statusText)

        // 进度条
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24
            )
        }
        rootLayout.addView(progressBar)

        // 按钮行
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }
        btnStartTest = Button(this).apply {
            text = getString(R.string.btn_start_test)
            setTextColor(0xFF0D0D0D.toInt())
            setBackgroundColor(0xFFD4AF37.toInt())
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            setOnClickListener { startTest() }
        }
        btnRow.addView(btnStartTest)

        btnExportReport = Button(this).apply {
            text = getString(R.string.btn_export)
            setTextColor(0xFFD4AF37.toInt())
            setBackgroundColor(0xFF1A1A1A.toInt())
            textSize = 15f
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8
            }
            setOnClickListener { exportReport() }
        }
        btnRow.addView(btnExportReport)
        rootLayout.addView(btnRow)

        // 滚动内容区
        scrollView = ScrollView(this)
        contentLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(contentLayout)
        rootLayout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(rootLayout)

        // ── 防止内容与顶部通知栏 / 底部导航栏重叠 ──
        // Android 15 (API 35+) 默认开启边到边渲染，需手动处理系统栏区域
        val basePaddingLeft = rootLayout.paddingLeft
        val basePaddingRight = rootLayout.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(basePaddingLeft, bars.top, basePaddingRight, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        // 状态栏背景色与 App 主题一致
        window.statusBarColor = 0xFF0D0D0D.toInt()

        checkStoragePermission()
    }

    private fun checkStoragePermission() {
        when {
            // ── Android 11+ (API 30+) ──────────────────────────────────
            // MediaStore 写入 Downloads 集合不需运行时权限。
            // 如需通过文件管理器直接访问，可选申请 MANAGE_EXTERNAL_STORAGE。
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (!Environment.isExternalStorageManager()) {
                    showManageStorageDialog()
                }
            }
            // ── Android 10 (API 29) ────────────────────────────────────
            // MediaStore 不需任何运行时权限即可导出报告。
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // 无需运行时权限
            }
            // ── Android 9 及以下 (API 28-) ─────────────────────────────
            else -> {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    ) {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.permission_title))
                            .setMessage(getString(R.string.permission_rationale))
                            .setPositiveButton(getString(R.string.permission_grant)) { _, _ ->
                                ActivityCompat.requestPermissions(
                                    this,
                                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                    REQUEST_WRITE_STORAGE
                                )
                            }
                            .setNegativeButton(getString(R.string.permission_skip)) { _, _ -> }
                            .show()
                    } else {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            REQUEST_WRITE_STORAGE
                        )
                    }
                }
            }
        }
    }

    /**
     * API 30+ 弹窗引导用户前往设置页授予「所有文件管理权限」，
     * 以便报告可直接通过文件管理器访问。此权限为可选项，不强制。
     */
    private fun showManageStorageDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.manage_storage_title))
            .setMessage(getString(R.string.manage_storage_message))
            .setPositiveButton(getString(R.string.manage_storage_goto)) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.permission_skip)) { _, _ -> }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.permission_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_WRITE_STORAGE = 100
    }

    private fun startTest() {
        if (btnStartTest.isEnabled.not()) return
        btnStartTest.isEnabled = false
        progressBar.progress = 0
        contentLayout.removeAllViews()
        fullReport = null

        scope.launch {
            // 1. 设备信息
            updateStatus(getString(R.string.status_collecting_device), 5)
            val deviceInfo = withContext(Dispatchers.IO) { DeviceInfoCollector.collect() }
            addResultSection(getString(R.string.section_device), buildDeviceString(deviceInfo))

            // 2. 编码器信息
            updateStatus(getString(R.string.status_collecting_codec), 25)
            val codecSummary = withContext(Dispatchers.IO) { CodecInfoCollector.collect() }
            addResultSection(getString(R.string.section_codec), buildCodecString(codecSummary))

            // 3. 格式支持
            updateStatus(getString(R.string.status_checking_formats), 45)
            addResultSection(getString(R.string.section_formats), buildFormatSupportString(codecSummary))

            // 4. 解码性能
            updateStatus(getString(R.string.status_stress_test), 65)
            val stressSummary = withContext(Dispatchers.IO) { DecoderStressTester.runFullTest(codecSummary) }
            addResultSection(getString(R.string.section_perf), buildStressString(stressSummary))

            // 5. 结论
            updateStatus(getString(R.string.status_generating), 90)
            val report = ReportGenerator.FullReport(
                deviceInfo = deviceInfo,
                codecSummary = codecSummary,
                stressSummary = stressSummary,
            )
            fullReport = report
            updateStatus(getString(R.string.status_done), 100)
            addResultSection(getString(R.string.section_conclusion), buildConclusionString(report))

            btnExportReport.isEnabled = true
            btnStartTest.isEnabled = true
        }
    }

    private suspend fun updateStatus(text: String, progress: Int) {
        statusText.text = text
        progressBar.progress = progress
        delay(100)
    }

    private fun addResultSection(title: String, content: String) {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
            setBackgroundColor(0xFF141414.toInt())
        }
        section.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(0xFFE8C84A.toInt())
            setPadding(12, 12, 12, 4)
        })
        section.addView(TextView(this).apply {
            text = content
            textSize = 12f
            setTextColor(0xFFC0C0C0.toInt())
            setPadding(12, 4, 12, 12)
        })
        // 分隔线
        section.addView(View(this).apply {
            setBackgroundColor(0xFF333333.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                setMargins(0, 4, 0, 4)
            }
        })
        contentLayout.addView(section)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun buildDeviceString(info: DeviceInfoCollector.DeviceInfo): String = buildString {
        appendLine("制造商: ${info.manufacturer}")
        appendLine("型号: ${info.model}")
        appendLine("硬件: ${info.hardware}")
        appendLine("Android: ${info.androidVersion}")
        appendLine("内核: ${info.kernelVersion}")
        appendLine("架构: ${info.cpu.architecture}")
        appendLine("核心数: ${info.cpu.cores}")
        appendLine("最大频率: ${info.cpu.maxFrequency}")
        appendLine("总 RAM: ${info.memory.totalRam}")
        appendLine("可用 RAM: ${info.memory.availableRam}")
        if (info.cpu.features.isNotEmpty()) {
            appendLine("CPU 特性: ${info.cpu.features.take(120)}")
        }
    }

    private fun buildCodecString(cs: CodecInfoCollector.CodecSummary): String = buildString {
        appendLine("硬件解码器: ${cs.hardwareDecoders.size} 个")
        for (c in cs.hardwareDecoders) {
            val shortName = c.canonicalName.substringAfterLast(".")
            appendLine("  ● $shortName (${c.mimeType})")
        }
        appendLine("")
        appendLine("软件解码器: ${cs.softwareDecoders.size} 个")
        for (c in cs.softwareDecoders.take(8)) {
            val shortName = c.canonicalName.substringAfterLast(".")
            appendLine("  ○ $shortName (${c.mimeType})")
        }
        if (cs.softwareDecoders.size > 8) {
            appendLine("  ... 还有 ${cs.softwareDecoders.size - 8} 个")
        }
    }

    private fun buildFormatSupportString(cs: CodecInfoCollector.CodecSummary): String = buildString {
        appendLine("视频格式:")
        for (mime in CodecInfoCollector.KNOWN_VIDEO_MIMES) {
            val supported = mime in cs.supportedMimeTypes
            val name = ReportGenerator.mimeToFriendlyName(mime)
            appendLine("  ${if (supported) "✔" else "✘"} $name")
        }
        appendLine("")
        appendLine("音频格式:")
        for (mime in CodecInfoCollector.KNOWN_AUDIO_MIMES) {
            val supported = mime in cs.supportedMimeTypes
            val name = ReportGenerator.mimeToFriendlyName(mime)
            appendLine("  ${if (supported) "✔" else "✘"} $name")
        }
    }

    private fun buildStressString(ss: DecoderStressTester.StressSummary): String = buildString {
        appendLine("【芯片硬件报告的解码能力（VideoCapabilities）】")
        listOfNotNull(
            "H.264" to ss.h264Result,
            "HEVC" to ss.hevcResult,
            "VP9" to ss.vp9Result,
            "AV1" to ss.av1Result,
        ).forEach { (name, res) ->
            if (res != null) {
                val status = if (res.canDecode) "✔" else "✘"
                val resInfo = if (res.maxResolution.isNotEmpty()) "最高 ${res.maxResolution}" else "不支持"
                val fpsInfo = if (res.maxFps > 0) " @ ${res.maxFps}fps" else ""
                val hwDetail = if (res.hwSupported.isNotEmpty() && res.canDecode) "\n     详细: ${res.hwSupported}" else ""
                val timeStr = if (res.initTimeMs >= 0) " (启动${res.initTimeMs}ms)" else ""
                appendLine("  $status $name: $resInfo$fpsInfo$timeStr$hwDetail")
            } else {
                appendLine("  ✘ $name: 未找到硬件解码器")
            }
        }
        if (ss.otherHwDecoders.isNotEmpty()) {
            appendLine("")
            appendLine("【其他视频硬件解码器】")
            for (r in ss.otherHwDecoders) {
                val status = if (r.canDecode) "✔" else "✘"
                val name = r.codecName.substringAfterLast(".")
                appendLine("  $status $name (${r.mimeType})")
            }
        }
        appendLine("")
        appendLine("【并发解码能力】")
        appendLine("  H.264: ${ss.concurrentH264} 路")
        appendLine("  HEVC: ${ss.concurrentHevc} 路")
        appendLine("")
        appendLine("最高可支持分辨率: ${ss.highestResolution.ifEmpty { "未知" }}")
    }

    private fun buildConclusionString(report: ReportGenerator.FullReport): String {
        val cs = report.codecSummary
        val ss = report.stressSummary
        val di = report.deviceInfo

        var score = 0
        val hasH264Hw = cs.hardwareDecoders.any { it.mimeType == "video/avc" || it.mimeType == "video/avc" }
        val hasHevcHw = cs.hardwareDecoders.any { it.mimeType == "video/hevc" }
        val hasVp9 = cs.hardwareDecoders.any { it.mimeType == "video/x-vnd.on2.vp9" || it.mimeType == "video/vp9" }
        val hasAv1 = cs.hardwareDecoders.any { it.mimeType == "video/av1" }

        val h264Ok = ss.h264Result?.canDecode == true
        val hevcOk = ss.hevcResult?.canDecode == true
        val vp9Ok = ss.vp9Result?.canDecode == true
        val av1Ok = ss.av1Result?.canDecode == true

        val bestRes = ss.highestResolution

        return buildString {
            appendLine("H.264 硬解: ${if (h264Ok) "✔ ${ss.h264Result?.maxResolution ?: "支持"}" else "✘ 不支持"}")
            appendLine("HEVC 硬解: ${if (hevcOk) "✔ ${ss.hevcResult?.maxResolution ?: "支持"}" else "✘ 不支持"}")
            appendLine("VP9 解码: ${if (vp9Ok) "✔ ${ss.vp9Result?.maxResolution ?: "支持"}" else "✘ 不支持"}")
            appendLine("AV1 解码: ${if (av1Ok) "✔ ${ss.av1Result?.maxResolution ?: "支持"}" else "✘ 不支持"}")
            appendLine("H.264 并发: ${ss.concurrentH264} 路")
            appendLine("HEVC 并发: ${ss.concurrentHevc} 路")
            appendLine("最高分辨率: ${bestRes.ifEmpty { "未识别" }}")

            if (h264Ok) score += 20
            if (hevcOk) score += 20
            if (vp9Ok) score += 15
            if (av1Ok) score += 15
            if (ss.concurrentH264 >= 4) score += 8
            else if (ss.concurrentH264 >= 2) score += 4
            if (ss.concurrentHevc >= 2) score += 4
            // 分辨率加分（含帧率）
            if (bestRes.contains("4320@60")) score += 15
            else if (bestRes.contains("4320")) score += 12
            else if (bestRes.contains("2160@60")) score += 10
            else if (bestRes.contains("2160")) score += 8
            else if (bestRes.contains("1440")) score += 5
            else if (bestRes.contains("1080")) score += 3
            // 内存加分
            val ramMatch = Regex("""(\d+\.?\d*)""").find(di.memory.totalRam)
            val ramGb = ramMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            if (ramGb >= 8) score += 5
            else if (ramGb >= 4) score += 3

            appendLine("")
            appendLine("综合评分: $score/100")
            appendLine("评级: ${ReportGenerator.getRating(score)}")
        }
    }

    private fun exportReport() {
        val report = fullReport ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val path = ReportGenerator.exportReport(this@VideoTestActivity, report)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoTestActivity, "报告已导出到: $path", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoTestActivity, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
