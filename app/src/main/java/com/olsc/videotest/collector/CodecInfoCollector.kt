package com.olsc.videotest.collector

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * 编码器/解码器信息收集器
 * 使用 MediaCodecList 查询设备支持的所有音视频编码格式
 */
object CodecInfoCollector {

    data class CodecEntry(
        val name: String,
        val canonicalName: String,
        val mimeType: String,
        val isEncoder: Boolean,
        val isHardware: Boolean,
        val isSoftware: Boolean,
        val supportedProfiles: List<String> = emptyList(),
        val maxResolution: String = "",
        val colorFormats: List<String> = emptyList(),
    )

    data class CodecSummary(
        val totalCodecs: Int = 0,
        val hardwareDecoders: List<CodecEntry> = emptyList(),
        val softwareDecoders: List<CodecEntry> = emptyList(),
        val hardwareEncoders: List<CodecEntry> = emptyList(),
        val softwareEncoders: List<CodecEntry> = emptyList(),
        val supportedMimeTypes: List<String> = emptyList(),
        val unsupportedMimeTypes: List<String> = emptyList(),
    )

    // 常见视频 MIME 类型
    val KNOWN_VIDEO_MIMES = listOf(
        MediaFormat.MIMETYPE_VIDEO_AVC,        // H.264
        MediaFormat.MIMETYPE_VIDEO_HEVC,       // H.265
        MediaFormat.MIMETYPE_VIDEO_VP8,
        MediaFormat.MIMETYPE_VIDEO_VP9,
        MediaFormat.MIMETYPE_VIDEO_AV1,
        MediaFormat.MIMETYPE_VIDEO_MPEG4,
        MediaFormat.MIMETYPE_VIDEO_MPEG2,
        MediaFormat.MIMETYPE_VIDEO_H263,
        "video/x-ms-wmv",                     // VC-1 / WMV
        MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
        "video/x-vnd.on2.vp9",                 // VP9 别名
        "video/x-vnd.on2.vp8",                 // VP8 别名
    )

    // 常见音频 MIME 类型
    val KNOWN_AUDIO_MIMES = listOf(
        MediaFormat.MIMETYPE_AUDIO_AAC,
        MediaFormat.MIMETYPE_AUDIO_MPEG,
        MediaFormat.MIMETYPE_AUDIO_AC3,
        MediaFormat.MIMETYPE_AUDIO_EAC3,
        MediaFormat.MIMETYPE_AUDIO_FLAC,
        MediaFormat.MIMETYPE_AUDIO_OPUS,
        MediaFormat.MIMETYPE_AUDIO_VORBIS,
        "audio/x-ms-wma",                    // WMA
        MediaFormat.MIMETYPE_AUDIO_AMR_NB,
        MediaFormat.MIMETYPE_AUDIO_AMR_WB,
    )

