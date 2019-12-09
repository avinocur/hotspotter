package com.avinocur.hotspotter.api

import cats.effect.IO
import com.avinocur.hotspotter.LogSupport
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.http4s.Response
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import io.circe.syntax._


trait HotspotterHttp4sErrorHandling extends Http4sDsl[IO] with LogSupport {
  def handleService[A](action: IO[A])(thunk: A => IO[Response[IO]]): IO[Response[IO]] = {
    action.attempt.flatMap {
      case Right(r) =>
        thunk(r)
      case Left(cause) =>
        log.error("Unexpected error", cause)

        val errorMessages = List(s"Unexpected Error. - ${cause.getClass.getName}: ${cause.getMessage}")
        val apiError = HotspotterErrorResponse(500, "Internal Server Error", errorMessages)
        InternalServerError(apiError.asJson)
    }
  }
}

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