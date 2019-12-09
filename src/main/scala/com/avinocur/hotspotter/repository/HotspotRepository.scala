package com.avinocur.hotspotter.repository

import java.time.LocalDateTime

import cats.effect.IO
import cats.implicits._
import com.avinocur.hotspotter.LogSupport
import com.avinocur.hotspotter.model.{KeyHit, KeyHitRecord}
import com.avinocur.hotspotter.utils.config.HotspotterConfig.KeyHitsConfig


class HotspotRepository(countersConnection: CountersConnection[IO], keyHitsConfig: KeyHitsConfig, bucketGenerator: BucketGenerator) extends HotspotRepositoryLike[IO] with LogSupport {
  import HotspotRepository._

  def save(keyHits: Seq[KeyHit]): IO[Unit] = {
    (
      for {
        keyHitRecord <- groupByKey(keyHits)
      } yield countersConnection.incrementCounter(bucketGenerator.currentBucket(LocalDateTime.now()),
        keyHitRecord.key, keyHitsConfig.expireAt, keyHitRecord.count)
    ).toList.sequence_.recoverWith {
      case e: Exception =>
        log.error(s"Could not save keys: ${keyHits.map(_.key).mkString(",")}", e)
        throw HotspotRepositoryException("Error saving keys to repository", e)
    }
  }

  def getTopKeys(): IO[List[String]] = {
    val timeWindow = keyHitsConfig.timeWindowHours
    val counterBuckets = bucketGenerator.aggregationWindowBuckets(LocalDateTime.now(), timeWindow)

    countersConnection.getTopKeys(counterBuckets, keyHitsConfig.keyLimit).map(t => t.toList).recoverWith {
      case e: Exception =>
        log.error(s"Could not retrieve top keys.", e)
        throw HotspotRepositoryException("Error retrieving top keys.", e)
    }
  }
}

trait HotspotRepositoryLike[F[_]] {
  def save(keyHits: Seq[KeyHit]): F[Unit]
  def getTopKeys(): F[List[String]]
}

object HotspotRepository {
  def groupByKey(hits: Seq[KeyHit]): Seq[KeyHitRecord] = hits.groupBy(_.key).map {
    case (key, keyHits) => KeyHitRecord(key, keyHits.size.toDouble)
  }.toSeq
}

final case class HotspotRepositoryException(message: String = "", cause: Throwable = None.orNull) extends RuntimeException(message, cause)
