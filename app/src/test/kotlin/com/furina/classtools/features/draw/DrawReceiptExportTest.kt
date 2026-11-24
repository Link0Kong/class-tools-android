package com.furina.classtools.features.draw

import android.app.Application
import com.furina.classtools.features.export.toCardSpec
import com.furina.classtools.features.share.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, instrumentedPackages = ["com.vivo.push.sdk"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawReceiptExportTest {
    @Test fun downloadedImageContentIncludesDecodableReceiptAndAllDrawnNumbers() {
        val result = DrawResult((1..20).map { DrawEntry(it) }, (1..70).map { DrawEntry(it) }, DrawConfig(count = 20))
        val receipt = DrawReceipt("dtest000123", 1700000000000, "a".repeat(64), 3, 5)
        val spec = DrawExportFactory.create(result, receipt = receipt).toCardSpec()
        val view = ShareSpecs.fromExportCard(spec, ShareKind.DRAW, "https://tool.furina.fun")
        assertEquals(spec, view.fullExport)
        assertEquals("https://tool.furina.fun/#verify=dtest000123", view.link.url)
        val bitmap = ShareCardRenderer().render(view)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
        val decoded = MultiFormatReader().decode(image, mapOf(DecodeHintType.TRY_HARDER to true))
        assertEquals(view.link.url, decoded.text)
        assertNull(DrawExportFactory.create(result).toCardSpec().qr)
    }
}