    fun collect(): CodecSummary {
        val hwDecoders = mutableListOf<CodecEntry>()
        val swDecoders = mutableListOf<CodecEntry>()
        val hwEncoders = mutableListOf<CodecEntry>()
        val swEncoders = mutableListOf<CodecEntry>()
        val supportedMimes = mutableSetOf<String>()

        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        var total = 0

        for (codecInfo in codecList.codecInfos) {
            // 跳过别名编码器（API 29+）
            if (Build.VERSION.SDK_INT >= 29 && codecInfo.isAlias) continue
            total++

            val name: String
            val canonicalName: String
            val isEncoder: Boolean
            val isHardware: Boolean
            val supportedTypes: Array<String>
            try {
                name = codecInfo.name
                // canonicalName 需要 API 29+，老版本回退到 name
                canonicalName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    codecInfo.canonicalName
                } else {
                    codecInfo.name
                }
                isEncoder = codecInfo.isEncoder
                isHardware = isHardwareCodec(codecInfo)
                supportedTypes = codecInfo.supportedTypes
            } catch (_: Exception) {
                continue  // 单个编码器信息读取失败，跳过
            }
            val isSoftware = !isHardware

            // 遍历该编码器支持的所有 MIME 类型
            for (mimeType in supportedTypes) {
                supportedMimes.add(mimeType)

                val profiles = mutableListOf<String>()
                var maxW = 0
                var maxH = 0
                val colorFmts = mutableListOf<String>()

                try {
                    val capabilities = codecInfo.getCapabilitiesForType(mimeType)
                    // 收集 profile 级别
                    val profileLevels = capabilities.profileLevels
                    if (profileLevels.isNotEmpty()) {
                        val seen = mutableSetOf<String>()
                        for (pl in profileLevels) {
                            val profileName = getProfileName(mimeType, pl.profile)
                            val levelName = getLevelName(mimeType, pl.level)
                            val key = "$profileName - $levelName"
                            if (seen.add(key)) {
                                profiles.add(key)
                            }
                        }
                    }

                    // 收集颜色格式
                    val cfgs = capabilities.colorFormats
                    if (cfgs.isNotEmpty()) {
                        for (cf in cfgs) {
                            colorFmts.add(getColorFormatName(cf))
                        }
                    }

                    // 获取最大分辨率
                    try {
                        val videoCap = capabilities.videoCapabilities
                        if (videoCap != null) {
                            val wa = videoCap.supportedWidths
                            val ha = videoCap.supportedHeights
                            if (wa.upper > maxW) maxW = wa.upper
                            if (ha.upper > maxH) maxH = ha.upper
                        }
                    } catch (_: Exception) {}

                } catch (_: Exception) {
                    // 某些类型可能不支持获取能力
                }

                val maxRes = if (maxW > 0 && maxH > 0) "${maxW}x${maxH}" else ""
                val entry = CodecEntry(
                    name = name,
                    canonicalName = canonicalName,
                    mimeType = mimeType,
                    isEncoder = isEncoder,
                    isHardware = isHardware,
                    isSoftware = isSoftware,
                    supportedProfiles = profiles,
                    maxResolution = maxRes,
                    colorFormats = colorFmts,
                )

                if (isEncoder) {
                    if (isHardware) hwEncoders.add(entry)
                    else swEncoders.add(entry)
                } else {
                    if (isHardware) hwDecoders.add(entry)
                    else swDecoders.add(entry)
                }
            }
        }

        // 检查不支持的常见格式
        val unsupported = KNOWN_VIDEO_MIMES.filter { it !in supportedMimes }

