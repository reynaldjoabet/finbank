package services

import zio.Task

trait CameroonRevenueAuthority {
  def calculateVAT(amount: Double): Task[Double]
}
