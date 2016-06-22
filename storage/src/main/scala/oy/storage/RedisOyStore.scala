package oy.storage

import com.twitter.bijection.Injection
import com.twitter.bijection.scrooge.JsonScalaCodec
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.tracing.{DefaultTracer, Trace}
import com.twitter.finagle.zipkin.thrift.ZipkinTracer
import com.twitter.finagle.ThriftMux
import com.twitter.finagle.redis.{Client, Redis}
import com.twitter.io.Buf
import com.twitter.io.Buf.ByteArray
import com.twitter.util.{Await, Future}
import oy.protocol.Oy

/**
  * Redis implementation of the OyStore
  *
  * OyStore stores Oys as Redis KVs. The keys are byte-encoded longs, the values are JSON-encoded
  * Thrift.
  *
  * @author Michael Rose (xorlev)
  */

class RedisOyStore(addrs: String) extends OyStore.FutureIface {
  val redis = Client(
      ClientBuilder()
        .hosts(addrs)
        .hostConnectionLimit(1)
        .codec(Redis())
        .daemon(true)
        .buildFactory())
  val codec = JsonScalaCodec(Oy)

  override def get(id: Long): Future[Oy] = multiget(Seq(id)).map(_.head)

  override def store(oy: Oy): Future[Boolean] = {
    redis.set(idToBuf(oy.id), Buf.Utf8(codec(oy)))
      .map(_ => true)
  }

  override def multiget(oyIds: Seq[Long]): Future[Seq[Oy]] = {
    if(oyIds.isEmpty)
      return Future.value(Seq())

    val documents = redis.mGet(oyIds.map(idToBuf))

    documents.map { docs =>
      oyIds.zip(docs).map { case (reqId, doc) =>
        doc.map { buf =>
          codec.invert(Buf.Utf8.unapply(buf).get).get
        }.getOrElse(Oy(reqId, -1, -1, -1, ""))
      }
    }
  }

  private[storage] def idToBuf(id: Long): Buf = ByteArray.Owned(Injection.long2BigEndian(id))

}
