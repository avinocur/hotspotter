name := "hotspotter"

val artifactId = "hotspotter"

version := "1.3.4"

organization := "com.avinocur"
name := artifactId
scalaVersion := "2.12.10"

fork in run := true

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-unchecked",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Ypartial-unification",
  "-Xfuture")

resolvers ++= Seq(
  Resolver.jcenterRepo,
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.typesafeRepo("releases"),
  Resolver.bintrayRepo("kamon-io", "releases"),
  Resolver.bintrayRepo("kamon-io", "snapshots"),
  Resolver.bintrayRepo("outworkers", "oss-releases")
)

val Http4sVersion        = "0.18.16"
val CirceVersion         = "0.12.3"

libraryDependencies ++= Seq(
  "org.http4s"                      %%  "http4s-blaze-server"         % Http4sVersion,
  "org.http4s"                      %%  "http4s-circe"                % Http4sVersion,
  "org.http4s"                      %%  "http4s-dsl"                  % Http4sVersion,
  "org.http4s"                      %%  "http4s-blaze-client"         % Http4sVersion,
  "io.circe"                        %%  "circe-generic"               % CirceVersion,

  "com.github.pureconfig"           %%  "pureconfig"                  % "0.9.1",
  "org.slf4j"                       %   "slf4j-api"                   % "1.7.29",
  "ch.qos.logback"                  %   "logback-classic"             % "1.2.1",

  "com.github.etaty"                %%  "rediscala"                   % "1.8.0",

  "org.scalatest"                   %   "scalatest_2.12"              % "3.0.3"                   % "test",
  "org.mockito"                     %%  "mockito-scala"               % "1.10.0"                  % "test",
  "com.github.sebruck"              %%  "scalatest-embedded-redis"    % "0.4.0"                   % "test",
  "com.typesafe.akka"               %%  "akka-testkit"                % "2.6.1"                   % "test"
)