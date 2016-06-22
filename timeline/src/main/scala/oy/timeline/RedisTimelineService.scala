package oy.timeline

import com.twitter.bijection.Injection
import com.twitter.finagle.ThriftMux
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.redis.util.StringToChannelBuffer
import com.twitter.finagle.redis.{Client, ClientError, Redis}
import com.twitter.util.Future
import org.jboss.netty.buffer.{BigEndianHeapChannelBuffer, ChannelBuffer}
import oy.ids.IdService
import oy.protocol.Oy
import oy.storage.{OyStore, TimelineResponse, TimelineService}

/**
  * Timeline Service & API
  *
  * Responsible for maintaining timelines of Oys globally and per person. Also accepts new Oys
  * to store and place into the timelines
  *
  * @author Michael Rose (xorlev)
  */
class RedisTimelineService(idSvcAddrs: String, storageAddrs: String, redisAddrs: String)
    extends TimelineService.FutureIface {
  type UserId = Int

  val storage = ThriftMux
    .client
    .withLabel("oystore-service")
    .newIface[OyStore.FutureIface](storageAddrs)
  val ids = ThriftMux
    .client
    .withLabel("id-service")
    .newIface[IdService.FutureIface](idSvcAddrs)
  val redis = Client(
    ClientBuilder()
      .hosts(redisAddrs)
      .hostConnectionLimit(1)
      .codec(Redis())
      .daemon(true)
      .buildFactory())

  override def globalTimeline(): Future[TimelineResponse] =
    retrieveTimeline().map(TimelineResponse(_))

  override def timelineForUser(userId: UserId): Future[TimelineResponse] =
    retrieveTimeline(Some(userId)).map(TimelineResponse(_))

  override def postOy(yo: Oy): Future[Boolean] = {
    // write to storage
    // push to destination timeline
    // push to global timeline
    for {
      nextId <- ids.getNextId()
      oy: Oy = yo.copy(id = nextId, timestamp = System.currentTimeMillis())
      str <- storage.store(oy)
      global <- pushToTimeline(None, oy)
      user <- pushToTimeline(Some(yo.toId), oy)
    } yield {
      true // because this is a bad API currently.
    }
  }

  private def pushToTimeline(scope: Option[UserId] = None, yo: Oy): Future[Boolean] = {
    val globalKey: ChannelBuffer = StringToChannelBuffer(timelineKey(scope))

    redis.lPush(globalKey, List(new BigEndianHeapChannelBuffer(Injection.long2BigEndian(yo.id)))).flatMap { _ =>
      redis.lTrim(globalKey, 0L, 1000L).map(x => true)
    }

  }

  private def retrieveTimeline(scope: Option[UserId] = None): Future[Seq[Oy]] = {
    val key = timelineKey(scope)

    redis.lRange(StringToChannelBuffer(key), 0L, -1L).flatMap { list =>
      val ids = list.map(_.readLong())

      storage.multiget(ids)
    }.rescue {
      case e: ClientError =>
        if(e.message.contains("Empty KeySet"))
          Future.value(Seq())
        else
          throw e
    }
  }

  private def timelineKey(scope: Option[UserId]): String = {
    scope match {
      case Some(userId) => s"timeline:$userId"
      case None => "timeline"
    }
  }
}
