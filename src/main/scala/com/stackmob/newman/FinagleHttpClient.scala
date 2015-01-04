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

package com.stackmob.newman

import java.net.URL
import com.stackmob.newman.request._
import com.stackmob.newman.response.{HttpResponseCode, HttpResponse}
import com.twitter.util.Duration
import com.twitter.finagle.http._
import com.twitter.finagle.builder.ClientBuilder
import org.jboss.netty.handler.codec.http.{HttpResponse => NettyHttpResponse, HttpRequest => NettyHttpRequest, HttpMethod => NettyHttpMethod, HttpResponseStatus}
import java.nio.ByteBuffer
import org.jboss.netty.buffer.{ChannelBuffer, ByteBufferBackedChannelBuffer}
import scalaz.Scalaz._
import collection.JavaConverters._
import FinagleHttpClient._
import com.stackmob.newman.concurrent.RichTwitterFuture
import scala.concurrent.Future
import com.stackmob.newman.concurrent.SequentialExecutionContext
import java.nio.charset.Charset
import com.stackmob.newman.Constants.UTF8Charset

class FinagleHttpClient(tcpConnectionTimeout: Duration = DefaultTcpConnectTimeout,
                        requestTimeout: Duration = DefaultRequestTimeout,
                        numConnsPerHost: Int = DefaultMaxConnsPerHost) extends HttpClient {

  override def get(url: URL, headers: Headers) = GetRequest(url, headers) {
    executeRequest(tcpConnectionTimeout, requestTimeout, numConnsPerHost, NettyHttpMethod.GET, url, headers)
  }

  override def post(url: URL, headers: Headers, body: RawBody) = PostRequest(url, headers, body) {
    executeRequest(tcpConnectionTimeout, requestTimeout, numConnsPerHost, NettyHttpMethod.POST, url, headers, Some(body))
  }


  override def put(url: URL, headers: Headers, body: RawBody) = PutRequest(url, headers, body) {
    executeRequest(tcpConnectionTimeout, requestTimeout, numConnsPerHost, NettyHttpMethod.PUT, url, headers, Some(body))
  }

  override def delete(url: URL, headers: Headers) = DeleteRequest(url, headers) {
    executeRequest(tcpConnectionTimeout, requestTimeout, numConnsPerHost, NettyHttpMethod.DELETE, url, headers)
  }

  override def head(url: URL, headers: Headers) = HeadRequest(url, headers) {
    executeRequest(tcpConnectionTimeout, requestTimeout, numConnsPerHost, NettyHttpMethod.HEAD, url, headers)
  }
}

object FinagleHttpClient {

  private[FinagleHttpClient] def executeRequest(tcpConnectionTimeout: Duration,
                                                requestTimeout: Duration,
                                                numConnsPerHost: Int,
                                                method: NettyHttpMethod,
                                                url: URL,
                                                headers: Headers,
                                                mbBody: Option[RawBody] = None): Future[HttpResponse] = {
    val (host, port) = url.hostAndPort
    val client = ClientBuilder()
      .codec(Http())
      .hosts(s"$host:$port")
      .hostConnectionLimit(numConnsPerHost)
      .tcpConnectTimeout(tcpConnectionTimeout)
      .requestTimeout(requestTimeout)
      .build()
    val req = createNettyHttpRequest(method, url, headers, mbBody)

    val scalaFut = client(req).toScalaFuture.map { res =>
      res.toNewmanHttpResponse | {
        throw new InvalidNettyResponse(res.getStatus)
      }
    }(SequentialExecutionContext)

    scalaFut.onComplete { _ =>
      client.close()
    }(SequentialExecutionContext)

    scalaFut
  }

  private[FinagleHttpClient] def createNettyHttpRequest(method: NettyHttpMethod,
                                                        url: URL,
                                                        headers: Headers,
                                                        mbBody: Option[RawBody]): NettyHttpRequest = {
    val headersMap = headers.map { headerList =>
      headerList.list.toMap
    } | {
      Map[String, String]()
    }

    val mbChannelBuf: Option[ChannelBuffer] = mbBody.map { rawBody =>
      rawBody.toChannelBuf
    }
    RequestBuilder()
      .url(url)
      .addHeaders(headersMap)
      .build(method, mbChannelBuf)
  }

  implicit class RichRawBody(rawBody: RawBody) {
    def toChannelBuf: ChannelBuffer = {
      val byteBuf = ByteBuffer.wrap(rawBody)
      new ByteBufferBackedChannelBuffer(byteBuf)
    }

    def stringRepresentation(implicit charset: Charset = UTF8Charset): String = {
      new String(rawBody, charset)
    }
  }

  implicit class RichNettyHttpResponse(resp: NettyHttpResponse) {
    def bodyBytes: Array[Byte] = {
      val channelBuf = resp.getContent
      //Check if channelBuf  can be handled with array method
      if (channelBuf.hasArray){
        val array = channelBuf.array

        //the index of the first byte in the backing byte array of this ChannelBuffer.
        //if there isn't a backing byte array, then this throws
        val arrayOffset = channelBuf.arrayOffset
        //the index of the first readable byte in this ChannelBuffer
        val readerIndex = channelBuf.readerIndex()

        //return a copy of the backing array, starting at the effective beginning of the HTTP response body
        array.slice(arrayOffset + readerIndex, array.length)
      } else {
        val byteBuffer = channelBuf.toByteBuffer
        byteBuffer.array
      }
    }
    def toNewmanHttpResponse: Option[HttpResponse] = {
      for {
        code <- HttpResponseCode.fromInt(resp.getStatus.getCode)
        rawHeaders <- Option(resp.headers)
        headers <- {
          val tupList = rawHeaders.asScala.map { entry =>
            entry.getKey -> entry.getValue
          }
          Option(tupList.toList.toNel)
        }
        body <- Some(bodyBytes)
      } yield {
        HttpResponse(code, headers, body)
      }
    }
  }

  class InvalidNettyResponse(nettyCode: HttpResponseStatus) extends Exception(s"Invalid netty response with code: ${nettyCode.getCode}")
  val DefaultTcpConnectTimeout = Duration.fromMilliseconds(500)
  val DefaultRequestTimeout = Duration.fromMilliseconds(200)
  val DefaultMaxConnsPerHost = 10
}
