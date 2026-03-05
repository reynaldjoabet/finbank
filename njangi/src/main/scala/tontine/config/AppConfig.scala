package tontine.config
import zio.config.magnolia.*
final case class AppConfig(
    server: ServerConfig,
    mobileMoney: MobileMoneyConfig,
    openBanking: OpenBankingConfig
) //derives DeriveConfigDescriptor

final case class ServerConfig(port: Int)

final case class MobileMoneyConfig(
    apiBaseUrl: String,
    apiKey: String
)

final case class OpenBankingConfig(
    apiBaseUrl: String,
    clientId: String,
    clientSecret: String
)
// add other configs
final case class PostgresConfig(
    url: String,
    user: String,
    password: String
)

final case class OktaSSOConfig(
    clientId: String,
    clientSecret: String,
    authorizationServerURL: String,
    scopes: List[String]
)

final case class OidcClientConfig(
    callbackUrl: String,
    clientAuthenticationMethod: String,
    customParams: Map[String, Object],
    disablePkce: Boolean,
    discoveryUri: String,
    id: String,
    maxAge: String,
    maxClockSkew: String,
    preferredJwsAlgorithm: String,
    prompt: String,
    responseType: String,
    scope: String,
    secret: String,
    serverUrl: String,
    sessionExpiry: Int,
    tenant: String,
    tokenValidity: Integer,
    `type`: String,
    useNonce: String
)

final case class LdapConfig(
    host: String,
    port: Int,
    useSsl: Boolean,
    bindDn: String,
    bindPassword: String,
    userBaseDn: String,
    userSearchFilter: String,
    groupBaseDn: String,
    groupSearchFilter: String,
    trustStorePath: Option[String],
    trustStorePassword: Option[String],
    trustStoreType: Option[String],
    trustStoreConfigType: Option[String],
    trustStoreConfig: Option[TruststoreConfig]
)

final case class TruststoreConfig()
