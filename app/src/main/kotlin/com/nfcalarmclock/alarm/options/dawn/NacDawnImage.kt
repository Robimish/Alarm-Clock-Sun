package com.nfcalarmclock.alarm.options.dawn

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.nfcalarmclock.log.NacLog
import java.io.File

/**
 * The image shown by the dawn instead of a color.
 *
 * The image is copied into the private storage of the app rather than kept as a
 * link to the file the user picked. A link relies on a permission that is easily
 * lost, and the image would then silently stop appearing. A copy cannot be lost.
 */
object NacDawnImage
{

	/**
	 * Name of the folder that holds the copies.
	 */
	private const val FOLDER = "dawn"

	/**
	 * Copy an image into the private storage of the app.
	 *
	 * Only one image is kept per alarm, so that copies do not pile up.
	 *
	 * @return The path of the copy, or null when the image could not be read.
	 */
	fun copyIntoApp(context: Context, uri: Uri, alarmId: Long): String?
	{
		try
		{
			val name = queryDisplayName(context, uri) ?: "image"
			val dir = File(context.filesDir, FOLDER)

			dir.mkdirs()

			// Throw away whatever image this alarm had before
			dir.listFiles()?.forEach {
				if (it.name.startsWith(prefix(alarmId)))
				{
					it.delete()
				}
			}

			// Copy the image
			val file = File(dir, prefix(alarmId) + name)

			context.contentResolver.openInputStream(uri).use { input ->

				// Nothing to read
				if (input == null)
				{
					return null
				}

				file.outputStream().use { output -> input.copyTo(output) }

			}

			return file.toUri().toString()
		}
		catch (e: Exception)
		{
			NacLog.e("Unable to copy the dawn image", throwable = e)

			return null
		}
	}

	/**
	 * Get the name to show for an image.
	 */
	fun displayName(context: Context, path: String, alarmId: Long): String?
	{
		// No image
		if (path.isEmpty())
		{
			return null
		}

		val uri = path.toUri()

		// The image is a copy held by the app. Its name carries the original one,
		// behind the prefix that ties it to this alarm
		if (uri.scheme == "file")
		{
			return (uri.lastPathSegment ?: path).removePrefix(prefix(alarmId))
		}

		// Older alarms still point at a file chosen elsewhere
		return queryDisplayName(context, uri) ?: uri.lastPathSegment ?: path
	}

	/**
	 * Ask the content resolver for the name of a file.
	 */
	private fun queryDisplayName(context: Context, uri: Uri): String?
	{
		try
		{
			context.contentResolver.query(uri, null, null, null, null)
				?.use { cursor ->

					val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

					if ((index >= 0) && cursor.moveToFirst())
					{
						return cursor.getString(index)
					}

				}
		}
		catch (_: Exception)
		{
			// Unable to query the image
		}

		return null
	}

	/**
	 * Prefix that ties a copy to an alarm.
	 */
	private fun prefix(alarmId: Long): String = "alarm_" + alarmId + "_"

}
