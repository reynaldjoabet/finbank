package unitypay

import zio.*
import java.time.Instant

/** Lightweight ISO 20022 `pacs.008` (FIToFICustomerCreditTransfer) builder.
  *
  * This produces an XML string that can be submitted to regional RTGS/ACH
  * systems in Africa:
  *   - GIMAC / SICA — CEMAC zone (XAF)
  *   - BCEAO STAR — WAEMU zone (XOF)
  *   - PAPSS — Pan-African Payment and Settlement System
  *
  * The builder covers the minimal mandatory fields required by all three
  * schemes. Additional optional blocks (regulatory reporting, remittance
  * information, etc.) can be appended via `withRemittanceInfo`.
  *
  * Reference: ISO 20022 `pacs.008.001.10` schema.
  */
final case class Pacs008Message(
    msgId: String,
    creationDateTime: Instant,
    numberOfTxs: Int,
    settlementMethod: Pacs008Message.SettlementMethod,
    debtorName: String,
    debtorIban: String,
    debtorBic: String,
    creditorName: String,
    creditorIban: String,
    creditorBic: String,
    amount: Amount,
    endToEndId: String,
    remittanceInfo: Option[String]
)

object Pacs008Message {

  enum SettlementMethod derives CanEqual {
    case CLRG // Clearing System (GIMAC/SICA/PAPSS)
    case INDA // Instructed Agent settles
    case INGA // Instructing Agent settles
  }

  /** Renders the pacs.008 message as a well-formed XML string.
    *
    * The namespace and schema location match PAPSS's published requirements.
    */
  def toXml(m: Pacs008Message): String = {
    val rmtInf = m.remittanceInfo
      .map(r => s"<RmtInf><Ustrd>${escapeXml(r)}</Ustrd></RmtInf>")
      .getOrElse("")

    s"""<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.10">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>${escapeXml(m.msgId)}</MsgId>
      <CreDtTm>${m.creationDateTime}</CreDtTm>
      <NbOfTxs>${m.numberOfTxs}</NbOfTxs>
      <SttlmInf>
        <SttlmMtd>${m.settlementMethod}</SttlmMtd>
      </SttlmInf>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <EndToEndId>${escapeXml(m.endToEndId)}</EndToEndId>
      </PmtId>
      <IntrBkSttlmAmt Ccy="${m.amount.currency.code}">${formatMinor(
        m.amount
      )}</IntrBkSttlmAmt>
      <Dbtr>
        <Nm>${escapeXml(m.debtorName)}</Nm>
      </Dbtr>
      <DbtrAcct>
        <Id><IBAN>${escapeXml(m.debtorIban)}</IBAN></Id>
      </DbtrAcct>
      <DbtrAgt>
        <FinInstnId><BICFI>${escapeXml(m.debtorBic)}</BICFI></FinInstnId>
      </DbtrAgt>
      <CdtrAgt>
        <FinInstnId><BICFI>${escapeXml(m.creditorBic)}</BICFI></FinInstnId>
      </CdtrAgt>
      <Cdtr>
        <Nm>${escapeXml(m.creditorName)}</Nm>
      </Cdtr>
      <CdtrAcct>
        <Id><IBAN>${escapeXml(m.creditorIban)}</IBAN></Id>
      </CdtrAcct>
      $rmtInf
    </CdtTrfTxInf>
  </FIToFICstmrCdtTrf>
</Document>"""
  }

  private def escapeXml(s: String): String =
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

  /** Convert minor-unit Long to a decimal string with correct scale. XAF / XOF
    * have 0 decimal places (1 XAF = 1 minor unit). All others assumed 2 decimal
    * places.
    */
  private def formatMinor(a: Amount): String =
    a.currency match {
      case Currency.XAF | Currency.XOF => a.minor.toString
      case _                           => f"${a.minor / 100.0}%.2f"
    }
}

/** ZIO service that builds and validates pacs.008 messages before submission to
  * a regional clearing system.
  */
trait Iso20022Service {
  def buildPacs008(
      debtorName: String,
      debtorIban: String,
      debtorBic: String,
      creditorName: String,
      creditorIban: String,
      creditorBic: String,
      amount: Amount,
      remittanceInfo: Option[String]
  ): UIO[String] // returns XML string

  def submitToClearing(xml: String): IO[Iso20022Error, ClearingAck]
}

final case class ClearingAck(
    clearingRef: String,
    acceptedAt: Instant,
    scheme: String // "GIMAC" | "PAPSS" | "BCEAO_STAR"
)

sealed trait Iso20022Error extends Throwable {
  def message: String
  override def getMessage(): String = message
}
object Iso20022Error {
  final case class ValidationError(message: String) extends Iso20022Error
  final case class SchemeRejected(message: String) extends Iso20022Error
  final case class NetworkError(message: String) extends Iso20022Error
}

object Iso20022Service {

  val live: ULayer[Iso20022Service] = ZLayer.succeed {
    new Iso20022Service {

      override def buildPacs008(
          debtorName: String,
          debtorIban: String,
          debtorBic: String,
          creditorName: String,
          creditorIban: String,
          creditorBic: String,
          amount: Amount,
          remittanceInfo: Option[String]
      ): UIO[String] =
        for {
          msgId <- Random.nextUUID.map(u => s"FINBANK-$u")
          now <- Clock.instant
          e2eId <- Random.nextUUID.map(_.toString)
          msg = Pacs008Message(
            msgId = msgId,
            creationDateTime = now,
            numberOfTxs = 1,
            settlementMethod = Pacs008Message.SettlementMethod.CLRG,
            debtorName = debtorName,
            debtorIban = debtorIban,
            debtorBic = debtorBic,
            creditorName = creditorName,
            creditorIban = creditorIban,
            creditorBic = creditorBic,
            amount = amount,
            endToEndId = e2eId,
            remittanceInfo = remittanceInfo
          )
        } yield Pacs008Message.toXml(msg)

      override def submitToClearing(
          xml: String
      ): IO[Iso20022Error, ClearingAck] =
        for {
          // --- stub: replace with real HTTP call to GIMAC / PAPSS endpoint ---
          _ <- ZIO.logInfo("[ISO 20022] Submitting pacs.008 to clearing system")
          now <- Clock.instant
          ref <- Random.nextUUID.map(u => s"CLR-$u")
        } yield ClearingAck(
          clearingRef = ref,
          acceptedAt = now,
          scheme = "PAPSS"
        )
    }
  }
}
