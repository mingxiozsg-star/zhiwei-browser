package com.java.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 扫码内容的类型：决定结果弹窗给出哪些动作 */
enum class ScanKind { URL, PRODUCT, WIFI, CONTACT, PHONE, EMAIL, SMS, GEO, TEXT }

/** 一次扫描命中 */
data class ScanHit(val text: String, val format: String, val kind: ScanKind)

/**
 * 二维码 / 条形码：格式清单、内容分类、相册图片解码。
 * 相机取景在 ui/ScanScreen.kt，这里只管「码本身」。
 */
object Scan {

    /** 相机与相册共用同一份格式清单（含 EAN/UPC 等一维商品码） */
    val formats: List<BarcodeFormat> = listOf(
        BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417,
        BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODABAR,
        BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.ITF,
        BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.RSS_14, BarcodeFormat.RSS_EXPANDED
    )

    fun hit(text: String, format: String): ScanHit = ScanHit(text.trim(), format, kindOf(text))

    fun kindOf(raw: String): ScanKind {
        val t = raw.trim()
        val low = t.lowercase()
        return when {
            low.startsWith("http://") || low.startsWith("https://") -> ScanKind.URL
            low.startsWith("wifi:") -> ScanKind.WIFI
            low.startsWith("begin:vcard") || low.startsWith("mecard:") -> ScanKind.CONTACT
            low.startsWith("tel:") -> ScanKind.PHONE
            low.startsWith("mailto:") -> ScanKind.EMAIL
            low.startsWith("smsto:") || low.startsWith("sms:") -> ScanKind.SMS
            low.startsWith("geo:") -> ScanKind.GEO
            Regex("^\\d{8,14}$").matches(t) -> ScanKind.PRODUCT
            // 没有 scheme 的裸域名，也按网址处理
            Regex("^[a-z0-9][a-z0-9-]*(\\.[a-z0-9-]+)+(/\\S*)?$", RegexOption.IGNORE_CASE).matches(t) ->
                ScanKind.URL
            else -> ScanKind.TEXT
        }
    }

    fun label(kind: ScanKind): String = when (kind) {
        ScanKind.URL -> "网址"
        ScanKind.PRODUCT -> "商品条码"
        ScanKind.WIFI -> "Wi-Fi 配置"
        ScanKind.CONTACT -> "联系人名片"
        ScanKind.PHONE -> "电话号码"
        ScanKind.EMAIL -> "邮箱地址"
        ScanKind.SMS -> "短信"
        ScanKind.GEO -> "地理位置"
        ScanKind.TEXT -> "文本"
    }

    /** 能否直接「打开」：网址进浏览器，其余交给系统/其他应用 */
    fun openable(kind: ScanKind): Boolean = kind == ScanKind.URL || kind == ScanKind.PHONE ||
        kind == ScanKind.EMAIL || kind == ScanKind.SMS || kind == ScanKind.GEO

    fun asUrl(text: String): String {
        val t = text.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    /** 命中反馈：短震一下，扫到码不用盯着屏幕看 */
    fun vibrate(context: Context) {
        runCatching {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createOneShot(60, 120))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(60)
            }
        }
    }

    /**
     * 从相册图片解码。先按最长边 2000 采样，避免大图 OOM；
     * 失败时逐步旋转 90° 再试——竖拍照片的旋转信息 BitmapFactory 不会自动应用。
     */
    suspend fun decodeFromUri(context: Context, uri: Uri): ScanHit? =
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it, null, bounds) }
            }
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            if (maxSide <= 0) return@withContext null
            var sample = 1
            while (maxSide / sample > 2000) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it, null, opts) }
            }.getOrNull() ?: return@withContext null

            var result = decodeBitmap(bmp)
            if (result == null) {
                repeat(3) {
                    bmp = rotate(bmp, 90f)
                    if (result == null) result = decodeBitmap(bmp)
                }
            }
            runCatching { bmp.recycle() }
            result
        }

    /** 解码整张 Bitmap；单次尝试，供相机帧与相册共用 */
    fun decodeBitmap(bmp: Bitmap): ScanHit? {
        val w = bmp.width
        val h = bmp.height
        if (w < 8 || h < 8) return null
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val binary = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels)))
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to formats,
            DecodeHintType.TRY_HARDER to true
        )
        val reader = MultiFormatReader()
        return try {
            val r = reader.decode(binary, hints)
            hit(r.text, r.barcodeFormat?.name ?: "UNKNOWN")
        } catch (e: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    /** 旋转并回收原图，避免连续扫码时内存堆积 */
    private fun rotate(src: Bitmap, degree: Float): Bitmap {
        val m = Matrix().apply { postRotate(degree) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }
}