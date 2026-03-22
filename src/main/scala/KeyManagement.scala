package services.keymanagement

import java.security.interfaces.{
  ECPrivateKey,
  ECPublicKey,
  RSAPrivateKey,
  RSAPublicKey
}
import java.security.{KeyPair, KeyPairGenerator}
import java.time.{Duration, Instant}

given CanEqual[Duration, Duration] = CanEqual.derived
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

// ============================================================
// Domain Models
// ============================================================

case class SerializedKey(
    version: Int,
    id: String,
    created: Instant,
    algorithm: String,
    isX509Certificate: Boolean,
    data: String,
    dataProtected: Boolean
)

abstract class KeyContainer(
    val id: String,
    val algorithm: String,
    val created: Instant,
    val hasX509Certificate: Boolean = false
) {
  def toSecurityKey(): Any // platform-specific security key
}

class RsaKeyContainer(
    keyPair: KeyPair,
    algorithm: String,
    created: Instant
) extends KeyContainer(
      id = java.util.UUID.randomUUID().toString,
      algorithm = algorithm,
      created = created
    ) {
  val publicKey: RSAPublicKey = keyPair.getPublic.asInstanceOf[RSAPublicKey]
  val privateKey: RSAPrivateKey = keyPair.getPrivate.asInstanceOf[RSAPrivateKey]

  override def toSecurityKey(): KeyPair = keyPair
}

class EcKeyContainer(
    keyPair: KeyPair,
    algorithm: String,
    created: Instant
) extends KeyContainer(
      id = java.util.UUID.randomUUID().toString,
      algorithm = algorithm,
      created = created
    ) {
  override def toSecurityKey(): KeyPair = keyPair
}

class X509KeyContainer(
    keyPair: KeyPair,
    algorithm: String,
    created: Instant,
    retirementAge: Duration,
    issuer: String = "OP"
) extends KeyContainer(
      id = java.util.UUID.randomUUID().toString,
      algorithm = algorithm,
      created = created,
      hasX509Certificate = true
    ) {
  // In a real implementation, a self-signed X509 cert would be created here
  val certificateRawData: String = "" // base64-encoded PFX

  override def toSecurityKey(): KeyPair = keyPair
}

// ============================================================
// Configuration
// ============================================================

/** Configuration for a signing algorithm */
final case class SigningAlgorithmConfig(
    name: String,
    useX509Certificate: Boolean = false
) {
  def isRsaKey: Boolean = name.startsWith("R") || name.startsWith("P")
  def isEcKey: Boolean = name.startsWith("E")
}

