package coala.ai.helper

import android.content.Context
import android.os.Handler
import android.speech.tts.UtteranceProgressListener
import coala.ai.interfaces.TextToSpeechCallback
import java.lang.ref.WeakReference

 class TtsProgressListener(context: Context, mTtsCallbacks: Map<String, TextToSpeechCallback>) :
     UtteranceProgressListener() {

    private var mTtsCallbacks: MutableMap<String, TextToSpeechCallback>
    private var contextWeakReference: WeakReference<Context>? = null

     init {
         contextWeakReference = WeakReference(context)
         this.mTtsCallbacks = mTtsCallbacks as MutableMap<String, TextToSpeechCallback>
     }

    override fun onStart(p0: String?) {
        val callback = mTtsCallbacks[p0]
        val context = contextWeakReference!!.get()
        if (callback != null && context != null) {
            Handler(context.mainLooper).post {
                callback.onStart()


            }
        }
    }

    override fun onDone(p0: String?) {
        val callback = mTtsCallbacks[p0]
        val context = contextWeakReference!!.get()
        if (callback != null && context != null) {
            Handler(context.mainLooper).post {
                callback.onCompleted()
                mTtsCallbacks.remove(p0)

            }
        }
    }

    override fun onError(p0: String?) {
        val callback = mTtsCallbacks[p0]
        val context = contextWeakReference!!.get()

        if (callback != null && context != null) {
            Handler(context.mainLooper).post {
                callback.onError()
                mTtsCallbacks.remove(p0)
            }
        }
    }
}