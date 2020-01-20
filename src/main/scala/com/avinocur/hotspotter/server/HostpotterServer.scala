package com.avinocur.hotspotter.server

import java.util.concurrent.{ExecutorService, Executors}

import com.avinocur.hotspotter.utils.ThreadUtils._
import akka.actor.ActorSystem
import cats.effect.IO
import com.avinocur.hotspotter.LogSupport
import com.avinocur.hotspotter.api.{HotspotsService, HotspotterErrorResponse, HotspotterKeysResponse}
import com.avinocur.hotspotter.repository.{BucketGenerator, CountersConnection, CountersRedisConnector}
import com.avinocur.hotspotter.service.{HotspotStoreService, HotspotStoreServiceLike}
import com.avinocur.hotspotter.utils.config.HotspotterConfig
import fs2.StreamApp
import fs2.internal.NonFatal
import org.http4s._
import org.http4s.dsl.Http4sDsl
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
  val hotspotRepository: HotspotStoreServiceLike[IO] = new HotspotStoreService(redisConnection, HotspotterConfig.keyHits, new BucketGenerator())

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

  override def stream(args: List[String], requestShutdown: IO[Unit]): fs2.Stream[IO, StreamApp.ExitCode] = {
    BlazeBuilder[IO]
      .bindHttp(serverConfig.port, serverConfig.interface)
      .withServiceErrorHandler(errorHandler)
      .mountService(HotspotsService.service(hotspotRepository), "/")
      .withExecutionContext(ExecutionContext.fromExecutor(executor))
      .serve
  }
}