/** Key management configuration */
final case class KeyManagementConfig(
    enabled: Boolean = true,
    rsaKeySize: Int = 2048,
    signingAlgorithms: List[SigningAlgorithmConfig] = List(
      SigningAlgorithmConfig("RS256")
    ),
    initializationDuration: Duration = Duration.ofMinutes(5),
    initializationSynchronizationDelay: Duration = Duration.ofSeconds(5),
    initializationKeyCacheDuration: Duration = Duration.ofMinutes(1),
    keyCacheDuration: Duration = Duration.ofHours(24),
    propagationTime: Duration = Duration.ofDays(14),
    rotationInterval: Duration = Duration.ofDays(90),
    retentionDuration: Duration = Duration.ofDays(14),
    deleteRetiredKeys: Boolean = true,
    dataProtectKeys: Boolean = true,
    keyPath: String = "keys"
) {

  def defaultSigningAlgorithm: String = signingAlgorithms.head.name
  def allowedSigningAlgorithmNames: Set[String] =
    signingAlgorithms.map(_.name).toSet
  def keyRetirementAge: Duration = rotationInterval.plus(retentionDuration)

  def isRetired(age: Duration): Boolean =
    !age.minus(keyRetirementAge).isNegative
  def isExpired(age: Duration): Boolean =
    !age.minus(rotationInterval).isNegative
  def isWithinInitializationDuration(age: Duration): Boolean =
    !age.minus(initializationDuration).isNegative || age.equals(
      initializationDuration
    )

  def validate(): Unit = {
    require(
      !initializationDuration.isNegative,
      "initializationDuration must be >= 0"
    )
    require(
      !initializationSynchronizationDelay.isNegative,
      "initializationSynchronizationDelay must be >= 0"
    )
    require(
      !initializationKeyCacheDuration.isNegative,
      "initializationKeyCacheDuration must be >= 0"
    )
    require(!keyCacheDuration.isNegative, "keyCacheDuration must be >= 0")
    require(
      !propagationTime.isNegative && !propagationTime.isZero,
      "propagationTime must be > 0"
    )
    require(
      !rotationInterval.isNegative && !rotationInterval.isZero,
      "rotationInterval must be > 0"
    )
    require(
      !retentionDuration.isNegative && !retentionDuration.isZero,
      "retentionDuration must be > 0"
    )
    require(
      rotationInterval.compareTo(propagationTime) > 0,
      "rotationInterval must be longer than propagationTime"
    )

    val dups = signingAlgorithms.groupBy(_.name).filter(_._2.size > 1)
    require(
      dups.isEmpty,
      s"Duplicate signing algorithms not allowed: '${dups.keys.mkString(", ")}'"
    )

    val invalidEc =
      signingAlgorithms.filter(a => a.isEcKey && a.useX509Certificate)
    require(
      invalidEc.isEmpty,
      s"UseX509Certificate not supported for EC keys: '${invalidEc.map(_.name).mkString(", ")}'"
    )
  }
}

/** Caching configuration */
final case class CachingConfig(
    cacheLockTimeout: Duration = Duration.ofSeconds(60)
)

/** Identity server configuration (combines key management and caching) */
final case class IdentityServerConfig(
    keyManagement: KeyManagementConfig,
    caching: CachingConfig = CachingConfig()
)

// ============================================================
// Service Interfaces (traits)
// ============================================================

trait Clock {
  def utcNow: Instant

  def getAge(date: Instant): Duration = {
    val now0 = utcNow
    val effectiveNow = if (date.isAfter(now0)) date else now0
    Duration.between(date, effectiveNow)
  }
}

trait SigningKeyStore {
  def loadKeysAsync(): Future[Seq[SerializedKey]]
  def storeKeyAsync(key: SerializedKey): Future[Unit]
  def deleteKeyAsync(id: String): Future[Unit]
}

trait SigningKeyStoreCache {
  def getKeysAsync(): Future[Option[Seq[KeyContainer]]]
  def storeKeysAsync(
      keys: Seq[KeyContainer],
      duration: Duration
  ): Future[Unit]
}

trait SigningKeyProtector {
  def protect(key: KeyContainer): SerializedKey
  def unprotect(key: SerializedKey): Option[KeyContainer]
}

trait ConcurrencyLock {
  def lockAsync(millisecondsTimeout: Int): Future[Boolean]
  def unlock(): Unit
}

trait IssuerNameService {
  def getCurrentAsync(): Future[String]
}

trait Logger {
  def trace(msg: String, args: Any*): Unit
  def debug(msg: String, args: Any*): Unit
  def info(msg: String, args: Any*): Unit
  def warn(msg: String, args: Any*): Unit
  def error(msg: String, args: Any*): Unit
  def error(ex: Throwable, msg: String, args: Any*): Unit
  def isDebugEnabled: Boolean
  def isTraceEnabled: Boolean
}

trait KeyManager {
  def getCurrentKeysAsync(): Future[Seq[KeyContainer]]
  def getAllKeysAsync(): Future[Seq[KeyContainer]]
}

// ============================================================
// Crypto Helper
// ============================================================

object CryptoHelper {
  def createRsaKeyPair(keySize: Int): KeyPair = {
    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(keySize)
    gen.generateKeyPair()
  }

  def createEcKeyPair(curveName: String): KeyPair = {
    import java.security.spec.ECGenParameterSpec
    val gen = KeyPairGenerator.getInstance("EC")
    gen.initialize(new ECGenParameterSpec(curveName))
    gen.generateKeyPair()
  }

