package com.olsc.videotest.collector

import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * 设备与 CPU 信息收集器
 * 收集 SoC 信息、CPU 架构、核心数、频率、内存等
 */
object DeviceInfoCollector {

    data class CpuInfo(
        val architecture: String = "",
        val cores: Int = 0,
        val features: String = "",
        val maxFrequency: String = "",
        val minFrequency: String = "",
        val governor: String = "",
        val implementer: String = "",
        val part: String = "",
    )

    data class MemoryInfo(
        val totalRam: String = "",
        val availableRam: String = "",
        val swapTotal: String = "",
        val totalStorage: String = "",
        val availableStorage: String = "",
    )

    data class DeviceInfo(
        val manufacturer: String = Build.MANUFACTURER,
        val model: String = Build.MODEL,
        val brand: String = Build.BRAND,
        val device: String = Build.DEVICE,
        val product: String = Build.PRODUCT,
        val board: String = Build.BOARD,
        val hardware: String = Build.HARDWARE,
        val fingerprint: String = Build.FINGERPRINT,
        val androidVersion: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        val buildType: String = Build.TYPE,
        val buildTime: String = "",
        val bootloader: String = Build.BOOTLOADER,
        val radioVersion: String = Build.getRadioVersion() ?: "未知",
        val display: String = Build.DISPLAY,
        val cpu: CpuInfo = CpuInfo(),
        val memory: MemoryInfo = MemoryInfo(),
        val screenResolution: String = "",
        val screenDpi: String = "",
        val openGlEsVersion: String = "",
        val kernelVersion: String = "",
        val javaVmVersion: String = "${System.getProperty("java.vm.version") ?: "未知"}",
        val osName: String = System.getProperty("os.name") ?: "未知",
    )

    fun collect(): DeviceInfo {
        val cpu = collectCpuInfo()
        val memory = collectMemoryInfo()
        return DeviceInfo(
            cpu = cpu,
            memory = memory,
            kernelVersion = readKernelVersion(),
            buildTime = readBuildTime(),
        )
    }

    private fun collectCpuInfo(): CpuInfo {
        val cpuInfo = StringBuilder()
        val cpuFile = File("/proc/cpuinfo")
        if (cpuFile.exists()) {
            try {
                BufferedReader(FileReader(cpuFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        cpuInfo.appendLine(line)
                    }
                }
            } catch (_: Exception) {}
        }

        val text = cpuInfo.toString()
        return CpuInfo(
            architecture = extractCpuValue(text, "CPU architecture") ?: extractCpuValue(text, "architecture") ?: Build.CPU_ABI,
            cores = Runtime.getRuntime().availableProcessors(),
            features = extractCpuValue(text, "Features") ?: extractCpuValue(text, "flags") ?: "",
            maxFrequency = readCpuFreq("cpuinfo_max_freq"),
            minFrequency = readCpuFreq("cpuinfo_min_freq"),
            implementer = extractCpuValue(text, "CPU implementer") ?: "",
            part = extractCpuValue(text, "CPU part") ?: "",
        )
    }

    private fun extractCpuValue(text: String, key: String): String? {
        val regex = Regex("""$key\s*:\s*(.+)""")
        val match = regex.find(text) ?: return null
        // 对于多核设备，每核都有相同字段，取第一个非空值
        return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
    }

    private fun readCpuFreq(fileName: String): String {
        return try {
            val freqFile = File("/sys/devices/system/cpu/cpu0/cpufreq/$fileName")
            if (freqFile.exists()) {
                val khz = freqFile.readText().trim().toLongOrNull() ?: 0L
                if (khz > 0) "${khz / 1000} MHz" else ""
            } else ""
        } catch (_: Exception) { "" }
    }

    private fun collectMemoryInfo(): MemoryInfo {
        val memInfo = StringBuilder()
        val memFile = File("/proc/meminfo")
        if (memFile.exists()) {
            try {
                BufferedReader(FileReader(memFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        memInfo.appendLine(line)
                    }
                }
            } catch (_: Exception) {}
        }

        val text = memInfo.toString()
        return MemoryInfo(
            totalRam = extractMemValue(text, "MemTotal"),
            availableRam = extractMemValue(text, "MemAvailable"),
            swapTotal = extractMemValue(text, "SwapTotal"),
            totalStorage = "",
            availableStorage = "",
        )
    }

    private fun extractMemValue(text: String, key: String): String {
        val regex = Regex("""$key\s*:\s*(\d+)\s*kB""")
        val match = regex.find(text) ?: return ""
        val kb = match.groupValues[1].toLongOrNull() ?: return ""
        return if (kb > 1_048_576) String.format("%.1f GB", kb / 1_048_576.0) else "${kb / 1024} MB"
    }

    private fun readKernelVersion(): String {
        return try {
            val proc = Runtime.getRuntime().exec("uname -r")
            proc.inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) { "" }
    }

    private fun readBuildTime(): String {
        return try {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = Build.TIME
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(cal.time)
        } catch (_: Exception) { "" }
    }
}
