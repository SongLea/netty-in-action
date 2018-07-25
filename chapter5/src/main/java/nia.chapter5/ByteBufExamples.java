package nia.chapter5;

import io.netty.buffer.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ByteProcessor;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Random;

import static io.netty.channel.DummyChannelHandlerContext.DUMMY_INSTANCE;

/**
 * Netty的数据处理API通过abstract class ByteBuf与interface ByteBufHolder暴露
 * 优点：
 * 1、可以被用户自定义的缓冲区类型扩展；
 * 2、通过内置的复合缓冲区类型实现了透明的零拷贝；
 * 3、容量可以按需增长；
 * 4、在读和写这两种模式之间切换不需要调用ByteBuffer的flip()方法；
 * 5、读和写使用了不同的索引；
 * 6、支持方法的链式调用；
 * 7、支持引用计数；
 * 8、支持池化
 */
public class ByteBufExamples {
    private final static Random random = new Random();
    private static final ByteBuf BYTE_BUF_FROM_SOMEWHERE = Unpooled.buffer(1024);
    private static final Channel CHANNEL_FROM_SOMEWHERE = new NioSocketChannel();
    private static final ChannelHandlerContext CHANNEL_HANDLER_CONTEXT_FROM_SOMEWHERE = DUMMY_INSTANCE;

    /**
     * 堆缓冲区：
     * Listing 5.1 Backing array
     * 最常用的ByteBuf模式是将数据存储在JVM的堆空间中；
     * 这种模式被称为支撑数组，能在没有使用池化的情况下快速的分配和释放
     */
    public static void heapBuffer() {
        ByteBuf heapBuf = BYTE_BUF_FROM_SOMEWHERE; //get reference form somewhere
        // 检查ByteBuf是否有一个支撑数组
        if (heapBuf.hasArray()) {
            byte[] array = heapBuf.array(); // 如果有则获取对该数组的引用
            int offset = heapBuf.arrayOffset() + heapBuf.readerIndex(); // 计算第一个字节的偏移量
            int length = heapBuf.readableBytes(); // 获取可读字节数
            handleArray(array, offset, length); // 使用数组、偏移量和长度作为参数调用你的方法
        }
    }

    /**
     * 直接缓冲区(允许JVM实现通过本地调用来分配内存)：
     * Listing 5.2 Direct buffer data access
     * 缺点：因为数据不是在堆上，不得不进行一次复制
     */
    public static void directBuffer() {
        ByteBuf directBuf = BYTE_BUF_FROM_SOMEWHERE; //get reference form somewhere
        // 检查ByteBuf是否有一个支撑数组，如果不是则这是一个直接缓冲区
        if (!directBuf.hasArray()) {
            int length = directBuf.readableBytes(); // 获取可读字节数
            byte[] array = new byte[length]; // 分配一个新的数组来保存具有该长度的字节数组
            directBuf.getBytes(directBuf.readerIndex(), array); // 将字节复制到该数组
            handleArray(array, 0, length);  // 使用数组、偏移量和长度作为参数调用你的方法
        }
    }

    /**
     * 复合缓存区：
     * Listing 5.3 Composite buffer pattern using ByteBuffer
     * Netty通过一个子类CompositeByteBuf实现了这个模式，它提供一个将多个缓冲区表示为单个合并缓冲区的虚拟表示
     */
    public static void byteBufferComposite(ByteBuffer header, ByteBuffer body) {
        // Use an array to hold the message parts
        ByteBuffer[] message = new ByteBuffer[]{header, body};

        // Create a new ByteBuffer and use copy to merge the header and body
        ByteBuffer message2 =
                ByteBuffer.allocate(header.remaining() + body.remaining());
        message2.put(header);
        message2.put(body);
        message2.flip();
    }

    /**
     * Listing 5.4 Composite buffer pattern using CompositeByteBuf
     */
    public static void byteBufComposite() {
        CompositeByteBuf messageBuf = Unpooled.compositeBuffer();
        ByteBuf headerBuf = BYTE_BUF_FROM_SOMEWHERE; // can be backing or direct
        ByteBuf bodyBuf = BYTE_BUF_FROM_SOMEWHERE;   // can be backing or direct
        messageBuf.addComponents(headerBuf, bodyBuf); // 将ByteBuf实例追加到CompositeByteBuf
        //...
        messageBuf.removeComponent(0); // remove the header/删除位于索引位置为0的ByteBuf
        for (ByteBuf buf : messageBuf) {
            System.out.println(buf.toString());
        }
    }

    /**
     * Listing 5.5 Accessing the data in a CompositeByteBuf
     * 访问CompositeByteBuf中的数据类似于访问直接缓冲区的模式
     */
    public static void byteBufCompositeArray() {
        CompositeByteBuf compBuf = Unpooled.compositeBuffer();
        int length = compBuf.readableBytes();
        byte[] array = new byte[length];
        compBuf.getBytes(compBuf.readerIndex(), array);
        handleArray(array, 0, array.length);
    }

