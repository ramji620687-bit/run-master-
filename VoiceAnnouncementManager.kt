package com.runmaster.app.domain

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Hindi / English spoken announcements. Falls back to English if the phone has no Hindi voice installed. */
class VoiceAnnouncementManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var wantHindi = false
    private var hindiAvailable = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            applyLanguage()
        }
    }

    fun setLanguage(languageCode: String) {
        wantHindi = languageCode == "hi"
        if (ready) applyLanguage()
    }

    private fun applyLanguage() {
        val t = tts ?: return
        if (wantHindi) {
            val r = t.setLanguage(Locale("hi", "IN"))
            hindiAvailable = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            if (!hindiAvailable) t.setLanguage(Locale.US)
        } else {
            hindiAvailable = false
            t.setLanguage(Locale.US)
        }
    }

    private fun hindi() = wantHindi && hindiAvailable

    fun announceKm(km: Int, elapsedSeconds: Long) {
        val min = elapsedSeconds / 60
        val sec = elapsedSeconds % 60
        val text = if (hindi()) {
            "$km किलोमीटर पूरे हुए। समय $min मिनट $sec सेकंड।"
        } else {
            "$km kilometers completed. Time $min minutes $sec seconds."
        }
        speak(text, "KM_ANNOUNCE")
    }

    fun announceLap(lapNumber: Int, durationSeconds: Long) {
        val text = if (hindi()) {
            "लैप $lapNumber पूरा हुआ। समय $durationSeconds सेकंड।"
        } else {
            "Lap $lapNumber completed. Time $durationSeconds seconds."
        }
        speak(text, "LAP_ANNOUNCE")
    }

    fun announceState(started: Boolean) {
        val text = if (hindi()) {
            if (started) "दौड़ शुरू हुई।" else "दौड़ पूरी हुई।"
        } else {
            if (started) "Workout started." else "Workout finished."
        }
        speak(text, "STATE_ANNOUNCE")
    }

    private fun speak(text: String, id: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
