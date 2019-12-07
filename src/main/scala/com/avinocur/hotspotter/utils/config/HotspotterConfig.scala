package com.avinocur.hotspotter.utils.config

import pureconfig.ConfigReader

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

object HotspotterConfig extends ConfigurationSupport {
  case class ThreadPool(size:Int)
  case class Server(interface: String, port: Int, threadPool: ThreadPool)
  case class RedisConfig(hosts: List[String], hostsPort: Int, password: Option[String])
  case class KeyHitsConfig(expireAt: Duration, currentKeyExpireAt: Duration, keyLimit: Int, timeWindowHours: Int)

  lazy val server: Server = load[Server]("server")
  lazy val redis: RedisConfig =  load[RedisConfig]("redis")
  lazy val keyHits: KeyHitsConfig =  load[KeyHitsConfig]("key-hits")

  def load[T](name: String)(implicit reader: ConfigReader[T], ct: ClassTag[T]): T =
    pureconfig.loadConfigOrThrow[T](config.getConfig(name))

}
