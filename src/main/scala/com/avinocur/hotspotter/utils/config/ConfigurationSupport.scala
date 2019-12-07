package com.avinocur.hotspotter.utils.config

import java.io.File

import com.avinocur.hotspotter.LogSupport
import com.typesafe.config.{Config, ConfigFactory}


trait ConfigurationSupport {
  val config: Config = ConfigurationSupport.configuration
}

object ConfigurationSupport extends LogSupport {
  val ENVIRONMENT_KEY = "environment"

  val configuration: Config = {
    val defaultConfig = ConfigFactory.load()
    val overrideFile = new File(Option(System.getProperty("environmentOverride")).getOrElse("environment-override.conf"))
    val environment = Option(System.getProperty(ENVIRONMENT_KEY)).getOrElse(defaultConfig.getString(ENVIRONMENT_KEY))

    log.info(s"Loading config from [$environment] overriding with [$overrideFile]")

    ConfigFactory.parseFile(overrideFile)
        .withFallback(defaultConfig.getConfig(environment))
        .withFallback(defaultConfig)
  }
}
