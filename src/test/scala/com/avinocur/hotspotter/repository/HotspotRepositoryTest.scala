package com.avinocur.hotspotter.repository

import java.time.LocalDateTime

import cats.effect.IO
import com.avinocur.hotspotter.model.KeyHit
import com.avinocur.hotspotter.utils.TestCatsEffectsIOAsync
import com.avinocur.hotspotter.utils.config.HotspotterConfig.KeyHitsConfig
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.duration._

class HotspotRepositoryTest extends TestCatsEffectsIOAsync with MockitoSugar with ArgumentMatchersSugar with BeforeAndAfterEach {
    val keyHitsConfig = KeyHitsConfig(expireAt = 1 hours, currentKeyExpireAt = 1 hours, keyLimit = 3, timeWindowHours = 5)

    var bucketGeneratorMockOpt: Option[BucketGenerator] = None
    def bucketGeneratorMock: BucketGenerator = bucketGeneratorMockOpt.get

    var countersConnectionMockOpt: Option[CountersConnection[IO]] = None
    def countersConnectionMock: CountersConnection[IO] = countersConnectionMockOpt.get

    var hotspotRepositoryOpt: Option[HotspotRepository] = None
    def hotspotRepository: HotspotRepository = hotspotRepositoryOpt.get

    val CURRENT_BUCKET = "CURRENTBUCKET"

    override def beforeEach(): Unit = {
        bucketGeneratorMockOpt = Some(mock[BucketGenerator])
        countersConnectionMockOpt = Some(mock[CountersConnection[IO]])
        hotspotRepositoryOpt = Some(new HotspotRepository(countersConnectionMock, keyHitsConfig, bucketGeneratorMock))


        when(bucketGeneratorMock.currentBucket(any[LocalDateTime])) thenReturn (CURRENT_BUCKET)
    }

    testResultAsync("save. Empty list. Should not save anything.") {
        when(countersConnectionMock.incrementCounter(any[String], any[String], any[Duration], any[Double])) thenReturn( IO {})

        hotspotRepository.save(Nil)
    } { _ =>
        verify(countersConnectionMock, times(0)).incrementCounter(any[String], any[String], any[Duration], any[Double])
    }

    testExpectAsync("save. CounterConnection returns exception. Should raise exception.", classOf[HotspotRepositoryException]){
        when(countersConnectionMock.incrementCounter(any[String], any[String],
            any[Duration], any[Double])) thenReturn( IO.raiseError(new RuntimeException("Intended Test Exception")))

        hotspotRepository.save(KeyHit("AKEY")::Nil)
    }

    testResultAsync("save. One key. Should save on current bucket and configured durations.") {
        when(countersConnectionMock.incrementCounter(any[String], any[String], any[Duration], any[Double])) thenReturn( IO {})

        hotspotRepository.save(KeyHit("AKEY")::Nil)
    } { _ =>
        verify(countersConnectionMock, times(1)).incrementCounter(CURRENT_BUCKET, "AKEY", keyHitsConfig.currentKeyExpireAt, 1)
    }

    testResultAsync("save. Many instances of the same key. Should save on current bucket and configured durations.") {
        when(countersConnectionMock.incrementCounter(any[String], any[String], any[Duration], any[Double])) thenReturn( IO {})

        hotspotRepository.save(KeyHit("AKEY")::KeyHit("AKEY")::KeyHit("AKEY")::Nil)
    } { _ =>
        verify(countersConnectionMock, times(1)).incrementCounter(CURRENT_BUCKET, "AKEY", keyHitsConfig.currentKeyExpireAt, 3)
    }

    testResultAsync("save. Many instances of the different keys. Should save all keys on current bucket and configured durations.") {
        when(countersConnectionMock.incrementCounter(any[String], any[String], any[Duration], any[Double])) thenReturn( IO {})

        hotspotRepository.save(KeyHit("KEY1")::KeyHit("KEY2")::KeyHit("KEY1")::KeyHit("KEY3")::KeyHit("KEY1")::KeyHit("KEY2")::Nil)
    } { _ =>
        verify(countersConnectionMock, times(1)).incrementCounter(CURRENT_BUCKET, "KEY1", keyHitsConfig.currentKeyExpireAt, 3)
        verify(countersConnectionMock, times(1)).incrementCounter(CURRENT_BUCKET, "KEY2", keyHitsConfig.currentKeyExpireAt, 2)
        verify(countersConnectionMock, times(1)).incrementCounter(CURRENT_BUCKET, "KEY3", keyHitsConfig.currentKeyExpireAt, 1)
    }

    testResultAsync("getTopKeys. CounterConnection returns keys. Should call with the correct buckets"){
        when(bucketGeneratorMock.aggregationWindowBuckets(any[LocalDateTime], any[Int])) thenReturn ("A"::"B"::"C"::Nil)
        when(countersConnectionMock.getTopKeys(any[List[String]], any[Int])) thenReturn ( IO {"KEY1" :: "KEY2" :: Nil})
        hotspotRepository.getTopKeys()
    } { topKeys =>
      topKeys shouldBe ("KEY1"::"KEY2"::Nil)
      verify(countersConnectionMock, times(1)).getTopKeys("A"::"B"::"C"::Nil, keyHitsConfig.keyLimit)
    }

    testResultAsync("getTopKeys. CounterConnection returns empty. Should call with the correct buckets"){
        when(bucketGeneratorMock.aggregationWindowBuckets(any[LocalDateTime], any[Int])) thenReturn ("A"::"B"::"C"::Nil)
        when(countersConnectionMock.getTopKeys(any[List[String]], any[Int])) thenReturn ( IO {Nil})
        hotspotRepository.getTopKeys()
    } { topKeys =>
        topKeys shouldBe (Nil)
        verify(countersConnectionMock, times(1)).getTopKeys("A"::"B"::"C"::Nil, keyHitsConfig.keyLimit)
    }

    testExpectAsync("getTopKeys. CounterConnection returns exception. Should raise exception.", classOf[HotspotRepositoryException]){
        when(bucketGeneratorMock.aggregationWindowBuckets(any[LocalDateTime], any[Int])) thenReturn ("A"::"B"::"C"::Nil)
        when(countersConnectionMock.getTopKeys(any[List[String]], any[Int])) thenReturn (IO.raiseError(new RuntimeException("Intended Test Exception")))
        hotspotRepository.getTopKeys()
    }
}
