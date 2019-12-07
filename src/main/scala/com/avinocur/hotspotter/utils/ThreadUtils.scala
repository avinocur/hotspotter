package com.avinocur.hotspotter.utils

import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicLong

object ThreadUtils {
  def namedThreadFactory(name: String): ThreadFactory =
    new ThreadFactory {
      val count = new AtomicLong()
      val defaultFactory: ThreadFactory = Executors.defaultThreadFactory()

      override def newThread(r: Runnable): Thread = {
        val thread = defaultFactory.newThread(r)
        thread.setName(s"$name-${count.incrementAndGet().toString}")
        thread
      }
    }
}