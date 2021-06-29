package coala.ai

/**
 * This configuration holds the keycloak configuration information.
 *
 * @author Gina Chatzimarkaki
 * @version  1.0
 */
object Config {
    const val clientId = "test-coala"
    const val baseUrl = "https://coala-keycloak.euprojects.net/auth/realms/coala/protocol/openid-connect"

    const val authenticationCodeUrl = "$baseUrl/auth"
    const val redirectUri = "mycroft://oauthresponse"
}