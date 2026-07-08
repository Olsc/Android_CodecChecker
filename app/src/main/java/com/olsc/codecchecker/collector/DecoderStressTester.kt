package com.olsc.codecchecker.collector

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log

/**
 * 解码器压力测试
 *
 * 核心思路：不靠 configure() 猜测，而是直接读取硬件驱动报告的
 * VideoCapabilities（areSizeAndRateSupported），这才是芯片真正的解码能力。
 *
 * 测试内容：
 * - 1080p30/60、4K30/60、8K30/60 逐级检测硬件是否支持
 * - H.264 / HEVC / VP9 / AV1 四种格式
 * - H.264 和 HEVC 的并发解码路数测试
 */
object DecoderStressTester {

    private const val TAG = "DecoderStressTester"

    data class DecodeTestResult(
        val codecName: String,
        val mimeType: String,
        val mode: String,
        val initTimeMs: Long = -1,
        val canDecode: Boolean = false,
        val maxResolution: String = "",
        val maxFps: Int = 0,            // 最高分辨率下的最大帧率
        val hwSupported: String = "",    // 硬件报告的全部支持情况
    )

    data class StressSummary(
        val h264Result: DecodeTestResult? = null,
        val hevcResult: DecodeTestResult? = null,
        val vp9Result: DecodeTestResult? = null,
        val av1Result: DecodeTestResult? = null,
        val otherHwDecoders: List<DecodeTestResult> = emptyList(),
        val swDecodeResults: List<DecodeTestResult> = emptyList(),
        val concurrentH264: Int = 0,
        val concurrentHevc: Int = 0,
        val highestResolution: String = "",
    )

    private val TEST_POINTS = listOf(
        Triple(1920, 1080, 30) to "1080p30",
        Triple(1920, 1080, 60) to "1080p60",
        Triple(3840, 2160, 30) to "4K30",
        Triple(3840, 2160, 60) to "4K60",
        Triple(7680, 4320, 30) to "8K30",
        Triple(7680, 4320, 60) to "8K60",
    )

    private val TARGET_MIMES = setOf(
        MediaFormat.MIMETYPE_VIDEO_AVC,
        MediaFormat.MIMETYPE_VIDEO_HEVC,
        MediaFormat.MIMETYPE_VIDEO_VP9,
        MediaFormat.MIMETYPE_VIDEO_AV1,
    )

    fun runFullTest(codecSummary: CodecInfoCollector.CodecSummary): StressSummary {
        // 即使部分测试失败也返回已有结果，不抛异常
        return try {
            val videoHwDecoders = codecSummary.hardwareDecoders.filter { it.mimeType.startsWith("video/") }

            val h264Res = safeCall { testBestDecoder(videoHwDecoders, MediaFormat.MIMETYPE_VIDEO_AVC, "H.264") }
            val hevcRes = safeCall { testBestDecoder(videoHwDecoders, MediaFormat.MIMETYPE_VIDEO_HEVC, "HEVC") }
            val vp9Res = safeCall { testBestDecoder(videoHwDecoders, MediaFormat.MIMETYPE_VIDEO_VP9, "VP9") }
            val av1Res = safeCall { testBestDecoder(videoHwDecoders, MediaFormat.MIMETYPE_VIDEO_AV1, "AV1") }

            val otherHw = safeCall {
                videoHwDecoders
                    .filter { it.mimeType !in TARGET_MIMES }
                    .distinctBy { it.mimeType }
                    .map { quickTest(it) }
            } ?: emptyList()

            val h264Hw = videoHwDecoders.firstOrNull { it.mimeType == MediaFormat.MIMETYPE_VIDEO_AVC }
            val hevcHw = videoHwDecoders.firstOrNull { it.mimeType == MediaFormat.MIMETYPE_VIDEO_HEVC }
            val maxH264 = safeCall { testConcurrent(h264Hw, MediaFormat.MIMETYPE_VIDEO_AVC) } ?: 0
            val maxHevc = safeCall { testConcurrent(hevcHw, MediaFormat.MIMETYPE_VIDEO_HEVC) } ?: 0

            val highest = listOfNotNull(h264Res, hevcRes, vp9Res, av1Res)
                .filter { it.canDecode }
                .maxByOrNull { res ->
                    TEST_POINTS.indexOfFirst { (_, label) -> res.maxResolution == label }
                }?.maxResolution ?: ""

            StressSummary(
                h264Result = h264Res, hevcResult = hevcRes,
                vp9Result = vp9Res, av1Result = av1Res,
                otherHwDecoders = otherHw,
                concurrentH264 = maxH264, concurrentHevc = maxHevc,
                highestResolution = highest,
            )
        } catch (e: Exception) {
            Log.e(TAG, "压力测试整体异常: ${e.message}")
            StressSummary()
        }
    }

