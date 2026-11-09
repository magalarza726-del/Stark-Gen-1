package com.maverick.airboard.tracking

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Gen 1 tracker. It intentionally solves a narrow problem:
 * a saturated blue pen cap on a white or otherwise uniform background.
 *
 * The image is downsampled to a small binary mask, connected components are
 * extracted, and the component closest to the previous target is preferred.
 */
class BlueCapAnalyzer(
    private val onFrame: (TrackingResult) -> Unit,
) : ImageAnalysis.Analyzer {

    data class TrackingResult(
        val x: Float,
        val y: Float,
        val confidence: Float,
        val frameAspectRatio: Float,
        val detected: Boolean,
    )

    @Volatile var hueCenter: Float = 220f
    @Volatile var hueTolerance: Float = 28f
    @Volatile var minSaturation: Float = 0.42f
    @Volatile var calibratedArea: Float = 0f

    private var previousRawX = 0.5f
    private var previousRawY = 0.5f
    private var lastOutputX = 0.5f
    private var lastOutputY = 0.5f
    private var missingFrames = 0

    private var calibrationFramesLeft = 0
    private var calibrationHueSum = 0f
    private var calibrationHueCount = 0
    private var calibrationAreaSum = 0f
    private var calibrationAreaCount = 0
    @Volatile var calibrationFinished: ((Boolean, Float, Float) -> Unit)? = null

    fun startCalibration(frames: Int = 18, callback: (Boolean, Float, Float) -> Unit) {
        calibrationFramesLeft = frames.coerceAtLeast(8)
        calibrationHueSum = 0f
        calibrationHueCount = 0
        calibrationAreaSum = 0f
        calibrationAreaCount = 0
        calibrationFinished = callback
    }

    override fun analyze(image: ImageProxy) {
        try {
            val rotation = image.imageInfo.rotationDegrees
            val width = image.width
            val height = image.height
            val step = 4
            val gridW = max(1, width / step)
            val gridH = max(1, height / step)
            val mask = BooleanArray(gridW * gridH)
            val hueValues = FloatArray(gridW * gridH)

            var idx = 0
            for (gy in 0 until gridH) {
                val y = min(height - 1, gy * step + step / 2)
                for (gx in 0 until gridW) {
                    val x = min(width - 1, gx * step + step / 2)
                    val rgb = yuvToRgb(image, x, y)
                    val hsv = rgbToHsv(rgb.first, rgb.second, rgb.third)
                    hueValues[idx] = hsv.first
                    mask[idx] = isBlue(hsv.first, hsv.second, hsv.third)
                    idx++
                }
            }

            val components = components(mask, hueValues, gridW, gridH)
            val best = chooseBest(components, gridW, gridH)

            val rawX: Float
            val rawY: Float
            val confidence: Float
            val detected: Boolean
            if (best != null) {
                rawX = best.cx / gridW.toFloat()
                rawY = best.cy / gridH.toFloat()
                previousRawX = rawX
                previousRawY = rawY
                val rotated = rotateNormalized(rawX, rawY, rotation)
                lastOutputX = rotated.first
                lastOutputY = rotated.second
                missingFrames = 0
                confidence = best.score.coerceIn(0f, 1f)
                detected = true

                if (calibrationFramesLeft > 0 && distance(rotated.first, rotated.second, .5f, .5f) < .24f) {
                    calibrationHueSum += best.meanHue
                    calibrationHueCount++
                    calibrationAreaSum += best.area.toFloat() / (gridW * gridH).toFloat()
                    calibrationAreaCount++
                }
            } else {
                missingFrames++
                rawX = previousRawX
                rawY = previousRawY
                confidence = 0f
                detected = false
            }

            if (calibrationFramesLeft > 0) {
                calibrationFramesLeft--
                if (calibrationFramesLeft == 0) finishCalibration()
            }

            val rotatedW = if (rotation == 90 || rotation == 270) height else width
            val rotatedH = if (rotation == 90 || rotation == 270) width else height
            val out = if (best != null) rotateNormalized(rawX, rawY, rotation) else lastOutputX to lastOutputY
            onFrame(
                TrackingResult(
                    x = out.first.coerceIn(0f, 1f),
                    y = out.second.coerceIn(0f, 1f),
                    confidence = confidence,
                    frameAspectRatio = rotatedW.toFloat() / rotatedH.toFloat(),
                    detected = detected,
                )
            )
        } finally {
            image.close()
        }
    }

    private fun finishCalibration() {
        val success = calibrationHueCount >= 6 && calibrationAreaCount >= 6
        if (success) {
            hueCenter = normalizeHue(calibrationHueSum / calibrationHueCount)
            calibratedArea = calibrationAreaSum / calibrationAreaCount
            hueTolerance = hueTolerance.coerceIn(14f, 42f)
        }
        calibrationFinished?.invoke(success, hueCenter, calibratedArea)
        calibrationFinished = null
    }

    private data class Component(
        val cx: Float,
        val cy: Float,
        val area: Int,
        val meanHue: Float,
        val width: Int,
        val height: Int,
        var score: Float = 0f,
    )

    private fun chooseBest(list: List<Component>, gridW: Int, gridH: Int): Component? {
        if (list.isEmpty()) return null
        val total = (gridW * gridH).toFloat()
        var best: Component? = null
        var bestScore = -1f
        for (c in list) {
            val areaFraction = c.area / total
            if (areaFraction < 0.00035f || areaFraction > 0.22f) continue
            val nx = c.cx / gridW
            val ny = c.cy / gridH
            val d = distance(nx, ny, previousRawX, previousRawY)
            val proximity = if (missingFrames < 8) (1f - d / .55f).coerceIn(0f, 1f) else 0.45f
            val expected = if (calibratedArea > 0f) {
                val ratio = min(areaFraction, calibratedArea) / max(areaFraction, calibratedArea)
                ratio.coerceIn(0f, 1f)
            } else 0.62f
            val shape = (min(c.width, c.height).toFloat() / max(1, max(c.width, c.height))).coerceIn(.08f, 1f)
            val hue = (1f - hueDistance(c.meanHue, hueCenter) / max(1f, hueTolerance)).coerceIn(0f, 1f)
            val score = 0.42f * hue + 0.28f * proximity + 0.20f * expected + 0.10f * shape
            c.score = score
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        return best?.takeIf { bestScore >= 0.42f }
    }

    private fun components(mask: BooleanArray, hues: FloatArray, w: Int, h: Int): List<Component> {
        val visited = BooleanArray(mask.size)
        val qx = IntArray(mask.size)
        val qy = IntArray(mask.size)
        val out = ArrayList<Component>()
        val dirs = intArrayOf(-1, 0, 1)

        for (sy in 0 until h) for (sx in 0 until w) {
            val start = sy * w + sx
            if (!mask[start] || visited[start]) continue
            var head = 0
            var tail = 0
            qx[tail] = sx
            qy[tail] = sy
            tail++
            visited[start] = true
            var count = 0
            var sumX = 0f
            var sumY = 0f
            var hueX = 0.0
            var hueY = 0.0
            var minX = sx
            var maxX = sx
            var minY = sy
            var maxY = sy

            while (head < tail) {
                val x = qx[head]
                val y = qy[head]
                head++
                val i = y * w + x
                count++
                sumX += x + .5f
                sumY += y + .5f
                val rad = Math.toRadians(hues[i].toDouble())
                hueX += kotlin.math.cos(rad)
                hueY += kotlin.math.sin(rad)
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)

                for (dy in dirs) for (dx in dirs) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val ni = ny * w + nx
                    if (mask[ni] && !visited[ni]) {
                        visited[ni] = true
                        qx[tail] = nx
                        qy[tail] = ny
                        tail++
                    }
                }
            }
            if (count >= 5) {
                var meanHue = Math.toDegrees(kotlin.math.atan2(hueY, hueX)).toFloat()
                if (meanHue < 0f) meanHue += 360f
                out += Component(
                    sumX / count,
                    sumY / count,
                    count,
                    meanHue,
                    maxX - minX + 1,
                    maxY - minY + 1,
                )
            }
        }
        return out
    }

    private fun isBlue(h: Float, s: Float, v: Float): Boolean {
        if (s < minSaturation || v < .12f) return false
        return hueDistance(h, hueCenter) <= hueTolerance
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(normalizeHue(a) - normalizeHue(b))
        return min(d, 360f - d)
    }

    private fun normalizeHue(h: Float): Float {
        var v = h % 360f
        if (v < 0f) v += 360f
        return v
    }

    private fun rotateNormalized(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        90 -> (1f - y) to x
        180 -> (1f - x) to (1f - y)
        270 -> y to (1f - x)
        else -> x to y
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun yuvToRgb(image: ImageProxy, x: Int, y: Int): Triple<Int, Int, Int> {
        val planes = image.planes
        val yP = planes[0]
        val uP = planes[1]
        val vP = planes[2]
        val yIndex = y * yP.rowStride + x * yP.pixelStride
        val uvX = x / 2
        val uvY = y / 2
        val uIndex = uvY * uP.rowStride + uvX * uP.pixelStride
        val vIndex = uvY * vP.rowStride + uvX * vP.pixelStride
        val yy = (yP.buffer.get(yIndex).toInt() and 0xff) - 16
        val u = (uP.buffer.get(uIndex).toInt() and 0xff) - 128
        val v = (vP.buffer.get(vIndex).toInt() and 0xff) - 128
        val c = max(0, yy)
        val r = ((298 * c + 409 * v + 128) shr 8).coerceIn(0, 255)
        val g = ((298 * c - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
        val b = ((298 * c + 516 * u + 128) shr 8).coerceIn(0, 255)
        return Triple(r, g, b)
    }

    private fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val mx = max(rf, max(gf, bf))
        val mn = min(rf, min(gf, bf))
        val d = mx - mn
        var h = when {
            d == 0f -> 0f
            mx == rf -> 60f * (((gf - bf) / d) % 6f)
            mx == gf -> 60f * (((bf - rf) / d) + 2f)
            else -> 60f * (((rf - gf) / d) + 4f)
        }
        if (h < 0f) h += 360f
        val s = if (mx == 0f) 0f else d / mx
        return Triple(h, s, mx)
    }
}
