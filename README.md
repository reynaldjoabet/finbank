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
in a traditional transfer from Lagos to Nairobi, the money doesn't go directly. It usually travels like this:

- Step 1: Your bank in Lagos sends NGN to a "Correspondent Bank" in London or New York.
- Step 2: That bank converts it to USD (taking a cut).
- Step 3: The USD is then sent to a bank in Kenya.
- Step 4: The Kenyan bank converts it to KES (taking another cut).

By using a stablecoin (USDC) as the "universal translator," you bypass the global SWIFT network and the European/US middleman banks that charge flat fees of $25–$50 per hop.

Settlement: Convert NGN to USDC → Send USDC to Kenya → Convert USDC to KES.

Small SMEs share a database and schema. Isolation is handled via PostgreSQL Row-Level Security (RLS).

The "Noisy Neighbor" & Data Leaks

In a shared environment, one heavy user can crash the app for everyone

```sql
-- Enforced at the DB level so Scala code can't "forget" the WHERE clause
CREATE POLICY tenant_isolation ON transactions 
USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

Routing:Subdomains (client1.unitypay.africa) or JWT Claims.

```scala
import zio.*
import zio.http.*
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

// Define your data and codec
case class User(name: String, age: Int)
object User {
  implicit val codec: JsonValueCodec[User] = JsonCodecMaker.make
}

val app: Routes[Any, Response] = Routes(
  Method.POST / "user" -> handler { (req: Request) =>
    for {
      // Read raw bytes from the request body
      bodyBytes <- req.body.asArray
      // Parse using jsoniter
      user <- ZIO.attempt(readFromArray[User](bodyBytes))
        .mapError(e => Response.badRequest(s"Invalid JSON: ${e.getMessage}"))
    } yield Response.json(writeToString(user))
  }
)
```

To get the maximum performance benefit, use `req.body.asArray` or `req.body.asStream`. Converting the body to a String first (via asString) creates extra allocations and negates some of the "zero-copy" benefits of `jsoniter-scala`

Unlike `zio-json`, which returns an Either, `jsoniter-scala` throws exceptions by default. Wrap your calls in ZIO.attempt

Prefer singular package/folder names Because a package is more like a “namespace/category” than a “collection”.

```scala
// 1. Constructor Parameters for dependencies
case class UserRepoLive(database: Database) extends UserRepo {
  def findUser(id: String) = ???
}

object UserRepoLive {
  // 2. The Layer wires the constructor into the ZIO ecosystem
  val layer: ZLayer[Database, Throwable, UserRepoLive] = 
    ZLayer.fromFunction(UserRepoLive.apply _)
}
```
In most of these other languages, the "Handler" is just a function. In ZIO HTTP, a Handler is a data structure. This means you can transform it, zip it with other handlers, and inspect it before the server even starts, which is much harder to do in Go or Express

Routes, which are essentially a collection of Handlers mapped to specific paths and methods

Unchecked Exceptions
- extends RuntimeExceptio
- Usually represents avoidable errors
- Can be thrown without declaration

Checked Exception
- extend Exception
- usually represent expectable errors
- must be declared

`sbt dependencyTree`

Quill 4.8.x uses Scalafmt internally for SQL formatting

### Chimney
- Persistence Layer → Domain: Mapping a DatabaseRow (Anorm/Doobie) to a DomainEntity.
- Domain → API Layer: Mapping a DomainEntity to a JSON DTO (Circe/Jsoniter).
- Protobuf/gRPC: Mapping generated Java/ScalaPB classes to your internal clean code models.

The Patcher: If you have a large `Config` object and a smaller `Update` object, you can "patch" the config:
Scala

`val newConfig = config.patchUsing(update)`

```scala
import io.getquill.*

class IdempotencyRepoLive {
  // Change from:
  // val table = quote { query[IdempotencyRow] }
  
  // To:
  inline def table = quote { query[IdempotencyRow] }
  
  def insert(row: IdempotencyRow) = {
    // Now this can be fully inlined at compile time
    run(table.insertValue(lift(row)))
  }
}
```

```scala
final class WalletRepoLive(quill: Quill.Postgres[SnakeCase]) extends WalletRepo {
  import quill.*

  // Change this:
  // private val wallets = quote(querySchema[WalletRow]("wallets"))
  
  // To this:
  private inline def wallets = quote(querySchema[WalletRow]("wallets"))

  // ...existing code...
}
```

In Scala 3 + Quill, the macro system needs to see the full query expression at compile time. When you use:

`private val wallets = quote(...)`
The reference this.wallets is opaque to the macro—it only sees a `Quoted[EntityQuery[WalletRow]] `value, not the actual query definition.

With `inline def`, the compiler substitutes the body directly:
```scala
// What you write:
run(wallets.insertValue(lift(row)))

