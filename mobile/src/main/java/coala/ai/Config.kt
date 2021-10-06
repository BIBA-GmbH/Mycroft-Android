package coala.ai

/**
 * This configuration holds the keycloak configuration information.
 *
 * @author Gina Chatzimarkaki
 * @version  1.0
 */
object Config {
    const val clientId = "client-dev"
    const val baseUrl = "https://diamond-dev.ikap.biba.uni-bremen.de:30022/auth/realms/coala-dev/protocol/openid-connect"

    const val authenticationCodeUrl = "$baseUrl/auth"
    const val redirectUri = "mycroft://oauthresponse"
}