    /**
     * 随机访问索引/顺序访问索引
     * Listing 5.6 Access data
     */
    public static void byteBufRelativeAccess() {
        ByteBuf buffer = BYTE_BUF_FROM_SOMEWHERE; //get reference form somewhere
        // 遍历内容，不会改变readerIndex与writerIndex，可以使用readerIndex(index)与writerIndex(index)来手动移动
        for (int i = 0; i < buffer.capacity(); i++) {
            byte b = buffer.getByte(i);
            System.out.println((char) b);
        }
    }

    /**
     * 可读字节
     * Listing 5.7 Read all data
     */
    public static void readAllData() {
        ByteBuf buffer = BYTE_BUF_FROM_SOMEWHERE; //get reference form somewhere
        while (buffer.isReadable()) {
            System.out.println(buffer.readByte());
        }
    }

    /**
     * 可写字节
     * Listing 5.8 Write data
     */
    public static void write() {
        // Fills the writable bytes of a buffer with random integers.
        ByteBuf buffer = BYTE_BUF_FROM_SOMEWHERE; //get reference form somewhere
        while (buffer.writableBytes() >= 4) { // 确定缓冲区中是否还有足够的空间
            // 如果尝试往目标写入超过目标容量的数据，将会引发一个IndexOutOfBoundException
            buffer.writeInt(random.nextInt());
        }
    }

    /**
     * Listing 5.9 Using ByteProcessor to find \r
     * <p>
     * use {@link io.netty.buffer.ByteBufProcessor in Netty 4.0.x}
     */
    public static void byteProcessor() {
        ByteBuf buffer = BYTE_BUF_FROM_SOMEWHERE; //get reference form somewhere
        int index = buffer.forEachByte(ByteProcessor.FIND_CR);
    }

    /**
     * Listing 5.9 Using ByteBufProcessor to find \r
     * <p>
     * use {@link io.netty.util.ByteProcessor in Netty 4.1.x}
     */
    public static void byteBufProcessor() {
        ByteBuf buffer = BYTE_BUF_FROM_SOMEWHERE; //get reference form somewhere
        int index = buffer.forEachByte(ByteBufProcessor.FIND_CR);
    }

    // 派生缓冲区：为ByteBuf提供了以专门的方式来呈现其内容的视图，这类视图可以通过以下方法被创建
    /* 这些方法都返回一个新的ByteBuf实例，具有自己的读索引、写索引和标记索引，内部存储和JDK的ByteBuffer是共享的，
    如果你修改了它的内容，也同时修改了其对应的源实例，所以要小心。
    复制：使用copy()或copy(int, int)方法，不同于派生缓冲区，由这个调用返回的ByteBuf拥有独立的数据副本
        duplicate()
        slice()
        slice(int, int)
        Unpooled.unmodifiableBuffer()
        order(ByteOrder)
        readSlice(int)
     */

    /**
     * 对ByteBuf进行切片(操作ByteBuf的一个分段)
     * Listing 5.10 Slice a ByteBuf
     */
    public static void byteBufSlice() {
        Charset utf8 = Charset.forName("UTF-8");
        ByteBuf buf = Unpooled.copiedBuffer("Netty in Action rocks!", utf8);
        // 创建该ByteBuf从索引0开始到索引15结束的一个新切片
        ByteBuf sliced = buf.slice(0, 15);
        System.out.println(sliced.toString(utf8));
        buf.setByte(0, (byte) 'J'); // 更新索引0处的字节
        // 将会成功，因为数据是共享的，对其中一个所做的更改对另外一个也是可见的
        assert buf.getByte(0) == sliced.getByte(0);
    }

    /**
     * ByteBuf的分段的副本和切片的区别，场景是相同的，但使用slice()方法来避免复制内存的开销
     * Listing 5.11 Copying a ByteBuf
     */
    public static void byteBufCopy() {
        Charset utf8 = Charset.forName("UTF-8");
        ByteBuf buf = Unpooled.copiedBuffer("Netty in Action rocks!", utf8);
        ByteBuf copy = buf.copy(0, 15);
        System.out.println(copy.toString(utf8));
        buf.setByte(0, (byte) 'J');
        // 将会成功，因为数据不是共享的
        assert buf.getByte(0) != copy.getByte(0);
    }

