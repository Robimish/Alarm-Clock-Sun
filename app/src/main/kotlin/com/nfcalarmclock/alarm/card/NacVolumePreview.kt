package com.nfcalarmclock.alarm.card

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.util.UnstableApi
import com.nfcalarmclock.alarm.db.NacAlarm
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.system.getDeviceProtectedStorageContext
import com.nfcalarmclock.system.media.NacAudioAttributes
import com.nfcalarmclock.system.media.getSafeStreamVolume
import com.nfcalarmclock.system.media.setStreamVolume
import com.nfcalarmclock.system.media.toPlayerGain
import com.nfcalarmclock.system.media.toStreamVolume
import com.nfcalarmclock.system.mediaplayer.NacMediaPlayer
import com.nfcalarmclock.view.quickToast
import com.nfcalarmclock.R

/**
 * Plays the media of an alarm while its volume slider is being held, so that the
 * level can be judged by ear instead of by the number.
 *
 * The volume of the stream is changed exactly as it would be when the alarm goes
 * off, and put back the way it was as soon as the preview stops. Nothing is left
 * behind: [stop] is safe to call at any time, as many times as needed.
 */
@UnstableApi
class NacVolumePreview(context: Context)
{

	/**
	 * Context used to build the player.
	 */
	private val playerContext: Context = getDeviceProtectedStorageContext(context)

	/**
	 * Context used for everything else.
	 */
	private val appContext: Context = context.applicationContext

	/**
	 * Audio manager.
	 */
	private val audioManager: AudioManager =
		appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

	/**
	 * Media player, only alive while the preview is running.
	 */
	private var mediaPlayer: NacMediaPlayer? = null

	/**
	 * Stream the preview plays on.
	 */
	private var stream: Int = AudioManager.USE_DEFAULT_STREAM_TYPE

	/**
	 * Volume of the stream before the preview started, so it can be put back.
	 */
	private var previousStreamVolume: Int = -1

	/**
	 * Id of the alarm being previewed.
	 */
	var alarmId: Long = 0
		private set

	/**
	 * Whether a preview is running or not.
	 */
	val isRunning: Boolean
		get() = (mediaPlayer != null)

	/**
	 * Start playing the media of an alarm.
	 */
	fun start(alarm: NacAlarm)
	{
		// Stop whatever was playing before
		stop()

		// There is nothing to play
		if (alarm.mediaPath.isEmpty())
		{
			quickToast(appContext, R.string.error_message_unable_to_preview_volume_options)
			return
		}

		try
		{
			val attributes = NacAudioAttributes(appContext, alarm)
			val player = NacMediaPlayer(playerContext, listener = null,
				audioAttributes = attributes, shouldGainTransientAudioFocus = true)

			mediaPlayer = player
			alarmId = alarm.id
			stream = attributes.stream

			// Remember the volume of the stream, to put it back afterwards
			previousStreamVolume = audioManager.getSafeStreamVolume(stream)

			// Play at the volume of the alarm
			setVolume(alarm)
			player.playAlarm(appContext, alarm)
		}
		catch (e: Exception)
		{
			NacLog.e("Unable to preview the volume", throwable = e)
			stop()
		}
	}

	/**
	 * Follow the slider while it is being moved.
	 */
	fun setVolume(alarm: NacAlarm)
	{
		val player = mediaPlayer ?: return

		try
		{
			audioManager.setStreamVolume(stream, alarm.toStreamVolume(audioManager, stream))
			player.exoPlayer.volume = alarm.toPlayerGain(audioManager, stream)
		}
		catch (e: Exception)
		{
			NacLog.e("Unable to set the volume of the preview", throwable = e)
		}
	}

	/**
	 * Stop the preview and give the stream volume back.
	 */
	fun stop()
	{
		val player = mediaPlayer

		mediaPlayer = null
		alarmId = 0

		// Put the volume of the stream back the way it was
		if (previousStreamVolume >= 0)
		{
			try
			{
				audioManager.setStreamVolume(stream, previousStreamVolume)
			}
			catch (e: Exception)
			{
				NacLog.e("Unable to give the stream volume back", throwable = e)
			}

			previousStreamVolume = -1
		}

		// Let the player go
		if (player != null)
		{
			try
			{
				player.stop()
				player.release()
			}
			catch (e: Exception)
			{
				NacLog.e("Unable to release the preview player", throwable = e)
			}
		}
	}

}
