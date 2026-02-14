# finbank

Open Banking Answers the Need for Seamless Cross-Border Payments

connectivity is a big issue gloabally but even more so in Africa and in ppayments
 safety and security
 use technology to manage finances
 An open banking solution that leverages machine learning and analytics
 digital identity
 82% of businesses fail as a result of poor cash flow management

Financial Inclusion at Scale

FinTech Software Development Company
1. Digital Banking
- Mobile and Online Banking
- Digital Wallets (eWallet)
- Digital Customer Onboarding and KYC Automation
- Digital Lending and BNPL Systems
- AI Powered Credit Scoring
- Money Transfer Applications
- CrowdFunding Platforms
- P2P Lending and Payments
- Marketplaces
- Trade Finance Solutions

2. Investment Management
- Wealth Management Solutions
- Stock, FX, and Crypto Trading Applications
- Investment Data Analytics Systems
- AI-Powered Robo Advisers
- Asset Management Solutions 
- Personal Finance Management Systems
- Portfolio Analysis and Recommender Platforms

3. Insurance
- On-Demand Insurance Platforms
- Automated Claims Management Systems
- P2P Insurance Platforms
- AI-Powered Insurance Advisors
- Risk Analysis & Management Solutions
- Policy Management Systems
- Telematics & Usage-Based Insurance (UBI) Platforms
- Insurance Aggregator Platforms – Online marketplaces comparing multiple insurance - providers.
- Self-Service Portals & Mobile Apps
- Compliance & Regulatory Reporting Software

4. Personal Finance Management
- Account Aggregation and Expense Management 
- Retirement Planning with Roth IRA accounts
- Debt Refinancing and Optimization
- Automated Investing and Savings
- AI-powered virtual financial advisers
- Gamification and social features

