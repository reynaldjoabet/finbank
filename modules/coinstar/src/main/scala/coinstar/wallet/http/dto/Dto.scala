package coinstar.wallet.http.dto

import zio.json.*

final case class ErrorDto(error: String, message: String)
object ErrorDto {
  given JsonEncoder[ErrorDto] = DeriveJsonEncoder.gen
  given JsonDecoder[ErrorDto] = DeriveJsonDecoder.gen
}
final case class CreateWalletRequest(asset: String)
object CreateWalletRequest {
  given JsonEncoder[CreateWalletRequest] = DeriveJsonEncoder.gen
  given JsonDecoder[CreateWalletRequest] = DeriveJsonDecoder.gen
}
final case class WalletDto(
    id: String,
    asset: String,
    balance_minor: Long,
    decimals: Int,
    balance: String
)
object WalletDto {
  given JsonEncoder[WalletDto] = DeriveJsonEncoder.gen
  given JsonDecoder[WalletDto] = DeriveJsonDecoder.gen
}
final case class RedeemVoucherRequest(wallet_id: String, voucher_code: String)
object RedeemVoucherRequest {
  given JsonEncoder[RedeemVoucherRequest] = DeriveJsonEncoder.gen
  given JsonDecoder[RedeemVoucherRequest] = DeriveJsonDecoder.gen
}
final case class RedeemVoucherResponse(tx_id: String)
object RedeemVoucherResponse {
  given JsonEncoder[RedeemVoucherResponse] = DeriveJsonEncoder.gen
  given JsonDecoder[RedeemVoucherResponse] = DeriveJsonDecoder.gen
}
final case class DevTokenRequest(user_id: String)
object DevTokenRequest {
  given JsonEncoder[DevTokenRequest] = DeriveJsonEncoder.gen
  given JsonDecoder[DevTokenRequest] = DeriveJsonDecoder.gen
}
final case class DevTokenResponse(token: String)
object DevTokenResponse {
  given JsonEncoder[DevTokenResponse] = DeriveJsonEncoder.gen
  given JsonDecoder[DevTokenResponse] = DeriveJsonDecoder.gen
}
