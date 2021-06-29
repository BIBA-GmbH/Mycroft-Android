package coala.ai

import android.app.Application
import coala.ai.di.apiModule
import coala.ai.di.sharedPrefsModule
import org.koin.android.ext.android.startKoin

/**
 * The Coala class starts the Android koin.
 *
 * @author Gina Chatzimarkaki
 * @version  1.0
 */
class Coala : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin(this, listOf(apiModule, sharedPrefsModule))
    }
}