// What the compiler sees after inlining:
run(quote(querySchema[WalletRow]("wallets")).insertValue(lift(row)))
```
Now Quill's macro can analyze the entire expression and generate static SQL

For every Quill repository class, ensure:

`Table definitions` use `inline def` (not val or def)

Static Query (Preferred)
- SQL is generated at compile time
The query string is embedded directly in the bytecode
Zero runtime overhead for query construction
Compile-time syntax validation
```scala
// Static: Quill sees the full expression at compile time
inline def table = quote(query[User])
run(table.filter(_.id == lift(userId)))
// Compiles to: SELECT id, name FROM users WHERE id = ?
```
Dynamic Query (Fallback)
SQL is generated at runtime
Query construction happens every time the method is called
Slight performance overhead
Still type-safe, but no compile-time SQL validation

```scala
// Dynamic: Quill can't see through `this.table` reference
val table = quote(query[User])  // opaque at macro expansion
run(table.filter(_.id == lift(userId)))
// Query built at runtime each call
```
Why Does This Happen?
Quill uses Scala macros to inspect your code. When you write:

```scala
private val vouchers = quote(querySchema[KioskVoucherRow]("kiosk_vouchers"))
run(vouchers.filter(_.code == lift(code)))
```
At the `run(...)` call site, the macro sees `this.vouchers`—a reference to a value—not the actual query definition. It can't "look inside" the `val` to see what query it contains.

With `inline def`, Scala substitutes the body before the macro runs, so Quill sees the full query

```scala
// This won't compile:
private inline val vouchers = quote(querySchema[KioskVoucherRow]("kiosk_vouchers"))
// Error: inline value must have a literal constant type
```
`inline val` in Scala 3 requires a literal constant type (like `42`, "`hello"`, `true`). The `quote(...)` macro returns a `Quoted[EntityQuery[...]]`, which is not a literal constant

```scala
// This works:
private inline def vouchers = quote(querySchema[KioskVoucherRow]("kiosk_vouchers"))
```
`inline def` doesn't have the literal constant restriction. The compiler substitutes the entire method body at call sites, allowing Quill's macro to see the full query expression.

For Quill table definitions, you must use `inline def`.

```scala
private inline def wallets = quote(querySchema[WalletRow]("wallets"))
//                           ↑ This is the DEFINITION SITE

run(wallets.filter(_.id == lift(id)))
//  ↑ CALL SITE - Quill's macro looks here

// With `inline def`, the EXPANSION SITE gets:
run(quote(querySchema[WalletRow]("wallets")).filter(_.id == lift(id)))
// Now Quill sees the full query at the CALL SITE after inlining
```

liquidity refers to how quickly and easily an asset can be converted into cash without significantly affecting its market price

High Liquidity (Cash-like)
- Cash: The most liquid asset because it is already the medium of exchange.
- Public Stocks: You can usually sell shares of a major company (like Apple or Google) in seconds during market hours.
- Government Bonds: These are typically traded in massive volumes and can be sold very quickly

Low Liquidity (Illiquid)
- Real Estate: Selling a house can take months. If you needed the money tomorrow, you would likely have to drop the price significantly to find a buyer.

- Collectibles: Rare art, vintage cars, or jewelry require finding a specific buyer who agrees on the value.
- Private Equity

Why It Matters
- For Individuals: It’s about having an "emergency fund." If all your wealth is tied up in a house, you are "asset rich but cash poor." You might struggle to pay for a sudden medical bill or car repair.
- For Businesses: Liquidity ratios measure if a company has enough cash or "near-cash" assets to pay its short-term debts. If a company runs out of liquidity, it can go bankrupt even if it owns valuable land or equipment

Imagine you have $10,000 in a Savings Account and $10,000 in a Rare Pokémon Card.

If you need to pay rent tomorrow, the savings account is liquid—you can withdraw it instantly.

The Pokémon card is illiquid—finding a buyer who will pay the full $10,000 might take weeks. If you sell it in 24 hours, you might only get $6,000.

One of the most "serious and profitable" issues in the CEMAC region (Cameroon, Gabon, etc.) is the liquidity and transparency gap in informal savings circles (Tontines)

While Tontines move billions of CFA, they are manual, risky (the "treasurer" can disappear), and disconnected from the formal economy.

### The Problem
Traditional Tontines rely on physical meetings and cash. If a member doesn't pay, the cycle breaks. If the treasurer is dishonest, the money is lost.

An application where:
- Collection is automated via Mobile Money API pull requests.
- Payouts are scheduled via Mobile Money disbursement.
- Credit Scoring is generated based on contribution history, allowing members to eventually access formal micro-loans.

