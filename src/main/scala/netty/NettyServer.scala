package lila.ws
package netty

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.epoll.{ EpollEventLoopGroup, EpollServerSocketChannel }
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http._
import javax.inject._
import scala.concurrent.ExecutionContext

@Singleton
final class NettyServer @Inject() (
    clients: ClientSystem,
    router: Router,
    config: Config
)(implicit ec: ExecutionContext) {

  val logger = Logger(getClass)

  def start: Unit = {

    logger.info("Start")

    val port = config.getInt("http.port")

    val bossGroup = new EpollEventLoopGroup(1) // 1 like in the netty examples (?)
    val workerGroup = new EpollEventLoopGroup

    try {
      val boot = new ServerBootstrap
      boot.group(bossGroup, workerGroup)
        .channel(classOf[EpollServerSocketChannel])
        .childHandler(new ChannelInitializer[SocketChannel] {
          override def initChannel(ch: SocketChannel): Unit = {
            val pipeline = ch.pipeline()
            pipeline.addLast(new HttpServerCodec)
            pipeline.addLast(new HttpObjectAggregator(4096)) // 8192?
            pipeline.addLast(new ProtocolHandler(
              clients,
              router,
              IpAddress(ch.localAddress.getAddress.getHostAddress)
            ))
            pipeline.addLast(new FrameHandler(clients))
          }
        })

      val server = boot.bind(port).sync().channel()

      logger.info(s"Listening to $port")

      server.closeFuture().sync()

      logger.info(s"Closed $port")
    }
    finally {
      bossGroup.shutdownGracefully()
      workerGroup.shutdownGracefully()
    }

  }
}