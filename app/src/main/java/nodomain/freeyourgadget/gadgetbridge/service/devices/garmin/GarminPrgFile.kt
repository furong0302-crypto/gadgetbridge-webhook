/*  Copyright (C) 2025 José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin

import android.content.Context
import android.net.Uri
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils
import nodomain.freeyourgadget.gadgetbridge.util.UriHelper
import org.slf4j.LoggerFactory
import java.io.IOException

class GarminPrgFile private constructor(
    val uri: Uri,
    val name: String,
    val size: Long,
) {
    @Throws(IOException::class)
    fun readBytes(context: Context): ByteArray = UriHelper.get(uri, context).openInputStream().use {
        FileUtils.readAll(it, MAX_SIZE)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GarminPrgFile::class.java)

        // This does not match the known 4-byte headers for older files
        private val MAGIC = byteArrayOf(0xd0.toByte(), 0x00, 0xd0.toByte())

        private const val MAX_SIZE = 10L * 1024 * 1024

        /**
         * Returns the PRG file at the uri, or null if it is not a PRG file.
         */
        @JvmStatic
        fun open(context: Context, uri: Uri): GarminPrgFile? {
            val uriHelper = try {
                UriHelper.get(uri, context)
            } catch (e: IOException) {
                LOG.error("Failed to get uri helper for {}", uri, e)
                return null
            }

            val magic = ByteArray(MAGIC.size)
            try {
                uriHelper.openInputStream().use { input ->
                    if (input.read(magic) != magic.size) {
                        LOG.error("Failed to read magic from {}", uri)
                        return null
                    }
                }
            } catch (e: IOException) {
                LOG.error("Failed to read bytes from {}", uri, e)
                return null
            }
            if (!magic.contentEquals(MAGIC)) {
                return null
            }

            if (uriHelper.fileSize > MAX_SIZE) {
                LOG.warn("{} is larger than the maximum expected file size of {}", uri, MAX_SIZE)
                return null
            }

            return GarminPrgFile(uri, uriHelper.fileName, uriHelper.fileSize)
        }
    }
}
