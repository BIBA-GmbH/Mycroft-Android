package coala.ai.token

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import androidx.work.*
import androidx.work.ExistingPeriodicWorkPolicy.REPLACE
import coala.ai.Config
import coala.ai.di.IKeycloakRest
import coala.ai.di.KeycloakToken
import coala.ai.helper.Helper.formatDate
import coala.ai.storage.IOAuth2AccessTokenStorage
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import java.util.*
import java.util.concurrent.TimeUnit.MINUTES

/**
 * The refresh token worker.
 * @property context
 * @property params the worker parameters
 *
 * @return the worker and the koinComponent
 *
 * @author Gina Chatzimarkaki
 * @version  1.0
 */
class RefreshTokenWorker(context: Context, params: WorkerParameters): Worker(context, params), KoinComponent {

    companion object {
        fun startPeriodicRefreshTokenTask() {
            val workConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWork = PeriodicWorkRequest.Builder(RefreshTokenWorker::class.java, 15, MINUTES)
                .setConstraints(workConstraints)
                .build()

            WorkManager.getInstance().enqueueUniquePeriodicWork("kc-refresh-token-work", REPLACE, periodicWork)
        }

        const val channelId = "keycloaker_channel_id"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "keycloaker notification channel", NotificationManager.IMPORTANCE_DEFAULT)
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /* Contains the keycloak rest endpoints */
    private val api: IKeycloakRest by inject()

    /* The keycloak storage */
    private val storage: IOAuth2AccessTokenStorage by inject()

    /**
     * The refresh token handler.
     *
     * @return the result of the authentication attempt.
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    override fun doWork(): Result {
        val notificationId = System.currentTimeMillis().toInt()
        triggerTokenNotification(notificationId)
        if (storage.getStoredAccessToken() == null || storage.getStoredAccessToken()!!.refreshToken == null)
            return Result.failure()

        return try {
            val token = saveTokenToStorage(api.refreshAccessToken(storage.getStoredAccessToken()!!.refreshToken!!, Config.clientId).blockingFirst())
            showOk(System.currentTimeMillis().toInt(), "Token is valid until: ${token.tokenExpirationDate!!.formatDate()}")
            showOk(notificationId, "Refresh token is valid until: ${token.refreshTokenExpirationDate!!.formatDate()}")
            Result.success()
        } catch (e: Exception) {
            showError(notificationId)
            Result.retry()
        }
    }

    /**
     * Send notification for using refresh token to authenticate with keycloak.
     *
     * @property id the id of the notification
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    private fun triggerTokenNotification(id: Int) {
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.notification_icon_background)
            .setContentTitle("Coala")
            .setContentText("Updating token using refresh token...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(Color.BLUE)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(id, builder.build())
        }
    }

    /**
     * Send notification for successful authentication with keycloak.
     *
     * @property id the id of the notification
     * @property message the message to be presented in the notification
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    private fun showOk(id: Int, message: String) {
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.notification_icon_background)
            .setContentTitle("Coala")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(Color.GREEN)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(id, builder.build())
        }
    }

    /**
     * Send notification for failed authentication with keycloak.
     *
     * @property id the id of the notification
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    private fun showError(id: Int) {
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.notification_icon_background)
            .setContentTitle("Coala")
            .setContentText("Token refresh failed :(")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(Color.RED)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(id, builder.build())
        }
    }

    /**
     * Save the access token to storage with defined expiration and refresh
     * expiration date.
     *
     * @property token the token to be save to storage.
     * @return the keycloak token
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    private fun saveTokenToStorage(token: KeycloakToken): KeycloakToken {
        val expirationDate = Calendar.getInstance().clone() as Calendar
        val refreshExpirationDate = Calendar.getInstance().clone() as Calendar
        expirationDate.add(Calendar.SECOND, token.expiresIn!!)
        refreshExpirationDate.add(Calendar.SECOND, token.refreshExpiresIn!!)
        token.tokenExpirationDate = expirationDate
        token.refreshTokenExpirationDate = refreshExpirationDate
        storage.storeAccessToken(token)
        return token
    }
}