    /**
     * get()和set()操作，从给定的索引开始，并且保持索引不变
     * 最常用的get()方法：
     * getBoolean(int) 返回给定索引处的Boolean值
     * getByte(int) 返回给定索引处的字节
     * getUnsignedByte(int) 将给定索引处的无符号字节值作为short返回
     * getMedium(int) 返回给定索引处的 24 位的中等int值
     * getUnsignedMedium(int) 返回给定索引处的无符号的 24 位的中等int值
     * getInt(int) 返回给定索引处的int值
     * getUnsignedInt(int) 将给定索引处的无符号int值作为long返回
     * getLong(int) 返回给定索引处的long值
     * getShort(int) 返回给定索引处的short值
     * getUnsignedShort(int) 将给定索引处的无符号short值作为int返回
     * getBytes(int, ...) 将该缓冲区中从给定索引开始的数据传送到指定的目的地
     * set()方法：
     * setBoolean(int, boolean) 设定给定索引处的Boolean值
     * setByte(int index, int value) 设定给定索引处的字节值
     * setMedium(int index, int value) 设定给定索引处的 24 位的中等int值
     * setInt(int index, int value) 设定给定索引处的int值
     * setLong(int index, long value) 设定给定索引处的long值
     * setShort(int index, int value) 设定给定索引处的short值
     * Listing 5.12 get() and set() usage
     */
    public static void byteBufSetGet() {
        Charset utf8 = Charset.forName("UTF-8");
        ByteBuf buf = Unpooled.copiedBuffer("Netty in Action rocks!", utf8);
        System.out.println((char) buf.getByte(0)); // 打印第一
        int readerIndex = buf.readerIndex();
        int writerIndex = buf.writerIndex();
        buf.setByte(0, (byte) 'B');
        System.out.println((char) buf.getByte(0));
        assert readerIndex == buf.readerIndex();
        assert writerIndex == buf.writerIndex();
    }

    /**
     * read()和write()操作，从给定的索引开始，并且会根据已经访问的字节数对索引进行调整
     * Listing 5.13 read() and write() operations on the ByteBuf
     */
    public static void byteBufWriteRead() {
        Charset utf8 = Charset.forName("UTF-8");
        ByteBuf buf = Unpooled.copiedBuffer("Netty in Action rocks!", utf8);
        System.out.println((char) buf.readByte());
        int readerIndex = buf.readerIndex();
        int writerIndex = buf.writerIndex();
        buf.writeByte((byte) '?'); // 将会移动writerIndex
        assert readerIndex == buf.readerIndex();
        assert writerIndex != buf.writerIndex(); // 将会成功，因为writeByte()方法移动了writerIndex
    }

    private static void handleArray(byte[] array, int offset, int len) {
    }

    // ByteBufHolder接口
    /*
        为Netty的高级特性提供了支持，如缓冲区池化
        1、content()：返回由这个ByteBufHolder所持有的ByteBuf
        2、copy()：返回这个ByteBufHolder的一个深拷贝，包括一个所包含的ByteBuf的非共享拷贝
        3、duplicate()： 返回这个ByteBufHolder的一个浅拷贝，包括一个其所包含的ByteBuf的共享拷贝
     */

    /**
     * ByteBufAllocator接口：实现ByteBuf的池化，它可以用来分配我们所描述的任意类型的ByteBuf实例
     * 1、PooledByteBufAllocator：池化了ByteBuf的实例以提高性能并最大限度地减少内存碎片；
     * 2、Unpooled-ByteBufAllocator：每次调用时都会一个新的实例。
     * Unpooled：可能某些情况下，你未能获取一个到ByteBufAllocator的引用，可以使用工具类Unpooled ，它提供静态的辅助方法来创建未池
     * 化的ByteBuf实例。
     * ByteBufUtil：用于操作ByteBuf的静态的辅助方法，是通用的并与池化无关、
     * 1、hexdump()：它以十六进制的表示形式打印 ByteBuf的内容；
     * 2、boolean equals(ByteBuf, ByteBuf)：用来判断两个ByteBuf实例的相等性；
     * Listing 5.14 Obtaining a ByteBufAllocator reference
     */
    public static void obtainingByteBufAllocatorReference() {
        Channel channel = CHANNEL_FROM_SOMEWHERE; //get reference form somewhere
        ByteBufAllocator allocator = channel.alloc(); // 从Channel获取一个到ByteBufAllocator的引用
        //...
        ChannelHandlerContext ctx = CHANNEL_HANDLER_CONTEXT_FROM_SOMEWHERE; //get reference form somewhere
        ByteBufAllocator allocator2 = ctx.alloc(); // 从ChannelHandlerContext获取一个到ByteBufAllocator的引用
        //...
    }

    /**
     * 引用计数是一种通过在某个对象所持有的资源不再被其他对象引用时释放该对象所持有的资源来优化内存使用和性能的技术
     * Listing 5.15 Reference counting
     */
    public static void referenceCounting() {
        Channel channel = CHANNEL_FROM_SOMEWHERE; //get reference form somewhere
        ByteBufAllocator allocator = channel.alloc();
        //...
        ByteBuf buffer = allocator.directBuffer();
        assert buffer.refCnt() == 1; // 检查引用计数是否为预期的1
        //...
    }

    /**11110
     * Listing 5.16 Release reference-counted object
     */
    public static void releaseReferenceCountedObject() {
        ByteBuf buffer = BYTE_BUF_FROM_SOMEWHERE; //get reference form somewhere
        boolean released = buffer.release(); // 减少该对象的活动引用，当减少到0时该对象被释放，并且该方法返回true
        //...
    }


}
