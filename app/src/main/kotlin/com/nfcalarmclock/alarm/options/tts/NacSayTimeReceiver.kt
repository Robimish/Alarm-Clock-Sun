package com.nfcalarmclock.alarm.options.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import com.nfcalarmclock.R
import com.nfcalarmclock.log.NacLog
import com.nfcalarmclock.shared.NacSharedPreferences
import com.nfcalarmclock.system.media.NacAudioAttributes
import com.nfcalarmclock.view.quickToast
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Says the time out loud.
 *
 * Everything that can ask for it goes through here: the quick settings tile and the
 * shake service both broadcast ACTION_SAY_TIME. It also stands the shake service back
 * up after a reboot and at the ends of the hours that were chosen.
 */
class NacSayTimeReceiver
	: BroadcastReceiver()
{

	/**
	 * Called when the broadcast is received.
	 */
	override fun onReceive(context: Context, intent: Intent?)
	{
		when (intent?.action)
		{

			// The button was tapped
			ACTION_SAY_TIME -> sayTheTime(context)

			// The hours for the spoken time have started or run out, so the shake
			// service stands up or down, and its notification with it
			ACTION_REFRESH_SHAKE -> NacSayTimeService.refresh(context)

			// The phone was restarted, so the shake service is gone with it
			Intent.ACTION_BOOT_COMPLETED,
			Intent.ACTION_LOCKED_BOOT_COMPLETED -> NacSayTimeService.refresh(context)

			else -> {}

		}
	}

	/**
	 * Say the time out loud.
	 */
	private fun sayTheTime(context: Context)
	{
		// Hold the broadcast open. A receiver that returns can be killed straight away,
		// and the engine needs a moment to start up and speak
		val pendingResult = goAsync()
		val handler = Handler(Looper.getMainLooper())
		val engine = arrayOfNulls<NacTextToSpeech>(1)
		val isFinished = AtomicBoolean(false)

		// Let go of the broadcast, whatever happened. Only the first call does anything
		val finish = object : Runnable
		{
			override fun run()
			{
				if (isFinished.getAndSet(true))
				{
					return
				}

				handler.removeCallbacks(this)

				try
				{
					engine[0]?.cleanup()
					engine[0] = null
					pendingResult.finish()
				}
				catch (e: Exception)
				{
					NacLog.e("Unable to finish saying the time", throwable = e)
				}
			}
		}

		// The system kills a broadcast that is held too long, so let go before that
		handler.postDelayed(finish, SPEAK_TIMEOUT_MILLIS)

		// The language that was chosen for the spoken time, which is not necessarily the
		// language the app is showing
		val shared = NacSharedPreferences(context)
		val language = shared.sayTimeLanguage
		val locale = if (language.isEmpty())
		{
			Locale.getDefault()
		}
		else
		{
			Locale.forLanguageTag(language)
		}

		// The words themselves come from the resources of that language
		val phraseContext = if (language.isEmpty())
		{
			context
		}
		else
		{
			val config = Configuration(context.resources.configuration)

			config.setLocale(locale)
			context.createConfigurationContext(config)
		}

		val speech = NacTextToSpeech(context,
			onDoneSpeaking = { handler.post(finish) },
			onErrorSpeaking = { handler.post(finish) })

		engine[0] = speech
		speech.language = locale

		// Say so rather than reading one language with the voice of another, which comes
		// out as gibberish
		speech.onMissingVoice = { missing ->
			quickToast(context, context.getString(
				R.string.error_message_no_voice_for_language,
				missing.getDisplayLanguage(missing)))
		}

		val phrase = NacTranslate.getTtsPhrase(phraseContext,
			shouldSayCurrentTime = true, shouldSayAlarmName = false, alarmName = "")

		speech.speak(phrase, NacAudioAttributes(context))
	}

	companion object
	{

		/**
		 * Action of the button that says the time.
		 */
		const val ACTION_SAY_TIME = "com.nfcalarmclock.ACTION_SAY_TIME"

		/**
		 * Action that asks the shake service to look at the clock again.
		 */
		const val ACTION_REFRESH_SHAKE = "com.nfcalarmclock.ACTION_REFRESH_SHAKE"

		/**
		 * How long the broadcast is held open at the most. [Units: ms]
		 */
		private const val SPEAK_TIMEOUT_MILLIS = 8000L

	}

}
