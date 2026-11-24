package com.lightbox.batch.model

import org.json.JSONObject

data class PresetParameters(
    val name: String = "Untitled",
    // Basic tone
    val exposure: Float = 0f,       // [-1..1] maps to roughly [-5..+5] stops
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    // Presence
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val texture: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val vignetteAmount: Float = 0f,
    val splitShadowHue: Float = 0f,
    val splitShadowSat: Float = 0f,
    val splitHighlightHue: Float = 0f,
    val splitHighlightSat: Float = 0f,
    // Tone curves
    val toneCurvePoints: String = "",
    val toneCurveRedPoints: String = "",
    val toneCurveGreenPoints: String = "",
    val toneCurveBluePoints: String = "",
    val toneCurveName: String = "",
    // White balance
    val temperature: Float = 0f,    // [-1..1] maps to cool..warm
    val tint: Float = 0f,           // [-1..1] maps to green..magenta
    // HSL adjustments (per channel: H, S, L each [-1..1])
    val hslRedHue: Float = 0f,
    val hslRedSaturation: Float = 0f,
    val hslRedLuminance: Float = 0f,
    val hslOrangeHue: Float = 0f,
    val hslOrangeSaturation: Float = 0f,
    val hslOrangeLuminance: Float = 0f,
    val hslYellowHue: Float = 0f,
    val hslYellowSaturation: Float = 0f,
    val hslYellowLuminance: Float = 0f,
    val hslGreenHue: Float = 0f,
    val hslGreenSaturation: Float = 0f,
    val hslGreenLuminance: Float = 0f,
    val hslAquaHue: Float = 0f,
    val hslAquaSaturation: Float = 0f,
    val hslAquaLuminance: Float = 0f,
    val hslBlueHue: Float = 0f,
    val hslBlueSaturation: Float = 0f,
    val hslBlueLuminance: Float = 0f,
    val hslPurpleHue: Float = 0f,
    val hslPurpleSaturation: Float = 0f,
    val hslPurpleLuminance: Float = 0f,
    val hslMagentaHue: Float = 0f,
    val hslMagentaSaturation: Float = 0f,
    val hslMagentaLuminance: Float = 0f
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("name", name)
        obj.put("exposure", exposure.toDouble())
        obj.put("contrast", contrast.toDouble())
        obj.put("highlights", highlights.toDouble())
        obj.put("shadows", shadows.toDouble())
        obj.put("whites", whites.toDouble())
        obj.put("blacks", blacks.toDouble())
        obj.put("saturation", saturation.toDouble())
        obj.put("vibrance", vibrance.toDouble())
        obj.put("texture", texture.toDouble())
        obj.put("clarity", clarity.toDouble())
        obj.put("dehaze", dehaze.toDouble())
        obj.put("vignetteAmount", vignetteAmount.toDouble())
        obj.put("splitShadowHue", splitShadowHue.toDouble())
        obj.put("splitShadowSat", splitShadowSat.toDouble())
        obj.put("splitHighlightHue", splitHighlightHue.toDouble())
        obj.put("splitHighlightSat", splitHighlightSat.toDouble())
        obj.put("toneCurvePoints", toneCurvePoints)
        obj.put("toneCurveRedPoints", toneCurveRedPoints)
        obj.put("toneCurveGreenPoints", toneCurveGreenPoints)
        obj.put("toneCurveBluePoints", toneCurveBluePoints)
        obj.put("toneCurveName", toneCurveName)
        obj.put("temperature", temperature.toDouble())
        obj.put("tint", tint.toDouble())
        // HSL
        obj.put("hslRedHue", hslRedHue.toDouble())
        obj.put("hslRedSaturation", hslRedSaturation.toDouble())
        obj.put("hslRedLuminance", hslRedLuminance.toDouble())
        obj.put("hslOrangeHue", hslOrangeHue.toDouble())
        obj.put("hslOrangeSaturation", hslOrangeSaturation.toDouble())
        obj.put("hslOrangeLuminance", hslOrangeLuminance.toDouble())
        obj.put("hslYellowHue", hslYellowHue.toDouble())
        obj.put("hslYellowSaturation", hslYellowSaturation.toDouble())
        obj.put("hslYellowLuminance", hslYellowLuminance.toDouble())
        obj.put("hslGreenHue", hslGreenHue.toDouble())
        obj.put("hslGreenSaturation", hslGreenSaturation.toDouble())
        obj.put("hslGreenLuminance", hslGreenLuminance.toDouble())
        obj.put("hslAquaHue", hslAquaHue.toDouble())
        obj.put("hslAquaSaturation", hslAquaSaturation.toDouble())
        obj.put("hslAquaLuminance", hslAquaLuminance.toDouble())
        obj.put("hslBlueHue", hslBlueHue.toDouble())
        obj.put("hslBlueSaturation", hslBlueSaturation.toDouble())
        obj.put("hslBlueLuminance", hslBlueLuminance.toDouble())
        obj.put("hslPurpleHue", hslPurpleHue.toDouble())
        obj.put("hslPurpleSaturation", hslPurpleSaturation.toDouble())
        obj.put("hslPurpleLuminance", hslPurpleLuminance.toDouble())
        obj.put("hslMagentaHue", hslMagentaHue.toDouble())
        obj.put("hslMagentaSaturation", hslMagentaSaturation.toDouble())
        obj.put("hslMagentaLuminance", hslMagentaLuminance.toDouble())
        return obj.toString()
    }

    fun hasAdjustments(): Boolean {
        return exposure != 0f || contrast != 0f || highlights != 0f || shadows != 0f ||
                whites != 0f || blacks != 0f || saturation != 0f || vibrance != 0f ||
                texture != 0f || clarity != 0f || dehaze != 0f || vignetteAmount != 0f ||
                splitShadowSat != 0f || splitHighlightSat != 0f ||
                toneCurvePoints.isNotBlank() || toneCurveRedPoints.isNotBlank() ||
                toneCurveGreenPoints.isNotBlank() || toneCurveBluePoints.isNotBlank() ||
                temperature != 0f || tint != 0f ||
                hslRedHue != 0f || hslRedSaturation != 0f || hslRedLuminance != 0f ||
                hslOrangeHue != 0f || hslOrangeSaturation != 0f || hslOrangeLuminance != 0f ||
                hslYellowHue != 0f || hslYellowSaturation != 0f || hslYellowLuminance != 0f ||
                hslGreenHue != 0f || hslGreenSaturation != 0f || hslGreenLuminance != 0f ||
                hslAquaHue != 0f || hslAquaSaturation != 0f || hslAquaLuminance != 0f ||
                hslBlueHue != 0f || hslBlueSaturation != 0f || hslBlueLuminance != 0f ||
                hslPurpleHue != 0f || hslPurpleSaturation != 0f || hslPurpleLuminance != 0f ||
                hslMagentaHue != 0f || hslMagentaSaturation != 0f || hslMagentaLuminance != 0f
    }

    fun combineWith(auto: PresetParameters): PresetParameters {
        fun blend(pVal: Float, aVal: Float): Float =
            (pVal * 0.6f + aVal * 0.4f).coerceIn(-1f, 1f)

        return this.copy(
            exposure = blend(this.exposure, auto.exposure),
            contrast = blend(this.contrast, auto.contrast),
            highlights = blend(this.highlights, auto.highlights),
            shadows = blend(this.shadows, auto.shadows),
            whites = blend(this.whites, auto.whites),
            blacks = blend(this.blacks, auto.blacks),
            saturation = blend(this.saturation, auto.saturation),
            vibrance = blend(this.vibrance, auto.vibrance),
            temperature = blend(this.temperature, auto.temperature),
            tint = blend(this.tint, auto.tint)
        )
    }

    companion object {
        fun fromJson(json: String): PresetParameters {
            val obj = JSONObject(json)
            return PresetParameters(
                name = obj.optString("name", "Untitled"),
                exposure = obj.optDouble("exposure", 0.0).toFloat(),
                contrast = obj.optDouble("contrast", 0.0).toFloat(),
                highlights = obj.optDouble("highlights", 0.0).toFloat(),
                shadows = obj.optDouble("shadows", 0.0).toFloat(),
                whites = obj.optDouble("whites", 0.0).toFloat(),
                blacks = obj.optDouble("blacks", 0.0).toFloat(),
                saturation = obj.optDouble("saturation", 0.0).toFloat(),
                vibrance = obj.optDouble("vibrance", 0.0).toFloat(),
                texture = obj.optDouble("texture", 0.0).toFloat(),
                clarity = obj.optDouble("clarity", 0.0).toFloat(),
                dehaze = obj.optDouble("dehaze", 0.0).toFloat(),
                vignetteAmount = obj.optDouble("vignetteAmount", 0.0).toFloat(),
                splitShadowHue = obj.optDouble("splitShadowHue", 0.0).toFloat(),
                splitShadowSat = obj.optDouble("splitShadowSat", 0.0).toFloat(),
                splitHighlightHue = obj.optDouble("splitHighlightHue", 0.0).toFloat(),
                splitHighlightSat = obj.optDouble("splitHighlightSat", 0.0).toFloat(),
                toneCurvePoints = obj.optString("toneCurvePoints", ""),
                toneCurveRedPoints = obj.optString("toneCurveRedPoints", ""),
                toneCurveGreenPoints = obj.optString("toneCurveGreenPoints", ""),
                toneCurveBluePoints = obj.optString("toneCurveBluePoints", ""),
                toneCurveName = obj.optString("toneCurveName", ""),
                temperature = obj.optDouble("temperature", 0.0).toFloat(),
                tint = obj.optDouble("tint", 0.0).toFloat(),
                hslRedHue = obj.optDouble("hslRedHue", 0.0).toFloat(),
                hslRedSaturation = obj.optDouble("hslRedSaturation", 0.0).toFloat(),
                hslRedLuminance = obj.optDouble("hslRedLuminance", 0.0).toFloat(),
                hslOrangeHue = obj.optDouble("hslOrangeHue", 0.0).toFloat(),
                hslOrangeSaturation = obj.optDouble("hslOrangeSaturation", 0.0).toFloat(),
                hslOrangeLuminance = obj.optDouble("hslOrangeLuminance", 0.0).toFloat(),
                hslYellowHue = obj.optDouble("hslYellowHue", 0.0).toFloat(),
                hslYellowSaturation = obj.optDouble("hslYellowSaturation", 0.0).toFloat(),
                hslYellowLuminance = obj.optDouble("hslYellowLuminance", 0.0).toFloat(),
                hslGreenHue = obj.optDouble("hslGreenHue", 0.0).toFloat(),
                hslGreenSaturation = obj.optDouble("hslGreenSaturation", 0.0).toFloat(),
                hslGreenLuminance = obj.optDouble("hslGreenLuminance", 0.0).toFloat(),
                hslAquaHue = obj.optDouble("hslAquaHue", 0.0).toFloat(),
                hslAquaSaturation = obj.optDouble("hslAquaSaturation", 0.0).toFloat(),
                hslAquaLuminance = obj.optDouble("hslAquaLuminance", 0.0).toFloat(),
                hslBlueHue = obj.optDouble("hslBlueHue", 0.0).toFloat(),
                hslBlueSaturation = obj.optDouble("hslBlueSaturation", 0.0).toFloat(),
                hslBlueLuminance = obj.optDouble("hslBlueLuminance", 0.0).toFloat(),
                hslPurpleHue = obj.optDouble("hslPurpleHue", 0.0).toFloat(),
                hslPurpleSaturation = obj.optDouble("hslPurpleSaturation", 0.0).toFloat(),
                hslPurpleLuminance = obj.optDouble("hslPurpleLuminance", 0.0).toFloat(),
                hslMagentaHue = obj.optDouble("hslMagentaHue", 0.0).toFloat(),
                hslMagentaSaturation = obj.optDouble("hslMagentaSaturation", 0.0).toFloat(),
                hslMagentaLuminance = obj.optDouble("hslMagentaLuminance", 0.0).toFloat()
            )
        }

        /** Auto-correct parameters derived from image histogram analysis */
        val AUTO_CORRECT = PresetParameters(
            name = "Auto Correct",
            exposure = 0.08f,
            contrast = 0.12f,
            highlights = -0.15f,
            shadows = 0.22f,
            whites = 0.06f,
            blacks = -0.06f,
            saturation = 0.10f,
            vibrance = 0.15f
        )

        /** Built-in high-quality presets ready to use */
        val BUILT_IN_PRESETS = listOf(
            PresetParameters(
                name = "Sony - Corrida - Auto - Básico 2",
                exposure = 0.08f,
                contrast = 0.18f,
                highlights = -0.15f,
                shadows = 0.20f,
                whites = 0.10f,
                blacks = -0.08f,
                vibrance = 0.18f,
                saturation = 0.06f,
                texture = 0.1f,
                clarity = 0.05f,
                dehaze = 0.08f,
                vignetteAmount = -0.15f,
                hslYellowHue = -0.05f,
                hslGreenHue = -0.07f,
                hslOrangeSaturation = 0.08f,
                hslBlueSaturation = 0.12f,
                splitShadowHue = 45f / 360f,
                splitShadowSat = 0.08f,
                splitHighlightHue = 191f / 360f,
                splitHighlightSat = 0.08f,
                toneCurveName = "Corrida 1",
                toneCurvePoints = "0, 0; 59, 53; 192, 196; 255, 255",
                toneCurveRedPoints = "0, 0; 73, 71; 190, 195; 255, 255",
                toneCurveGreenPoints = "0, 0; 192, 194; 255, 255",
                toneCurveBluePoints = "0, 0; 65, 68; 200, 196; 255, 255"
            ),
            PresetParameters(
                name = "Vivid Landscape",
                exposure = 0.10f,
                contrast = 0.20f,
                highlights = -0.15f,
                shadows = 0.18f,
                whites = 0.12f,
                blacks = -0.08f,
                saturation = 0.25f,
                vibrance = 0.30f,
                temperature = 0.05f,
                hslGreenSaturation = 0.25f,
                hslGreenLuminance = -0.05f,
                hslBlueSaturation = 0.30f,
                hslBlueLuminance = -0.10f
            ),
            PresetParameters(
                name = "Portra Film",
                exposure = 0.12f,
                contrast = -0.08f,
                highlights = -0.20f,
                shadows = 0.25f,
                whites = -0.05f,
                blacks = 0.10f,
                saturation = -0.05f,
                vibrance = 0.10f,
                temperature = 0.15f,
                tint = 0.08f,
                hslOrangeHue = -0.05f,
                hslOrangeSaturation = 0.12f
            ),
            PresetParameters(
                name = "Cinematic Teal & Orange",
                exposure = 0.05f,
                contrast = 0.18f,
                highlights = -0.10f,
                shadows = 0.15f,
                saturation = 0.12f,
                vibrance = 0.20f,
                temperature = 0.12f,
                hslOrangeSaturation = 0.35f,
                hslOrangeLuminance = 0.08f,
                hslBlueHue = -0.15f,
                hslBlueSaturation = 0.30f,
                hslBlueLuminance = -0.15f,
                hslAquaSaturation = 0.25f
            ),
            PresetParameters(
                name = "B&W High Contrast",
                exposure = 0.08f,
                contrast = 0.35f,
                highlights = -0.10f,
                shadows = 0.10f,
                whites = 0.20f,
                blacks = -0.18f,
                saturation = -1.0f,
                vibrance = -1.0f
            ),
            PresetParameters(
                name = "Moody Matte",
                exposure = -0.05f,
                contrast = -0.12f,
                highlights = -0.25f,
                shadows = 0.18f,
                whites = -0.15f,
                blacks = 0.25f,
                saturation = -0.15f,
                vibrance = -0.08f,
                temperature = 0.08f
            ),
            PresetParameters(
                name = "Clean Bright",
                exposure = 0.20f,
                contrast = 0.05f,
                highlights = -0.18f,
                shadows = 0.20f,
                whites = 0.15f,
                blacks = -0.02f,
                saturation = 0.08f,
                vibrance = 0.12f,
                temperature = -0.02f
            )
        )
    }
}
