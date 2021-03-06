package com.avinocur.hotspotter.repository

import akka.actor.ActorSystem
import cats.implicits._
import cats.effect.IO
import com.avinocur.hotspotter.LogSupport
import com.avinocur.hotspotter.utils.config.HotspotterConfig
import com.avinocur.hotspotter.utils.config.HotspotterConfig.KeyHitsConfig
import redis.{RedisClient, RedisClientMasterSlaves, RedisCommands, RedisServer}
import redis.commands.Transactions

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait CountersConnection[F[_]] extends LogSupport {
  def incrementCounter(bucket: String, key: String, expireAt: Duration, quantity: Double = 1): F[Unit]
  def getTopKeys(counterBuckets: List[String], keyLimit: Int): F[Seq[String]]

  def flushAll: F[Boolean]
}

class CountersRedisConnector(client: Transactions with RedisCommands, keyHitsConfig: KeyHitsConfig) extends CountersConnection[IO] {
  /*
   * Bucket to store the cached aggregation.
   * This bucket will be kept alive by the time configured in key-hits.current-key-expire-at
   */
  val aggregationBucket = "aggregated"

  /**
   * Counters for each key are stored in hourly buckets in redis sorted sets. Each bucket contains the whole set for
   * the keys requested during that hour.
   * The operation used in redis to perform this is ZINCRBY: https://redis.io/commands/zincrby
   *
   * If the bucket does not exist, it will be created. If the key is not present, it will be created in the redis set.
   *
   * @param bucket the time bucket where the key should be updated
   * @param key the key to update
   * @param expireAt the time to live for the bucket
   * @param quantity the number of hits to increment for the given key
   * @return
   */
  override def incrementCounter(bucket: String, key: String, expireAt: Duration, quantity: Double): IO[Unit] =
    IO.fromFuture( IO {
      for {
        newScore ← client.zincrby(bucket, quantity, key)
        ttlSet ← client.expire(bucket, expireAt.toSeconds)
      } yield (newScore, ttlSet) match {
        case (_: Double, true) ⇒ log.debug(s"Saved ($quantity) [$bucket → $key] new score:> $newScore (expires in ${expireAt.toMinutes} minutes)")
        case (ns, tt) ⇒ log.error(s"Something went wrong saving [$bucket → $key] Redis response:> (score: $ns, expires: $tt)")
      }
    })

  /**
   * Retrieves the top keys from Redis.
   * The operation used is ZREVRANGE, which returns a range of elements sorted by score (in this case, key hits)
   * from the highest to the lowest.
   * See Redis docs: https://redis.io/commands/zrevrange
   *
   * To avoid having to compute this every time (performance is favored over precision), the result is cached in an
   * aggregationBucket for the time configured in key-hits.current-key-expire-at.
   *
   * @param counterBuckets The names of all the counter buckets to be used.
   * @param keyLimit The number of keys to consider (see key-hits.key-limit)
   * @return A sorted sequence of the top keys
   */
  override def getTopKeys(counterBuckets: List[String], keyLimit: Int): IO[Seq[String]] = {
    val lowerBound: Long = 0
    val upperBound: Long = keyLimit.toLong - 1

    (for {
      _ ← IO {log.debug(s"executing: ZREVRANGE $aggregationBucket $lowerBound $upperBound")}
      current ← IO.fromFuture(IO {client.zrevrange[String](aggregationBucket, lowerBound, upperBound)})
    } yield current match {
      case Nil ⇒ createAggregationBucket(counterBuckets, lowerBound, upperBound)
      case resp ⇒ IO.pure(resp)
    }).flatten
  }

  /**
   * Computes the union of the counterBuckets sorted sets, and stores the result in the aggregationBucket,
   * using the counterBuckets for each hour stored in redis.
   * The operation used is ZUNIONSTORE in Redis: https://redis.io/commands/zunionstore.
   *
   *
   * @param counterBuckets the names of the buckets in Redis to be considered
   * @param lowerBound the lower bound for the range of keys
   * @param upperBound the upper bound for the range of keys
   * @return the top aggregated keys, sorted descending by hits
   */
  private def createAggregationBucket(counterBuckets: List[String], lowerBound: Long, upperBound: Long): IO[Seq[String]] = {
    val expiration: Long = keyHitsConfig.currentKeyExpireAt.toSeconds

    log.debug(s"executing ZUNIONSTORE $aggregationBucket ${counterBuckets.size.toString} ${counterBuckets.mkString(",")}")
    log.debug(s"executing EXPIRE $aggregationBucket $expiration")

    IO.fromFuture(IO {
      for {
        _ ← client.zunionstore(aggregationBucket, counterBuckets.size.toString, counterBuckets)
        _ ← client.expire(aggregationBucket, expiration)
        resp ← client.zrevrange[String](aggregationBucket, lowerBound, upperBound)
      } yield resp
    })
  }

  def flushAll: IO[Boolean] = if(HotspotterConfig.redis.hosts == List("localhost"))
    IO.fromFuture(IO { for {r ← client.flushall} yield r})
  else IO.pure(false)
}

object CountersRedisConnector extends LogSupport {
  private val masterRoleName = "master"
  private val slaveRoleName = "slave"
  private val replicationSectionName = "Replication"
  private val pongResponse = "PONG"
  private val role = "role"

  def apply()(implicit as: ActorSystem): CountersRedisConnector = resolveConnections(HotspotterConfig.redis.hosts.map(h ⇒ {
    RedisServer(h, HotspotterConfig.redis.hostsPort, HotspotterConfig.redis.password)})) match {
    case connections if connections.nonEmpty ⇒ new CountersRedisConnector(resolveClient(connections), HotspotterConfig.keyHits)
    case _ ⇒ throw new RuntimeException("No available redis server was found")
  }

