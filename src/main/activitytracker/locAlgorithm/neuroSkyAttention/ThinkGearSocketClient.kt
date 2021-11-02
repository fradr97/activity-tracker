package activitytracker.locAlgorithm.neuroSkyAttention

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

    @Throws(IOException::class)
    fun connect() {
        try {
            if (!isConnected) {
                channel = SocketChannel.open(InetSocketAddress(DEFAULT_HOST, DEFAULT_PORT))
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

    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 13854
    }

    init {
        isConnected = false
    }
}