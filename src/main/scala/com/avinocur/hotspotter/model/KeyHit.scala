package com.avinocur.hotspotter.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class KeyHit(key: String)

object KeyHit {
  implicit val encoder: Encoder[KeyHit] = deriveEncoder
  implicit val decoder: Decoder[KeyHit] = deriveDecoder
}

case class KeyHits(hits: Seq[KeyHit])

object KeyHits {
  implicit val encoder: Encoder[KeyHits] = deriveEncoder
  implicit val decoder: Decoder[KeyHits] = deriveDecoder
}

case class KeyHitRecord(key: String, count: Double)

