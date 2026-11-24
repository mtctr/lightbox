package com.lightbox.batch.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.lightbox.batch.model.PresetParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * High-performance batch image processor using AGSL shaders for GPU-accelerated
 * real-time previews in Jetpack Compose, and an ultra-fast, multi-threaded 256-level Look-Up Table (LUT)
 * tone curve and color transformation engine for mass export.
 *
 * Optimized specifically for the Snapdragon 8 Gen 3 on the Xiaomi Pad 8 Pro.
 */
class BatchImageProcessor(private val context: Context) {

    companion object {
        private const val TAG = "BatchImageProcessor"

        // Concurrency limiter for heavy bitmap operations to prevent out-of-memory errors
        // Aligned with Snapdragon 8 Gen 3 multi-threading (1x X4, 5x A720, 2x A520)
        private val processingSemaphore = Semaphore(8)

        // Thread-local reusable pixel buffer pool to eliminate garbage collection pauses
        private val threadLocalBuffer = ThreadLocal<IntArray>()

        private fun getPooledBuffer(requiredSize: Int): IntArray {
            var buf = threadLocalBuffer.get()
            if (buf == null || buf.size < requiredSize) {
                buf = IntArray(requiredSize)
                threadLocalBuffer.set(buf)
            }
            return buf
        }

        /** The AGSL shader source for single-pass image adjustment on GPU */
        val SHADER_SOURCE = """
            uniform shader inputImage;
            
            // Basic adjustments [-1, 1]
            uniform float uExposure;
            uniform float uContrast;
            uniform float uHighlights;
            uniform float uShadows;
            uniform float uWhites;
            uniform float uBlacks;
            uniform float uSaturation;
            uniform float uVibrance;
            
            // White balance [-1, 1]
            uniform float uTemperature;
            uniform float uTint;
            
            // Texture, Clarity, Dehaze [-1, 1]
            uniform float uDehaze;
            uniform float uClarity;
            uniform float uTexture;
            
            // Split toning: vec4(shadowHue, shadowSat, highlightHue, highlightSat)
            uniform vec4 uSplitToning;
            
            // Tone curves: vec4(x0, x1, x2, x3), vec4(y0, y1, y2, y3)
            uniform vec4 uCurveMasterX;
            uniform vec4 uCurveMasterY;
            uniform vec4 uCurveRedX;
            uniform vec4 uCurveRedY;
            uniform vec4 uCurveGreenX;
            uniform vec4 uCurveGreenY;
            uniform vec4 uCurveBlueX;
            uniform vec4 uCurveBlueY;
            
            // HSL adjustments: 8 channels x 3 (hue, sat, lum)
            uniform vec3 uHslRed;
            uniform vec3 uHslOrange;
            uniform vec3 uHslYellow;
            uniform vec3 uHslGreen;
            uniform vec3 uHslAqua;
            uniform vec3 uHslBlue;
            uniform vec3 uHslPurple;
            uniform vec3 uHslMagenta;
            
            float srgbToLinear(float c) {
                return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4);
            }
            
            vec3 srgbToLinearVec(vec3 c) {
                return vec3(srgbToLinear(c.r), srgbToLinear(c.g), srgbToLinear(c.b));
            }
            
            float linearToSrgb(float c) {
                return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(max(c, 0.0), 1.0 / 2.4) - 0.055;
            }
            
            vec3 linearToSrgbVec(vec3 c) {
                return vec3(linearToSrgb(c.r), linearToSrgb(c.g), linearToSrgb(c.b));
            }
            
            float evalCurve4(float x, vec4 px, vec4 py) {
                if (x <= px.x) return py.x;
                if (x <= px.y) return mix(py.x, py.y, clamp((x - px.x) / max(px.y - px.x, 0.0001), 0.0, 1.0));
                if (x <= px.z) return mix(py.y, py.z, clamp((x - px.y) / max(px.z - px.y, 0.0001), 0.0, 1.0));
                if (x <= px.w) return mix(py.z, py.w, clamp((x - px.z) / max(px.w - px.z, 0.0001), 0.0, 1.0));
                return py.w;
            }
            
            float adjustTonal(float c, float h, float sh, float w, float b) {
                if (c > 0.5 && h != 0.0) {
                    float mask = (c - 0.5) * 2.0;
                    c += h * 0.35 * mask * (1.0 - c) * 2.0;
                }
                if (c < 0.5 && sh != 0.0) {
                    float mask = (0.5 - c) * 2.0;
                    c += sh * 0.35 * mask * c * 2.0;
                }
                if (c > 0.6 && w != 0.0) {
                    float mask = (c - 0.6) / 0.4;
                    c += w * 0.25 * mask;
                }
                if (c < 0.4 && b != 0.0) {
                    float mask = (0.4 - c) / 0.4;
                    c += b * 0.25 * mask;
                }
                return clamp(c, 0.0, 1.0);
            }
            
            vec3 rgb2hsl(vec3 c) {
                float maxC = max(max(c.r, c.g), c.b);
                float minC = min(min(c.r, c.g), c.b);
                float delta = maxC - minC;
                float l = (maxC + minC) * 0.5;
                float h = 0.0;
                float s = 0.0;
                
                if (delta > 0.001) {
                    s = l < 0.5 ? delta / (maxC + minC) : delta / (2.0 - maxC - minC);
                    if (maxC == c.r) {
                        h = (c.g - c.b) / delta + (c.g < c.b ? 6.0 : 0.0);
                    } else if (maxC == c.g) {
                        h = (c.b - c.r) / delta + 2.0;
                    } else {
                        h = (c.r - c.g) / delta + 4.0;
                    }
                    h /= 6.0;
                }
                return vec3(h, s, l);
            }
            
            float hue2rgb(float p, float q, float t) {
                if (t < 0.0) t += 1.0;
                if (t > 1.0) t -= 1.0;
                if (t < 1.0/6.0) return p + (q - p) * 6.0 * t;
                if (t < 1.0/2.0) return q;
                if (t < 2.0/3.0) return p + (q - p) * (2.0/3.0 - t) * 6.0;
                return p;
            }
            
            vec3 hsl2rgb(vec3 hsl) {
                if (hsl.y <= 0.0001) return vec3(hsl.z);
                float q = hsl.z < 0.5 ? hsl.z * (1.0 + hsl.y) : hsl.z + hsl.y - hsl.z * hsl.y;
                float p = 2.0 * hsl.z - q;
                float r = hue2rgb(p, q, hsl.x + 1.0/3.0);
                float g = hue2rgb(p, q, hsl.x);
                float b = hue2rgb(p, q, hsl.x - 1.0/3.0);
                return vec3(r, g, b);
            }
            
            float channelWeight(float hue, float center, float width) {
                float d = abs(hue - center);
                if (d > 0.5) d = 1.0 - d;
                float t = clamp((width - d) / width, 0.0, 1.0);
                return t * t * (3.0 - 2.0 * t);
            }
            
            half4 main(float2 coord) {
                half4 pixel = inputImage.eval(coord);
                if (pixel.a <= 0.001) return half4(0.0);
                
                vec3 color = vec3(pixel.rgb) / pixel.a;
                
                // 1. Exposure & White Balance in linear optical space
                vec3 linearColor = srgbToLinearVec(color);
                linearColor *= pow(2.0, uExposure * 2.0);
                linearColor.r *= 1.0 + uTemperature * 0.3 + uTint * 0.07;
                linearColor.g *= 1.0 - uTint * 0.15;
                linearColor.b *= 1.0 - uTemperature * 0.3;
                color = clamp(linearToSrgbVec(linearColor), 0.0, 1.0);
                
                // 2. Contrast in perceptual gamma space (centered at mid-gray 0.5)
                if (uContrast != 0.0) {
                    float cFactor = 1.0 + uContrast * 0.9;
                    color = clamp((color - 0.5) * cFactor + 0.5, 0.0, 1.0);
                }
                
                // 3. Tone Curves (Master + RGB)
                color.r = evalCurve4(color.r, uCurveMasterX, uCurveMasterY);
                color.g = evalCurve4(color.g, uCurveMasterX, uCurveMasterY);
                color.b = evalCurve4(color.b, uCurveMasterX, uCurveMasterY);
                
                color.r = evalCurve4(color.r, uCurveRedX, uCurveRedY);
                color.g = evalCurve4(color.g, uCurveGreenX, uCurveGreenY);
                color.b = evalCurve4(color.b, uCurveBlueX, uCurveBlueY);
                color = clamp(color, 0.0, 1.0);
                
                // 4. Highlights / Shadows / Whites / Blacks (per channel, identical to LUT)
                color.r = adjustTonal(color.r, uHighlights, uShadows, uWhites, uBlacks);
                color.g = adjustTonal(color.g, uHighlights, uShadows, uWhites, uBlacks);
                color.b = adjustTonal(color.b, uHighlights, uShadows, uWhites, uBlacks);
                
                // 6. HSL Color Adjustments
                vec3 hsl = rgb2hsl(color);
                float h = hsl.x;
                
                float wRed     = channelWeight(h, 0.0, 0.12) + channelWeight(h, 1.0, 0.12);
                float wOrange  = channelWeight(h, 0.083, 0.12);
                float wYellow  = channelWeight(h, 0.167, 0.12);
                float wGreen   = channelWeight(h, 0.333, 0.15);
                float wAqua    = channelWeight(h, 0.500, 0.12);
                float wBlue    = channelWeight(h, 0.667, 0.15);
                float wPurple  = channelWeight(h, 0.750, 0.12);
                float wMagenta = channelWeight(h, 0.833, 0.12);
                
                float dHue = wRed * uHslRed.x + wOrange * uHslOrange.x + wYellow * uHslYellow.x +
                             wGreen * uHslGreen.x + wAqua * uHslAqua.x + wBlue * uHslBlue.x +
                             wPurple * uHslPurple.x + wMagenta * uHslMagenta.x;
                             
                float dSat = wRed * uHslRed.y + wOrange * uHslOrange.y + wYellow * uHslYellow.y +
                             wGreen * uHslGreen.y + wAqua * uHslAqua.y + wBlue * uHslBlue.y +
                             wPurple * uHslPurple.y + wMagenta * uHslMagenta.y;
                             
                float dLum = wRed * uHslRed.z + wOrange * uHslOrange.z + wYellow * uHslYellow.z +
                             wGreen * uHslGreen.z + wAqua * uHslAqua.z + wBlue * uHslBlue.z +
                             wPurple * uHslPurple.z + wMagenta * uHslMagenta.z;
                
                hsl.x = fract(hsl.x + dHue * 0.1);
                hsl.y = clamp(hsl.y + dSat * 0.8, 0.0, 1.0);
                hsl.z = clamp(hsl.z + dLum * 0.4, 0.0, 1.0);
                color = hsl2rgb(hsl);

                // 7. Saturation & Vibrance
                float gray = dot(color, vec3(0.2126, 0.7152, 0.0722));
                float satFactor = uSaturation < 0.0 ? (1.0 + uSaturation) : (1.0 + uSaturation * 1.2);
                color = mix(vec3(gray), color, satFactor);
                
                float maxC = max(max(color.r, color.g), color.b);
                float minC = min(min(color.r, color.g), color.b);
                float currentSat = maxC > 0.001 ? (maxC - minC) / maxC : 0.0;
                float vibWeight = clamp(1.0 - currentSat, 0.0, 1.0);
                color = mix(vec3(gray), color, 1.0 + uVibrance * 1.0 * vibWeight);
                
                // 7. Texture, Clarity, Dehaze
                float curLum = dot(color, vec3(0.2126, 0.7152, 0.0722));
                if (uTexture != 0.0 || uClarity != 0.0 || uDehaze != 0.0) {
                    float factor = 1.0 + uTexture * 0.15 + uClarity * 0.25 + uDehaze * 0.2;
                    color = mix(vec3(curLum), color, factor);
                    if (uDehaze > 0.0) {
                        float dh = uDehaze * 0.05;
                        color = (color - dh) / (1.0 - dh);
                    }
                    curLum = dot(color, vec3(0.2126, 0.7152, 0.0722));
                }
                
                // 8. Split Toning (additive tint cast, preserves contrast)
                if (uSplitToning.y > 0.0 && curLum < 0.5) {
                    vec3 shadowTint = hsl2rgb(vec3(uSplitToning.x, 1.0, 0.5));
                    float sFactor = uSplitToning.y * (1.0 - curLum * 2.0) * 0.4;
                    color += (shadowTint - 0.5) * sFactor;
                }
                if (uSplitToning.w > 0.0 && curLum >= 0.5) {
                    vec3 highlightTint = hsl2rgb(vec3(uSplitToning.z, 1.0, 0.5));
                    float hFactor = uSplitToning.w * ((curLum - 0.5) * 2.0) * 0.4;
                    color += (highlightTint - 0.5) * hFactor;
                }
                
                color = clamp(color, 0.0, 1.0);
                return half4(half3(color * pixel.a), pixel.a);
            }
        """.trimIndent()

        data class CurvePoint(val x: Float, val y: Float)

        fun parseCurve(pointsStr: String): List<CurvePoint> {
            if (pointsStr.isBlank()) return listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
            return try {
                val pts = pointsStr.split(";").mapNotNull { pair ->
                    val parts = pair.trim().split(",")
                    if (parts.size == 2) {
                        val x = parts[0].trim().toFloatOrNull() ?: return@mapNotNull null
                        val y = parts[1].trim().toFloatOrNull() ?: return@mapNotNull null
                        CurvePoint((x / 255f).coerceIn(0f, 1f), (y / 255f).coerceIn(0f, 1f))
                    } else null
                }.sortedBy { it.x }
                if (pts.isEmpty()) listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)) else pts
            } catch (_: Exception) {
                listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
            }
        }

        fun evaluateCurvePoints(norm: Float, pts: List<CurvePoint>): Float {
            if (pts.isEmpty()) return norm
            if (norm <= pts.first().x) return pts.first().y
            if (norm >= pts.last().x) return pts.last().y
            for (i in 0 until pts.size - 1) {
                val p0 = pts[i]
                val p1 = pts[i + 1]
                if (norm in p0.x..p1.x) {
                    val span = p1.x - p0.x
                    if (span <= 0.00001f) return p0.y
                    val t = (norm - p0.x) / span
                    return p0.y + t * (p1.y - p0.y)
                }
            }
            return norm
        }

        fun precomputeCurveLut(pointsStr: String): FloatArray {
            val lut = FloatArray(256)
            val pts = parseCurve(pointsStr)
            for (i in 0..255) {
                lut[i] = evaluateCurvePoints(i / 255f, pts)
            }
            return lut
        }

        fun curveToVec4(pointsStr: String): Pair<FloatArray, FloatArray> {
            val pts = parseCurve(pointsStr)
            val x = FloatArray(4)
            val y = FloatArray(4)
            when {
                pts.isEmpty() || pointsStr.isBlank() -> {
                    x[0] = 0f; x[1] = 0.33f; x[2] = 0.67f; x[3] = 1f
                    y[0] = 0f; y[1] = 0.33f; y[2] = 0.67f; y[3] = 1f
                }
                pts.size == 1 -> {
                    x[0] = 0f; x[1] = pts[0].x * 0.5f; x[2] = pts[0].x; x[3] = 1f
                    y[0] = 0f; y[1] = pts[0].y * 0.5f; y[2] = pts[0].y; y[3] = 1f
                }
                pts.size == 2 -> {
                    val p0 = pts[0]
                    val p1 = pts[1]
                    val dx = p1.x - p0.x
                    val dy = p1.y - p0.y
                    x[0] = p0.x; x[1] = p0.x + dx * 0.333f; x[2] = p0.x + dx * 0.667f; x[3] = p1.x
                    y[0] = p0.y; y[1] = p0.y + dy * 0.333f; y[2] = p0.y + dy * 0.667f; y[3] = p1.y
                }
                pts.size == 3 -> {
                    x[0] = pts[0].x; x[1] = pts[1].x
                    x[2] = pts[1].x + (pts[2].x - pts[1].x) * 0.5f; x[3] = pts[2].x
                    y[0] = pts[0].y; y[1] = pts[1].y
                    y[2] = pts[1].y + (pts[2].y - pts[1].y) * 0.5f; y[3] = pts[2].y
                }
                pts.size == 4 -> {
                    x[0] = pts[0].x; x[1] = pts[1].x; x[2] = pts[2].x; x[3] = pts[3].x
                    y[0] = pts[0].y; y[1] = pts[1].y; y[2] = pts[2].y; y[3] = pts[3].y
                }
                else -> {
                    x[0] = pts.first().x; x[1] = pts[(pts.size - 1) / 3].x; x[2] = pts[(pts.size - 1) * 2 / 3].x; x[3] = pts.last().x
                    y[0] = pts.first().y; y[1] = pts[(pts.size - 1) / 3].y; y[2] = pts[(pts.size - 1) * 2 / 3].y; y[3] = pts.last().y
                }
            }
            return Pair(x, y)
        }

        /**
         * Factory method to create a configured RuntimeShader for hardware-accelerated Compose rendering.
         */
        fun createRuntimeShader(params: PresetParameters): RuntimeShader {
            val shader = RuntimeShader(SHADER_SOURCE)
            configureShaderUniforms(shader, params)
            return shader
        }

        /** Set all shader uniform values from PresetParameters */
        fun configureShaderUniforms(shader: RuntimeShader, params: PresetParameters) {
            shader.setFloatUniform("uExposure", params.exposure)
            shader.setFloatUniform("uContrast", params.contrast)
            shader.setFloatUniform("uHighlights", params.highlights)
            shader.setFloatUniform("uShadows", params.shadows)
            shader.setFloatUniform("uWhites", params.whites)
            shader.setFloatUniform("uBlacks", params.blacks)
            shader.setFloatUniform("uSaturation", params.saturation)
            shader.setFloatUniform("uVibrance", params.vibrance)
            shader.setFloatUniform("uTemperature", params.temperature)
            shader.setFloatUniform("uTint", params.tint)

            shader.setFloatUniform("uDehaze", params.dehaze)
            shader.setFloatUniform("uClarity", params.clarity)
            shader.setFloatUniform("uTexture", params.texture)

            shader.setFloatUniform("uSplitToning",
                params.splitShadowHue, params.splitShadowSat,
                params.splitHighlightHue, params.splitHighlightSat
            )

            val (mX, mY) = curveToVec4(params.toneCurvePoints)
            val (rX, rY) = curveToVec4(params.toneCurveRedPoints)
            val (gX, gY) = curveToVec4(params.toneCurveGreenPoints)
            val (bX, bY) = curveToVec4(params.toneCurveBluePoints)

            shader.setFloatUniform("uCurveMasterX", mX[0], mX[1], mX[2], mX[3])
            shader.setFloatUniform("uCurveMasterY", mY[0], mY[1], mY[2], mY[3])
            shader.setFloatUniform("uCurveRedX", rX[0], rX[1], rX[2], rX[3])
            shader.setFloatUniform("uCurveRedY", rY[0], rY[1], rY[2], rY[3])
            shader.setFloatUniform("uCurveGreenX", gX[0], gX[1], gX[2], gX[3])
            shader.setFloatUniform("uCurveGreenY", gY[0], gY[1], gY[2], gY[3])
            shader.setFloatUniform("uCurveBlueX", bX[0], bX[1], bX[2], bX[3])
            shader.setFloatUniform("uCurveBlueY", bY[0], bY[1], bY[2], bY[3])

            shader.setFloatUniform("uHslRed",
                params.hslRedHue, params.hslRedSaturation, params.hslRedLuminance)
            shader.setFloatUniform("uHslOrange",
                params.hslOrangeHue, params.hslOrangeSaturation, params.hslOrangeLuminance)
            shader.setFloatUniform("uHslYellow",
                params.hslYellowHue, params.hslYellowSaturation, params.hslYellowLuminance)
            shader.setFloatUniform("uHslGreen",
                params.hslGreenHue, params.hslGreenSaturation, params.hslGreenLuminance)
            shader.setFloatUniform("uHslAqua",
                params.hslAquaHue, params.hslAquaSaturation, params.hslAquaLuminance)
            shader.setFloatUniform("uHslBlue",
                params.hslBlueHue, params.hslBlueSaturation, params.hslBlueLuminance)
            shader.setFloatUniform("uHslPurple",
                params.hslPurpleHue, params.hslPurpleSaturation, params.hslPurpleLuminance)
            shader.setFloatUniform("uHslMagenta",
                params.hslMagentaHue, params.hslMagentaSaturation, params.hslMagentaLuminance)
        }
    }

    /**
     * Reads EXIF orientation from an image URI.
     */
    private fun getExifOrientation(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * Computes the transformation Matrix needed to rotate/flip an image to upright (orientation 1).
     * Returns null if orientation is already normal or undefined.
     */
    private fun getRotationMatrix(orientation: Int): Matrix? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return null
        }
        return matrix
    }

    /**
     * Process a single image directly to a SAF document URI.
     * Evaluates adjustments, bakes in EXIF rotation so pixels are physically upright,
     * updates EXIF metadata (orientation 1, correct dimensions), and saves to SAF.
     */
    suspend fun processImageToUri(
        inputUri: Uri,
        outputDocUri: Uri,
        params: PresetParameters,
        quality: Int = 92
    ) = withContext(Dispatchers.Default) {
        processingSemaphore.acquire()
        try {
            ensureActive()

            // Fast-path: If no adjustments are needed, copy original stream directly
            if (!params.hasAdjustments()) {
                context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                    context.contentResolver.openOutputStream(outputDocUri, "w")?.use { outputStream ->
                        inputStream.copyTo(outputStream, bufferSize = 65536)
                    }
                } ?: throw IllegalStateException("Cannot stream copy: $inputUri")
                return@withContext
            }

            // 1. Decode JPEG as mutable bitmap
            val inputBitmap = decodeBitmap(inputUri)
                ?: throw IllegalStateException("Failed to decode image from: $inputUri")

            try {
                ensureActive()

                // 2. Multi-threaded in-place LUT adjustment (utilizes all CPU cores)
                applyAdjustments(inputBitmap, params)

                ensureActive()

                // 3. Check orientation and physically rotate pixels to upright if necessary
                val orientation = getExifOrientation(inputUri)
                val matrix = getRotationMatrix(orientation)
                val finalBitmap = if (matrix != null) {
                    val rotated = Bitmap.createBitmap(
                        inputBitmap, 0, 0,
                        inputBitmap.width, inputBitmap.height,
                        matrix, true
                    )
                    if (rotated !== inputBitmap) {
                        inputBitmap.recycle()
                    }
                    rotated
                } else {
                    inputBitmap
                }

                try {
                    // 4. Read EXIF metadata from source and update orientation/dimensions
                    val exifData = readExifData(inputUri).toMutableMap()
                    if (matrix != null) {
                        // Pixels are now physically upright in finalBitmap
                        exifData[ExifInterface.TAG_ORIENTATION] = ExifInterface.ORIENTATION_NORMAL.toString()
                        exifData[ExifInterface.TAG_IMAGE_WIDTH] = finalBitmap.width.toString()
                        exifData[ExifInterface.TAG_IMAGE_LENGTH] = finalBitmap.height.toString()
                    }

                    // 5. Write to SAF destination with full EXIF metadata preserved
                    writeToSafDirect(outputDocUri, finalBitmap, quality, exifData)
                } finally {
                    if (finalBitmap !== inputBitmap && !finalBitmap.isRecycled) {
                        finalBitmap.recycle()
                    }
                }

            } finally {
                if (!inputBitmap.isRecycled) {
                    inputBitmap.recycle()
                }
            }
        } finally {
            processingSemaphore.release()
        }
    }

    /**
     * Writes compressed JPEG directly to SAF Document URI preserving full EXIF metadata.
     * Uses a fast local cache file to reliably apply EXIF attributes via ExifInterface,
     * then streams directly to the SAF output stream.
     */
    private fun writeToSafDirect(
        outputDocUri: Uri,
        bitmap: Bitmap,
        quality: Int,
        exifData: Map<String, String>
    ) {
        if (exifData.isEmpty()) {
            context.contentResolver.openOutputStream(outputDocUri, "w")?.use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                fos.flush()
            } ?: throw IllegalStateException("Cannot open output stream for: $outputDocUri")
            return
        }

        val tempFile = File(context.cacheDir, "lb_exp_${Thread.currentThread().id}_${System.nanoTime()}.jpg")
        try {
            FileOutputStream(tempFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                fos.flush()
            }

            try {
                val exif = ExifInterface(tempFile.absolutePath)
                for ((tag, value) in exifData) {
                    exif.setAttribute(tag, value)
                }
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, "LightBox 1.0")
                exif.saveAttributes()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save EXIF attributes on temp file", e)
            }

            tempFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(outputDocUri, "w")?.use { output ->
                    input.copyTo(output, bufferSize = 65536)
                    output.flush()
                } ?: throw IllegalStateException("Cannot open output stream for: $outputDocUri")
            }
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Generate a micro-thumbnail for UI display.
     * Decodes at reduced sample size, rotates upright according to EXIF,
     * and saves to cache file.
     */
    suspend fun generateThumbnail(
        inputUri: Uri,
        outputFile: File,
        maxDimension: Int = 256
    ): File = withContext(Dispatchers.IO) {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(inputUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val sampleSize = calculateSampleSize(
            boundsOptions.outWidth, boundsOptions.outHeight, maxDimension
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // Low memory for thumbnail grid
        }

        val thumbnail = context.contentResolver.openInputStream(inputUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Cannot decode thumbnail: $inputUri")

        try {
            val orientation = getExifOrientation(inputUri)
            val matrix = getRotationMatrix(orientation)
            val finalThumbnail = if (matrix != null) {
                val rotated = Bitmap.createBitmap(
                    thumbnail, 0, 0,
                    thumbnail.width, thumbnail.height,
                    matrix, true
                )
                if (rotated !== thumbnail) {
                    thumbnail.recycle()
                }
                rotated
            } else {
                thumbnail
            }

            try {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { fos ->
                    finalThumbnail.compress(Bitmap.CompressFormat.JPEG, 75, fos)
                }
            } finally {
                if (finalThumbnail !== thumbnail && !finalThumbnail.isRecycled) {
                    finalThumbnail.recycle()
                }
            }
        } finally {
            if (!thumbnail.isRecycled) {
                thumbnail.recycle()
            }
        }

        outputFile
    }

    /** Precomputes 256-element RGB Look-Up Tables */
    private fun buildLuts(params: PresetParameters): Triple<IntArray, IntArray, IntArray> {
        val lutR = IntArray(256)
        val lutG = IntArray(256)
        val lutB = IntArray(256)

        // 1. Exposure multiplier in optical linear space
        val expFactor = 2.0.pow((params.exposure * 2.0).toDouble()).toFloat()

        // 2. White Balance in optical linear space
        val tempR = 1.0f + params.temperature * 0.3f + params.tint * 0.07f
        val tempG = 1.0f - params.tint * 0.15f
        val tempB = 1.0f - params.temperature * 0.3f

        // Contrast factor in perceptual gamma space (centered at mid-gray 0.5)
        val contrastFactor = 1.0f + params.contrast * 0.9f

        val masterLut = if (params.toneCurvePoints.isNotBlank()) precomputeCurveLut(params.toneCurvePoints) else null
        val redLut = if (params.toneCurveRedPoints.isNotBlank()) precomputeCurveLut(params.toneCurveRedPoints) else null
        val greenLut = if (params.toneCurveGreenPoints.isNotBlank()) precomputeCurveLut(params.toneCurveGreenPoints) else null
        val blueLut = if (params.toneCurveBluePoints.isNotBlank()) precomputeCurveLut(params.toneCurveBluePoints) else null

        for (i in 0..255) {
            val norm = i / 255.0f

            fun computeChannel(base: Float, wbMult: Float, channelLut: FloatArray?): Int {
                // Step A: Exposure & White Balance in linear light
                var linear = srgbToLinear(base)
                linear *= expFactor
                linear *= wbMult
                var s = linearToSrgb(linear)

                // Step B: Contrast in perceptual gamma space (centered around mid-gray 0.5)
                if (params.contrast != 0f) {
                    s = ((s - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)
                }

                // Step C: Master Tone Curve
                if (masterLut != null) {
                    val idx = (s * 255f + 0.5f).toInt().coerceIn(0, 255)
                    s = masterLut[idx]
                }

                // Step D: Channel Tone Curve
                if (channelLut != null) {
                    val idx = (s * 255f + 0.5f).toInt().coerceIn(0, 255)
                    s = channelLut[idx]
                }

                // Step E: Highlights, Shadows, Whites, Blacks
                if (s > 0.5f && params.highlights != 0f) {
                    val mask = (s - 0.5f) * 2.0f
                    s += params.highlights * 0.35f * mask * (1f - s) * 2f
                }
                if (s < 0.5f && params.shadows != 0f) {
                    val mask = (0.5f - s) * 2.0f
                    s += params.shadows * 0.35f * mask * s * 2f
                }
                if (s > 0.6f && params.whites != 0f) {
                    val mask = (s - 0.6f) / 0.4f
                    s += params.whites * 0.25f * mask
                }
                if (s < 0.4f && params.blacks != 0f) {
                    val mask = (0.4f - s) / 0.4f
                    s += params.blacks * 0.25f * mask
                }

                return (s.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
            }

            lutR[i] = computeChannel(norm, tempR, redLut)
            lutG[i] = computeChannel(norm, tempG, greenLut)
            lutB[i] = computeChannel(norm, tempB, blueLut)
        }

        return Triple(lutR, lutG, lutB)
    }

    /**
     * High-speed, multi-threaded in-place transformation engine.
     * Uses precomputed 256-level RGB Look-Up Tables (LUT), slices the image across
     * all Snapdragon 8 Gen 3 CPU cores with Dispatchers.Default, and reuses thread-local
     * buffers to eliminate allocations. Modifies the source bitmap in-place.
     */
    suspend fun applyAdjustments(source: Bitmap, params: PresetParameters): Bitmap = withContext(Dispatchers.Default) {
        if (!params.hasAdjustments()) return@withContext source

        val width = source.width
        val height = source.height

        val (lutR, lutG, lutB) = buildLuts(params)

        val hasHsl = params.hslRedHue != 0f || params.hslRedSaturation != 0f || params.hslRedLuminance != 0f ||
                params.hslOrangeHue != 0f || params.hslOrangeSaturation != 0f || params.hslOrangeLuminance != 0f ||
                params.hslYellowHue != 0f || params.hslYellowSaturation != 0f || params.hslYellowLuminance != 0f ||
                params.hslGreenHue != 0f || params.hslGreenSaturation != 0f || params.hslGreenLuminance != 0f ||
                params.hslAquaHue != 0f || params.hslAquaSaturation != 0f || params.hslAquaLuminance != 0f ||
                params.hslBlueHue != 0f || params.hslBlueSaturation != 0f || params.hslBlueLuminance != 0f ||
                params.hslPurpleHue != 0f || params.hslPurpleSaturation != 0f || params.hslPurpleLuminance != 0f ||
                params.hslMagentaHue != 0f || params.hslMagentaSaturation != 0f || params.hslMagentaLuminance != 0f

        val hasSatVib = params.saturation != 0f || params.vibrance != 0f
        val hasTextureClarityDehaze = params.texture != 0f || params.clarity != 0f || params.dehaze != 0f
        val hasSplitToning = params.splitShadowSat > 0f || params.splitHighlightSat > 0f
        val hasVignette = params.vignetteAmount != 0f

        val needsPerPixelPass = hasHsl || hasSatVib || hasTextureClarityDehaze || hasSplitToning || hasVignette

        val satFactor = if (params.saturation < 0f) 1.0f + params.saturation else 1.0f + params.saturation * 1.2f
        val clarityDehazeFactor = 1.0f + params.texture * 0.15f + params.clarity * 0.25f + params.dehaze * 0.2f

        val cx = width / 2f
        val cy = height / 2f
        val maxDist = kotlin.math.sqrt(cx * cx + cy * cy)

        // Split rows across Snapdragon 8 Gen 3 CPU cores (1x Cortex-X4, 5x Cortex-A720)
        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
        val sliceHeight = (height + numCores - 1) / numCores

        coroutineScope {
            (0 until numCores).map { coreIndex ->
                async(Dispatchers.Default) {
                    val startY = coreIndex * sliceHeight
                    if (startY >= height) return@async
                    val rowsToProcess = min(sliceHeight, height - startY)
                    val totalPixels = width * rowsToProcess

                    // Reusable thread-local buffer: 0 allocations
                    val pixelBuffer = getPooledBuffer(totalPixels)

                    source.getPixels(pixelBuffer, 0, width, 0, startY, width, rowsToProcess)

                    if (!needsPerPixelPass) {
                        // Direct LUT mapping
                        for (i in 0 until totalPixels) {
                            val pixel = pixelBuffer[i]
                            val a = pixel and -0x1000000 // preserve alpha (0xFF000000)
                            val rawR = (pixel ushr 16) and 0xFF
                            val rawG = (pixel ushr 8) and 0xFF
                            val rawB = pixel and 0xFF
                            pixelBuffer[i] = a or (lutR[rawR] shl 16) or (lutG[rawG] shl 8) or lutB[rawB]
                        }
                    } else {
                        for (i in 0 until totalPixels) {
                            val pixel = pixelBuffer[i]
                            val a = (pixel ushr 24) and 0xFF
                            val rawR = (pixel ushr 16) and 0xFF
                            val rawG = (pixel ushr 8) and 0xFF
                            val rawB = pixel and 0xFF

                            var r = lutR[rawR] / 255f
                            var g = lutG[rawG] / 255f
                            var b = lutB[rawB] / 255f

                            // 5. HSL
                            if (hasHsl) {
                                val (h, s, l) = rgbToHsl(r, g, b)
                                val wRed     = channelWeight(h, 0.0f, 0.12f) + channelWeight(h, 1.0f, 0.12f)
                                val wOrange  = channelWeight(h, 0.083f, 0.12f)
                                val wYellow  = channelWeight(h, 0.167f, 0.12f)
                                val wGreen   = channelWeight(h, 0.333f, 0.15f)
                                val wAqua    = channelWeight(h, 0.500f, 0.12f)
                                val wBlue    = channelWeight(h, 0.667f, 0.15f)
                                val wPurple  = channelWeight(h, 0.750f, 0.12f)
                                val wMagenta = channelWeight(h, 0.833f, 0.12f)

                                val dHue = wRed * params.hslRedHue + wOrange * params.hslOrangeHue + wYellow * params.hslYellowHue +
                                           wGreen * params.hslGreenHue + wAqua * params.hslAquaHue + wBlue * params.hslBlueHue +
                                           wPurple * params.hslPurpleHue + wMagenta * params.hslMagentaHue
                                val dSat = wRed * params.hslRedSaturation + wOrange * params.hslOrangeSaturation + wYellow * params.hslYellowSaturation +
                                           wGreen * params.hslGreenSaturation + wAqua * params.hslAquaSaturation + wBlue * params.hslBlueSaturation +
                                           wPurple * params.hslPurpleSaturation + wMagenta * params.hslMagentaSaturation
                                val dLum = wRed * params.hslRedLuminance + wOrange * params.hslOrangeLuminance + wYellow * params.hslYellowLuminance +
                                           wGreen * params.hslGreenLuminance + wAqua * params.hslAquaLuminance + wBlue * params.hslBlueLuminance +
                                           wPurple * params.hslPurpleLuminance + wMagenta * params.hslMagentaLuminance

                                val newH = (h + dHue * 0.1f + 1f) % 1f
                                val newS = (s + dSat * 0.8f).coerceIn(0f, 1f)
                                val newL = (l + dLum * 0.4f).coerceIn(0f, 1f)
                                val outRgb = hslToRgb(newH, newS, newL)
                                r = outRgb[0]; g = outRgb[1]; b = outRgb[2]
                            }

                            // 6. Saturation & Vibrance
                            if (hasSatVib) {
                                val gray = 0.2126f * r + 0.7152f * g + 0.0722f * b
                                if (params.saturation != 0f) {
                                    r = mix(gray, r, satFactor).coerceIn(0f, 1f)
                                    g = mix(gray, g, satFactor).coerceIn(0f, 1f)
                                    b = mix(gray, b, satFactor).coerceIn(0f, 1f)
                                }
                                if (params.vibrance != 0f) {
                                    val maxC = maxOf(r, g, b)
                                    val minC = minOf(r, g, b)
                                    val sat = if (maxC > 0.001f) (maxC - minC) / maxC else 0f
                                    val vibWeight = (1.0f - sat).coerceIn(0f, 1f)
                                    val vibFactor = 1.0f + params.vibrance * 1.0f * vibWeight
                                    r = mix(gray, r, vibFactor).coerceIn(0f, 1f)
                                    g = mix(gray, g, vibFactor).coerceIn(0f, 1f)
                                    b = mix(gray, b, vibFactor).coerceIn(0f, 1f)
                                }
                            }

                            // 7. Texture, Clarity, Dehaze
                            if (hasTextureClarityDehaze) {
                                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                                r = mix(lum, r, clarityDehazeFactor).coerceIn(0f, 1f)
                                g = mix(lum, g, clarityDehazeFactor).coerceIn(0f, 1f)
                                b = mix(lum, b, clarityDehazeFactor).coerceIn(0f, 1f)
                                if (params.dehaze > 0f) {
                                    val dh = params.dehaze * 0.05f
                                    r = ((r - dh) / (1f - dh)).coerceIn(0f, 1f)
                                    g = ((g - dh) / (1f - dh)).coerceIn(0f, 1f)
                                    b = ((b - dh) / (1f - dh)).coerceIn(0f, 1f)
                                }
                            }

                            // 8. Split Toning (additive color cast)
                            if (hasSplitToning) {
                                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                                if (params.splitShadowSat > 0f && lum < 0.5f) {
                                    val shadowColor = hslToRgb(params.splitShadowHue, 1.0f, 0.5f)
                                    val factor = params.splitShadowSat * (1.0f - lum * 2.0f) * 0.4f
                                    r = (r + (shadowColor[0] - 0.5f) * factor).coerceIn(0f, 1f)
                                    g = (g + (shadowColor[1] - 0.5f) * factor).coerceIn(0f, 1f)
                                    b = (b + (shadowColor[2] - 0.5f) * factor).coerceIn(0f, 1f)
                                }
                                if (params.splitHighlightSat > 0f && lum >= 0.5f) {
                                    val highlightColor = hslToRgb(params.splitHighlightHue, 1.0f, 0.5f)
                                    val factor = params.splitHighlightSat * ((lum - 0.5f) * 2.0f) * 0.4f
                                    r = (r + (highlightColor[0] - 0.5f) * factor).coerceIn(0f, 1f)
                                    g = (g + (highlightColor[1] - 0.5f) * factor).coerceIn(0f, 1f)
                                    b = (b + (highlightColor[2] - 0.5f) * factor).coerceIn(0f, 1f)
                                }
                            }

                            // 9. Vignette
                            if (hasVignette) {
                                val x = (i % width).toFloat()
                                val y = (startY + (i / width)).toFloat()
                                val dx = x - cx
                                val dy = y - cy
                                val dist = kotlin.math.sqrt(dx * dx + dy * dy) / maxDist
                                val vig = (1.0f + params.vignetteAmount * (dist * dist)).coerceIn(0f, 2f)
                                r *= vig
                                g *= vig
                                b *= vig
                            }

                            val finalR = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            val finalG = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                            val finalB = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

                            pixelBuffer[i] = (a shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                        }
                    }

                    // In-place write back directly into the source bitmap slice
                    source.setPixels(pixelBuffer, 0, width, 0, startY, width, rowsToProcess)
                }
            }.awaitAll()
        }

        source
    }

    private fun mix(x: Float, y: Float, a: Float): Float = x * (1f - a) + y * a

    private fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        val delta = maxC - minC
        val l = (maxC + minC) * 0.5f
        var h = 0f
        var s = 0f
        if (delta > 0.0001f) {
            s = if (l < 0.5f) delta / (maxC + minC) else delta / (2.0f - maxC - minC)
            h = when (maxC) {
                r -> (g - b) / delta + (if (g < b) 6f else 0f)
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            } / 6f
        }
        return floatArrayOf(h, s, l)
    }

    private fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
        if (s <= 0.0001f) return floatArrayOf(l, l, l)
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        fun hue2rgb(tIn: Float): Float {
            var t = tIn
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 1f / 2f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        return floatArrayOf(hue2rgb(h + 1f / 3f), hue2rgb(h), hue2rgb(h - 1f / 3f))
    }

    private fun channelWeight(hue: Float, center: Float, width: Float): Float {
        var d = abs(hue - center)
        if (d > 0.5f) d = 1f - d
        val t = ((width - d) / width).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true // Enables in-place pixel modifications
            })
        }
    }

    private fun readExifData(uri: Uri): Map<String, String> {
        val exifTags = mutableMapOf<String, String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val tagsToPreserve = arrayOf(
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME_DIGITIZED,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_IMAGE_WIDTH,
                    ExifInterface.TAG_IMAGE_LENGTH,
                    ExifInterface.TAG_WHITE_BALANCE,
                    ExifInterface.TAG_FLASH,
                    ExifInterface.TAG_METERING_MODE
                )
                for (tag in tagsToPreserve) {
                    exif.getAttribute(tag)?.let { exifTags[tag] = it }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF from $uri", e)
        }
        return exifTags
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        val maxSide = max(width, height)
        while (maxSide / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun srgbToLinear(c: Float): Float {
        val nc = c.coerceIn(0f, 1f)
        return if (nc <= 0.04045f) nc / 12.92f else ((nc + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(c: Float): Float {
        val nc = c.coerceIn(0f, 1f)
        return if (nc <= 0.003308f) nc * 12.92f else 1.055f * nc.pow(1.0f / 2.4f) - 0.05f
    }
}
