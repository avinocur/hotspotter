package com.avinocur.hotspotter.repository

import akka.actor.ActorSystem
import cats.effect.IO
import cats.implicits._
import com.avinocur.hotspotter.model.KeyHitRecord
import com.avinocur.hotspotter.utils.TestCatsEffectsIOAsync
import com.avinocur.hotspotter.utils.config.HotspotterConfig.KeyHitsConfig
import com.github.sebruck.EmbeddedRedis
import org.scalatest.BeforeAndAfterEach

import redis.RedisClient
import redis.embedded.RedisServer

import scala.concurrent.duration._

class RedisConnectorTest extends TestCatsEffectsIOAsync with EmbeddedRedis with BeforeAndAfterEach {
  implicit val actorSystem = ActorSystem("test-system")

  var maybeRedis: Option[RedisServer] = None
  var maybeRedisPort: Option[Int] = None
  var maybeRedisConnector: Option[RedisConnector] = None
  var maybeRedisClient: Option[RedisClient] = None
  val keyHitsConfig = KeyHitsConfig(expireAt = 2 minutes, currentKeyExpireAt = 2 minutes, keyLimit = 3, timeWindowHours = 4)

  def redisConnector = maybeRedisConnector.get
  def redisClient = maybeRedisClient.get

  override def beforeEach() = {
    maybeRedis = Some(startRedis()) // A random free port is chosen
    maybeRedisPort = Some(maybeRedis.get.ports().get(0))
    maybeRedisClient = Some(RedisClient("localhost", maybeRedisPort.get, None))
    maybeRedisConnector = Some(new RedisConnector(maybeRedisClient.get, keyHitsConfig))
  }


  testResultAsync("incrementCounter. No previous keys. Should create key in specified bucket"){
    for {
      _ <- redisConnector.incrementCounter("ABUCKET", "AKEY", 1 hour, 2d)
      res <- IO.fromFuture(IO {redisClient.zscore("ABUCKET", "AKEY")})
    } yield res
  }{ zscoreRes =>
    zscoreRes shouldBe Some(2d)
  }

  testResultAsync("incrementCounter. Key already present. Should increment key in specified bucket"){
    val increments = List(redisConnector.incrementCounter("ABUCKET", "AKEY", 1 hour, 2d),
      redisConnector.incrementCounter("ABUCKET", "AKEY", 1 hour, 5d)).sequence_
    for {
      _ <- increments
      res <- IO.fromFuture(IO {redisClient.zscore("ABUCKET", "AKEY")})
    } yield res
  }{ zscoreRes =>
    zscoreRes shouldBe Some(7d)
  }

  testResultAsync("getTopKeys. No keys. Should return empty") {
    redisConnector.getTopKeys("2012-12-06-23":: Nil, 3)
  }{ res: Seq[String] =>
    res.size shouldBe 0
  }

  testResultAsync("getTopKeys. One bucket one key. Should return that key.") {
    for {
      _ <- redisConnector.incrementCounter("2012-12-06-23", "1111", 1 hours, 1)
      res <- redisConnector.getTopKeys("2012-12-06-23":: Nil, 3)
    } yield res
  }{ res: Seq[String] =>
    res shouldBe "1111" :: Nil
  }

  testResultAsync("getTopKeys. One bucket many keys. Should return top keys sorted by hits.") {
    val keyHits = KeyHitRecord("1111", 2d) :: KeyHitRecord("2222", 5d) :: KeyHitRecord("3333", 3d) :: KeyHitRecord("4444", 7d) :: Nil

    for {
      _ <- keyHits.map {h => redisConnector.incrementCounter("2012-12-06-23", h.key, 1 hours, h.count)}.sequence_
      res <- redisConnector.getTopKeys("2012-12-06-23":: Nil, 3)
    } yield res
  }{ res: Seq[String] =>
    res shouldBe "4444" :: "2222" :: "3333" :: Nil
  }

  testResultAsync("getTopKeys. Many buckets, keys present in all buckets. Should summarize and return top keys sorted by hits.") {
    val bucket1 = "2012-12-06-22"
    val keyHits1 = KeyHitRecord("1111", 2d) :: KeyHitRecord("2222", 5d) :: KeyHitRecord("3333", 3d) :: KeyHitRecord("4444", 7d) :: Nil
    val f1 = keyHits1.map {h => redisConnector.incrementCounter(bucket1, h.key, 1 hours, h.count)}.sequence_

    val bucket2 = "2012-12-06-23"
    val keyHits2 = KeyHitRecord("1111", 5d) :: KeyHitRecord("2222", 9d) :: KeyHitRecord("3333", 3d) :: KeyHitRecord("4444", 2d) :: Nil
    val f2 = keyHits2.map {h => redisConnector.incrementCounter(bucket2, h.key, 1 hours, h.count)}.sequence_

    for {
      _ <- (f1::f2::Nil).sequence_
      res <- redisConnector.getTopKeys("2012-12-06-22" :: "2012-12-06-23":: Nil, 3)
    } yield res
  }{ res: Seq[String] =>
    res shouldBe "2222" :: "4444" :: "1111" :: Nil
  }

  testResultAsync("getTopKeys. Many buckets, som keys not present in all buckets. Should summarize correctly.") {
    val bucket1 = "2012-12-06-22"
    val keyHits1 = KeyHitRecord("1111", 2d) :: KeyHitRecord("2222", 5d) :: KeyHitRecord("3333", 3d) :: KeyHitRecord("4444", 7d) :: Nil
    val f1 = keyHits1.map {h => redisConnector.incrementCounter(bucket1, h.key, 1 hours, h.count)}.sequence_

    val bucket2 = "2012-12-06-23"
    val keyHits2 = KeyHitRecord("1111", 6d) :: KeyHitRecord("5555", 9d) :: KeyHitRecord("6666", 3d) :: KeyHitRecord("4444", 3d) :: Nil
    val f2 = keyHits2.map {h => redisConnector.incrementCounter(bucket2, h.key, 1 hours, h.count)}.sequence_

    // aggregated counts: 1111 -> 8, 2222 -> 5, 3333 -> 3, 4444 -> 10, 5555 -> 9, 6666 -> 3

    for {
      _ <- (f1::f2::Nil).sequence_
      res <- redisConnector.getTopKeys("2012-12-06-22" :: "2012-12-06-23":: Nil, 3)
    } yield res
  }{ res: Seq[String] =>
    res shouldBe "4444" :: "5555" :: "1111" :: Nil
  }

  override def afterEach() = {
    stopRedis(maybeRedis.get)
  }
}
