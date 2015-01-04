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

import org.scalacheck.Gen
import java.util.concurrent.TimeUnit
import com.stackmob.newman._
import com.stackmob.newman.caching._
import com.stackmob.newman.request._
import java.net.URL
import com.stackmob.newman.response.{HttpResponseCode, HttpResponse}
import HttpResponseCode._
import org.apache.commons.codec.digest.DigestUtils
import scala.concurrent.duration.Duration
import scala.concurrent.Future
import com.stackmob.newman.test.caching.DummyHttpResponseCacher

package object scalacheck {
  private[test] lazy val genNonEmptyString: Gen[String] = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)

  private[test] lazy val timeUnits = Seq[TimeUnit](TimeUnit.DAYS,
    TimeUnit.HOURS,
    TimeUnit.MICROSECONDS,
    TimeUnit.MILLISECONDS,
    TimeUnit.MINUTES,
    TimeUnit.NANOSECONDS,
    TimeUnit.SECONDS)

  private[test] lazy val genTimeUnit: Gen[TimeUnit] = Gen.oneOf(timeUnits)

  private[test] lazy val genPositiveDuration: Gen[Duration] = {
    for {
      magnitude <- Gen.posNum[Long]
    } yield {
      Duration(magnitude, TimeUnit.MILLISECONDS)
    }
  }

  private[test] lazy val httpResponseCodes = Seq[HttpResponseCode](Accepted,
    BadGateway,
    MethodNotAllowed,
    BadRequest,
    ClientTimeout,
    Conflict,
    Created,
    EntityTooLarge,
    FailedDependency,
    Forbidden,
    GatewayTimeout,
    Gone,
    InsufficientStorage,
    InternalServerError,
    LengthRequired,
    Locked,
    MovedPermanently,
    TemporaryRedirect,
    MultipleChoices,
    MultiStatus,
    NoContent,
    NotAcceptable,
    NonAuthoritativeInformation,
    NotFound,
    NotImplemented,
    NotModified,
    Ok,
    PartialContent,
    PaymentRequired,
    PreconditionFailed,
    AuthenticationRequired,
    RequestURITooLarge,
    ResetContent,
    SeeOther,
    Unauthorized,
    ServiceUnavailable,
    UnprocessableEntity,
    UnsupportedMediaType,
    UseProxy,
    HttpVersionNotSupported
  )

  private[test] lazy val genHttpResponseCode: Gen[HttpResponseCode] = Gen.oneOf(httpResponseCodes)

  private[test] lazy val genHashCode: Gen[HashCode] = genNonEmptyString.map(DigestUtils.md5Hex)

  private[test] lazy val genRawBody: Gen[RawBody] = for {
    str <- genNonEmptyString
  } yield {
    str.getBytes("UTF-8")
  }

  private[test] lazy val genHeader: Gen[Header] = for {
    key <- genNonEmptyString
    value <- genNonEmptyString
  } yield {
    key -> value
  }

  private[test] lazy val genHeaders: Gen[Headers] = for {
    headers <- Gen.listOf(genHeader)
  } yield {
    Headers(headers)
  }

  private[test] lazy val genURL: Gen[URL] = for {
    urlString <- genNonEmptyString
  } yield {
    new URL("http://%s.com".format(urlString))
  }

  private[test] def genHttpRequest(client: HttpClient): Gen[HttpRequest] = for {
    url <- genURL
    headers <- genHeaders
  } yield {
    client.get(url, headers)
  }

  private[test] lazy val genHttpResponse: Gen[HttpResponse] = for {
    code <- genHttpResponseCode
    headers <- genHeaders
    body <- genRawBody
  } yield {
    new HttpResponse(code, headers, body)
  }

  private[test] def genEitherSuccessFuture[T](gen: Gen[T]): Gen[Either[Future[T], Unit]] = {
    gen.map { t =>
      Left(Future.successful(t))
    }
  }

  private[test] def genSuccessFuture[T](gen: Gen[T]): Gen[Future[T]] = {
    gen.map { t =>
      Future.successful(t)
    }
  }

  private[test] def genFailFuture[T](gen: Gen[Throwable]): Gen[Future[T]] = {
    gen.map { t =>
      Future.failed[T](t)
    }
  }

  private[test] def genSomeOption[T](gen: Gen[T]): Gen[Option[T]] = {
    gen.map { t =>
      Some(t)
    }
  }

  private[test] def genNoneOption[T]: Gen[Option[T]] = {
    Gen.const[Option[T]](Option.empty[T])
  }

  private[test] def genDummyHttpResponseCache(genApplyBehavior: Gen[Either[Future[HttpResponse], Unit]], genFoldBehavior: Gen[Either[Future[HttpResponse], Unit]]): Gen[DummyHttpResponseCacher] = {
    for {
      applyBehavior <- genApplyBehavior
      foldBehavior <- genFoldBehavior
    } yield {
      new DummyHttpResponseCacher(applyBehavior = applyBehavior, foldBehavior = foldBehavior)
    }
  }

  private[test] def genDummyHttpClient: Gen[DummyHttpClient] = for {
    resp <- genHttpResponse
  } yield {
    new DummyHttpClient(Future.successful(resp))
  }

}
