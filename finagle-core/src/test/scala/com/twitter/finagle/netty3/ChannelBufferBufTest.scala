package com.twitter.finagle.netty3

import com.twitter.io.Buf
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.jboss.netty.buffer.ChannelBuffers
import org.scalatest.prop.GeneratorDrivenPropertyChecks

@RunWith(classOf[JUnitRunner])
class ChannelBufferBufTest
  extends FunSuite
  with GeneratorDrivenPropertyChecks {

  test("ChannelBufferBuf.slice: slices according to the underlying ChannelBuffer") {
    val cb = ChannelBuffers.buffer(128)
    cb.writeBytes(Array[Byte](1,2,3,4,5,6,7,8,9))
    assert(cb.readByte() == 1)
    val buf = ChannelBufferBuf.Owned(cb)
    assert(buf.length == 8)
    assert(buf.slice(2,4) == Buf.ByteArray.Owned(Array[Byte](4,5)))
    assert(buf == Buf.ByteArray.Owned(Array[Byte](2,3,4,5,6,7,8,9)))
  }

  test("ChannelBufferBuf.slice: truncates on out-of-bounds indices") {
    val cb = ChannelBuffers.buffer(128)
    cb.writeBytes(Array[Byte](1,2,3,4,5,6,7,8,9))
    val buf = ChannelBufferBuf.Owned(cb)
    buf.slice(1, Int.MaxValue)
  }

  test("ChannelBufferBuf.Shared.apply") {
    val bytes = Array[Byte](1,2,3,4,5,6,7,8,9)
    val cb = ChannelBuffers.wrappedBuffer(bytes)
    val buf = ChannelBufferBuf.Shared(cb)
    bytes(0) = 0.toByte
    assert(buf == Buf.ByteArray.Owned(Array[Byte](1,2,3,4,5,6,7,8,9)))
    cb.clear()
    assert(buf == Buf.ByteArray.Owned(Array[Byte](1,2,3,4,5,6,7,8,9)))
  }

  test("ChannelBufferBuf.Direct.apply") {
    val bytes = Array[Byte](1,2,3,4,5,6,7,8,9)
    val cb = ChannelBuffers.wrappedBuffer(bytes)
    val buf = ChannelBufferBuf.Owned(cb)
    bytes(0) = 0.toByte
    assert(buf == Buf.ByteArray.Owned(Array[Byte](0,2,3,4,5,6,7,8,9)))
    cb.clear()
    assert(buf.length == 0)
  }

  test("ChannelBufferBuf.Shared.unapply") {
    val cb0 = ChannelBuffers.buffer(128)
    cb0.writeBytes(Array[Byte](1,2,3,4,5,6,7,8,9))
    val ChannelBufferBuf.Shared(cb1) = ChannelBufferBuf.Owned(cb0)
    cb0.clear()
    assert(cb1.readableBytes() == 9)
  }

  test("ChannelBufferBuf.Direct.unapply") {
    val cb0 = ChannelBuffers.buffer(128)
    cb0.writeBytes(Array[Byte](1,2,3,4,5,6,7,8,9))
    val ChannelBufferBuf.Owned(cb1) = ChannelBufferBuf.Owned(cb0)
    cb0.clear()
    assert(cb1.readableBytes() == 0)
  }

  test("ChannelBufferBuf.coerce(ChannelBufferBuf)") {
    val cb = ChannelBuffers.wrappedBuffer(Array[Byte](1,2,3,4,5,6,7,8,9))
    val orig = ChannelBufferBuf.Owned(cb)
    val coerced = ChannelBufferBuf.coerce(orig)
    assert(coerced eq orig)
  }

  test("ChannelBufferBuf.coerce(ByteArray)") {
    val orig = Buf.ByteArray.Owned(Array[Byte](1,2,3,4,5,6,7,8,9))
    val coerced = ChannelBufferBuf.coerce(orig)
    assert(coerced == orig)
  }

  test("ByteArray.coerce(ChannelBufferBuf)") {
    val orig = ChannelBufferBuf.Owned(
      ChannelBuffers.wrappedBuffer(Array[Byte](1,2,3,4,5,6,7,8,9), 2, 4))
    val coerced = Buf.ByteArray.coerce(orig)
    assert(coerced == orig)
    val Buf.ByteArray.Owned(bytes, begin, end) = coerced
    assert(bytes.length == 9)
    assert(begin == 2)
    assert(end == 6)
  }

  test("apply(Int)") {
    val out = new Array[Byte](1)
    forAll { bytes: Array[Byte] =>
      if (bytes.length >= 2) {
        val cb = ChannelBuffers.wrappedBuffer(bytes)
        cb.readByte()
        val buf = new ChannelBufferBuf(cb)

        // compare slice/write to apply
        buf.slice(0, 1).write(out, 0)
        assert(out(0) == buf(0))

        buf.slice(buf.length - 1, buf.length).write(out, 0)
        assert(out(0) == buf(buf.length - 1))
      }
    }
  }

  test("apply(Int) over the length") {
    forAll { bytes: Array[Byte] =>
      val cb = ChannelBuffers.wrappedBuffer(bytes)
      val buf = new ChannelBufferBuf(cb)
      intercept[IndexOutOfBoundsException] {
        buf(buf.length)
      }
    }
  }

  test("process returns -1 when fully processed") {
    forAll { bytes: Array[Byte] =>
      val cb = ChannelBuffers.wrappedBuffer(bytes)
      val buf = new ChannelBufferBuf(cb)

      var n = 0
      val processor = new Buf.Indexed.Processor {
        def apply(byte: Byte): Boolean = {
          n += 1
          true
        }
      }
      assert(-1 == buf.process(processor))
      assert(buf.length == n)
    }
  }

  test("process returns index where processing stopped") {
    val processor = new Buf.Indexed.Processor {
      def apply(byte: Byte): Boolean = false
    }
    forAll { bytes: Array[Byte] =>
      val cb = ChannelBuffers.wrappedBuffer(bytes)
      val buf = new ChannelBufferBuf(cb)
      assert(buf.process(processor) == (if (buf.isEmpty) -1 else 0))

      var n = 0
      val maxThree = new Buf.Indexed.Processor {
        def apply(byte: Byte): Boolean = {
          n += 1
          n <= 3
        }
      }

      val res = buf.process(maxThree)
      if (bytes.length <= 3)
        assert(-1 == res)
      else
        assert(3 == res)
    }
  }
}
