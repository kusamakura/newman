/**
 * Copyright 2012-2013 StackMob
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stackmob.newman.test
package dsl

import scalaz.Scalaz._
import org.specs2.Specification
import com.stackmob.newman.response.{HttpResponseCode, HttpResponse}
import com.stackmob.newman.{RawBody, Headers}
import com.stackmob.newman.Constants._
import com.stackmob.newman.dsl._
import scala.concurrent._
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

class AsyncResponseHandlerDSLSpecs extends Specification { def is =
  "AsyncResponseHandlerDSLSpecs".title                                                                                  ^ end ^
  "AsyncResponseHandlerDSL is a DSL used to handle responses with Newman"                                               ^ end ^
  "The DSL should"                                                                                                      ^
    "return a Failure if the IO throws"                                                                                 ! ThrowingIO().returnsFailure ^ end ^
    "return a Success if the IO doesn't throw, types aren't specified, and Unit is returned"                            ! ThrowingIO().returnsEmptySuccess ^ end ^
    "return a Success if the IO doesn't throw, types aren't specified, and non-Unit is returned"                        ! ThrowingIO().returnsNonEmptySuccess ^ end ^
    "return non throwable error types if specified & the IO throws"                                                     ! CustomErrors().returnsErrorCorrectly ^ end ^
    "return successful validation of nonthrowable error type if specified"                                              ! CustomErrors().returnsSuccessCorrectly ^ end ^
    "evaluate the first applicable handler in the list, not the last"                                                   ! Handlers().evalFirst ^ end ^
  end

  import com.stackmob.newman.concurrent.SequentialExecutionContext
  override val concurrentExecutionContext: ExecutionContext = SequentialExecutionContext

  private trait Context {
    //we need to wait longer than the default to account for scheduling the map function inside AsyncResponseHandlerDSL's default method
    protected val waitDur = Duration(2, TimeUnit.SECONDS)
  }

  private case class ThrowingIO() extends Context {
    def returnsFailure = {
      val ex = new Exception("test exception")
      val resp = Future.failed[HttpResponse](ex).handleCode(HttpResponseCode.Ok) { _ =>
        ().success[Throwable]
      }
      resp.toFutureValidation.block().toEither must beLeft.like {
        case e => e must beEqualTo(ex)
      }
    }

    def returnsEmptySuccess = {
      val resp = Future.successful(HttpResponse(HttpResponseCode.Ok, Headers.empty, RawBody.empty)).handleCode(HttpResponseCode.Ok) { _ =>
        ().success[Throwable]
      }
      resp.toFutureValidation.block(waitDur).toEither must beRight.like {
        case e => e must beEqualTo(())
      }
    }

    def returnsNonEmptySuccess = {
      val bodyString = "test body"
      val resp = Future.successful(HttpResponse(HttpResponseCode.Ok, Headers.empty, bodyString.getBytes(UTF8Charset))).handleCode(HttpResponseCode.Ok){ resp =>
        resp.bodyString.success[Throwable]
      }
      resp.toFutureValidation.block(waitDur).toEither must beRight.like {
        case e => e must beEqualTo(bodyString)
      }
    }
  }

  private case class Handlers() extends Context {
    def evalFirst = {
      val expected = 1
      val resp = Future.successful(HttpResponse(HttpResponseCode.Ok, Headers.empty, RawBody.empty)).handleCode(HttpResponseCode.Ok) { resp =>
        expected.success[Throwable]
      }.handleCode(HttpResponseCode.Ok) { resp =>
        (expected + 1).success[Throwable]
      }
      resp.toFutureValidation.block(waitDur).toEither must beRight.like {
        case e => e must beEqualTo(expected)
      }
    }
  }

  private case class CustomErrors() extends CustomErrorContext {
    def returnsErrorCorrectly = {
      val exceptionMessage = "test exception"
      val ex = new Exception(exceptionMessage)
      val customError = new CustomErrorForSpecs(exceptionMessage)
      val resp = Future.failed[HttpResponse](ex).handleCode[CustomErrorForSpecs, Unit](HttpResponseCode.Ok) { _ =>
        ().success
      }
      resp.toFutureValidation.block().toEither must beLeft.like {
        case e => e must beEqualTo(customError)
      }
    }

    def returnsSuccessCorrectly = {
      val bodyString = "test body"
      val resp = Future.successful(HttpResponse(HttpResponseCode.Ok, Headers.empty, bodyString.getBytes(UTF8Charset))).handleCode[CustomErrorForSpecs, String](HttpResponseCode.Ok){ resp =>
        resp.bodyString.success
      }
      resp.toFutureValidation.block(waitDur).toEither must beRight.like {
        case e => e must beEqualTo(bodyString)
      }
    }
  }

  private trait CustomErrorContext extends Context {
    case class CustomErrorForSpecs(msg: String)
    implicit def exToCustomError(ex: Throwable): CustomErrorForSpecs = {
      new CustomErrorForSpecs(ex.getMessage)
    }
  }


}
