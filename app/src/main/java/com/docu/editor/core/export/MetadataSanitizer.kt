package com.docu.editor.core.export

import androidx.exifinterface.media.ExifInterface
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MetadataSanitizer {

    suspend fun sanitizeJpeg(jpegFile: File) = withContext(Dispatchers.IO) {
        val exif = ExifInterface(jpegFile.absolutePath)

        exif.setAttribute(ExifInterface.TAG_SOFTWARE, null)
        exif.setAttribute(ExifInterface.TAG_MAKE, null)
        exif.setAttribute(ExifInterface.TAG_MODEL, null)
        exif.setAttribute(ExifInterface.TAG_ARTIST, null)
        exif.setAttribute(ExifInterface.TAG_COPYRIGHT, null)
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, null)

        exif.setAttribute(ExifInterface.TAG_DATETIME, null)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)

        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)

        exif.setAttribute(ExifInterface.TAG_X_RESOLUTION, "300/1")
        exif.setAttribute(ExifInterface.TAG_Y_RESOLUTION, "300/1")
        exif.setAttribute(ExifInterface.TAG_RESOLUTION_UNIT, "2")

        exif.saveAttributes()
    }

    suspend fun sanitizePdf(pdfFile: File) = withContext(Dispatchers.IO) {
        val document = PDDocument.load(pdfFile)
        try {
            val cleanInfo = PDDocumentInformation().apply {
                producer = ""
                creator = ""
                author = ""
                title = ""
                subject = ""
                keywords = ""
                creationDate = null
                modificationDate = null
            }
            document.documentInformation = cleanInfo
            document.documentCatalog.metadata = null
            document.save(pdfFile)
        } finally {
            document.close()
        }
    }
}