  def resolveClient(connections: Map[RedisServer, RedisClient])(implicit as: ActorSystem): Transactions with RedisCommands =
    buildCluster(connections) match {
      case Some((master, Nil)) ⇒
        log.info(s"Created single redis connection with ${master.host}")
        RedisClient(master.host, master.port, master.password)
      case Some((master, slaves)) ⇒
        log.info(s"Created redis cluster with ${master.host} as master of ${slaves.map(_.host).mkString(",")}")
        RedisClientMasterSlaves(master, slaves)
      case _ ⇒ throw new RuntimeException("Couldn't resolve redis client")
    }

  private def buildCluster(clientByServerMap: Map[RedisServer, RedisClient])(implicit as: ActorSystem): Option[(RedisServer, List[RedisServer])] = {
    val (mastersList, slavesList) = performRolePartition(clientByServerMap)

    def resolveNoMasterSituation(astraySlaves: List[RedisServer]) = Some(bindFirstAsMaster(astraySlaves, clientByServerMap))
    def resolveMultipleMasterSituation(multipleMasters: List[RedisServer]) =
      bindFirstAsMaster(multipleMasters, clientByServerMap) match { case (chosenMaster, slaves) ⇒ (Some(chosenMaster), slaves) }

    val (masterOption: Option[RedisServer], newSlaves: List[RedisServer]) = mastersList match {
      case Nil ⇒             (None, List.empty)
      case only::Nil ⇒       (Some(only), List.empty)
      case multipleMasters ⇒ resolveMultipleMasterSituation(multipleMasters)
    }

    val response = (masterOption, slavesList) match {
      case (None, Nil) ⇒    None
      case (None, slaves) ⇒ resolveNoMasterSituation(slaves)
      case (Some(master), slaves) ⇒
        bindRelation(master, slaves, clientByServerMap)
        Some((master, slaves ++ newSlaves))
    }

    closeConnections(clientByServerMap.values)
    response
  }

  private def performRolePartition(clientByServerMap: Map[RedisServer, RedisClient]) =
    (for (clientByServer ← clientByServerMap) yield getServerRole(clientByServer))
      .flatten.toMap
      .filter(info ⇒ info._2.isDefined && (info._2.get == masterRoleName || info._2.get == slaveRoleName))
      .partition(_._2.get == masterRoleName) match {
      case (mastersMap, slavesMap) ⇒ (mastersMap.keys.toList, slavesMap.keys.toList)
    }

  private def bindFirstAsMaster(servers: List[RedisServer], clientByServerMap: Map[RedisServer, RedisClient]) = {
    val master::slaves = servers
    bindRelation(master, slaves, clientByServerMap)
    (master, slaves)
  }

  private def bindRelation(master: RedisServer, slaves: List[RedisServer], clientByServerMap: Map[RedisServer, RedisClient]): Unit =
    IO.fromFuture(IO {
      for {
        _ ← clientByServerMap(master).slaveofNoOne
        response ← Future.sequence(slaves.map(server ⇒ clientByServerMap(server).slaveof(master.host, master.port)))
      } yield if(!response.contains(false)) {
        log.info(s"Now ${master.host} is master server of ${slaves.map(_.host).mkString(",")}")
      } else {
        log.info(s"${master.host} already was master for (${response.count(_ == false)}) of the following: ${slaves.map(_.host).mkString(",")}")
      }
    }).attempt.unsafeRunSync

  private def getServerRole(clientByServer: (RedisServer, RedisClient)): Option[(RedisServer, Option[String])] =
    IO.fromFuture(IO {
      for {
        response ← clientByServer._2.info(replicationSectionName)
      } yield {
        val roleResponse: Option[String] = response.split("\r\n").drop(1).flatMap(_.split(":") match {
          case Array(key, value) ⇒ List(key → value)
          case _                 ⇒ List.empty
        }).find(_._1 == role).map(_._2)
        clientByServer._1 → roleResponse
      }
    }).attempt.unsafeRunTimed(5 seconds) match {
      case Some(Right(resp)) ⇒ Some(resp)
      case Some(Left(error)) ⇒ log.error(s"Something went wrong with redis: $error"); None
      case None ⇒ log.error(s"Couldn't get role from server, expired timeout of 5 seconds"); None
    }

  private def resolveConnections(servers: List[RedisServer])(implicit as: ActorSystem): Map[RedisServer, RedisClient] =
    servers.flatMap { server: RedisServer ⇒
      val client: RedisClient = RedisClient(server.host, server.port, server.password)
      def handleConnectionFailure(message: String) = {client.stop; log.error(message); None}
      IO.fromFuture(IO(client.ping)).attempt.unsafeRunTimed(2 seconds) match {
        case Some(Right(message)) if message == pongResponse ⇒
          log.info(s"$message received from ${server.host}:${server.port}")
          Some((server, client))
        case Some(Right(message)) ⇒
          handleConnectionFailure(s"Unexpected message: $message received from ${server.host}:${server.port}")
        case Some(Left(error)) ⇒
          handleConnectionFailure(s"Couldn't connect to ${server.host}:${server.port} Exception: $error")
        case None ⇒
          handleConnectionFailure(s"Couldn't connect to ${server.host}:${server.port}, expired timeout of 2 seconds")
      }
    }.toMap

  private def closeConnections(clients: Iterable[RedisClient]): Unit = {
    IO.fromFuture(IO(Future.sequence(clients.map(_.quit)))).attempt.unsafeRunSync match {
      case Right(_) ⇒ log.debug(s"Closed connections")
      case Left(e) ⇒ log.debug(s"Something went wrong closing connections: $e")
    }
    clients.foreach(_.stop)
  }
}
