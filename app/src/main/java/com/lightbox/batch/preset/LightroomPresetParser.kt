package com.lightbox.batch.preset

import android.content.Context
import android.net.Uri
import com.lightbox.batch.model.PresetParameters
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Parses Adobe Lightroom preset files (.xmp and .lrtemplate) and converts
 * their adjustment values into normalized PresetParameters for the GPU pipeline.
 *
 * Handles XMP attribute syntax, child element syntax, and RDF metadata structures,
 * as well as .lrtemplate Lua format files.
 */
object LightroomPresetParser {

    /**
     * Parse a preset file from a content URI.
     * Detects format by file extension or content sniffing.
     */
    fun parse(context: Context, uri: Uri, fileName: String): PresetParameters {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open preset file: $uri")

        val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

        return if (fileName.endsWith(".lrtemplate", ignoreCase = true)) {
            parseLrTemplateContent(content, fileName)
        } else if (fileName.endsWith(".xmp", ignoreCase = true)) {
            parseXmpContent(content, fileName)
        } else {
            // Sniff format from content
            val trimmed = content.trimStart()
            if (trimmed.startsWith("<") || content.contains("x:xmpmeta") || content.contains("crs:")) {
                parseXmpContent(content, fileName)
            } else {
                parseLrTemplateContent(content, fileName)
            }
        }
    }

    /**
     * Parse XMP format preset from InputStream.
     */
    fun parseXmp(inputStream: InputStream, fileName: String = "preset"): PresetParameters {
        val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseXmpContent(content, fileName)
    }