    /** 安全调用包装：任何异常返回 null 而不是抛出去 */
    private fun <T> safeCall(block: () -> T): T? {
        return try { block() } catch (e: Exception) { Log.w(TAG, "测试子项跳过: ${e.message}"); null }
    }

    /**
     * 找到格式对应的硬件解码器，通过 VideoCapabilities API 查询真实能力
     */
    private fun testBestDecoder(
        decoders: List<CodecInfoCollector.CodecEntry>,
        mimeType: String,
        label: String,
    ): DecodeTestResult? {
        val decoder = decoders.firstOrNull { it.mimeType == mimeType } ?: return null

        // 1. 从 MediaCodecInfo 读取硬件报告的 VideoCapabilities
        val videoCap = getVideoCapabilities(decoder.name, mimeType)
        if (videoCap == null) {
            // 降级：用 configure() 测试
            return testByConfigure(decoder, label)
        }

        val sb = StringBuilder()
        var bestPoint = ""
        var bestFps = 0
        var highestW = 0
        var highestH = 0

        for ((w, h, fps) in TEST_POINTS.map { it.first }) {
            val supported = videoCap.areSizeAndRateSupported(w, h, fps.toDouble())
            val pointLabel = TEST_POINTS.first { (t, _) -> t == Triple(w, h, fps) }.second
            sb.append("$pointLabel:${if (supported) "✔" else "✘"} ")

            if (supported) {
                bestPoint = pointLabel
                bestFps = fps
                if (w > highestW) { highestW = w; highestH = h }
            }
        }

        val canDecode = bestPoint.isNotEmpty()

        // 2. 用最高分辨率做 configure() 验证
        var initTime = -1L
        if (canDecode && highestH > 0) {
            try {
                val fmt = MediaFormat.createVideoFormat(mimeType, highestW, highestH)
                fmt.setInteger(MediaFormat.KEY_BIT_RATE, estimateBitrate(highestW, highestH, 30))
                fmt.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    fmt.setInteger(MediaFormat.KEY_PRIORITY, 0)
                }
                val codec = MediaCodec.createByCodecName(decoder.name)
                val t0 = System.currentTimeMillis()
                codec.configure(fmt, null, null, 0)
                codec.start()
                initTime = System.currentTimeMillis() - t0
                codec.stop()
                codec.release()
            } catch (e: Exception) {
                Log.w(TAG, "$label configure验证失败: ${e.message}")
            }
        }

