package coala.ai.helper

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.migcomponents.migbase64.Base64
import coala.ai.di.KeycloakToken
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.text.SimpleDateFormat
import java.util.*

/**
 * The async helper.
 *
 * @author Gina Chatzimarkaki
 * @version  1.0
 */
object AsyncHelper {
    @SuppressLint("CheckResult")
    fun <T> asyncRxExecutor(heavyFunction: () -> T, response : (response : T?) -> Unit) {
        val observable = Single.create<T> { e ->
            e.onSuccess(heavyFunction())
        }
        observable.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { t: T? ->
                response(t)
            }
    }

    inline fun uiThreadExecutor(crossinline block: () -> Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post{
            block()
        }
    }
}

/**
 * The keycloak helper.
 *
 * @author Gina Chatzimarkaki
 * @version  1.0
 */
object Helper {
    /**
     * Check if keycloak token is expired
     *
     * @property token the keycloak token to check if expired
     * @return true if token is expired or false if it is active.
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    fun isTokenExpired(token: KeycloakToken?): Boolean {
        token?.apply {
            if (tokenExpirationDate == null) return true
            return Calendar.getInstance().after(tokenExpirationDate)
        }
        return true
    }

    /**
     * Check if refresh keycloak token is expired
     *
     * @property token the keycloak token to check if expired
     * @return true if token is expired or false if it is active.
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    fun isRefreshTokenExpired(token: KeycloakToken?): Boolean {
        token?.apply {
            if (refreshTokenExpirationDate == null) return true
            return Calendar.getInstance().after(refreshTokenExpirationDate)
        }
        return true
    }

    /**
     * Format Date as String
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    @SuppressLint("SimpleDateFormat")
    fun Calendar.formatDate(): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
        return formatter.format(this.time)
    }

    /**
     * Parse Jwt token as {@link Principal}.
     *
     * @property token the keycloak token to check if expired
     * @return true if token is expired or false if it is active.
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     * @see Principal
     */
    fun parseJwtToken(jwtToken: String?): Principal {
        jwtToken ?: return Principal()
        jwtToken.apply {
            val splitString = split(".")
            val base64EncodedBody = splitString[1]

            val body = String(Base64.decodeFast(base64EncodedBody))
            val jsonBody = Gson().fromJson(body, JsonObject::class.java)
            val userId = jsonBody.get("sub")?.asString
            val email = jsonBody.get("email")?.asString ?: "n/a"
            val name = jsonBody.get("given_name")?.asString ?: "n/a"
            val surname = jsonBody.get("family_name")?.asString ?: "n/a"
            val preferred_username = jsonBody.get("preferred_username")?.asString ?: "n/a"
            val roles = jsonBody.get("realm_access")?.asJsonObject?.getAsJsonArray("roles")?.map {it.asString} ?: emptyList()

            return Principal(userId, email, name, surname, roles, preferred_username)
        }
    }
}

/**
 * A principal class that contains the token info.
 *
 * @author Gina Chatzimarkaki
 * @version  1.0
 */
data class Principal(
    val userId: String? = null,
    val email: String? = null,
    val name: String? = null,
    val surname: String? = null,
    val roles: List<String> = emptyList(),
    val preferred_username: String? = null
)