  def getCurveNameFromSigningAlgorithm(alg: String): String = alg match {
    case "ES256" => "secp256r1"
    case "ES384" => "secp384r1"
    case "ES512" => "secp521r1"
    case _       =>
      throw new IllegalArgumentException(s"Invalid EC signing algorithm: $alg")
  }
}

// ============================================================
// KeyManager Implementation
// ============================================================

class DefaultKeyManager(
    options: IdentityServerConfig,
    store: SigningKeyStore,
    cache: SigningKeyStoreCache,
    protector: SigningKeyProtector,
    clock: Clock,
    newKeyLock: ConcurrencyLock,
    logger: Logger,
    issuerNameService: IssuerNameService
)(implicit ec: ExecutionContext)
    extends KeyManager {

  // Validate configuration on construction
  options.keyManagement.validate()

  private val km = options.keyManagement

  // ---- Public API ----

  override def getCurrentKeysAsync(): Future[Seq[KeyContainer]] = {
    logger.trace("Getting the current key.")

    getAllKeysInternalAsync().map { case (_, currentKeys) =>
      if (logger.isDebugEnabled) {
        currentKeys.foreach { key =>
          val age = clock.getAge(key.created)
          val expiresIn = km.rotationInterval.minus(age)
          val retiresIn = km.keyRetirementAge.minus(age)
          logger.info(
            "Active signing key found with kid {} for alg {}. Expires in {}. Retires in {}",
            key.id,
            key.algorithm,
            expiresIn,
            retiresIn
          )
        }
      }
      currentKeys
    }
  }

  override def getAllKeysAsync(): Future[Seq[KeyContainer]] = {
    logger.trace("Getting all the keys.")
    getAllKeysInternalAsync().map(_._1)
  }

  // ---- Internal: Core Key Resolution ----

  private[keymanagement] def getAllKeysInternalAsync()
      : Future[(Seq[KeyContainer], Seq[KeyContainer])] = {
    for {
      // Try cache first, then fall back to store
      cachedResult <- getAllKeysFromCacheAsync()
      (cached, initialKeys) =
        if (cachedResult.nonEmpty) (true, cachedResult)
        else (false, Seq.empty[KeyContainer])
      keys0 <-
        if (initialKeys.isEmpty) getAllKeysFromStoreAsync()
        else Future.successful(initialKeys)

      // Check signing keys
      signingKeys0 = getAllCurrentSigningKeys(keys0)
      signingKeysSuccess0 = tryGetAllCurrentSigningKeys(keys0)._1

      _ = if (!signingKeysSuccess0 && cached)
        logger.trace(
          "Not all signing keys current in cache, reloading keys from database."
        )

      // Check rotation
      rotationRequired0 =
        if (signingKeysSuccess0) isKeyRotationRequired(keys0) else false
      _ = if (rotationRequired0 && cached)
        logger.trace("Key rotation required, reloading keys from database.")

      // If work needed, acquire lock and proceed
      result <-
        if (!signingKeysSuccess0 || rotationRequired0)
          acquireLockAndResolveKeys(signingKeysSuccess0, rotationRequired0)
        else
          Future.successful((keys0, signingKeys0))
    } yield {
      val (finalKeys, finalSigningKeys) = result
      if (finalSigningKeys.isEmpty) {
        logger.error("Failed to create and then load new keys.")
        throw new RuntimeException("Failed to create and then load new keys.")
      }
      (finalKeys, finalSigningKeys)
    }
  }

  private def acquireLockAndResolveKeys(
      initialSigningKeysSuccess: Boolean,
      initialRotationRequired: Boolean
  ): Future[(Seq[KeyContainer], Seq[KeyContainer])] = {
    logger.trace("Entering new key lock.")

    newKeyLock
      .lockAsync(options.caching.cacheLockTimeout.toMillis.toInt)
      .flatMap { acquired =>
        if (!acquired) {
          throw new RuntimeException(
            s"Failed to obtain new key lock for: '${getClass.getName}'"
          )
        }

        val work = for {
          // Check if another thread did the work already (from cache)
          cacheKeys <- getAllKeysFromCacheAsync()

          signingKeysSuccess1 =
            if (!initialSigningKeysSuccess)
              tryGetAllCurrentSigningKeys(cacheKeys)._1
            else initialSigningKeysSuccess

          rotationRequired1 =
            if (initialRotationRequired)
              isKeyRotationRequired(cacheKeys)
            else false

          needsWork1 = !signingKeysSuccess1 || rotationRequired1

          result <-
            if (needsWork1) {
              // Still need to do work; check if another server did the work (from store)
              for {
                storeKeys <- getAllKeysFromStoreAsync()

                (signingKeysSuccess2, signingKeys2) =
                  tryGetAllCurrentSigningKeys(storeKeys)
                rotationRequired2 =
                  if (initialRotationRequired) isKeyRotationRequired(storeKeys)
                  else false

                result <-
                  if (!signingKeysSuccess2 || rotationRequired2) {
                    if (!signingKeysSuccess2)
                      logger.trace("No active keys; new key creation required.")
                    else
                      logger.trace(
                        "Approaching key retirement; new key creation required."
                      )
                    createNewKeysAndAddToCacheAsync()
                  } else {
                    logger.trace("Another server created new key.")
                    Future.successful((storeKeys, signingKeys2))
                  }
              } yield result
            } else {
              logger.trace("Another thread created new key.")
              val signingKeys = getAllCurrentSigningKeys(cacheKeys)
              Future.successful((cacheKeys, signingKeys))
            }
        } yield result

        work.andThen { case _ =>
          logger.trace("Releasing new key lock.")
          newKeyLock.unlock()
        }
      }
  }

  // ---- Internal: Key Rotation Check ----

  private[keymanagement] def isKeyRotationRequired(
      allKeys: Seq[KeyContainer]
  ): Boolean = {
    if (allKeys == null || allKeys.isEmpty) true
    else {
      val groupedKeys = allKeys.groupBy(_.algorithm)

      val success = groupedKeys.size == km.allowedSigningAlgorithmNames.size &&
        groupedKeys.keys.forall(km.allowedSigningAlgorithmNames.contains)

      if (!success) true
      else {
        groupedKeys.exists { case (algName, keysForAlg) =>
          getCurrentSigningKey(keysForAlg) match {
            case None                   => true
            case Some(initialActiveKey) =>
              // Rotation is needed if:
              // 1) there are no other keys next in line (younger), AND
              // 2) the current active key is near expiration (within the propagation window)

              val youngerKeys =
                keysForAlg.filter(_.created.isAfter(initialActiveKey.created))

              val activeKey = if (youngerKeys.nonEmpty) {
                // Find the youngest key and treat it as if it's the active key
                youngerKeys.maxBy(_.created)
              } else {
                initialActiveKey
              }

              val age = clock.getAge(activeKey.created)
              val diff = km.rotationInterval.minus(age)
              val needed = diff.compareTo(km.propagationTime) <= 0

              if (!needed) {
                logger.trace(
                  "Key rotation not required for alg {}; New key expected to be created in {}",
                  algName,
                  diff.minus(km.propagationTime)
                )
              } else {
                logger.trace("Key rotation required now for alg {}.", algName)
              }
              needed
          }
        }
      }
    }
  }

  // ---- Internal: Key Creation ----

  private[keymanagement] def createAndStoreNewKeyAsync(
      alg: SigningAlgorithmConfig
  ): Future[KeyContainer] = {
    logger.trace("Creating new key.")
    val now = clock.utcNow

    val containerFuture: Future[KeyContainer] =
      if (alg.isRsaKey) {
        val rsaKeyPair = CryptoHelper.createRsaKeyPair(km.rsaKeySize)
        if (alg.useX509Certificate) {
          issuerNameService.getCurrentAsync().map { iss =>
            new X509KeyContainer(
              rsaKeyPair,
              alg.name,
              now,
              km.keyRetirementAge,
              iss
            )
          }
        } else {
          Future.successful(new RsaKeyContainer(rsaKeyPair, alg.name, now))
        }
      } else if (alg.isEcKey) {
        val curveName = CryptoHelper.getCurveNameFromSigningAlgorithm(alg.name)
        val ecKeyPair = CryptoHelper.createEcKeyPair(curveName)
        // X509 certs don't currently work with EC keys
        Future.successful(new EcKeyContainer(ecKeyPair, alg.name, now))
      } else {
        Future.failed(
          new IllegalArgumentException(s"Invalid alg '${alg.name}'")
        )
      }

    containerFuture.flatMap { container =>
      val serialized = protector.protect(container)
      store.storeKeyAsync(serialized).map { _ =>
        logger.debug("Created and stored new key with kid {}.", container.id)
        container
      }
    }
  }

  private[keymanagement] def createNewKeysAndAddToCacheAsync()
      : Future[(Seq[KeyContainer], Seq[KeyContainer])] = {
    for {
      existingCached <- cache.getKeysAsync().map(_.getOrElse(Seq.empty))

      newKeys <- km.signingAlgorithms.foldLeft(
        Future.successful(Seq.empty[KeyContainer])
      ) { (acc, alg) =>
        acc.flatMap { soFar =>
          createAndStoreNewKeyAsync(alg).map(soFar :+ _)
        }
      }

      allKeys = existingCached ++ newKeys

      // If all keys are within initialization duration, delay and reload
      finalKeys <-
        if (areAllKeysWithinInitializationDuration(allKeys)) {
          if (
            !km.initializationSynchronizationDelay.isZero && !km.initializationSynchronizationDelay.isNegative
          ) {
            logger.trace(
              "All keys are new; delaying before reloading keys from store by InitializationSynchronizationDelay for {}.",
              km.initializationSynchronizationDelay
            )
            // Simulate Task.Delay
            Future {
              Thread.sleep(km.initializationSynchronizationDelay.toMillis)
            }.flatMap(_ => getAllKeysFromStoreAsync(shouldCache = false))
          } else {
            logger.trace("All keys are new; reloading keys from store.")
            getAllKeysFromStoreAsync(shouldCache = false)
          }
        } else {
          Future.successful(allKeys)
        }

      // Explicitly cache since we didn't when loading above
      _ <- cacheKeysAsync(finalKeys)

      activeKeys = getAllCurrentSigningKeys(finalKeys)
    } yield (finalKeys, activeKeys)
  }

  // ---- Internal: Cache ----

  private[keymanagement] def getAllKeysFromCacheAsync()
      : Future[Seq[KeyContainer]] = {
    cache.getKeysAsync().map {
      case Some(keys) =>
        logger.trace("Cache hit when loading all keys.")
        keys
      case None =>
        logger.trace("Cache miss when loading all keys.")
        Seq.empty
    }
  }

  private[keymanagement] def cacheKeysAsync(
      keys: Seq[KeyContainer]
  ): Future[Unit] = {
    if (keys.nonEmpty) {
      val duration =
        if (areAllKeysWithinInitializationDuration(keys)) {
          if (
            !km.initializationKeyCacheDuration.isZero && !km.initializationKeyCacheDuration.isNegative
          ) {
            logger.trace(
              "Caching keys with InitializationKeyCacheDuration for {}",
              km.initializationKeyCacheDuration
            )
          }
          km.initializationKeyCacheDuration
        } else {
          if (!km.keyCacheDuration.isZero && !km.keyCacheDuration.isNegative) {
            logger.trace(
              "Caching keys with KeyCacheDuration for {}",
              km.keyCacheDuration
            )
          }
          km.keyCacheDuration
        }

      if (!duration.isZero && !duration.isNegative)
        cache.storeKeysAsync(keys, duration)
      else Future.unit
    } else {
      Future.unit
    }
  }

  // ---- Internal: Store ----

  private[keymanagement] def getAllKeysFromStoreAsync(
      shouldCache: Boolean = true
  ): Future[Seq[KeyContainer]] = {
    logger.trace("Loading keys from store.")

    store.loadKeysAsync().flatMap { protectedKeys =>
      if (protectedKeys != null && protectedKeys.nonEmpty) {
        filterAndDeleteRetiredKeysAsync(protectedKeys).flatMap { nonRetired =>
          val keys = nonRetired.flatMap { serialized =>
            Try(protector.unprotect(serialized)) match {
              case Success(Some(container)) => Some(container)
              case Success(None)            =>
                logger.warn(
                  "Key with kid {} failed to unprotect.",
                  serialized.id
                )
                None
              case Failure(ex: java.security.GeneralSecurityException) =>
                logger.error(
                  ex,
                  "Error unprotecting the signing key with kid {}. " +
                    "This is likely due to the data protection key not being available.",
                  serialized.id
                )
                None
              case Failure(ex) =>
                logger.error(
                  ex,
                  "Error loading key with kid {}.",
                  serialized.id
                )
                None
            }
          }

          if (logger.isTraceEnabled && keys.nonEmpty) {
            logger.trace(
              "Loaded keys from store: {}",
              keys.map(_.id).mkString(",")
            )
          }

          // Only use keys with allowed algorithms
          val filtered = keys
            .filter(k => km.allowedSigningAlgorithmNames.contains(k.algorithm))

          if (logger.isTraceEnabled && filtered.nonEmpty) {
            logger.trace(
              "Keys with allowed alg from store: {}",
              filtered.map(_.id).mkString(",")
            )
          }

          if (filtered.nonEmpty) {
            logger.trace("Keys successfully returned from store.")
            if (shouldCache) cacheKeysAsync(filtered).map(_ => filtered)
            else Future.successful(filtered)
          } else {
            logger.trace("No keys returned from store.")
            Future.successful(Seq.empty)
          }
        }
      } else {
        logger.trace("No keys returned from store.")
        Future.successful(Seq.empty)
      }
    }
  }

  // ---- Internal: Filtering ----

  private[keymanagement] def filterAndDeleteRetiredKeysAsync(
      keys: Seq[SerializedKey]
  ): Future[Seq[SerializedKey]] = {
    val (retired, remaining) =
      keys.partition(k => k != null && km.isRetired(clock.getAge(k.created)))

    val deleteWork = if (retired.nonEmpty) {
      if (logger.isTraceEnabled) {
        logger.trace(
          "Filtered retired keys from store: {}",
          retired.map(_.id).mkString(",")
        )
      }
      if (km.deleteRetiredKeys) {
        val ids = retired.map(_.id)
        if (logger.isDebugEnabled) {
          logger.debug(
            "Deleting retired keys from store: {}",
            ids.mkString(",")
          )
        }
        deleteKeysAsync(ids)
      } else {
        Future.unit
      }
    } else {
      Future.unit
    }

    deleteWork.map(_ => remaining)
  }

  private[keymanagement] def deleteKeysAsync(ids: Seq[String]): Future[Unit] = {
    if (ids == null || ids.isEmpty) Future.unit
    else {
      ids.foldLeft(Future.unit) { (acc, id) =>
        acc.flatMap(_ => store.deleteKeyAsync(id))
      }
    }
  }

  private[keymanagement] def filterExpiredKeys(
      keys: Seq[KeyContainer]
  ): Seq[KeyContainer] = {
    keys.filterNot(k => km.isExpired(clock.getAge(k.created)))
  }

  private[keymanagement] def areAllKeysWithinInitializationDuration(
      keys: Seq[KeyContainer]
  ): Boolean = {
    if (km.initializationDuration == Duration.ZERO) return false

    val nonExpired = filterExpiredKeys(keys)
    nonExpired.forall(k =>
      km.isWithinInitializationDuration(clock.getAge(k.created))
    )
  }

  // ---- Internal: Signing Key Selection ----

  private[keymanagement] def tryGetAllCurrentSigningKeys(
      keys: Seq[KeyContainer]
  ): (Boolean, Seq[KeyContainer]) = {
    val signingKeys = getAllCurrentSigningKeys(keys)
    val success = signingKeys.size == km.allowedSigningAlgorithmNames.size &&
      signingKeys.forall(k =>
        km.allowedSigningAlgorithmNames.contains(k.algorithm)
      )
    (success, signingKeys)
  }

  private[keymanagement] def getAllCurrentSigningKeys(
      allKeys: Seq[KeyContainer]
  ): Seq[KeyContainer] = {
    if (allKeys == null || allKeys.isEmpty) return Seq.empty

    logger.trace("Looking for active signing keys.")

    allKeys
      .groupBy(_.algorithm)
      .flatMap { case (algName, keysForAlg) =>
        logger.trace("Looking for an active signing key for alg {}.", algName)

        getCurrentSigningKey(keysForAlg) match {
          case Some(key) =>
            logger.trace(
              "Found active signing key for alg {} with kid {}.",
              algName,
              key.id
            )
            Some(key)
          case None =>
            logger.trace(
              "Failed to find active signing key for alg {}.",
              algName
            )
            None
        }
      }
      .toSeq
  }

  private[keymanagement] def getCurrentSigningKey(
      keys: Seq[KeyContainer]
  ): Option[KeyContainer] = {
    if (keys == null || keys.isEmpty) return None

    // First: look for keys past the activation delay
    getCurrentSigningKeyInternal(keys, ignoreActivationDelay = false).orElse {
      logger.trace(
        "No active signing key found (respecting the activation delay)."
      )
      // Fall back: check if any keys were recently created (ignore activation delay)
      val result =
        getCurrentSigningKeyInternal(keys, ignoreActivationDelay = true)
      if (result.isEmpty)
        logger.trace(
          "No active signing key found (ignoring the activation delay)."
        )
      result
    }
  }

  private[keymanagement] def getCurrentSigningKeyInternal(
      keys: Seq[KeyContainer],
      ignoreActivationDelay: Boolean = false
  ): Option[KeyContainer] = {
    if (keys == null) return None

    val eligible =
      keys.filter(k => canBeUsedAsCurrentSigningKey(k, ignoreActivationDelay))
    if (eligible.isEmpty) return None

    // Use the oldest eligible key — ensures consistency across servers
    // when multiple keys are created at roughly the same time
    Some(eligible.minBy(_.created))
  }

  private[keymanagement] def canBeUsedAsCurrentSigningKey(
      key: KeyContainer,
      ignoreActiveDelay: Boolean = false
  ): Boolean = {
    if (key == null) return false

    km.signingAlgorithms.find(_.name == key.algorithm) match {
      case None =>
        logger.trace(
          "Key {} signing algorithm {} not allowed by server options.",
          key.id,
          key.algorithm
        )
        return false
      case Some(alg) if alg.useX509Certificate && !key.hasX509Certificate =>
        logger.trace(
          "Server configured to wrap keys in X509 certs, but key {} is not wrapped in cert.",
          key.id
        )
        return false
      case _ => // ok
    }

    var now = clock.utcNow
    val start = key.created

    // Clock skew handling: if the key was created in the "future" relative to this server,
    // adjust our view of "now" to match the other server's time
    if (start.isAfter(now)) now = start

    val activationStart =
      if (!ignoreActiveDelay) {
        logger.trace(
          "Checking if key with kid {} is active (respecting activation delay).",
          key.id
        )
        start.plusMillis(km.propagationTime.toMillis)
      } else {
        logger.trace(
          "Checking if key with kid {} is active (ignoring activation delay).",
          key.id
        )
        start
      }

    if (activationStart.isAfter(now)) {
      logger.trace(
        "Key with kid {} is inactive: the current time is prior to its activation delay.",
        key.id
      )
      return false
    }

    // Expiration check
    val end = key.created.plusMillis(km.rotationInterval.toMillis)
    if (end.isBefore(now)) {
      logger.trace(
        "Key with kid {} is inactive: the current time is past its expiration.",
        key.id
      )
      return false
    }

    logger.trace("Key with kid {} is active.", key.id)
    true
  }
}
