/*
 *  Copyright (c) 2017. Mycroft AI, Inc.
 *
 *  This file is part of Mycroft-Android a client for Mycroft Core.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package coala.ai.helper



import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import coala.ai.interfaces.TextToSpeechCallback
import java.util.*




/**
 * TTSManager is a wrapper around the Android System's Text-To-Speech ('TTS')
 * API.
 *
 *
 * All constructors in this class require a context reference.
 * Make sure to clean up with [.shutDown] when the context's
 * [Activity.onDestroy] or [Service.onDestroy] method is called.
 *
 *
 * @see TextToSpeech
 *
 *
 * @author Paul Scott
 */

class TTSManager  {

    /**
     * Backing TTS for this instance. Should not (ever) be null.
     */
    private lateinit var mTts: TextToSpeech
    /**
     * Whether the TTS is available for use (i.e. loaded into memory)
     */
    private var isLoaded = false

    /**
     * External listener for error and success events. May be null.
     */
    private var mTTSListener: TTSListener? = null

    private lateinit var _context : Context
    private val mAudioStream = TextToSpeech.Engine.DEFAULT_STREAM

    private var result = 0

    private var mTtsProgressListener: UtteranceProgressListener? = null
    private var mTtsCallbacks: MutableMap<String, TextToSpeechCallback> = HashMap<String, TextToSpeechCallback>()

    var onInitListener: TextToSpeech.OnInitListener = TextToSpeech.OnInitListener { status ->
        if (status == TextToSpeech.SUCCESS) {
            mTtsProgressListener = TtsProgressListener(_context, mTtsCallbacks)
            val lang = Resources.getSystem().configuration.locales.get(0).language
            result = when {
                lang!!.contentEquals("en") -> {
                    mTts.setLanguage(Locale.ENGLISH)
                }
                lang.contentEquals("nl") -> {
                    mTts.setLanguage(Locale("de_nl"))
                }
                lang.contentEquals("it") -> {
                    mTts.setLanguage(Locale.ITALIAN)
                }
                lang.contentEquals("de") -> {
                    mTts.setLanguage(Locale.GERMAN)
                }
                else -> {
                    mTts.setLanguage(Locale.US)
                }
            }


            isLoaded = true
            Log.i(TAG, "TTS initialized")
            mTts.setOnUtteranceProgressListener(mTtsProgressListener)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                logError("This Language is not supported")
            }
        } else {
            logError("Initialization Failed!")
        }


    }




    /**
     * Create a new TTSManager attached to the given context.
     *
     * @param context any non-null context.
     */
    constructor(context: Context) {
        _context = context
        mTts = TextToSpeech(context, onInitListener)

    }

    /**
     * Special overload of constructor for testing purposes.
     *
     * @param textToSpeech the internal TTS this object will manage
     */
    constructor(textToSpeech: TextToSpeech) {
        mTts = textToSpeech
    }

    fun setTTSListener(mTTSListener: TTSListener) {
        this.mTTSListener = mTTSListener
    }

    /**
     * Wrapper for [TextToSpeech.shutdown]
     */
    fun shutDown() {
        mTtsCallbacks.clear()
        mTts.stop()
        mTts.shutdown()
    }

    fun stopSpeaking() {
        mTts.speak("", TextToSpeech.QUEUE_FLUSH, null, "")
        mTts.stop()
    }

    fun addQueue(text: String, callback: TextToSpeechCallback?) {
        val utteranceId = UUID.randomUUID().toString()
        if (callback != null) {
            mTtsCallbacks[utteranceId] = callback
        }
        if (isLoaded){
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, mAudioStream.toString())
            mTts.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)}
        else {
            logError("TTS Not Initialized")
        }
    }

    fun addQueue(text: String) {
        if (isLoaded)
            mTts.speak(text, TextToSpeech.QUEUE_ADD, null,"")
        else {
            logError("TTS Not Initialized")
        }
    }


    fun initQueue(text: String) {

        if (isLoaded)
            mTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        else
            logError("TTS Not Initialized")
    }

    /**
     * Wrapper around [Log.e] that also notifies
     * the [.setTTSListener], if present.
     *
     * @param msg any non-null message
     */
    private fun logError(msg: String) {
        if (mTTSListener != null) {
            mTTSListener!!.onError(msg)
        }
        Log.e(TAG, msg)
    }

    fun isTTSSpeaking(): Boolean {
      //  Log.i(TAG, mTts.isSpeaking.toString())
       return mTts.isSpeaking
    }




    interface TTSListener {
        fun onError(message: String)
    }

    companion object {

        private const val TAG = "TTSManager"
    }


}
