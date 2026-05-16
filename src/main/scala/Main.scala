import com.github.plokhotnyuk.jsoniter_scala.core.*

object Main{
//Runtime: ReaderConfig + WriterConfig    
// Fastest reader config for production
val productionReaderConfig: ReaderConfig = ReaderConfig
  .withThrowReaderExceptionWithStackTrace(false) // no stack traces (default, keep it)
  .withAppendHexDumpToParseException(false)      // skip hex dump = faster error paths
  .withCheckForEndOfInput(false)                 // skip trailing-bytes check if input is trusted
  .withPreferredBufSize(65536)                   // 64KB: fewer reallocations for typical messages
  .withPreferredCharBufSize(8192)                // 8KB: fewer reallocations for string-heavy payloads

// Fastest writer config for production
val productionWriterConfig: WriterConfig = WriterConfig
  .withIndentionStep(0)                          // compact JSON (default, keep it)
  .withEscapeUnicode(false)                      // allow raw UTF-8 (default, keep it)
  .withPreferredBufSize(65536)                   // 64KB: avoids intermediate buffer growth


// . Compile-time: CodecMakerConfig

import com.github.plokhotnyuk.jsoniter_scala.macros.*

// Fastest codec generation settings
given CodecMakerConfig = CodecMakerConfig
  .withSkipUnexpectedFields(true)       // silently ignore unknown JSON fields (default)
  .withTransientDefault(true)           // omit fields equal to their default (default)
  .withTransientEmpty(true)             // omit empty collections (default)
  .withTransientNone(true)              // omit None fields (default)
  .withCheckFieldDuplication(false)     // skip duplicate-field check at runtime
  .withRequireDiscriminatorFirst(true)  // ADT discriminator must be first field (default)
  .withInlineOneValueClasses(true)      // inline single-arg wrapper types = zero boxing overhead

final case class MyModel()
// Then derive codecs as usual:
val codec: JsonValueCodec[MyModel] = JsonCodecMaker.make


val fintechReaderConfig: ReaderConfig = ReaderConfig
  // Keep false (default) — stack traces are expensive under load/DoS
  .withThrowReaderExceptionWithStackTrace(false)
  // Disable hex dump in hot paths (market data, payment APIs) to save allocations
  .withAppendHexDumpToParseException(false)
  // Mandatory — reject trailing bytes after valid JSON
  .withCheckForEndOfInput(true)
  // Cap at 1MB to prevent memory abuse from oversized payloads
  .withMaxBufSize(1048576)
  .withMaxCharBufSize(262144)
  // Tune to your typical payload (32KB default is fine for most API payloads)
  .withPreferredBufSize(32768)
  .withPreferredCharBufSize(4096)

val fintechWriterConfig: WriterConfig = WriterConfig
  // ASCII-safe output — prevents encoding ambiguity in financial messages
  .withEscapeUnicode(true)
  // Compact (indentionStep = 0 is default) — no whitespace overhead
  .withIndentionStep(0)
  // Disable stack traces in production
  .withThrowWriterExceptionWithStackTrace(false)
  // Tune to your typical serialized output size
  .withPreferredBufSize(32768)
val fintechCodecConfig: CodecMakerConfig = CodecMakerConfig
  // CRITICAL: reject unknown fields — strict schema validation
  .withSkipUnexpectedFields(false)
  // Reject duplicate fields — prevents injection/override attacks
  .withCheckFieldDuplication(true)
  // All collection fields (amounts, legs, line items) must be present
  .withRequireCollectionFields(true)
  // Always emit all fields for audit trail completeness
  .withTransientDefault(false)
  .withTransientNone(false)
  .withTransientEmpty(false)
  // BigDecimal precision: 34 (Decimal128/IEEE 754) is fine for currency
  .withBigDecimalPrecision(34)
  // Limit scale — e.g., 10 is enough for any currency denomination
  .withBigDecimalScaleLimit(10)
  // Limit mantissa digits to prevent DoS via crafted large decimals
  .withBigDecimalDigitsLimit(28)
  // Same for BigInt — 18 digits covers Int64 range safely
  .withBigIntDigitsLimit(18)
  // Domain-specific insert limits; reduce if maps are bounded in your schema
  .withMapMaxInsertNumber(256)
  .withSetMaxInsertNumber(256)

  }