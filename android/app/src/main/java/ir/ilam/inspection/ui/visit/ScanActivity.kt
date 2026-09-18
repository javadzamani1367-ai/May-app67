package ir.ilam.inspection.ui.visit

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import ir.ilam.inspection.R

/**
 * The barcode scanner in a box rather than over the whole screen. The library
 * hands its capture activity a layout, so this replaces that layout with one
 * where the preview takes about a quarter of the height — enough to aim a
 * miner's label at, while the instruction below it stays readable.
 */
class ScanActivity : CaptureActivity() {

    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_scan)
        return findViewById(R.id.zxing_barcode_scanner)
    }
}
