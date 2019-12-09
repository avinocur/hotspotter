package com.avinocur.hotspotter.repository

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BucketGenerator {
  protected val yyyy_MM_dd_Formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")

  def currentBucket(now: LocalDateTime): String = now.format(yyyy_MM_dd_Formatter)

  def aggregationWindowBuckets(now: LocalDateTime, hours: Int): List[String] =
    (0 to hours).map(h ⇒ now.minusHours(h)).map(_.format(yyyy_MM_dd_Formatter)).toList
}

