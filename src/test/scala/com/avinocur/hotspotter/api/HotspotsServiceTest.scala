package com.avinocur.hotspotter.api

import com.avinocur.hotspotter.model.{KeyHit, KeyHits}
import com.avinocur.hotspotter.utils.TestCatsEffectsIOAsync
import org.http4s.{EntityDecoder, HttpService, Request, Response, Status, Uri}
import org.http4s.dsl.io.{POST, uri}
import cats.effect.IO
import com.avinocur.hotspotter.service.{HotspotRepositoryException, HotspotStoreServiceLike}
import io.circe.Json
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.BeforeAndAfterEach

class HotspotsServiceTest extends TestCatsEffectsIOAsync with MockitoSugar with ArgumentMatchersSugar with BeforeAndAfterEach {
  var hotspotStoreServiceMockOpt: Option[HotspotStoreServiceLike[IO]] = None
  def hotspotStoreServiceMock: HotspotStoreServiceLike[IO] = hotspotStoreServiceMockOpt.get

  var serviceOpt: Option[HttpService[IO]] = None
  def service: HttpService[IO] = serviceOpt.get

  override def beforeEach(): Unit = {
    hotspotStoreServiceMockOpt = Some(mock[HotspotStoreServiceLike[IO]])

    serviceOpt = Some(HotspotsService.service(hotspotStoreServiceMock))
  }

  testResultAsync("POST /keys with many keys should save on repository"){
    val json = KeyHits(KeyHit("KEY1")::KeyHit("KEY2")::Nil).asJson

    when(hotspotStoreServiceMock.save(any[Seq[KeyHit]])) thenReturn (IO {})

    postForKeys(json)
  } { response =>
    checkResponseWithBody(response, Accepted, "Key hits will be stored ASAP!")

    verify(hotspotStoreServiceMock, times(1)).save(KeyHit("KEY1")::KeyHit("KEY2")::Nil)
  }

  testResultAsync("POST /keys Empty keys should not fail"){
    val json = KeyHits(Nil).asJson

    when(hotspotStoreServiceMock.save(any[Seq[KeyHit]])) thenReturn (IO {})

    postForKeys(json)
  } { response =>
    checkResponseWithBody(response, Accepted, "Key hits will be stored ASAP!")

    verify(hotspotStoreServiceMock, times(1)).save(Nil)
  }

  testResultAsync("POST /keys Repository throws exception. Should return error response"){
    val json = KeyHits(KeyHit("KEY1")::KeyHit("KEY2")::Nil).asJson

    when(hotspotStoreServiceMock.save(any[Seq[KeyHit]])) thenReturn (IO.raiseError(HotspotRepositoryException("Intended Test Exception")))

    postForKeys(json)
  } { response =>
    checkResponseWithBody(response, InternalServerError, HotspotterErrorResponse(500, "Internal Server Error",
      List(s"Unexpected Error. - com.avinocur.hotspotter.service.HotspotRepositoryException: Intended Test Exception")).asJson)
  }

  testResultAsync("GET /hotspots Response is empty. Should return empty"){
    when(hotspotStoreServiceMock.getTopKeys()).thenReturn(IO {Nil})

    getForHotspots()
  } { response =>
    checkResponseWithBody(response, Ok, HotspotterKeysResponse(Nil).asJson)
  }

  testResultAsync("GET /hotspots Response has keys. Should return keys"){
    when(hotspotStoreServiceMock.getTopKeys()).thenReturn(IO { "KEY1" :: "KEY2" :: "KEY3" :: Nil })

    getForHotspots()
  } { response =>
    checkResponseWithBody(response, Ok, HotspotterKeysResponse("KEY1" :: "KEY2" :: "KEY3" :: Nil).asJson)
  }

  testResultAsync("GET /hotspots Repository throws exception. Should return error response"){
    when(hotspotStoreServiceMock.getTopKeys()) thenReturn (IO.raiseError(HotspotRepositoryException("Intended Test Exception")))

    getForHotspots()
  } { response =>
    checkResponseWithBody(response, InternalServerError, HotspotterErrorResponse(500, "Internal Server Error",
      List(s"Unexpected Error. - com.avinocur.hotspotter.service.HotspotRepositoryException: Intended Test Exception")).asJson)
  }

  testResultAsync("GET /hotspots/key Key is not a hotspot. Should return NotFound"){
    when(hotspotStoreServiceMock.getTopKeys()).thenReturn(IO {Nil})

    getForHotspotKey("AKEY")
  } { response =>
    checkResponseWithoutBody(response, NotFound)
  }

  testResultAsync("GET /hotspots/key Key is a hotspot. Should return NoContent"){
    when(hotspotStoreServiceMock.getTopKeys()).thenReturn(IO { "KEY1" :: "KEY2" :: "KEY3" :: Nil })

    getForHotspotKey("KEY2")
  } { response =>
    checkResponseWithoutBody(response, NoContent)
  }

  testResultAsync("GET /hotspots/key Repository throws exception. Should return error response"){
    when(hotspotStoreServiceMock.getTopKeys()) thenReturn (IO.raiseError(HotspotRepositoryException("Intended Test Exception")))

    getForHotspotKey("AKEY")
  } { response =>
    checkResponseWithBody(response, InternalServerError, HotspotterErrorResponse(500, "Internal Server Error",
      List(s"Unexpected Error. - com.avinocur.hotspotter.service.HotspotRepositoryException: Intended Test Exception")).asJson)
  }

  def postForKeys(json: Json): IO[Response[IO]] =
    service.orNotFound.run(Request[IO](POST, uri("/keys")).withBody(json).unsafeRunSync)

  def getForHotspots(): IO[Response[IO]] =
    service.orNotFound.run(Request[IO](GET, uri("/hotspots")))

  def getForHotspotKey(key: String): IO[Response[IO]] = {
    val url = s"/hotspots/$key"
    service.orNotFound.run(Request[IO](GET, Uri.fromString(url).right.get))
  }

  def checkResponseWithBody[A](actual: Response[IO], expectedStatus: Status, expectedBody: A)
                              (implicit ev: EntityDecoder[IO, A]): Unit = {
    actual.status shouldBe (expectedStatus)
    actual.as[A].unsafeRunSync shouldBe (expectedBody)
  }

  def checkResponseWithoutBody(actual: Response[IO], expectedStatus: Status): Unit = {
    actual.status shouldBe (expectedStatus)
  }

}
