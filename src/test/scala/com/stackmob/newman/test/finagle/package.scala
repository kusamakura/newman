package com.stackmob.newman.test

import org.scalacheck.{Arbitrary, Gen}
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.buffer._
import com.stackmob.newman.FinagleHttpClient.RichRawBody

/**
 * Created by IntelliJ IDEA.
 *
 * com.stackmob.newman.test.finagle
 *
 * User: aaron
 * Date: 9/27/13
 * Time: 3:19 PM
 */
package object finagle {
  lazy val genHttpResponseStatus: Gen[HttpResponseStatus] = {
    Gen.const(HttpResponseStatus.OK)
  }

  lazy val genNonEmptyString: Gen[String] = Gen.alphaStr.suchThat { s =>
    s.length > 0
  }

  lazy val genHeader: Gen[(String, String)] = {
    for {
      headerName <- genNonEmptyString
      headerVal <- genNonEmptyString
    } yield {
      headerName -> headerVal
    }
  }

  lazy val genHeaders: Gen[Map[String, String]] = {
    Gen.nonEmptyListOf(genHeader).map { list =>
      list.toMap
    }
  }

  lazy val genByteArray: Gen[Array[Byte]] = {
    val genByte = Arbitrary.arbitrary[Byte]
    Gen.nonEmptyListOf(genByte).map { list =>
      list.toArray
    }
  }

  lazy val genChannelBuffer: Gen[ChannelBuffer] = {
    genByteArray.map { arr =>
      arr.toChannelBuf
    }
  }

  lazy val genNettyResponse: Gen[(HttpResponseStatus, Map[String, String], ChannelBuffer, HttpResponse)] = {
    for {
      status <- genHttpResponseStatus
      headers <- genHeaders
      body <- genChannelBuffer
    } yield {
      val resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status)
      headers.foreach { tup =>
        val (headerName, headerValue) = tup
        resp.setHeader(headerName, headerValue)
      }
      resp.setContent(body)
      (status, headers, body, resp)
    }
  }
}
