package plugin.neuroSkyAttention

import plugin.config.Config
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.CharBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.*

class ThinkGearSocketClient {
    var isConnected: Boolean
        private set
    var channel: SocketChannel? = null
    var scanner: Scanner? = null

    init {
        isConnected = false
    }

    @Throws(IOException::class)
    fun connect() {
        try {
            if (!isConnected) {
                channel = SocketChannel.open(InetSocketAddress(Config.DEFAULT_HOST, Config.DEFAULT_PORT))
                val enc = StandardCharsets.US_ASCII.newEncoder()
                val jsonCommand = "{\"enableRawOutput\": false, \"format\": \"Json\"}\n"
                channel?.write(enc.encode(CharBuffer.wrap(jsonCommand)))
                scanner = Scanner(channel)
                isConnected = true
            }
        } catch (ex : Exception) {
            isConnected = false
        }
    }

    val isDataAvailable: Boolean
        get() = if (isConnected) {
            scanner!!.hasNextLine()
        } else {
            false
        }

    val data: String
        get() = scanner!!.nextLine()

    @Throws(IOException::class)
    fun close() {
        if (isConnected) {
            scanner!!.close()
            channel!!.close()
            isConnected = false
        }
    }
}