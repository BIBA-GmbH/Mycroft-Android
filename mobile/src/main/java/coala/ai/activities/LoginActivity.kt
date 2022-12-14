package coala.ai.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Toast
import coala.ai.R
import coala.ai.configuration.Config.authenticationCodeUrl
import coala.ai.configuration.Config.clientId
import coala.ai.configuration.Config.redirectUri
import coala.ai.di.IKeycloakRest
import coala.ai.di.KeycloakToken
import coala.ai.helper.Helper.parseJwtToken
import coala.ai.helper.SharedPreferenceManager
import coala.ai.storage.IOAuth2AccessTokenStorage
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_login.*
import org.koin.android.ext.android.inject
import java.util.*

/**
 * The LoginActivity is created so it can present the splash screen of the app while opening an
 * showing the login (with keycloak) button.
 *
 * @see RxAppCompatActivity
 *
 * @author Gina Chatzimarkaki
 * @version  1.0
 */
class LoginActivity : RxAppCompatActivity() {

    /* Contains the keycloak rest endpoints */
    private val api by inject<IKeycloakRest>()
    /* The keycloak storage */
    private val storage by inject<IOAuth2AccessTokenStorage>()
    /* App Settings - Preferences*/
    private var sharedPreferenceManager: SharedPreferenceManager? = null

    /* The auth code url for keycloak*/
    private val authCodeUrl = Uri.parse(authenticationCodeUrl)
        .buildUpon()
        .appendQueryParameter("client_id", clientId)
        .appendQueryParameter("redirect_uri", redirectUri)
        .appendQueryParameter("response_type", "code")
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferenceManager = SharedPreferenceManager.getInstance(this)
        this.supportActionBar?.hide()
        //set content view AFTER ABOVE sequence (to avoid crash)
        this.setContentView(R.layout.activity_login)

        //authenticate user
        authenticate(intent.data)
        initViews()
        login_button.setOnClickListener {
            hideLoginButton()
            startActivity(Intent(Intent.ACTION_VIEW, authCodeUrl))
        }
    }

    /**
     * Initialise view and activate their visibility.
     * @version  1.0
     */
    private fun initViews() {
        login_text.visibility = View.GONE
        login_button.visibility = View.VISIBLE
    }

    /**
     * When user pressed login button, hide button to avoid re-pressing.
     * @version  1.0
     */
    private fun hideLoginButton() {
        Handler().postDelayed({
            login_text.visibility = View.VISIBLE
            login_button.visibility = View.GONE
        }, 1000L)
    }

    /**
     * When user authenticated hide all views.
     * @version  1.0
     */
    private fun hideAll() {
        login_text.visibility = View.GONE
        login_button.visibility = View.GONE
    }

    /**
     * When new intent received, call authentication func.
     * @version  1.0
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        authenticate(intent.data)
    }

    override fun onBackPressed() {}

    /**
     * Authenticate user by redirecting to the redirectURI,
     * waiting for token to be received.
     * @version  1.0
     */
    @SuppressLint("CheckResult")
    private fun authenticate(uri: Uri?) {
        if (uri != null && uri.toString().startsWith(redirectUri)) {
            val code = uri.getQueryParameter("code")
            System.out.println(code)
            hideAll()
            if (code != null) {
                exchangeCodeForToken(code)
            }
        }
    }

    /**
     * Waits for the new access token to be received for keycloak.
     * @version  1.0
     */
    @SuppressLint("CheckResult")
    private fun exchangeCodeForToken(code: String) {
        api.grantNewAccessToken(code, clientId, redirectUri)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(handleSuccess(), handleError())
    }

    /**
     * Function that takes action when user authenticated successfully.
     * @version  1.0
     */
    private fun handleSuccess(): Consumer<KeycloakToken> {
        return Consumer { token ->
            val expirationDate = Calendar.getInstance().clone() as Calendar
            val refreshExpirationDate = Calendar.getInstance().clone() as Calendar
            expirationDate.add(Calendar.SECOND, token.expiresIn!!)
            refreshExpirationDate.add(Calendar.SECOND, token.refreshExpiresIn!!)
            token.tokenExpirationDate = expirationDate
            token.refreshTokenExpirationDate = refreshExpirationDate
            storage.storeAccessToken(token)
            // store username to app general preferences to access it when sending messages for session id creation
            sharedPreferenceManager?.putValue(
                "username",
                parseJwtToken(token.accessToken!!).preferred_username
            )
            val username = sharedPreferenceManager?.getString("username")

            // TODO Commented the following lines to avoid redundant variable setting. The session time is set in MainActivity.kt
            // editor.putInt("max_session_milliseconds", 120000)
            setResult(RESULT_OK)

            if(isTaskRoot){
                this@LoginActivity.startActivity(
                    Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            }
            else{
                finish()
            }

        }
    }

    /**
     * Function that takes action when user authenticated failed.
     * @version  1.0
     */
    private fun handleError(): Consumer<Throwable> {
        return Consumer {
            it.printStackTrace()
            Toast.makeText(this@LoginActivity, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}
