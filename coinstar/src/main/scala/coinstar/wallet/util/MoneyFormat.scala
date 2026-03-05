package coinstar.wallet.util

import coinstar.wallet.domain.Asset
import scala.math.BigDecimal.RoundingMode

object MoneyFormat {
  def toDisplay(minor: Long, asset: Asset): String = {
    val bd = BigDecimal(minor) / BigDecimal(10).pow(asset.decimals)
    bd.setScale(asset.decimals, RoundingMode.DOWN).toString
  }
}