    /**
     * Parse XMP content string using regex-based extraction with XmlPullParser fallback.
     */
    fun parseXmpContent(content: String, fileName: String = "preset"): PresetParameters {
        val attrs = mutableMapOf<String, String>()
        var presetName = fileName.substringBeforeLast(".")

        // 1. Extract preset name from XMP tags or attributes
        val nameInTagMatch = Regex("""<crs:Name>[\s\S]*?<rdf:li[^>]*>([^<]+)</rdf:li>""", RegexOption.IGNORE_CASE).find(content)
            ?: Regex("""<crs:Name>([^<]+)</crs:Name>""", RegexOption.IGNORE_CASE).find(content)
            ?: Regex("""<Name>([^<]+)</Name>""", RegexOption.IGNORE_CASE).find(content)

        if (nameInTagMatch != null) {
            val extractedName = nameInTagMatch.groupValues[1].trim()
            if (extractedName.isNotEmpty()) {
                presetName = extractedName
            }
        } else {
            val nameAttrMatch = Regex("""(?:crs:)?Name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(content)
            if (nameAttrMatch != null) {
                val extractedName = nameAttrMatch.groupValues[1].trim()
                if (extractedName.isNotEmpty()) {
                    presetName = extractedName
                }
            }
        }

        // 2. Extract key-value pairs from XML attributes (e.g. crs:Exposure2012="+0.50")
        val attrRegex = Regex("""(?:crs:)?(\w+)\s*=\s*["']([^"']+)["']""")
        for (match in attrRegex.findAll(content)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            if (isLightroomKey(key)) {
                attrs[key] = value
            }
        }

        // 3. Extract key-value pairs from XML child elements (e.g. <crs:Exposure2012>+0.50</crs:Exposure2012>)
        val elemRegex = Regex("""<(?:crs:)?(\w+)>([^<]+)</(?:crs:)?\1>""")
        for (match in elemRegex.findAll(content)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2].trim()
            if (isLightroomKey(key)) {
                attrs[key] = value
            }
        }

        // 4. Secondary fallback: XmlPullParser if no attributes were matched
        if (attrs.isEmpty()) {
            try {
                val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
                val parser = factory.newPullParser()
                parser.setInput(content.reader())

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        for (i in 0 until parser.attributeCount) {
                            val rawName = parser.getAttributeName(i)
                            val key = rawName.substringAfter(":")
                            val value = parser.getAttributeValue(i)
                            if (isLightroomKey(key)) {
                                attrs[key] = value
                            }
                        }
                    }
                    eventType = parser.next()
                }
            } catch (_: Exception) {
                // Ignore XML parse exceptions when regex extraction succeeds
            }
        }

        val params = mapToPresetParameters(presetName, attrs)
        fun extractCurve(tag: String): String {
            val pattern = Regex("""<(?:crs:)?$tag>[\s\S]*?<rdf:Seq>([\s\S]*?)</rdf:Seq>[\s\S]*?</(?:crs:)?$tag>""", RegexOption.IGNORE_CASE)
            val match = pattern.find(content) ?: return ""
            val seqContent = match.groupValues[1]
            val liRegex = Regex("""<rdf:li>([^<]+)</rdf:li>""", RegexOption.IGNORE_CASE)
            return liRegex.findAll(seqContent).map { it.groupValues[1].trim() }.joinToString("; ")
        }

        val toneCurve = extractCurve("ToneCurvePV2012").ifEmpty { extractCurve("ToneCurve") }
        val toneCurveRed = extractCurve("ToneCurvePV2012Red").ifEmpty { extractCurve("ToneCurveRed") }
        val toneCurveGreen = extractCurve("ToneCurvePV2012Green").ifEmpty { extractCurve("ToneCurveGreen") }
        val toneCurveBlue = extractCurve("ToneCurvePV2012Blue").ifEmpty { extractCurve("ToneCurveBlue") }

        val curveNameMatch = Regex("""(?:crs:)?ToneCurveName(?:2012)?\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(content)
            ?: Regex("""<(?:crs:)?ToneCurveName(?:2012)?>([^<]+)</(?:crs:)?ToneCurveName(?:2012)?>""", RegexOption.IGNORE_CASE).find(content)
        val toneCurveName = curveNameMatch?.groupValues?.get(1)?.trim() ?: ""

        val hasAutoTone = Regex("""(?:crs:)?AutoTone\s*=\s*["']True["']""", RegexOption.IGNORE_CASE).containsMatchIn(content)
                || Regex("""<(?:crs:)?AutoTone>\s*True\s*</(?:crs:)?AutoTone>""", RegexOption.IGNORE_CASE).containsMatchIn(content)

        return if (hasAutoTone && params.exposure == 0f && params.contrast == 0f) {
            params.copy(
                exposure = 0.08f,
                contrast = 0.18f,
                highlights = -0.15f,
                shadows = 0.20f,
                whites = 0.10f,
                blacks = -0.08f,
                vibrance = 0.18f,
                saturation = 0.06f,
                toneCurvePoints = toneCurve,
                toneCurveRedPoints = toneCurveRed,
                toneCurveGreenPoints = toneCurveGreen,
                toneCurveBluePoints = toneCurveBlue,
                toneCurveName = toneCurveName
            )
        } else {
            params.copy(
                toneCurvePoints = toneCurve,
                toneCurveRedPoints = toneCurveRed,
                toneCurveGreenPoints = toneCurveGreen,
                toneCurveBluePoints = toneCurveBlue,
                toneCurveName = toneCurveName
            )
        }
    }

    /**
     * Parse .lrtemplate format preset.
     */
    fun parseLrTemplate(inputStream: InputStream, fileName: String = "preset"): PresetParameters {
        val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseLrTemplateContent(content, fileName)
    }

    private fun parseLrTemplateContent(content: String, fileName: String = "preset"): PresetParameters {
        val attrs = mutableMapOf<String, String>()
        var presetName = fileName.substringBeforeLast(".")

        val namePatterns = listOf(
            Regex("""internalName\s*=\s*"([^"]+)""""),
            Regex("""title\s*=\s*"([^"]+)""""),
            Regex("""name\s*=\s*"([^"]+)"""")
        )
        for (pattern in namePatterns) {
            val match = pattern.find(content)
            if (match != null) {
                presetName = match.groupValues[1]
                break
            }
        }

        val kvPattern = Regex("""(\w+)\s*=\s*(-?[\d.]+)""")
        for (match in kvPattern.findAll(content)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            if (isLightroomKey(key)) {
                attrs[key] = value
            }
        }

        return mapToPresetParameters(presetName, attrs)
    }

    private fun mapToPresetParameters(name: String, attrs: Map<String, String>): PresetParameters {
        return PresetParameters(
            name = name,
            exposure = parseAndNormalize(attrs, "Exposure2012", -5f, 5f, "Exposure"),
            contrast = parseAndNormalize(attrs, "Contrast2012", -100f, 100f, "Contrast"),
            highlights = parseAndNormalize(attrs, "Highlights2012", -100f, 100f, "Highlights"),
            shadows = parseAndNormalize(attrs, "Shadows2012", -100f, 100f, "Shadows"),
            whites = parseAndNormalize(attrs, "Whites2012", -100f, 100f, "Whites"),
            blacks = parseAndNormalize(attrs, "Blacks2012", -100f, 100f, "Blacks"),
            saturation = parseAndNormalize(attrs, "Saturation", -100f, 100f),
            vibrance = parseAndNormalize(attrs, "Vibrance", -100f, 100f),
            texture = parseAndNormalize(attrs, "Texture", -100f, 100f),
            clarity = parseAndNormalize(attrs, "Clarity2012", -100f, 100f, "Clarity"),
            dehaze = parseAndNormalize(attrs, "Dehaze", -100f, 100f),
            vignetteAmount = parseAndNormalize(attrs, "PostCropVignetteAmount", -100f, 100f),
            splitShadowHue = (attrs["SplitToningShadowHue"]?.toFloatOrNull() ?: 0f) / 360f,
            splitShadowSat = (attrs["SplitToningShadowSaturation"]?.toFloatOrNull() ?: 0f) / 100f,
            splitHighlightHue = (attrs["SplitToningHighlightHue"]?.toFloatOrNull() ?: 0f) / 360f,
            splitHighlightSat = (attrs["SplitToningHighlightSaturation"]?.toFloatOrNull() ?: 0f) / 100f,
            temperature = normalizeTemperature(attrs["Temperature"]),
            tint = parseAndNormalize(attrs, "Tint", -150f, 150f),
            hslRedHue = parseAndNormalize(attrs, "HueAdjustmentRed", -100f, 100f),
            hslRedSaturation = parseAndNormalize(attrs, "SaturationAdjustmentRed", -100f, 100f),
            hslRedLuminance = parseAndNormalize(attrs, "LuminanceAdjustmentRed", -100f, 100f),
            hslOrangeHue = parseAndNormalize(attrs, "HueAdjustmentOrange", -100f, 100f),
            hslOrangeSaturation = parseAndNormalize(attrs, "SaturationAdjustmentOrange", -100f, 100f),
            hslOrangeLuminance = parseAndNormalize(attrs, "LuminanceAdjustmentOrange", -100f, 100f),
            hslYellowHue = parseAndNormalize(attrs, "HueAdjustmentYellow", -100f, 100f),
            hslYellowSaturation = parseAndNormalize(attrs, "SaturationAdjustmentYellow", -100f, 100f),
            hslYellowLuminance = parseAndNormalize(attrs, "LuminanceAdjustmentYellow", -100f, 100f),
            hslGreenHue = parseAndNormalize(attrs, "HueAdjustmentGreen", -100f, 100f),
            hslGreenSaturation = parseAndNormalize(attrs, "SaturationAdjustmentGreen", -100f, 100f),
            hslGreenLuminance = parseAndNormalize(attrs, "LuminanceAdjustmentGreen", -100f, 100f),
            hslAquaHue = parseAndNormalize(attrs, "HueAdjustmentAqua", -100f, 100f),
            hslAquaSaturation = parseAndNormalize(attrs, "SaturationAdjustmentAqua", -100f, 100f),
            hslAquaLuminance = parseAndNormalize(attrs, "LuminanceAdjustmentAqua", -100f, 100f),
            hslBlueHue = parseAndNormalize(attrs, "HueAdjustmentBlue", -100f, 100f),
            hslBlueSaturation = parseAndNormalize(attrs, "SaturationAdjustmentBlue", -100f, 100f),
            hslBlueLuminance = parseAndNormalize(attrs, "LuminanceAdjustmentBlue", -100f, 100f),
            hslPurpleHue = parseAndNormalize(attrs, "HueAdjustmentPurple", -100f, 100f),
            hslPurpleSaturation = parseAndNormalize(attrs, "SaturationAdjustmentPurple", -100f, 100f),
            hslPurpleLuminance = parseAndNormalize(attrs, "LuminanceAdjustmentPurple", -100f, 100f),
            hslMagentaHue = parseAndNormalize(attrs, "HueAdjustmentMagenta", -100f, 100f),
            hslMagentaSaturation = parseAndNormalize(attrs, "SaturationAdjustmentMagenta", -100f, 100f),
            hslMagentaLuminance = parseAndNormalize(attrs, "LuminanceAdjustmentMagenta", -100f, 100f)
        )
    }

    private fun parseAndNormalize(
        attrs: Map<String, String>,
        key: String,
        minVal: Float,
        maxVal: Float,
        fallbackKey: String? = null
    ): Float {
        val rawStr = attrs[key] ?: fallbackKey?.let { attrs[it] }
        val raw = rawStr?.replace("+", "")?.toFloatOrNull() ?: return 0f
        val clamped = raw.coerceIn(minVal, maxVal)
        return if (maxVal == minVal) 0f
        else ((clamped - minVal) / (maxVal - minVal)) * 2f - 1f
    }

    private fun normalizeTemperature(value: String?): Float {
        val raw = value?.replace("+", "")?.toFloatOrNull() ?: return 0f
        return when {
            raw >= 2000f -> {
                when {
                    raw <= 2000f -> -1f
                    raw >= 50000f -> 1f
                    raw < 6500f -> -((6500f - raw) / (6500f - 2000f))
                    raw > 6500f -> (raw - 6500f) / (50000f - 6500f)
                    else -> 0f
                }
            }
            else -> (raw / 100f).coerceIn(-1f, 1f)
        }
    }

    private fun isLightroomKey(key: String): Boolean {
        return key in LIGHTROOM_KEYS
    }

    private val LIGHTROOM_KEYS = setOf(
        "Exposure2012", "Contrast2012", "Highlights2012", "Shadows2012",
        "Whites2012", "Blacks2012", "Saturation", "Vibrance",
        "Texture", "Clarity2012", "Clarity", "Dehaze", "PostCropVignetteAmount",
        "SplitToningShadowHue", "SplitToningShadowSaturation",
        "SplitToningHighlightHue", "SplitToningHighlightSaturation", "SplitToningBalance",
        "ToneCurveName2012", "ToneCurveName",
        "ToneCurvePV2012", "ToneCurvePV2012Red", "ToneCurvePV2012Green", "ToneCurvePV2012Blue",
        "ToneCurve", "ToneCurveRed", "ToneCurveGreen", "ToneCurveBlue",
        "Temperature", "Tint",
        "Exposure", "Contrast", "Highlights", "Shadows", "Whites", "Blacks",
        "HueAdjustmentRed", "SaturationAdjustmentRed", "LuminanceAdjustmentRed",
        "HueAdjustmentOrange", "SaturationAdjustmentOrange", "LuminanceAdjustmentOrange",
        "HueAdjustmentYellow", "SaturationAdjustmentYellow", "LuminanceAdjustmentYellow",
        "HueAdjustmentGreen", "SaturationAdjustmentGreen", "LuminanceAdjustmentGreen",
        "HueAdjustmentAqua", "SaturationAdjustmentAqua", "LuminanceAdjustmentAqua",
        "HueAdjustmentBlue", "SaturationAdjustmentBlue", "LuminanceAdjustmentBlue",
        "HueAdjustmentPurple", "SaturationAdjustmentPurple", "LuminanceAdjustmentPurple",
        "HueAdjustmentMagenta", "SaturationAdjustmentMagenta", "LuminanceAdjustmentMagenta"
    )
}