[](https://itexus.com)

Mobile E-Wallet Application
Mobile e-wallet application that lets users link their debit and credit cards to their accounts through banking partners, create e-wallets and virtual cards, and use them for money transfers, cash withdrawals, bills and online payments, etc.

Digital lending platform (and a matching mobile app client) with an automated loan-lending process.

A large-scale autonomous billing ecosystem for an international fintech organization operating in the B2B payments sector. The platform automates invoicing, payment processing, KYB verification, financial reconciliation, and back-office synchronization - helping service-based businesses eliminate manual billing operations, reduce errors, and accelerate cash flow

A banking application that provides students with unique credit, debit, and payment tools, helps to build credit score, and instills financial literacy and money management habits through engaging educational content.

Galileo is a payment processing platform and banking API that allows you to quickly create sophisticated payment card programs. It is used to issue virtual and physical credit/debit cards, process payments, etc.

Mobile app-to-bank transfer solution enabling its users to send money from US-issued bank cards to Nigerian bank accounts when money debited from senders’ cards instantly enters bank accounts in Nigeria. The app also allows paying bills internationally

A digital wallet app ecosystem for Coinstar, a $2.2B global fintech company — including mobile digital wallet apps, ePOS kiosk software, web applications, and a cloud API server enabling cryptocurrency and digital asset trading, bank account linking, crypto-fiat-cash conversions, and online payments.Client Subsystem

PSD2 wants to break down the banks' monopoly on the customer relation

secure, conveneient payments, seamless account opening and loans approved instantly all possible thanks to oepn finance as consumers consent to securely sharing their financial data
 backed by scalable apis

 Each hop (MUR → USD → USDT → XAF) takes a "bite" out of your capital through both service fees and unfavorable exchange rate spreads.

Open Banking can fix this by replacing the expensive "relay race" of banks with a direct, digital bridge

Traditional transfers use Correspondent Banking, where your bank in Mauritius doesn't talk directly to a bank in Cameroon

Open Banking uses APIs that allow a licensed Fintech app to talk directly to your MCB/SBM account and a mobile money wallet in Cameroon (like MTN or Orange)

Banks often hide their fees in the exchange rate (e.g., they buy USD at 45 MUR but sell it to you at 47 MUR).
Right now, your flow switches logic mid-way (fiat → crypto → fiat).
Option A – Full crypto rail
`MUR → USDT → Recipient → XAF (locally)`
or
Option B – Full fiat rail
`MUR → XAF (via regional clearing or partner bank)`

With proper open banking implementation
Best crypto-assisted route
`MUR → USDT → XAF`
Best fiat-only route
`MUR → XAF`
Both remove:
One FX layer
One liquidity bottleneck
One pricing abuse point

An FX layer is any forced currency conversion that exists only because of system limitations, not because value truly needs to change.

## Benefits of Open Banking in Cross Border Payments
- Open banking has led to numerous benefits in the realm of cross-border payments.
- Open banking facilitates expedited and streamlined cross-border transactions, eliminating needless delays.
- Customers can select the most economical alternative for their cross-border when they have access to real-time exchange rate data and a greater variety of service providers.
- Open banking decreases the need for intermediaries, enhancing the affordability of cross-border transactions for people and businesses.
- By using advanced encryption technology and secure APIs, open banking ensures that customer data is protected throughout the process of cross-border transactions.
- The seamless integration of various financial services into a single platform by open banking streamlines and improves the convenience of cross-border payments.
- Open banking enables alternative payment providers and fintech firms to provide inventive solutions that address the needs of unbanked and underbanked communities through the provision of access to banking data to third-party providers. This inclusiveness fosters financial empowerment and facilitates cross-border economic development

[how-open-banking-answers-the-need-for-seamless-cross-border-payments](https://www.macroglobal.co.uk/blog/regulatory-technology/how-open-banking-answers-the-need-for-seamless-cross-border-payments)


Circe has two distinct layers:
A. JSON <-> Circe AST
```sh
String / bytes
   ↓ parse
io.circe.Json   (AST)
   ↑ print
String / bytes
```
This is done by:
io.circe.parser.parse
Printer.noSpaces.print(json)


```scala
import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec._
import com.github.plokhotnyuk.jsoniter_scala.core._
```

```scala
package com.github.plokhotnyuk.jsoniter_scala.circe

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
import io.circe._

object JsoniterScalaCodec {
  /**
   * The implicit instance of jsoniter-scala's value codec for circe's Json.
   *
   * Uses default parameters for `JsoniterScalaCodec.jsonCodec`.
   */
  implicit val jsonC3c: JsonValueCodec[Json] = jsonCodec()

  /**
   * Creates a JSON value codec that parses and serialize to/from circe's JSON AST.
   *
   * @param maxDepth the maximum depth for decoding
   * @param initialSize the initial size hint for object and array collections
   * @param doSerialize a predicate that determines whether a value should be serialized
   * @param numberParser a function that parses JSON numbers
   * @return The JSON codec
   */
  def jsonCodec(
      maxDepth: Int,
      initialSize: Int,
      doSerialize: Json => Boolean,
      numberParser: JsonReader => Json): JsonValueCodec[Json] =
    jsonCodec(maxDepth, initialSize, doSerialize, numberParser, io.circe.JsoniterScalaCodec.defaultNumberSerializer)

  /**
   * Creates a JSON value codec that parses and serialize to/from circe's JSON AST.
   *
   * @param maxDepth the maximum depth for decoding
   * @param initialSize the initial size hint for object and array collections
   * @param doSerialize a predicate that determines whether a value should be serialized
   * @param numberParser a function that parses JSON numbers
   * @param numberSerializer a routine that serializes JSON numbers
   * @return The JSON codec
   */
  def jsonCodec(
      maxDepth: Int = 128,
      initialSize: Int = 8,
      doSerialize: Json => Boolean = _ => true,
      numberParser: JsonReader => Json = io.circe.JsoniterScalaCodec.defaultNumberParser,
      numberSerializer: (JsonWriter, JsonNumber) => Unit = io.circe.JsoniterScalaCodec.defaultNumberSerializer): JsonValueCodec[Json] =
    new io.circe.JsoniterScalaCodec(maxDepth, initialSize, doSerialize, numberParser, numberSerializer)
}
```

JsonValueCodec[A]
This is one object that knows how to:
```scala
decode: (JsonReader) => A
encode: (A, JsonWriter) => Unit
```
Not:
Json => A
A => Json
But:
bytes ⇄ A
No AST. No intermediate objects.

Now you can see why the circe module exists:
```sh
jsoniter-core:   bytes ⇄ A
jsoniter-circe:  bytes ⇄ io.circe.Json
circe-core:      Json ⇄ A
```

The circe module:
reuses jsoniter’s fast byte parsing
but still emits Circe’s AST
so Circe Encoder / Decoder can run unchanged

```sh
Circe:
bytes → AST → domain → AST → bytes

jsoniter-core:
bytes → domain → bytes

jsoniter-circe:
bytes → AST (fast) → domain → AST → bytes (fast)
```

```scala

/**
 * A JSON value codec that parses and serialize to/from circe's JSON AST.
 *
 * @param maxDepth the maximum depth for decoding
 * @param initialSize the initial size hint for object and array collections
 * @param doSerialize a predicate that determines whether a value should be serialized
 * @param numberParser a function that parses JSON numbers
 * @param numberSerializer a function that serializes JSON numbers
 * @return The JSON codec
 */
final class JsoniterScalaCodec(
                                maxDepth: Int,
                                initialSize: Int,
                                doSerialize: Json => Boolean,
                                numberParser: JsonReader => Json,
                                numberSerializer: (JsonWriter, JsonNumber) => Unit) extends JsonValueCodec[Json]
```

parses bytes → Circe AST and serializes Circe AST → bytes using jsoniter’s `JsonReader / JsonWriter`.

jsoniter-circe replaces only Circe’s JSON parser and printer.
It does NOT replace Circe’s encoders, decoders

## pure Circe (no jsoniter)

```scala
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

case class QuoteRequest(amountMUR: BigDecimal)

val jsonString = """{"amountMUR":100}"""
```

What happens step by step
```sh
[String]
   |
   |  (1) Circe parser
   v
[io.circe.Json]   <-- AST (tree of objects)
   |
   |  (2) Circe Decoder[QuoteRequest]
   v
[QuoteRequest]
```

Reverse direction:
```sh
[QuoteRequest]
   |
   |  (3) Circe Encoder[QuoteRequest]
   v
[io.circe.Json]
   |
   |  (4) Circe Printer
   v
[String]
```

Important
- Steps (1) and (4) are slow
- Steps (2) and (3) are unchanged by jsoniter-circe


**Now we add jsoniter-scala-circe**
```scala
import com.github.plokhotnyuk.jsoniter_scala.circe._
import com.github.plokhotnyuk.jsoniter_scala.core._
```
Same input, same output, different internals
```scala
val json: Json = readFromArray[Json](jsonString.getBytes)
val req: QuoteRequest = json.as[QuoteRequest].toOption.get

```

What happens now
```sh
[String → bytes]
   |
   |  (1') jsoniter parser   <-- REPLACED
   v
[io.circe.Json]             <-- SAME AST TYPE
   |
   |  (2) Circe Decoder
   v
[QuoteRequest]
```

Reverse:
```sh
[QuoteRequest]
   |
   |  (3) Circe Encoder
   v
[io.circe.Json]
   |
   |  (4') jsoniter writer   <-- REPLACED
   v
[bytes → String]
```

```sh
| Step            | Circe default           | With jsoniter-circe   |
| --------------- | ----------------------- | --------------------- |
| Parse JSON text | `io.circe.parser.parse` | `jsoniter JsonReader` |
| Print JSON text | `Printer.noSpaces`      | `jsoniter JsonWriter` |
| JSON AST type   | `io.circe.Json`         | same                  | 
| Encoder         | `Encoder[A]`            | same                  |
| Decoder         | `Decoder[A]`            | same                  |
| Auto-derivation | Circe                   | same                  |
```
Something that makes Open Banking stand out is enabling real-time payments. Open banking payment solutions sever the role of conventional credit card payments that may take as much as 3- 5 days to clear, by settling transactions almost immediately. 

Some new payment solutions are Direct Bank Transfers (DBTs), mobile wallets, and payment initiation services, which have been made possible by Open Banking.

