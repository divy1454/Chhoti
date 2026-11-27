package com.divy.chhoti

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager

/**
 * Makes a Chhoti call sound like a phone call: voice-call audio mode, earpiece by default (or a headset / Bluetooth
 * if one is connected), loudspeaker on request, and the screen switching off when the phone is at your ear.
 */
class CallAudio(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val pm = context.getSystemService(PowerManager::class.java)
    private var focus: AudioFocusRequest? = null
    private var proximity: PowerManager.WakeLock? = null
    private var active = false
    /** Some phones' speech recognizers can't record in call mode; then call mode is paused while listening. */
    var normalWhileListening = false

    var speakerOn = false
        private set

    fun start() {
        if (active) return
        active = true
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build()
            .also { am.requestAudioFocus(it) }
        callMode()
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "chhoti:call")
        }
        route()
    }

    /** Voice-call mode (earpiece routing, echo cancellation). */
    fun callMode() {
        if (active) am.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun beforeListening() {
        if (active && normalWhileListening) am.mode = AudioManager.MODE_NORMAL
    }

    fun setSpeaker(on: Boolean) {
        speakerOn = on
        route()
    }

    private fun route() {
        if (!active) return
        if (Build.VERSION.SDK_INT >= 31) {
            val devices = am.availableCommunicationDevices
            val headset = devices.firstOrNull {
                it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET)
            }
            val wanted = when {
                speakerOn -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                headset != null -> headset
                else -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            }
            if (wanted != null) am.setCommunicationDevice(wanted)
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = speakerOn
        }
        // Screen off at the ear only when the sound comes from the earpiece.
        proximity?.let { wl ->
            if (!speakerOn && !wl.isHeld) wl.acquire(60 * 60 * 1000L)
            if (speakerOn && wl.isHeld) wl.release()
        }
    }

    fun stop() {
        if (!active) return
        active = false
        proximity?.let { if (it.isHeld) it.release() }
        proximity = null
        if (Build.VERSION.SDK_INT >= 31) {
            am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
        }
        am.mode = AudioManager.MODE_NORMAL
        focus?.let { am.abandonAudioFocusRequest(it) }
        focus = null
    }
}
