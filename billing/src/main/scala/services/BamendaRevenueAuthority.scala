package services

import zio.Task

trait BamendaRevenueAuthority {
  def calculateVAT(amount: Double): Task[Double]
}