        return DecodeTestResult(
            codecName = decoder.name,
            mimeType = mimeType,
            mode = "硬解",
            initTimeMs = initTime,
            canDecode = canDecode,
            maxResolution = bestPoint,
            maxFps = bestFps,
            hwSupported = sb.toString().trimEnd(),
        )
    }

    /** 降级方案：通过 configure() 逐级测试分辨率 */
    private fun testByConfigure(codec: CodecInfoCollector.CodecEntry, label: String): DecodeTestResult {
        val mode = if (codec.isHardware) "硬解" else "软解"
        var best = ""
        var bestTime = -1L
        var ok = false

        for ((w, h, fps) in TEST_POINTS.map { it.first }) {
            try {
                val fmt = MediaFormat.createVideoFormat(codec.mimeType, w, h)
                fmt.setInteger(MediaFormat.KEY_BIT_RATE, estimateBitrate(w, h, fps))
                fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, fps.coerceIn(1, 2))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    fmt.setInteger(MediaFormat.KEY_PRIORITY, 0)
                }
                val decoder = MediaCodec.createByCodecName(codec.name)
                val t0 = System.currentTimeMillis()
                decoder.configure(fmt, null, null, 0)
                decoder.start()
                val elapsed = System.currentTimeMillis() - t0
                decoder.stop()
                decoder.release()
                val pointLabel = TEST_POINTS.first { (t, _) -> t == Triple(w, h, fps) }.second
                best = pointLabel
                if (elapsed > 0) bestTime = elapsed
                ok = true
            } catch (_: Exception) { continue }
        }

        return DecodeTestResult(
            codecName = codec.name, mimeType = codec.mimeType,
            mode = mode, initTimeMs = bestTime,
            canDecode = ok, maxResolution = best,
        )
    }

    /** 快速测试：仅 1080p30 configure()
     *  即使失败也返回结果（canDecode=false），不会中断测试 */
    private fun quickTest(codec: CodecInfoCollector.CodecEntry): DecodeTestResult {
        val mode = if (codec.isHardware) "硬解" else "软解"
        try {
            val fmt = MediaFormat.createVideoFormat(codec.mimeType, 1920, 1080)
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) fmt.setInteger(MediaFormat.KEY_PRIORITY, 0)
            val dec = MediaCodec.createByCodecName(codec.name)
            val t0 = System.currentTimeMillis()
            dec.configure(fmt, null, null, 0)
            dec.start()
            val time = System.currentTimeMillis() - t0
            dec.stop(); dec.release()
            return DecodeTestResult(codec.name, codec.mimeType, mode, time, true, "1920x1080")
        } catch (e: Exception) {
            Log.w(TAG, "快速测试失败 ${codec.name}: ${e.message}")
            return DecodeTestResult(codec.name, codec.mimeType, mode, -1, false, "")
        }
    }

    /** 从 MediaCodecInfo 读取 VideoCapabilities
     *  用 ALL_CODECS + 匹配名称和别名确保能找到 */
    private fun getVideoCapabilities(codecName: String, mimeType: String): MediaCodecInfo.VideoCapabilities? {
        return try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in codecList.codecInfos) {
                val nameOk = info.name == codecName || (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info.canonicalName == codecName
                )
                if (nameOk) {
                    return try {
                        info.getCapabilitiesForType(mimeType)?.videoCapabilities
                    } catch (_: Exception) { null }
                }
            }
            null
        } catch (_: Exception) { null }
    }

    /** 并发测试：720p 下逐级增加实例数
     *  异常时返回已成功的数字而非 0 */
    private fun testConcurrent(
        decoder: CodecInfoCollector.CodecEntry?,
        mimeType: String,
    ): Int {
        if (decoder == null) return 0
        var max = 0
        for (n in 1..12) {
            val list = mutableListOf<MediaCodec>()
            try {
                for (i in 0 until n) {
                    val fmt = MediaFormat.createVideoFormat(mimeType, 1280, 720).apply {
                        setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                        setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    }
                    val c = MediaCodec.createByCodecName(decoder.name)
                    c.configure(fmt, null, null, 0)
                    c.start(); list.add(c)
                }
                max = n
            } catch (e: Exception) {
                Log.w(TAG, "并发 $n 个失败: ${e.message}")
            } finally {
                list.forEach { try { it.stop(); it.release() } catch (_: Exception) {} }
            }
            // 当前 n 失败了就停止，但保留已有的 max 值
            if (list.size < n) break
        }
        return max
    }

    private fun estimateBitrate(w: Int, h: Int, fps: Int = 30): Int {
        val base = when { w >= 7680 -> 100_000_000; w >= 3840 -> 50_000_000
            w >= 1920 -> 20_000_000; w >= 1280 -> 10_000_000; else -> 5_000_000 }
        return (base * (fps / 30.0)).toInt().coerceAtMost(200_000_000)
    }
}
