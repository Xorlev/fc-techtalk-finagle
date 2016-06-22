package oy.storage

import com.twitter.finagle.ThriftMux
import com.twitter.finagle.tracing.DefaultTracer
import com.twitter.finagle.zipkin.thrift.ZipkinTracer
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Duration, Future}

/**
  * TwitterServer for the OyStore
  *
  * @author Michael Rose (xorlev)
  */
object StoreApplication extends TwitterServer {
  val tracer = ZipkinTracer.mk(port=9410, sampleRate = 1.0f)
  DefaultTracer.self = tracer
  val store = new RedisOyStore("127.0.0.1:6379")
  val server = ThriftMux.server
    .withLabel("oystore-server")
    .serveIface("0.0.0.0:6666", store)

  def main() {
    Await.ready(adminHttpServer)
  }

  override def close(after: Duration): Future[Unit] = {
    server.close()
  }
}
