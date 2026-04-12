package com.migrantbank.service

import com.migrantbank.domain.*
import zio.*
import java.time.Instant
import java.util.UUID

/**
 * Buy Now Pay Later (BNPL) instalment plan.
 *
 * The merchant requests a BNPL plan for a customer.  The service:
 *   1. Checks the customer's `CreditBand`.
 *   2. Verifies the purchase amount fits within the band's `maxLoanMinor`.
 *   3. Issues a virtual card via `CardService` (or reuses an existing one)
 *      with a spending limit equal to the purchase amount.
 *   4. Creates an instalment schedule with `instalments` equal payments.
 */
final case class BnplPlan(
    id: UUID,
    userId: UUID,
    merchantRef: String,
    purchaseAmount: Money,
    instalmentCount: Int,
    instalmentAmountMinor: Long,
    firstDueDate: String,
    cardId: UUID,
    creditBand: CreditBand,
    createdAt: Instant,
    status: BnplPlanStatus
) derives zio.json.JsonCodec

enum BnplPlanStatus derives CanEqual, zio.json.JsonCodec {
  case Active, FullyRepaid, Defaulted, Cancelled
}

final case class BnplInstalment(
    planId: UUID,
    sequence: Int,          // 1-based
    dueDate: String,        // ISO date string
    amountMinor: Long,
    currency: String,
    paid: Boolean
) derives zio.json.JsonCodec

trait BnplService {
  def requestPlan(
      userId: UUID,
      merchantRef: String,
      purchaseAmount: Money,
      instalmentCount: Int,
      correlationId: String
  ): IO[AppError, BnplPlan]

  def getPlan(planId: UUID): IO[AppError, BnplPlan]
  def listByUser(userId: UUID): IO[AppError, List[BnplPlan]]
  def instalments(planId: UUID): IO[AppError, List[BnplInstalment]]
  def markInstalmentPaid(
      planId: UUID,
      sequence: Int,
      correlationId: String
  ): IO[AppError, BnplInstalment]
}

object BnplService {

  val live: ZLayer[CreditScoringService & CardService, Nothing, BnplService] =
    ZLayer.fromZIO {
      for {
        scoring   <- ZIO.service[CreditScoringService]
        cardSvc   <- ZIO.service[CardService]
        plans     <- Ref.make(Map.empty[UUID, BnplPlan])
        instStore <- Ref.make(Map.empty[UUID, List[BnplInstalment]])
      } yield new BnplService {

        override def requestPlan(
            userId: UUID,
            merchantRef: String,
            purchaseAmount: Money,
            instalmentCount: Int,
            correlationId: String
        ): IO[AppError, BnplPlan] =
          for {
            _ <- ZIO
              .fail(AppError.Validation("instalmentCount must be between 1 and 12"))
              .when(instalmentCount < 1 || instalmentCount > 12)
            _ <- ZIO
              .fail(AppError.Validation("purchaseAmount must be > 0"))
              .when(purchaseAmount.amountMinor <= 0)

            creditScore <- scoring.score(userId)
            params = creditScore.params
            _ <- ZIO
              .fail(
                AppError.Validation(
                  s"Purchase amount ${purchaseAmount.amountMinor} exceeds your credit limit ${params.maxLoanMinor}"
                )
              )
              .when(purchaseAmount.amountMinor > params.maxLoanMinor)

            // Issue a virtual card for this BNPL plan
            existingCards <- cardSvc.list(userId)
            card <- existingCards
              .find(c => c.kind == CardKind.VIRTUAL && c.status == CardStatus.ACTIVE) match {
              case Some(c) => ZIO.succeed(c)
              case None =>
                ZIO.fail(AppError.NotFound(s"No active virtual card found for user $userId; issue one first"))
            }

            id  <- Random.nextUUID
            now <- Clock.instant
            today <- Clock.localDateTime.map(_.toLocalDate)

            instAmt = Math.max(1L, purchaseAmount.amountMinor / instalmentCount)
            plan = BnplPlan(
              id = id,
              userId = userId,
              merchantRef = merchantRef,
              purchaseAmount = purchaseAmount,
              instalmentCount = instalmentCount,
              instalmentAmountMinor = instAmt,
              firstDueDate = today.plusDays(30).toString,
              cardId = card.id,
              creditBand = creditScore.band,
              createdAt = now,
              status = BnplPlanStatus.Active
            )
            insts = (1 to instalmentCount).toList.map { i =>
              BnplInstalment(
                planId = id,
                sequence = i,
                dueDate = today.plusDays(30L * i).toString,
                amountMinor = instAmt,
                currency = purchaseAmount.currency,
                paid = false
              )
            }
            _ <- plans.update(_ + (id -> plan))
            _ <- instStore.update(_ + (id -> insts))
            _ <- ZIO.logInfo(
              s"[BNPL] Plan $id created for user=$userId merchant=$merchantRef " +
                s"amount=${purchaseAmount.amountMinor} ${purchaseAmount.currency} " +
                s"band=${creditScore.band} cid=$correlationId"
            )
          } yield plan

        override def getPlan(planId: UUID): IO[AppError, BnplPlan] =
          plans.get.flatMap { m =>
            ZIO.fromOption(m.get(planId)).orElseFail(AppError.NotFound(s"BNPL plan $planId not found"))
          }

        override def listByUser(userId: UUID): IO[AppError, List[BnplPlan]] =
          plans.get.map(_.values.filter(_.userId == userId).toList)

        override def instalments(planId: UUID): IO[AppError, List[BnplInstalment]] =
          instStore.get.flatMap { m =>
            ZIO.fromOption(m.get(planId)).orElseFail(AppError.NotFound(s"BNPL plan $planId not found"))
          }

        override def markInstalmentPaid(
            planId: UUID,
            sequence: Int,
            correlationId: String
        ): IO[AppError, BnplInstalment] =
          for {
            allInsts <- instalments(planId)
            inst <- ZIO
              .fromOption(allInsts.find(_.sequence == sequence))
              .orElseFail(AppError.NotFound(s"Instalment $sequence not found in plan $planId"))
            _ <- ZIO
              .fail(AppError.Conflict(s"Instalment $sequence is already paid"))
              .when(inst.paid)
            updated = inst.copy(paid = true)
            newList = allInsts.map(i => if i.sequence == sequence then updated else i)
            _ <- instStore.update(_ + (planId -> newList))
            // Check if all paid → mark plan as FullyRepaid
            _ <- ZIO.when(newList.forall(_.paid)) {
              plans.update(m =>
                m.get(planId) match {
                  case Some(p) => m + (planId -> p.copy(status = BnplPlanStatus.FullyRepaid))
                  case None    => m
                }
              )
            }
            _ <- ZIO.logInfo(
              s"[BNPL] Instalment paid plan=$planId seq=$sequence cid=$correlationId"
            )
          } yield updated
      }
    }
}
