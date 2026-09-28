package com.nfcalarmclock.settings

import android.content.Context
import androidx.core.net.toUri
import com.nfcalarmclock.alarm.db.NacAlarm
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.system.getDeviceProtectedStorageContext
import java.io.File

/**
 * Throw away the copies of sounds and images that the app is holding on to and
 * that no alarm needs any more.
 *
 * Both are kept on purpose: a sound or an image chosen elsewhere can be moved or
 * deleted by the user at any time, and the alarm would then go quiet or dark. The
 * copies are what makes an alarm reliable. They do, however, pile up, which is
 * what this is for.
 */
object NacStorageCleanup
{

	/**
	 * What a clean up freed.
	 */
	data class Result(
		val files: Int = 0,
		val bytes: Long = 0,
		val alarmsToClear: List<NacAlarm> = emptyList()
	)

	/**
	 * Name of the folder that holds the copies of the images.
	 */
	private const val IMAGE_FOLDER = "dawn"

	/**
	 * Delete the copies of the images that no alarm is showing.
	 *
	 * An image is kept as long as its alarm is actually using it. Turning the image
	 * off for an alarm, or picking a color instead, leaves the copy behind on
	 * purpose, so that it can be turned back on without picking the file again.
	 * This is what throws those away.
	 */
	fun cleanImages(context: Context, alarms: List<NacAlarm>): Result
	{
		val dir = File(context.filesDir, IMAGE_FOLDER)

		// Nothing was ever copied
		if (!dir.isDirectory)
		{
			return Result()
		}

		// Paths that are in use right now
		val inUse = alarms
			.filter { it.shouldUseDawnImage && it.dawnImagePath.isNotEmpty() }
			.map { it.dawnImagePath }
			.toSet()

		var files = 0
		var bytes = 0L

		dir.listFiles()?.forEach { file ->

			// Leave directories alone
			if (!file.isFile)
			{
				return@forEach
			}

			// This one is on screen during a dawn
			if (inUse.contains(file.toUri().toString()))
			{
				return@forEach
			}

			val size = file.length()

			if (file.delete())
			{
				files += 1
				bytes += size
			}
			else
			{
				NacLog.w("Unable to delete the dawn image ${file.name}")
			}

		}

		// The alarms that were pointing at a copy that is now gone
		val gone = alarms.filter {
			it.dawnImagePath.isNotEmpty() && !inUse.contains(it.dawnImagePath)
		}

		return Result(files, bytes, gone)
	}

	/**
	 * Delete the copies of the sounds that no alarm is playing.
	 *
	 * The app copies the chosen sound into its own storage so that an alarm still
	 * rings when the original has been moved, deleted, or is out of reach because
	 * the phone has rebooted and not been unlocked yet.
	 */
	fun cleanSounds(context: Context, alarms: List<NacAlarm>): Result
	{
		val deviceContext = getDeviceProtectedStorageContext(context)
		val dir = deviceContext.filesDir
		val shared = NacSharedPreferences(context)

		// Nothing to look at
		if ((dir == null) || !dir.isDirectory)
		{
			return Result()
		}

		// Paths that are in use right now, plus the one of the default alarm
		val inUse = alarms
			.map { it.localMediaPath }
			.plus(shared.localMediaPath)
			.filter { it.isNotEmpty() }
			.toSet()

		var files = 0
		var bytes = 0L

		dir.listFiles()?.forEach { file ->

			// Leave directories alone. In particular the folder of the dawn images,
			// which lands here on a phone without direct boot
			if (!file.isFile)
			{
				return@forEach
			}

			// This one is what an alarm plays
			if (inUse.contains(file.path))
			{
				return@forEach
			}

			val size = file.length()

			if (file.delete())
			{
				files += 1
				bytes += size
			}
			else
			{
				NacLog.w("Unable to delete the sound ${file.name}")
			}

		}

		return Result(files, bytes)
	}

	/**
	 * Show a size in a way that means something, without needing a unit from the
	 * caller.
	 */
	fun readableSize(bytes: Long): String
	{
		return when
		{
			bytes >= 1024L*1024L -> "%.1f MB".format(bytes / (1024.0*1024.0))
			bytes >= 1024L       -> "%d kB".format(bytes / 1024L)
			else                 -> "$bytes B"
		}
	}

}
