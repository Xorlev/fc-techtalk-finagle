package oy.timeline

import com.twitter.bijection.scrooge.JsonScalaCodec
import com.twitter.finagle.http._
import com.twitter.finagle.http.filter.{CommonLogFormatter, LoggingFilter, StatsFilter}
import com.twitter.finagle.param.Stats
import com.twitter.finagle.tracing.DefaultTracer
import com.twitter.finagle.zipkin.thrift.ZipkinTracer
import com.twitter.finagle.{Http, Service, ThriftMux, http}
import com.twitter.io.{Buf, Reader}
import com.twitter.scrooge.{JsonThriftSerializer, ThriftStructSerializer}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Duration, Future}
import org.apache.thrift.protocol.TJSONProtocol
import oy.protocol.Oy
import oy.storage.TimelineResponse

/**
  * TwitterServer hosting both a Thrift RPC server as well as a HTTP server, plus handling
  * the lifecycle of both servers.
  *
  * @author Michael Rose (xorlev)
  */
object TimelineApplication extends TwitterServer {
  val tracer = ZipkinTracer.mk(port=9410,sampleRate = 1.0f)
  DefaultTracer.self = tracer
  val store = new RedisTimelineService("127.0.0.1:6664","127.0.0.1:6666","127.0.0.1:6379")

  val codec = new JsonScalaCodec(TimelineResponse)

  val ser = new ThriftStructSerializer[TimelineResponse] {
    override def codec = TimelineResponse
    override val protocolFactory = new TJSONProtocol.Factory
  }
  val json = JsonThriftSerializer(TimelineResponse)
  val muxer = new HttpMuxer()
    .withHandler("/api/timeline", new Service[http.Request, http.Response] {
      override def apply(request: Request): Future[Response] = {
        if(request.containsParam("id"))
          toResponse(store.timelineForUser(request.getParam("id").toInt))
        else
          toResponse(store.globalTimeline())
      }

      def toResponse(timelineResponse: Future[TimelineResponse]) = {
        timelineResponse.map { rep =>
          http.Response(Version.Http11, Status.Ok, Reader.fromBuf(Buf.Utf8(json.toString(rep))))
        }
      }
    })
    .withHandler("/api/post_oy", new Service[http.Request, http.Response] {
      override def apply(request: Request): Future[Response] = {
        val maybeString: Option[String] = Buf.Utf8.unapply(request.content)

        // Thrift's JSON representations are poor: bidirectional JSON looks like garbage, e.g.
        // {"1": "thing"} based on underlying tagids. The codecs above provide readable JSON
        // at the expense of not being bidirectional. So here we read in form params. Whoo.
        // Protocol buffers wouldn't have this shortcoming...
        val oy = Oy(
          fromId = request.getParam("from_id").toInt,
          toId = request.getParam("to_id").toInt,
          message = request.getParam("message"),
          id = -1L,
          timestamp = -1L
        )

        store.postOy(oy).map { oy =>
          http.Response(Version.Http11, Status.Ok)
        }
      }
    })

  val httpService = new LoggingFilter(log, new CommonLogFormatter)
    .andThen(new StatsFilter[Request](statsReceiver))
    .andThen(muxer)

  val thriftServer = ThriftMux.server
    .withLabel("timeline-server")
    .withStatsReceiver(statsReceiver)
    .serveIface("0.0.0.0:6665", store)

  val httpServer = Http
    .server
    .withLabel("timeline-server")
    .withStatsReceiver(statsReceiver)
    .configured(Stats(statsReceiver))
    .withAdmissionControl.concurrencyLimit(16, 0)
    .serve(":3000", httpService)

  def main() {
    Await.ready(adminHttpServer)
  }

  override def close(after: Duration): Future[Unit] = {
    thriftServer.close()
    httpServer.close()
  }
}
