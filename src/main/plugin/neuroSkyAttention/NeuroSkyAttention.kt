package plugin.neuroSkyAttention

import org.joda.time.DateTime
import org.json.JSONObject
import plugin.config.Config
import java.io.IOException

open class NeuroSkyAttention {
    private val thinkGearSocketClient: ThinkGearSocketClient = ThinkGearSocketClient()
    var isStarted = false
        private set
    private var timer: Int
    var checkAttention: Int = 0
    var time: String = ""

    private fun starting(): Boolean {
        if (thinkGearSocketClient.isDataAvailable) {
            while (!isStarted && timer < Config.WAITING_TIME) {
                val jsonString: String = thinkGearSocketClient.data
                try {
                    val jsonObject = JSONObject(jsonString).getJSONObject("eSense")
                    if (jsonObject != null) isStarted = true
                } catch (ignored: Exception) { }
            }
        }
        return isStarted
    }

    fun waitForStarting(): Boolean {
        if(!thinkGearSocketClient.isConnected)
            thinkGearSocketClient.connect()

        timer = 0
        isStarted = false

        val timerThread = Thread(TimerThread())
        timerThread.start()
        starting()
        return timer < Config.WAITING_TIME && isStarted
    }

    val attention: MutableList<Array<String>>?
        get() {
            var attention = Config.NO_ATTENTION
            val list: MutableList<Array<String>> = ArrayList()
            return if (thinkGearSocketClient.isDataAvailable) {
                while (thinkGearSocketClient.isConnected && isStarted) {
                    val jsonString: String = thinkGearSocketClient.data
                    try {
                        val jsonObject = JSONObject(jsonString).getJSONObject("eSense")
                        attention = jsonObject.getInt("attention")
                        setAttention(attention)
                    } catch (ignored: Exception) { }
                    val timestamp = DateTime.now()
                    val time = timestamp.toString()
                        .replace("T", " ")
                    setTimestamp(time)
                    list.add(arrayOf(time, attention.toString()))
                }
                list
            } else null
        }

    private fun setAttention(attention: Int) {
        this.checkAttention = attention
    }

    fun getAttention(): Int {
        return this.checkAttention
    }

    private fun setTimestamp(timestamp: String) {
        this.time = timestamp
    }

    fun getTimestamp(): String {
        return this.time
    }

    fun checkAttention(): Int {
        val thinkGearSocketClient = ThinkGearSocketClient()
        if(!thinkGearSocketClient.isConnected)
            thinkGearSocketClient.connect()

        var attention = Config.NO_ATTENTION
        return if (thinkGearSocketClient.isDataAvailable) {
            val jsonString: String = thinkGearSocketClient.data
            try {
                val jsonObject = JSONObject(jsonString).getJSONObject("eSense")
                attention = jsonObject.getInt("attention")
            } catch (ignored: Exception) { }
            attention
        } else Config.NULL_CODE
    }

    @Throws(IOException::class)
    fun stopConnection() {
        isStarted = false
        thinkGearSocketClient.close()
    }

    protected inner class TimerThread : Runnable {
        override fun run() {
            try {
                while (!isStarted && timer < Config.WAITING_TIME) {
                    timer++
                    Thread.sleep(1000)
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    init {
        thinkGearSocketClient.connect()
        timer = 0
    }
}