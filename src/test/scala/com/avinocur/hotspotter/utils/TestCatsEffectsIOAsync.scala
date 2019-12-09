package com.avinocur.hotspotter.utils

import cats.effect.IO
import org.scalactic.source
import org.scalatest.{Assertion, AsyncFunSuite, Matchers, compatible}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

trait TestCatsEffectsIOAsync extends AsyncFunSuite with Matchers {
  def testResultAsync[T](testName: String)(testThunk: => IO[T])(assertThunk: T => Any)(implicit pos: source.Position): Unit = {
    test(testName){
      doTestResultAsync(testThunk) {
        case Success(result) =>
          assertThunk(result)
          1 shouldBe 1
        case Failure(exception) => fail(s"Unexpected exception on test $testName", exception)
      }
    }
  }

  def testExpectAsync[T, E <: Throwable](testName: String, expectedClass: Class[E], expectedMessage: Option[String] = None)(testThunk: => IO[T])(implicit pos: source.Position): Unit = {
    test(testName){
      doTestResultAsync(testThunk) {
        case Failure(exception) =>
          exception.getClass shouldBe(expectedClass)
          exception.getMessage shouldBe(expectedMessage.getOrElse(exception.getMessage))
        case _ => fail(s"Expected exception: ${expectedClass.getName} not thrown on test: $testName.")
      }
    }
  }

  private def doTestResultAsync[T](testThunk: => IO[T])(assertThunk: Try[T] => Assertion): Future[Assertion] = {
    val effect = Promise[T]()
    val attempt = Promise[Try[T]]()
    effect.future.onComplete(attempt.success)

    val io = testThunk.runAsync {
      case Right(a) => IO(effect.success(a))
      case Left(e)  => IO(effect.failure(e))
    }

    for (_ <- io.unsafeToFuture(); v <- attempt.future) yield {
      assertThunk(v)
    }
  }
}
