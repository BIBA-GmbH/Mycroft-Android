package coala.ai.storage

import android.content.SharedPreferences
import com.google.gson.Gson
import coala.ai.di.KeycloakToken

/**
 * Interface for the Keycloak Access Token Storage
 *
 * @author Gina Chatzimarkaki
 * @version  1.0
 */
interface IOAuth2AccessTokenStorage {
    fun getStoredAccessToken(): KeycloakToken?
    fun storeAccessToken(token: KeycloakToken)
    fun hasAccessToken(): Boolean
    fun removeAccessToken()
}

/**
 * The Shared Preferences for the Keycloak Storage.
 * @author Gina Chatzimarkaki
 * @version  1.0
 *
 * @property prefs the share preferences
 * @property gson the Gson instance that holds the keycloak token
 */
class SharedPreferencesOAuth2Storage(val prefs: SharedPreferences, val gson: Gson) : IOAuth2AccessTokenStorage {
    val ACCESS_TOKEN_PREFERENCES_KEY = "OAuth2AccessToken"

    /**
     * Get stored access token.
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    override fun getStoredAccessToken(): KeycloakToken? {
        val tokenStr = prefs.getString(ACCESS_TOKEN_PREFERENCES_KEY, null)
        return if (tokenStr == null) null
        else gson.fromJson(tokenStr, KeycloakToken::class.java)
    }

    /**
     * Store access token.
     *
     * @property token the accress token to be stored
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    override fun storeAccessToken(token: KeycloakToken) {
        prefs.edit()
            .putString(ACCESS_TOKEN_PREFERENCES_KEY, gson.toJson(token))
            .apply()
    }

    /**
     * Checks if access token exists.
     *
     * @return  if the access token exists return true otherwise returns false.
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    override fun hasAccessToken(): Boolean {
        return prefs.contains(ACCESS_TOKEN_PREFERENCES_KEY)
    }

    /**
     * Remove access token from preferences.
     *
     * @author Gina Chatzimarkaki
     * @version  1.0
     */
    override fun removeAccessToken() {
        prefs.edit()
            .remove(ACCESS_TOKEN_PREFERENCES_KEY)
            .apply()
    }
}
