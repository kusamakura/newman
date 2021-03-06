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

import com.stackmob.newman.request._
import scalaz._
import Scalaz._
import com.stackmob.newman.caching.HttpResponseCacher
import response.HttpResponse
import java.net.URL
import scala.concurrent.{ExecutionContext, Future}
import ETagAwareHttpClient._
import scalaz.NonEmptyList._
import org.apache.http.HttpHeaders

/**
 * an HttpClient that respects ETag headers and caches {{{HttpResponse}}}s appropriately
 * @param httpClient the underlying HttpClient to do network requests when appropriate
 * @param httpResponseCacher the cacher to cache {{{HttpResponse}}}s when appropriate
 * @param ctx the execution context to handle future scheduling for acquiring and releasing cache line access
 */
class ETagAwareHttpClient(httpClient: HttpClient,
                          httpResponseCacher: HttpResponseCacher)
                         (implicit ctx: ExecutionContext) extends HttpClient {

  override def get(u: URL, h: Headers): GetRequest = new ETagGetRequest(u, h, httpResponseCacher)({ (url: URL, headers: Headers) =>
    httpClient.get(url, headers).apply
  })(ctx)

  override def post(u: URL, h: Headers, b: RawBody): PostRequest = PostRequest(u, h, b) {
    httpClient.post(u, h, b).apply
  }

  override def put(u: URL, h: Headers, b: RawBody): PutRequest = PutRequest(u, h, b) {
    httpClient.put(u, h, b).apply
  }

  override def delete(u: URL, h: Headers): DeleteRequest = DeleteRequest(u, h) {
    httpClient.delete(u, h).apply
  }

  override def head(u: URL, h: Headers): HeadRequest = HeadRequest(u, h) {
    httpClient.head(u, h).apply
  }
}

object ETagAwareHttpClient {
  /**
   * add an If-None-Match header to the given headers, using the given eTag
   * @param eTag the eTag to set in the new If-None-Match headers
   * @return the new headers
   */
  private[ETagAwareHttpClient] def addIfNoneMatch(existingHeaders: Headers, eTag: String): Headers = {
    existingHeaders.map { headerList: HeaderList =>
      nel(HttpHeaders.IF_NONE_MATCH -> eTag, headerList.list.filterNot(_._1 === HttpHeaders.IF_NONE_MATCH))
    }.orElse {
      Headers(HttpHeaders.IF_NONE_MATCH -> eTag)
    }
  }

  /**
   * a {{{GetRequest}}} that honors ETags. this function must be called mutually exclusively
   * @param url the URL to get
   * @param headers the headers to send with the request
   * @param cache the cache to use for retrieving and setting responses
   * @param rawGet a function to do a GET request directly against the server, with no cache interaction. used to execute If-None-Match requests
   */
  private[ETagAwareHttpClient] class ETagGetRequest(override val url: URL,
                                                    override val headers: Headers,
                                                    cache: HttpResponseCacher)
                                                   (rawGet: (URL, Headers) => Future[HttpResponse])
                                                   (implicit ctx: ExecutionContext) extends GetRequest {
    /**
     * check the eTag in a cached HttpResponse.
     * If it exists, then execute a request against the server with an If-None-Match.
     * If it doesn't exist, then execute a request against the server to fetch the body.
     * @param cachedResponseFuture the cached response that we'll check
     * @return a Future containing the most up to date HttpResponse, either from the server or the cache if it's fresh
     */
    private def cacheHit(cachedResponseFuture: Future[HttpResponse]): Future[HttpResponse] = {
      cachedResponseFuture.flatMap { resp =>
        resp.eTag.map { eTag =>
        //the response was cached and has an eTag so check it against the server
          val newHeaderList = addIfNoneMatch(resp.headers, eTag)
          rawGet(url, newHeaderList).flatMap { response: HttpResponse =>
            if(response.notModified) {
              //the response was not modified, so return the cached response
              Future.successful(resp)
            } else {
              //the response may have been modified, so execute it directly against the server
              rawGet(url, headers)
            }
          }
        } | {
          //the response was cached and is missing an eTag, so execute it directly against the server
          rawGet(url, headers)
        }
      }
    }

    override def apply: Future[HttpResponse] = {
      /**
       * this method uses an AsyncMutex to protect all the cache accesses for this {{{GetRequest}}}.
       * the general algorithm is check the cache, check the etag of the cached result, rerun the request if necessary, and return.
       * if it's not in the cache, just run the request as normal. all of the preceding steps are done inside the critical section
       * for this request. the critical section is necessary because this method and applyImpl (above) chain together multiple
       * cache operations
       */
      cache.fold(this, cacheHit = cacheHit, cacheMiss = rawGet(url, headers))
    }
  }

}
