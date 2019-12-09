package com.avinocur.hotspotter.repository

import java.time.LocalDateTime

import org.scalatest.{FunSuite, Matchers}

class BucketGeneratorTest extends FunSuite with Matchers {
  val bucketGenerator = new BucketGenerator()

  test("currentBucket. Should format correctly.") {
    val bucket = bucketGenerator.currentBucket(LocalDateTime.of(2019, 10, 5, 22, 15, 17));

    bucket shouldBe("2019-10-05-22")
  }

  test("aggregationWindowBuckets. Date in the middle of the day. Should generate correctly.") {
    val date = LocalDateTime.of(2019, 10, 5, 22, 15, 17)

    val buckets = bucketGenerator.aggregationWindowBuckets(date, 5)

    buckets shouldBe(Seq("2019-10-05-22","2019-10-05-21","2019-10-05-20","2019-10-05-19","2019-10-05-18","2019-10-05-17"))
  }

  test("aggregationWindowBuckets. Date in the beginning of the day, with buckets on the day before. Should generate correctly.") {
    val date = LocalDateTime.of(2019, 10, 5, 2, 15, 17)

    val buckets = bucketGenerator.aggregationWindowBuckets(date, 5)

    buckets shouldBe(Seq("2019-10-05-02","2019-10-05-01","2019-10-05-00","2019-10-04-23","2019-10-04-22","2019-10-04-21"))
  }

  test("aggregationWindowBuckets. Date in the beginning of the month, with buckets on the day before. Should generate correctly.") {
    val date = LocalDateTime.of(2019, 10, 1, 2, 15, 17)

    val buckets = bucketGenerator.aggregationWindowBuckets(date, 5)

    buckets shouldBe(Seq("2019-10-01-02","2019-10-01-01","2019-10-01-00","2019-09-30-23","2019-09-30-22","2019-09-30-21"))
  }

  test("aggregationWindowBuckets. Date in the beginning of the year, with buckets on the day before. Should generate correctly.") {
    val date = LocalDateTime.of(2019, 1, 1, 2, 15, 17)

    val buckets = bucketGenerator.aggregationWindowBuckets(date, 5)

    buckets shouldBe(Seq("2019-01-01-02","2019-01-01-01","2019-01-01-00","2018-12-31-23","2018-12-31-22","2018-12-31-21"))
  }
}
