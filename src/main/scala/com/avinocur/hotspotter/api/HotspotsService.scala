package com.avinocur.hotspotter.api

import cats.effect.IO
import com.avinocur.hotspotter.model.KeyHits
import com.avinocur.hotspotter.repository.HotspotRepositoryLike
import org.http4s._
import org.http4s.circe._
import io.circe.syntax._


object HotspotsService extends HotspotterHttp4sErrorHandling {

  def service(hotspotRepository: HotspotRepositoryLike[IO]): HttpService[IO] = HttpService[IO] {
    case GET -> Root / "hotspots" / key  =>
      handleService {
        hotspotRepository.getTopKeys()
      } {
        topKeys =>
          if(key.isEmpty) BadRequest("Key is mandatory. Maybe you are looking for the /hotspots resource...")
          else if(topKeys.contains(key)) NoContent()
          else NotFound()
      }

    case GET -> Root / "hotspots" =>
      handleService{
        hotspotRepository.getTopKeys()
      }{
        topKeys => Ok(HotspotterKeysResponse(topKeys).asJson)
      }

    case req @ POST -> Root / "keys" => req.decodeWith(jsonOf[IO, KeyHits], strict = false) { keyHits =>
      handleService{
        hotspotRepository.save(keyHits.hits)
      }{
        _ => Accepted("Key hits will be stored ASAP!")
      }
    }
  }
}
