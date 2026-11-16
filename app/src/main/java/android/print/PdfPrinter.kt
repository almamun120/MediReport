package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Writes the output of a [PrintDocumentAdapter] (e.g. from a WebView) straight into a PDF file,
 * without showing the system print dialog.
 *
 * It lives in the `android.print` package because the layout/write callbacks have
 * package-private constructors.
 */
class PdfPrinter(private val attributes: PrintAttributes) {

    fun print(adapter: PrintDocumentAdapter, file: File, onDone: (Boolean) -> Unit) {
        val fd: ParcelFileDescriptor = try {
            ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_WRITE or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            )
        } catch (e: Exception) {
            onDone(false)
            return
        }

        fun finish(ok: Boolean) {
            runCatching { fd.close() }
            onDone(ok)
        }

        adapter.onLayout(
            null,
            attributes,
            CancellationSignal(),
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    adapter.onWrite(
                        arrayOf(PageRange.ALL_PAGES),
                        fd,
                        CancellationSignal(),
                        object : PrintDocumentAdapter.WriteResultCallback() {
                            override fun onWriteFinished(pages: Array<out PageRange>?) = finish(true)
                            override fun onWriteFailed(error: CharSequence?) = finish(false)
                            override fun onWriteCancelled() = finish(false)
                        }
                    )
                }

                override fun onLayoutFailed(error: CharSequence?) = finish(false)
                override fun onLayoutCancelled() = finish(false)
            },
            null
        )
    }
}
