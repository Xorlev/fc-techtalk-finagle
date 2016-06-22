package oy.ids
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.redis.{Client, Redis}
import com.twitter.io.Buf
import com.twitter.util.Future

/**
  * "Snowflake" ID generation service
  *
  * @author Michael Rose (xorlev)
  */
class RedisIdService(addrs: String) extends IdService.FutureIface {
  val redis = Client(
    ClientBuilder()
      .hosts(addrs)
      .hostConnectionLimit(1)
      .codec(Redis())
      .daemon(true)
      .buildFactory())

  override def getNextId(): Future[Long] = redis.incr(Buf.Utf8("oycount")).map(_.toLong)
}
