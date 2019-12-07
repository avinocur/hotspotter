package com.avinocur.hotspotter.repository

import cats.effect.IO
import cats.implicits._
import com.avinocur.hotspotter.LogSupport
import com.avinocur.hotspotter.model.{KeyHit, KeyHitRecord}
import com.avinocur.hotspotter.utils.BucketUtils
import com.avinocur.hotspotter.utils.config.HotspotterConfig


class RedisHotspotRepository(redisClient: RedisConnection[IO]) extends HotspotRepository with LogSupport {
  import HotspotRepository._

  def save(keyHits: Seq[KeyHit]): IO[Unit] = {
    (for {
      keyHitRecord <- groupByKey(keyHits)
    } yield redisClient.incrementCounter(BucketUtils.currentBucket, keyHitRecord.key, HotspotterConfig.keyHits.expireAt, keyHitRecord.count)).toList.sequence_
  }

  def getTopKeys(): IO[List[String]] =
    redisClient.getTopKeys().map(t => t.toList)
}

trait HotspotRepository {
  def save(keyHits: Seq[KeyHit]): IO[Unit]
  def getTopKeys(): IO[List[String]]
}

object HotspotRepository {
  def groupByKey(hits: Seq[KeyHit]): Seq[KeyHitRecord] = hits.groupBy(_.key).map {
    case (key, keyHits) => KeyHitRecord(key, keyHits.size.toDouble)
  }.toSeq
}
