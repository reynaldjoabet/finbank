package migrantbank.config

import zio.*
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.TypesafeConfigProvider

object ConfigLoader {

  // Automatically derives the configuration structure from your case classes
  // Note: It maps camelCase Scala fields to camelCase HOCON keys by default.
  private val configDescriptor: Config[AppConfig] =
    deriveConfig[AppConfig].nested("app")

  private val configProvider: ConfigProvider =
    TypesafeConfigProvider.fromResourcePath()

  // Provides the AppConfig as a ZLayer
  val layer: ZLayer[Any, Config.Error, AppConfig] =
    ZLayer.fromZIO {
      ZIO.config(configDescriptor).withConfigProvider(configProvider)
    }

  // If you still want a Task for a quick manual load:
  def load: IO[Config.Error, AppConfig] =
    ZIO.config(configDescriptor).withConfigProvider(configProvider)

  val secretKeyLayer: ZLayer[Any, Config.Error, String] = ZLayer.fromZIO(
    ZIO.config(Config.string("FLW_SECRET_KEY").withDefault("your_test_key"))
  )
}
