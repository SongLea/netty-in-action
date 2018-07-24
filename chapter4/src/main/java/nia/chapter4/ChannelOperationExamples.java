package nia.chapter4;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Listing 4.5 Writing to a Channel
 * <p>
 * Listing 4.6 Using a Channel from many threads
 *
 * @author <a href="mailto:norman.maurer@gmail.com">Norman Maurer</a>
 */
public class ChannelOperationExamples {
    private static final Channel CHANNEL_FROM_SOMEWHERE = new NioSocketChannel();

    /**
     * Listing 4.5 Writing to a Channel
     */
    public static void writingToChannel() {
        Channel channel = CHANNEL_FROM_SOMEWHERE; // Get the channel reference from somewhere
        ByteBuf buf = Unpooled.copiedBuffer("your data", CharsetUtil.UTF_8);
        ChannelFuture cf = channel.writeAndFlush(buf);
        // 添加ChannelFutureListener以便在写操作完成后接收通知
        cf.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                // 写操作完成，并且没有错误发生
                if (future.isSuccess()) {
                    System.out.println("Write successful");
                } else {
                    System.err.println("Write error");
                    future.cause().printStackTrace();
                }
            }
        });
    }

    /**
     * Listing 4.6 Using a Channel from many threads
     */
    public static void writingToChannelFromManyThreads() {
        final Channel channel = CHANNEL_FROM_SOMEWHERE; // Get the channel reference from somewhere
        final ByteBuf buf = Unpooled.copiedBuffer("your data",
                CharsetUtil.UTF_8);
        // 创建将数据写到Channel的Runnable
        Runnable writer = new Runnable() {
            @Override
            public void run() {
                // 派生类buffer，简单的把所有的数据访问请求发送给内部的buffer
                channel.write(buf.duplicate());
            }
        };
        // 获取到线程池Executor的引用
        Executor executor = Executors.newCachedThreadPool();

        // write in one thread
        executor.execute(writer); // 递交写任务给线程池以便在某个线程中执行

        // write in another thread
        executor.execute(writer); // 递交另一个写任务以便在另一个线程中执行
        //...
    }
}
