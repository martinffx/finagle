package com.twitter.finagle.http2.transport

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http2.{Http2Headers, Http2HeadersFrame, Http2ResetFrame}
import org.mockito.Mockito._
import org.scalatest.FunSuite
import org.scalatest.mockito.MockitoSugar

class H2UriValidatorHandlerTest extends FunSuite with MockitoSugar {

  test("Accepts valid URI") {
    val channel = new EmbeddedChannel(H2UriValidatorHandler)

    val frame = mock[Http2HeadersFrame]
    val headers = mock[Http2Headers]
    when(frame.headers()).thenReturn(headers)
    when(headers.path()).thenReturn("/abc.jpg")

    assert(channel.writeInbound(frame))
    assert(channel.readInbound[Http2HeadersFrame].headers().path() == "/abc.jpg")
  }

  test("Rejects invalid URI") {
    val channel = new EmbeddedChannel(H2UriValidatorHandler)

    val frame = mock[Http2HeadersFrame]
    val headers = mock[Http2Headers]
    when(frame.headers()).thenReturn(headers)
    when(headers.path()).thenReturn("/DSC02175拷貝.jpg")

    assert(channel.writeInbound(frame) == false)
    assert(channel.readOutbound[Http2ResetFrame].errorCode() == 400)
  }

}