        return CodecSummary(
            totalCodecs = total,
            hardwareDecoders = hwDecoders,
            softwareDecoders = swDecoders,
            hardwareEncoders = hwEncoders,
            softwareEncoders = swEncoders,
            supportedMimeTypes = supportedMimes.toList().sorted(),
            unsupportedMimeTypes = unsupported,
        )
    }

    private fun isHardwareCodec(info: MediaCodecInfo): Boolean {
        val name = info.name.lowercase()
        // canonicalName 需要 API 29+，老版本回退到 name
        val alias = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.canonicalName
        } else {
            info.name
        }).lowercase()

        // ========================================================
        // 明确判定为软件解码器
        // ========================================================
        val softwarePatterns = listOf(
            ".sw.",                // 常规 OMX 软件标记
            "omx.google.",         // Google 软件实现
            "omx.ffmpeg.",         // FFmpeg 软解
            "c2.android.",         // Codec2 Android 软件实现
            "c2.google.",          // Codec2 Google 软件实现
            ".software.",          // 通用软件标记
        )
        if (softwarePatterns.any { name.contains(it) || alias.contains(it) }) return false

        // ========================================================
        // 明确判定为硬件解码器（按 SoC 厂商）
        // ========================================================
        val hwMarkers = listOf(
            // 高通 Qualcomm
            "qcom", "qti", "qualcomm",
            // 联发科 MediaTek
            "mediatek", "mtk", "mt",
            // 三星 Exynos
            "exynos", "samsung",
            // 海思 HiSilicon / 麒麟
            "hisilicon", "kirin",
            // 瑞芯微 Rockchip
            "rockchip", "rk",
            // 晶晨 Amlogic
            "amlogic",
            // 全志 Allwinner
            "allwinner", "sunxi",
            // 晨星 MStar
            "mstar",
            // 英伟达 NVIDIA
            "nvidia", "tegra",
            // 英特尔 Intel
            "intel",
            // 博通 Broadcom
            "broadcom",
            // 德州仪器 Texas Instruments
            "tiomap", "ti.",
            // 美满 Marvell
            "marvell",
            // Mali GPU 驱动
            "mali",
            // Adreno GPU 驱动
            "reno",
            // PowerVR GPU 驱动
            "powervr", "imgtec",
            // Khronos 参考实现
            "khronos",
            // 华为海思
            "hisi",
        )
        if (hwMarkers.any { name.contains(it) || alias.contains(it) }) return true

        // ========================================================
        // 框架级启发式判断
        // ========================================================

        // OMX 框架：包含 .hw. 标记的肯定是硬件
        if (name.contains("omx.") && name.contains(".hw.")) return true

        // OMX 框架：非 Google 实现且不含 .sw. 标记的，很可能是硬件
        if (name.contains("omx.") && !name.contains("google.")) return true

        // Codec2 框架：c2. 非 .android/.google 的基本都是硬件
        if (name.startsWith("c2.")) return true

        return false
    }

    private fun getProfileName(mime: String, profile: Int): String {
        return when (mime) {
            MediaFormat.MIMETYPE_VIDEO_AVC -> when (profile) {
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> "Baseline"
                MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> "Main"
                MediaCodecInfo.CodecProfileLevel.AVCProfileExtended -> "Extended"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> "High"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> "High 10"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422 -> "High 422"
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444 -> "High 444"
                else -> "Profile $profile"
            }
            MediaFormat.MIMETYPE_VIDEO_HEVC -> when (profile) {
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain -> "Main"
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main 10 HDR10"
                else -> "Profile $profile"
            }
            MediaFormat.MIMETYPE_VIDEO_VP9 -> when (profile) {
                MediaCodecInfo.CodecProfileLevel.VP9Profile0 -> "Profile 0"
                MediaCodecInfo.CodecProfileLevel.VP9Profile1 -> "Profile 1"
                MediaCodecInfo.CodecProfileLevel.VP9Profile2 -> "Profile 2"
                MediaCodecInfo.CodecProfileLevel.VP9Profile3 -> "Profile 3"
                MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR -> "Profile 2 HDR"
                MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR -> "Profile 3 HDR"
                else -> "Profile $profile"
            }
            MediaFormat.MIMETYPE_VIDEO_AV1 -> when (profile) {
                0 -> "Main"
                1 -> "High"
                2 -> "Professional"
                else -> "Profile $profile"
            }
            else -> "Profile $profile"
        }
    }

    private fun getLevelName(mime: String, level: Int): String {
        return when (mime) {
            MediaFormat.MIMETYPE_VIDEO_AVC -> when (level) {
                MediaCodecInfo.CodecProfileLevel.AVCLevel1 -> "1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel1b -> "1b"
                MediaCodecInfo.CodecProfileLevel.AVCLevel11 -> "1.1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel12 -> "1.2"
                MediaCodecInfo.CodecProfileLevel.AVCLevel13 -> "1.3"
                MediaCodecInfo.CodecProfileLevel.AVCLevel2 -> "2"
                MediaCodecInfo.CodecProfileLevel.AVCLevel21 -> "2.1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel22 -> "2.2"
                MediaCodecInfo.CodecProfileLevel.AVCLevel3 -> "3"
                MediaCodecInfo.CodecProfileLevel.AVCLevel31 -> "3.1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel32 -> "3.2"
                MediaCodecInfo.CodecProfileLevel.AVCLevel4 -> "4"
                MediaCodecInfo.CodecProfileLevel.AVCLevel41 -> "4.1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel42 -> "4.2"
                MediaCodecInfo.CodecProfileLevel.AVCLevel5 -> "5"
                MediaCodecInfo.CodecProfileLevel.AVCLevel51 -> "5.1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel52 -> "5.2"
                MediaCodecInfo.CodecProfileLevel.AVCLevel6 -> "6"
                MediaCodecInfo.CodecProfileLevel.AVCLevel61 -> "6.1"
                MediaCodecInfo.CodecProfileLevel.AVCLevel62 -> "6.2"
                else -> "Level $level"
            }
            MediaFormat.MIMETYPE_VIDEO_HEVC -> when (level) {
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel1 -> "Main 1"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel2 -> "Main 2"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel21 -> "Main 2.1"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel3 -> "Main 3"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31 -> "Main 3.1"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4 -> "Main 4"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41 -> "Main 4.1"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel5 -> "Main 5"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51 -> "Main 5.1"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel52 -> "Main 5.2"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel6 -> "Main 6"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel61 -> "Main 6.1"
                MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel62 -> "Main 6.2"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel4 -> "High 4"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel41 -> "High 4.1"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel5 -> "High 5"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel51 -> "High 5.1"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel52 -> "High 5.2"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel6 -> "High 6"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel61 -> "High 6.1"
                MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel62 -> "High 6.2"
                else -> "Level $level"
            }
            MediaFormat.MIMETYPE_VIDEO_VP9 -> when (level) {
                MediaCodecInfo.CodecProfileLevel.VP9Level1 -> "1"
                MediaCodecInfo.CodecProfileLevel.VP9Level11 -> "1.1"
                MediaCodecInfo.CodecProfileLevel.VP9Level2 -> "2"
                MediaCodecInfo.CodecProfileLevel.VP9Level21 -> "2.1"
                MediaCodecInfo.CodecProfileLevel.VP9Level3 -> "3"
                MediaCodecInfo.CodecProfileLevel.VP9Level31 -> "3.1"
                MediaCodecInfo.CodecProfileLevel.VP9Level4 -> "4"
                MediaCodecInfo.CodecProfileLevel.VP9Level41 -> "4.1"
                MediaCodecInfo.CodecProfileLevel.VP9Level5 -> "5"
                MediaCodecInfo.CodecProfileLevel.VP9Level51 -> "5.1"
                MediaCodecInfo.CodecProfileLevel.VP9Level52 -> "5.2"
                MediaCodecInfo.CodecProfileLevel.VP9Level6 -> "6"
                else -> "Level $level"
            }
            else -> "Level $level"
        }
    }

    private fun getColorFormatName(format: Int): String {
        return when (format) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar -> "YUV420 Planar"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar -> "YUV420 SemiPlanar"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar -> "YUV420 Packed Planar"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar -> "YUV420 Packed SemiPlanar"
            MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar -> "TI YUV420 Packed SemiPlanar"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Planar -> "YUV422 Planar"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422SemiPlanar -> "YUV422 SemiPlanar"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422PackedPlanar -> "YUV422 Packed Planar"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422PackedSemiPlanar -> "YUV422 Packed SemiPlanar"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYCbYCr -> "YCbYCr"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYCrYCb -> "YCrYCb"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatCbYCrY -> "CbYCrY"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatCrYCbY -> "CrYCbY"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV444Interleaved -> "YUV444 Interleaved"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatRawBayer8bit -> "Raw Bayer 8bit"
            MediaCodecInfo.CodecCapabilities.COLOR_FormatRawBayer10bit -> "Raw Bayer 10bit"
            0x7000000C -> "Raw Bayer 12bit"  // COLOR_FormatRawBayer12bit (API 31+)
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface -> "Surface"
            MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR8888 -> "32bit ABGR"
            MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888 -> "32bit ARGB"
            MediaCodecInfo.CodecCapabilities.COLOR_Format24bitBGR888 -> "24bit BGR"
            MediaCodecInfo.CodecCapabilities.COLOR_Format16bitARGB1555 -> "16bit ARGB1555"
            MediaCodecInfo.CodecCapabilities.COLOR_Format16bitARGB4444 -> "16bit ARGB4444"
            MediaCodecInfo.CodecCapabilities.COLOR_Format16bitRGB565 -> "16bit RGB565"
            MediaCodecInfo.CodecCapabilities.COLOR_Format32bitBGRA8888 -> "32bit BGRA"
            else -> "格式 0x${format.toString(16)}"
        }
    }
}
