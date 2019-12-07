logLevel := Level.Warn

resolvers += "Typesafe repository" at "https://dl.bintray.com/typesafe/maven-releases/"

resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.7")