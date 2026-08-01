package coinstar.wallet.util

import scala.math.BigDecimal.RoundingMode

import coinstar.wallet.domain.Asset

object MoneyFormat {

  def toDisplay(minor: Long, asset: Asset): String = {
    val bd = BigDecimal(minor) / BigDecimal(10).pow(asset.decimals)
    bd.setScale(asset.decimals, RoundingMode.DOWN).toString
  }

}
