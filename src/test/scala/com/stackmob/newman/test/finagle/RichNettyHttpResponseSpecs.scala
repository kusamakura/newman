package com.stackmob.newman.test.finagle

import org.specs2.{ScalaCheck, Specification}
import com.stackmob.newman.FinagleHttpClient.RichNettyHttpResponse
import com.stackmob.newman.response.{HttpResponse, HttpResponseCode}
import org.scalacheck.Prop.forAllNoShrink

/**
 * Created by IntelliJ IDEA.
 *
 * com.stackmob.newman.test.finagle
 *
 * User: aaron
 * Date: 9/27/13
 * Time: 3:03 PM
 */
class RichNettyHttpResponseSpecs extends Specification with ScalaCheck { def is =
  "RichNettyHttpResponseSpecs".title                                                                                    ^ end ^
  "RichNettyHttpResponse is a class extension for org.jboss.netty.handler.codec.http.HttpResponse"                      ^ end ^
  "toNewmanHttpResponse should"                                                                                         ^
    "return the correct code"                                                                                           ! ToNewmanHttpResponse().correctCode ^
    "return the correct headers"                                                                                        ! ToNewmanHttpResponse().correctHeaders ^
    "return the correct body"                                                                                           ! ToNewmanHttpResponse().correctBody ^
  end ^
  "bodyBytes should return the correct body"                                                                            ! BodyBytes().correctBody ^ end ^
  end

  case class ToNewmanHttpResponse() {
    def correctCode = forAllNoShrink(genNettyResponse) { tup =>
      val (code, _, _, nettyResp) = tup
      nettyResp.toNewmanHttpResponse must beSome.like {
        case resp: HttpResponse => {
          HttpResponseCode.fromInt(code.getCode) must beSome.like {
            case respCode => respCode.code must beEqualTo(code.getCode)
          }
        }
      }
    }
    def correctHeaders = forAllNoShrink(genNettyResponse) { tup =>
      val (_, headers, _, nettyResp) = tup
      nettyResp.toNewmanHttpResponse must beSome.like {
        case resp: HttpResponse => resp.headers must beSome.like {
          case headerList => headerList.list.toMap must beEqualTo(headers)
        }
      }
    }
    def correctBody = forAllNoShrink(genNettyResponse) { tup =>
      val (_, _, body, nettyResp) = tup
      nettyResp.toNewmanHttpResponse must beSome.like {
        case resp => {
          resp.rawBody must beEqualTo(body.array())
        }
      }
    }
  }

  case class BodyBytes() {
    def correctBody = forAllNoShrink(genNettyResponse) { tup =>
      val (_, _, body, nettyResp) = tup
      nettyResp.bodyBytes.toList must beEqualTo(body.array.toList)
    }
  }
}
