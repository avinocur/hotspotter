package com.avinocur.hotspotter.server

import java.util.concurrent.{ExecutorService, Executors}

import com.avinocur.hotspotter.utils.ThreadUtils._
import akka.actor.ActorSystem
import cats.effect.IO
import com.avinocur.hotspotter.LogSupport
import com.avinocur.hotspotter.api.{HotspotterErrorResponse, HotspotterKeysResponse}
import com.avinocur.hotspotter.model.KeyHits
import com.avinocur.hotspotter.repository.{BucketGenerator, CountersConnection, CountersRedisConnector, HotspotRepository, HotspotRepositoryLike}
import com.avinocur.hotspotter.utils.config.HotspotterConfig
import fs2.StreamApp
import fs2.internal.NonFatal
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import io.circe.syntax._
import org.http4s.headers.{Connection, `Content-Length`}
import org.http4s.server.ServiceErrorHandler
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext

object HostpotterServer extends StreamApp[IO] with Http4sDsl[IO] with LogSupport{
  import HotspotterKeysResponse._
  import HotspotterErrorResponse._

  val serverConfig: HotspotterConfig.Server = HotspotterConfig.server

  private val executor: ExecutorService = Executors.newFixedThreadPool(serverConfig.threadPool.size, namedThreadFactory("hotspotter-server-pool"))
  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutorService(executor)
  implicit val actorSystem: ActorSystem = createActorSystem

  val redisConnection: CountersConnection[IO] = CountersRedisConnector()
  val hotspotRepository: HotspotRepositoryLike = new HotspotRepository(redisConnection, HotspotterConfig.keyHits, new BucketGenerator())

  protected def createActorSystem: ActorSystem = ActorSystem("RedisClient")

  def errorHandler: ServiceErrorHandler[IO] = req => {
    case mf: MessageFailure =>
      val errorMsg = s"Failed for request: ${req}. Message: ${mf.message}."
      mf.cause match {
        case None => log.error(errorMsg)
        case Some(cause) => log.error(errorMsg, cause)
      }

      mf.toHttpResponse(req.httpVersion)
    case NonFatal(t) =>
      log.error(s"Non Fatal error for request: ${req}.", t.getCause)

      IO.pure(
        Response(
          Status.InternalServerError,
          req.httpVersion,
          Headers(
            Connection("close".ci),
            `Content-Length`.zero
          )))
  }

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

  val service = HttpService[IO] {
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

  override def stream(args: List[String], requestShutdown: IO[Unit]): fs2.Stream[IO, StreamApp.ExitCode] = {
    BlazeBuilder[IO]
      .bindHttp(serverConfig.port, serverConfig.interface)
      .withServiceErrorHandler(errorHandler)
      .mountService(service, "/")
      .withExecutionContext(ExecutionContext.fromExecutor(executor))
      .serve
  }
}
