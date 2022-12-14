package coala.ai.interfaces

public interface TextToSpeechCallback {
    fun onStart()
    fun onCompleted()
    fun onError()
}