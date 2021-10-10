package activitytracker.locAlgorithm.neuroSkyAttention

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.CharBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.*

class ThinkGearSocketClient {
    var host: String = DEFAULT_HOST
    var port: Int = DEFAULT_PORT

    var isConnected: Boolean
        private set
    var channel: SocketChannel? = null
    var `in`: Scanner? = null

    @Throws(IOException::class)
    fun connect() {
        if (!isConnected) {
            println("connect() - Starting new connection...")
            channel = SocketChannel.open(InetSocketAddress(host, port))
            val enc = StandardCharsets.US_ASCII.newEncoder()
            val jsonCommand = "{\"enableRawOutput\": false, \"format\": \"Json\"}\n"
            channel?.write(enc.encode(CharBuffer.wrap(jsonCommand)))
            `in` = Scanner(channel)
            isConnected = true
        } else {
            println("connect() - Already connected...")
        }
    }

    val isDataAvailable: Boolean
        get() = if (isConnected) {
            `in`!!.hasNextLine()
        } else {
            false
        }

    val data: String
        get() = `in`!!.nextLine()

    @Throws(IOException::class)
    fun close() {
        if (isConnected) {
            println("close() - Closing connection...")
            `in`!!.close()
            channel!!.close()
            isConnected = false
        }
    }

    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 13854
    }

    init {
        isConnected = false
    }
}