```sbt
// Core Domain & Business Logic Module
lazy val core = (project in file("core"))
// API & Mobile Money Integration Module
lazy val api = (project in file("api"))

// Root Project Aggregator
lazy val root = (project in file("."))
  .aggregate(core, api)
 ``` 

 ```sh
 ziotontine/
├── build.sbt
├── core/
│   └── src/main/scala/com/ziotontine/core/
│       ├── Domain.scala       // TontineStatus, Member, Cycle
│       └── MoMoService.scala  // The trait and ZLayer implementations
└── api/
    └── src/main/scala/com/ziotontine/api/
        ├── Server.scala       // The ZIO HTTP App startup
        └── Routes.scala       // Webhooks for MoMo callbacks
 ```       

 ## Multiversal Equality
- Use `given CanEqual` in opaque type companions to enable safe equality checks
- This prevents accidental use of `==` on incompatible types, which can lead to bugs
- Prefer **opaque types** over case class wrappers — zero runtime cost, same type safety
- Example:
```scala
opaque type UserId = String
object UserId {
  def apply(value: String): UserId = value
  given CanEqual[UserId, UserId] = CanEqual.derived
}

opaque type ProductId = String
object ProductId {
  def apply(value: String): ProductId = value
  given CanEqual[ProductId, ProductId] = CanEqual.derived
}

// Now you can't accidentally compare a UserId to a ProductId
val u = UserId("u1")
val p = ProductId("p1")
// u == p  // compile error
```
`CanEqual` is not `symmetric` — `CanEqual[A, B]` only allows `a == b`, not `b == a`. So    
```scala
given CanEqual[A, B] = CanEqual.derived  // allows: a == b
given CanEqual[B, A] = CanEqual.derived  // allows: b == a
```

- fintech
- banking

### Banking and Payments
- `A2A (Account-to-Account)`: Payments that involve the transfer of funds between two accounts owned by a single party. These accounts may be at the same or different financial institutions.
- `accounts payable (AP)`: Amounts due to vendors or suppliers for goods or services received that have not yet been paid for.
- `accounts receivable (AR)`: Amounts owed for goods or services delivered that have not yet been paid for.
- `KYC (Know Your Customer)`: A standard banking risk assessment practice to prevent identity theft, money laundering, fraud, and terrorism by verifying customer identities and understanding their transaction habits. KYC is a mandatory requirement of legal compliance in the financial sector.
- `mobile wallet`: A type of digital wallet, this is a software application usually used in conjunction with a mobile payment system to facilitate electronic payments using a smartphone for online transactions as well as purchases at physical stores. Mobile wallets need to be linked to a user’s bank or credit card account.
- `non-sufficient funds (NSF)`: Occurs when a payment is attempted, but there aren’t enough funds in the account to cover it entirely. This commonly results in a fee and a canceled, or “bounced” payment. If the bank doesn’t cancel and covers the payment, it’s known as an overdraft.
- `overdraft`: Occurs when more money is spent than is available in a checking account. An overdraft fee will be charged, and the payment will be processed
- overlimit: When a cardholder exceeds the predetermined credit limit on a payment card, that cardholder’s account is deemed over-limit. At that point, the card issuer may decline the transaction or process the transaction and then assess a penalty fee for surpassing the agreed-upon limit.
- P2P (Peer-to-Peer): Payments that involve the transfer of funds between two different parties’ accounts. These accounts may be at the same or different financial institutions. Examples of P2P include Venmo, Zelle, and Cash App.
- payment: The transfer of value from one end party to another.
- payment aggregator: A service provider that facilitates online payments by enabling merchants to accept transactions without the need to set up a merchant account with a bank.
- payment gateway: An internet-based system used in an e-commerce transaction to transmit payment card information to a credit card processor for verification, completing the authorization process between the merchant and the consumer.
- `payment message structure (ISO 20022)`: An ISO 20022 payment initiation message is composed of three parts: Group Header, Payment Information, and Credit Transfer Transaction Information.
- `payment method`: The form of payment a consumer uses to purchase goods or services from a seller (e.g., cash, credit card, debit card, money order, bank transfer).
- `payment rails`: The technological infrastructure for moving money from one party to another. Some examples of payment rails include ACH, RTP, and cards.
- `payment service provider (PSP)`: A payment service provider is a third party that provides merchants the ability to accept electronic payments, enabling connectivity to financial institutions and credit card acquirers.
- `savings account`: An interest-bearing account that offers limited access to funds to encourage savings goals. A minimum balance may be required.
- `settlement`: The actual movement of funds from one financial institution to another that completes a transaction.
- `stablecoin`: A cryptocurrency designed to have a stable value by being pegged to a fiat currency or other assets like gold.
- `checking account`: Allows for easy access to funds and often comes bundled with a debit card and checks. No minimum balance is required. Also called a demand deposit account (DDA).

