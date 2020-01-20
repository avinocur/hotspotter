package com.avinocur.hotspotter.service

import java.time.LocalDateTime

import cats.effect.IO
import cats.implicits._
import com.avinocur.hotspotter.LogSupport
import com.avinocur.hotspotter.model.{KeyHit, KeyHitRecord}
import com.avinocur.hotspotter.repository.{BucketGenerator, CountersConnection}
import com.avinocur.hotspotter.utils.config.HotspotterConfig.KeyHitsConfig


class HotspotStoreService(countersConnection: CountersConnection[IO], keyHitsConfig: KeyHitsConfig, bucketGenerator: BucketGenerator) extends HotspotStoreServiceLike[IO] with LogSupport {

  /**
   * Stores the key hits in the current time bucket.
   *
   * @param keyHits key hits to be saved
   * @return
   */
    def save(keyHits: Seq[KeyHit]): IO[Unit] = {
    (
      for {
        keyHitRecord <- groupByKey(keyHits)
      } yield countersConnection.incrementCounter(bucketGenerator.currentBucket(LocalDateTime.now()),
        keyHitRecord.key, keyHitsConfig.expireAt, keyHitRecord.count)
    ).toList.sequence_.recoverWith {
      case e: Exception =>
        log.error(s"Could not save keys: ${keyHits.map(_.key).mkString(",")}", e)
        IO.raiseError(HotspotRepositoryException("Error saving keys to repository", e))
    }
  }

  /**
   * Retrieves the top N keys by the number of requests in the last H hours.
   * N is configured by the property key-hits.key-limit
   * H is configured by the property key-hits.time-window-hours
   *
   * @return A sorted list with the top keys
   */
  def getTopKeys(): IO[List[String]] = {
    val timeWindow = keyHitsConfig.timeWindowHours
    val counterBuckets = bucketGenerator.aggregationWindowBuckets(LocalDateTime.now(), timeWindow)

    countersConnection.getTopKeys(counterBuckets, keyHitsConfig.keyLimit).map(t => t.toList).recoverWith {
      case e: Exception =>
        log.error(s"Could not retrieve top keys.", e)
        IO.raiseError(HotspotRepositoryException("Error retrieving top keys.", e))
    }
  }

  private def groupByKey(hits: Seq[KeyHit]): Seq[KeyHitRecord] = hits.groupBy(_.key).map {
    case (key, keyHits) => KeyHitRecord(key, keyHits.size.toDouble)
  }.toSeq
}

trait HotspotStoreServiceLike[F[_]] {
  def save(keyHits: Seq[KeyHit]): F[Unit]
  def getTopKeys(): F[List[String]]
}

final case class HotspotRepositoryException(message: String = "", cause: Throwable = None.orNull) extends RuntimeException(message, cause)
