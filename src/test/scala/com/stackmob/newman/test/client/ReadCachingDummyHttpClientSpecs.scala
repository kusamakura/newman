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
package client

import com.stackmob.newman.test.caching.DummyHttpResponseCacher
import com.stackmob.newman.test.scalacheck._
import org.specs2.{ScalaCheck, Specification}
import com.stackmob.newman.caching._
import com.stackmob.newman.response.HttpResponse
import org.scalacheck._
import Prop._
import java.net.URL
import com.stackmob.newman._
import com.stackmob.newman.request.HttpRequestWithoutBody

class ReadCachingDummyHttpClientSpecs extends Specification with ScalaCheck { def is =
  "ReadCachingDummyHttpClientSpecs".title                                                                               ^ end ^
  "CachingDummyHttpClient is an HttpClient that caches responses for some defined TTL"                                  ^ end ^
  "GET cache hit should not call Client"                                                                                ! getCallsApplyCacheHit ^ end ^
  "HEAD cache hit should not call Client"                                                                               ! headCallsApplyCacheHit ^ end ^
  "GET cache miss should call Client"                                                                                   ! getCallsApplyCacheMiss ^ end ^
  "HEAD cache miss should call Client"                                                                                  ! headCallsApplyCacheMiss ^ end ^
  "POST, PUT, DELETE should not touch the cache"                                                                        ! postPutDeleteIgnoreCache ^ end ^
  end

  private case class CacheInteraction(numApplies: Int, numFolds: Int)
  private def verifyCacheInteraction(cache: DummyHttpResponseCacher, interaction: CacheInteraction) = {
    val applyCalls = cache.applyCalls.size must beEqualTo(interaction.numApplies)
    val foldCalls = cache.foldCalls.size must beEqualTo(interaction.numFolds)
    applyCalls and foldCalls
  }

  private case class ClientInteraction(numGets: Int, numPosts: Int, numPuts: Int, numDeletes: Int, numHeads: Int)
  private def verifyClientInteraction(client: DummyHttpClient, interaction: ClientInteraction) = {
    val getReqs = client.getRequests.size must beEqualTo(interaction.numGets)
    val postReqs = client.postRequests.size must beEqualTo(interaction.numPosts)
    val putReqs = client.putRequests.size must beEqualTo(interaction.numPuts)
    val deleteReqs = client.deleteRequests.size must beEqualTo(interaction.numDeletes)
    val headReqs = client.headRequests.size must beEqualTo(interaction.numHeads)

    getReqs and postReqs and putReqs and deleteReqs and headReqs
  }

  private def verifyCallsApplyCacheHit[T <: HttpRequestWithoutBody](fn: (ReadCachingHttpClient, URL, Headers) => T) = {
    val genOnApply = genEitherSuccessFuture(genHttpResponse)
    forAll(genURL,
      genHeaders,
      genDummyHttpClient,
      genDummyHttpResponseCache(genOnApply, Gen.const(Right(())))) { (url, headers, dummyClient, dummyCache) =>

      val client = new ReadCachingHttpClient(dummyClient, dummyCache)

      // ensure repeated calls return the same response
      val resp1 = fn(client, url, headers).apply.block()
      val resp2 = fn(client, url, headers).apply.block()
      val resp3 = fn(client, url, headers).apply.block()
      val respMatch1 = resp1 must beEqualTo(resp2)
      val respMatch2 = resp2 must beEqualTo(resp3)

      // ensure that cache is hit for every call and client is never accessed
      respMatch1 and respMatch2 and verifyCacheInteraction(dummyCache, CacheInteraction(3, 0)) and verifyClientInteraction(dummyClient, ClientInteraction(0, 0, 0, 0, 0))
    }
  }

  private def verifyCallsApplyCacheMiss[T <: HttpRequestWithoutBody](expectedClientInteraction: ClientInteraction)(fn: (ReadCachingHttpClient, URL, Headers) => T) = {
    forAll(genURL,
      genHeaders,
      genDummyHttpClient,
      genDummyHttpResponseCache(Gen.const(Right(())), Gen.const(Right(())))) { (url, headers, dummyClient, dummyCache) =>

      val client = new ReadCachingHttpClient(dummyClient, dummyCache)

      // ensure repeated calls return the same response
      val resp1 = fn(client, url, headers).apply.block()
      val resp2 = fn(client, url, headers).apply.block()
      val resp3 = fn(client, url, headers).apply.block()
      val respMatch1 = resp1 must beEqualTo(resp2)
      val respMatch2 = resp2 must beEqualTo(resp3)

      // ensure that cache is missed for every call and client is always accessed
      respMatch1 and respMatch2 and verifyCacheInteraction(dummyCache, CacheInteraction(3, 0)) and verifyClientInteraction(dummyClient, expectedClientInteraction)
    }
  }

  private def getCallsApplyCacheHit = verifyCallsApplyCacheHit { (client, url, headers) =>
    client.get(url, headers)
  }

  private def headCallsApplyCacheHit = verifyCallsApplyCacheHit { (client, url, headers) =>
    client.head(url, headers)
  }

  private def getCallsApplyCacheMiss = verifyCallsApplyCacheMiss(ClientInteraction(3, 0, 0, 0, 0)) { (client, url, headers) =>
    client.get(url, headers)
  }

  private def headCallsApplyCacheMiss = verifyCallsApplyCacheMiss(ClientInteraction(0, 0, 0, 0, 3)) { (client, url, headers) =>
    client.head(url, headers)
  }

  private def postPutDeleteIgnoreCache = {
    forAll(genURL,
      genHeaders,
      genRawBody,
      genDummyHttpClient,
      genDummyHttpResponseCache(Gen.const(Right(())), Gen.const(Right(())))) { (url, headers, body, dummyClient, dummyCache) =>
      val client = new ReadCachingHttpClient(dummyClient, dummyCache)

      val postRes = client.post(url, headers, body).block() must beEqualTo(dummyClient.responseToReturn.block())
      val putRes = client.put(url, headers, body).block() must beEqualTo(dummyClient.responseToReturn.block())
      val deleteRes = client.delete(url, headers).block() must beEqualTo(dummyClient.responseToReturn.block())

      postRes and
      putRes and
      deleteRes and
      verifyCacheInteraction(dummyCache, CacheInteraction(0, 0)) and
      verifyClientInteraction(dummyClient, ClientInteraction(0, 1, 1, 1, 0))
    }
  }
}