`BaaS utilizes APIs and webhooks to make integration seamless and cost-effective.`

`INFRA (db, http clients, config, logging)`

```scala

import com.github.plokhotnyuk.jsoniter_scala.macros.*

// Fastest codec generation settings
given CodecMakerConfig = CodecMakerConfig
  .withSkipUnexpectedFields(true)       // silently ignore unknown JSON fields (default)
  .withTransientDefault(true)           // omit fields equal to their default (default)
  .withTransientEmpty(true)             // omit empty collections (default)
  .withTransientNone(true)              // omit None fields (default)
  .withCheckFieldDuplication(false)     // skip duplicate-field check at runtime
  .withRequireDiscriminatorFirst(true)  // ADT discriminator must be first field (default)
  .withInlineOneValueClasses(true)      // inline single-arg wrapper types = zero
```  


`checkFieldDuplication(false)` is the main non-default tweak — it removes a hash-set lookup on every parsed field. Safe if your JSON source is trusted (internal services, databases). Keep it true for public/untrusted APIs.

`inlineOneValueClasses(true) `inlines wrapper types like case class UserId(value: String) — eliminates boxing at no correctness cost.

```scala
// Bad for production
given JsonCodec[MyModel] = JsonCodecMaker.make

// Good
given JsonValueCodec[MyModel] = JsonCodecMaker.make
```
```sh
 Ultra-Low Latency (trading, order routing, market data)
Sub-millisecond pauses — ZGC


-XX:+UseZGC

# Pre-fault all heap pages — eliminates first-touch latency spikes
-XX:+AlwaysPreTouch                    # gc_globals.hpp:181, default: false

# NUMA-aware allocation on multi-socket servers
-XX:+UseNUMA                           # globals.hpp:197, default: false

# Disable periodic scheduled safepoints (set 0 = off, default)
-XX:GuaranteedSafepointInterval=0      # globals.hpp, diagnostic

# Eliminate service thread wakeup jitter
-XX:ServiceThreadCleanupInterval=0     # globals.hpp, diagnostic

# Off-heap for order books / market data — bypasses GC entirely
-XX:MaxDirectMemorySize=16g            # globals.hpp, default: 0 (unlimited)

# ZGC-specific tuning
-XX:ZFragmentationLimit=5              # z_globals.hpp, default: 5.0
-XX:ZAllocationSpikeTolerance=2.0      # z_globals.hpp, default: 2.0
-XX:+ZProactive                        # z_globals.hpp, default: true

# Heap >32GB: disable compressed oops (encoding overhead gone)
-XX:-UseCompressedOops
```

**The 6 GC Collectors in JDK 25**

| Flag | Name | Status | Default |
|---|---|---|---|
| `-XX:+UseG1GC` | Garbage-First (G1) | Production | Yes — server-class machines |
| `-XX:+UseParallelGC` | Parallel | Production | Fallback if G1 not included |
| `-XX:+UseSerialGC` | Serial | Production | Non-server-class machines |
| `-XX:+UseZGC` | Z Garbage Collector | Production | No |
| `-XX:+UseShenandoahGC` | Shenandoah | Production | No |
| `-XX:+UseEpsilonGC` | Epsilon (no-op) | Experimental | No |

```sh
server-class machine → G1GC  (fallback: Parallel → Serial)
non-server machine   → SerialGC
```
A pause is when the JVM forces every application thread to stop — your code stops executing completely — while the GC does its work. This is called a stop-the-world (STW) event.

`"The Parallel collector is a stop-the-world garbage collector that does parts of the collection using parallel threads."`

The time your threads are frozen = the pause time.

**How each collector handles pauses**

| Collector | Flag | Pause behavior |
|---|---|---|
| Serial | `-XX:+UseSerialGC` | Full STW — one thread does all GC |
| Parallel | `-XX:+UseParallelGC` | Full STW — but many threads in parallel |
| G1 | `-XX:+UseG1GC` | Short STW pauses + concurrent background work |
| ZGC | `-XX:+UseZGC` | Almost all work is concurrent — pauses under 1ms |
| Shenandoah | `-XX:+UseShenandoahGC` | Same — concurrent, pause-focused |
| Epsilon | `-XX:+UseEpsilonGC` | No GC at all — no pauses, but OOM if heap fills |

```sh
-XX:MaxGCPauseMillis=<N>    # Tell the GC: "don't pause longer than N ms"
```
This is a goal, not a hard guarantee. G1 tries to respect it. ZGC/Shenandoah make it nearly irrelevant because their pauses are already sub-millisecond by design.