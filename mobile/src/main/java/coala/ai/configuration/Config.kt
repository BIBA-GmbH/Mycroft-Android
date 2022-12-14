package coala.ai.configuration

/**
 * This configuration holds the keycloak configuration information.
 *
 * @author Gina Chatzimarkaki
 * @version  1.0
 */
object Config {

    // Add connection details of the KeyCloak server.
    private const val realmName = ""
    private const val hostname = ""
    const val clientId = ""


    const val baseUrl = "https://$hostname/coala/uaa/realms/$realmName/protocol/openid-connect"

    const val authenticationCodeUrl = "$baseUrl/auth"
    const val redirectUri = "mycroft://oauthresponse"
}