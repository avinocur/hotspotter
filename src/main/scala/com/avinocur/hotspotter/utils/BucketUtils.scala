package com.avinocur.hotspotter.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object BucketUtils {
  protected val yyyy_MM_dd_Formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")
  def currentBucket: String = LocalDateTime.now().format(yyyy_MM_dd_Formatter)

  def aggregationWindowBuckets(hours: Int): List[String] =
    (0 to hours).map(h ⇒ LocalDateTime.now.minusHours(h)).map(_.format(yyyy_MM_dd_Formatter)).toList
}
