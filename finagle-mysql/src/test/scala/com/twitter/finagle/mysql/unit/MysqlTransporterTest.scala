package com.twitter.finagle.mysql

import com.twitter.finagle.{Stack, Status}
import com.twitter.util.{Await, Duration}
import java.net.{InetAddress, InetSocketAddress, ServerSocket, SocketAddress}
import org.scalatest.FunSuite

class MysqlTransporterTest extends FunSuite {
  // This is an example MySQL server response in bytes
  val initialBytes: Array[Byte] = Array(74, 0, 0, 0, 10, 53, 46, 55, 46, 50, 52, 0, -71, 44, 0, 0,
    88, 10, 77, 4, 94, 126, 122, 117, 0, -1, -1, 8, 2, 0, -1, -63, 21, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    64, 116, 69, 9, 124, 24, 53, 73, 96, 24, 14, 21, 0, 109, 121, 115, 113, 108, 95, 110, 97, 116,
    105, 118, 101, 95, 112, 97, 115, 115, 119, 111, 114, 100, 0)

  val handshakeResponseResult: Array[Byte] = Array(7, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0)

  test("MysqlTransporter remoteAddress is the address passed in") {
    val addr: SocketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress, 0)
    val transporter = new MysqlTransporter(addr, Stack.Params.empty, performHandshake = false)
    assert(transporter.remoteAddress == addr)
  }

  test("MysqlTransporter can create a transport for MySQL") {
    // Setup the ServerSocket. 50 is the default for the listen backlog.
    // Need to supply it in order to supply the third param (bindAddr)
    val server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
    try {
      val addr = new InetSocketAddress(InetAddress.getLoopbackAddress, server.getLocalPort)
      val transporter = new MysqlTransporter(addr, Stack.Params.empty, performHandshake = false)
      val transportFut = transporter()
      val acceptedSocket = server.accept()
      val transport = Await.result(transportFut, Duration.fromSeconds(2))
      try {
        assert(transport.status == Status.Open)

        // Write the MySQL initial greeting to the client
        val outStream = acceptedSocket.getOutputStream
        outStream.write(initialBytes)
        outStream.flush()
        outStream.close()

        // Read the initial greeting on the client side
        // Make sure that it can be seen as a MySQL Packet
        val packet = Await.result(transport.read(), Duration.fromSeconds(2))
        assert(packet.seq == 0)
        assert(packet.body.length == 74)
      } finally {
        transport.close()
      }
    } finally {
      server.close()
    }
  }

  test("MysqlTransporter can create a Transport which performs a plain handshake") {
    // Setup the ServerSocket. 50 is the default for the listen backlog.
    // Need to supply it in order to supply the third param (bindAddr)
    val server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress)
    try {
      val addr = new InetSocketAddress(InetAddress.getLoopbackAddress, server.getLocalPort)
      val transporter = new MysqlTransporter(addr, Stack.Params.empty, performHandshake = true)
      val transportFut = transporter()
      val acceptedSocket = server.accept()
      // Write the MySQL initial greeting to the client
      val outStream = acceptedSocket.getOutputStream
      outStream.write(initialBytes)
      outStream.flush()

      outStream.write(handshakeResponseResult)
      outStream.flush()

      val transport = Await.result(transportFut, Duration.fromSeconds(2))
      try {
        assert(transport.status == Status.Open)

        val result = Await.result(transport.close(), Duration.fromSeconds(2))
      } finally {
        transport.close()
      }
    } finally {
      server.close()
    }
  }
}
