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
package response.serialization

import org.specs2.Specification
import org.specs2.execute.{Result => SpecsResult, Failure => SpecsFailure}
import com.stackmob.newman.response.{HttpResponseCode, HttpResponse}
import com.stackmob.newman._

class HttpResponseSerializationSpecs extends Specification { def is =
  "HttpResponseSerializationSpecs".title                                                                                ^
    """
  HttpResponse serialization is responsible for changing an HttpResponse into a JValue and back. It can be
  used to serialize an HttpResponse for storage in a cache, for output to the console, etc...
  """                                                                                                                   ^
  "HttpResponseSerialization should"                                                                                    ^
    "round trip correctly"                                                                                              ! RoundTrip().succeeds ^
                                                                                                                        end
  trait Context extends BaseContext {
    protected val headers = Headers("header1" -> "header1")
    protected val body = RawBody("abcd")
    protected val resp = HttpResponse(HttpResponseCode.Ok, headers, RawBody.empty)
  }

  case class RoundTrip() extends Context {
    def succeeds: SpecsResult = {
      val json = resp.toJson(true)
      HttpResponse.fromJson(json).fold(
        succ = { deserialized: HttpResponse =>
          (deserialized.code must beEqualTo(resp.code)) and
          (deserialized.headers must haveTheSameHeadersAs(resp.headers)) and
          (deserialized.rawBody.toList must containTheSameElementsAs(resp.rawBody.toList))
        },
        fail = { e =>
          SpecsFailure("deserialization failed with error %s".format(e.toString()))
        }
      )
    }
  }

}
