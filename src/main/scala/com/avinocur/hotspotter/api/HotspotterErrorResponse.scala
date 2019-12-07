package com.avinocur.hotspotter.api

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class HotspotterErrorResponse(httpCode: Int, status: String, messages: List[String])

object HotspotterErrorResponse {
  implicit val encoder: Encoder[HotspotterErrorResponse] = deriveEncoder
  implicit val decoder: Decoder[HotspotterErrorResponse] = deriveDecoder
}

case class HotspotterKeysResponse(keys: List[String])

object HotspotterKeysResponse {
  implicit val encoder: Encoder[HotspotterKeysResponse] = deriveEncoder
  implicit val decoder: Decoder[HotspotterKeysResponse] = deriveDecoder
}