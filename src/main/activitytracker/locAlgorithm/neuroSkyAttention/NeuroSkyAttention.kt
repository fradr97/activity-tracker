package activitytracker.locAlgorithm.neuroSkyAttention

import org.joda.time.DateTime
import org.json.JSONObject
import java.io.IOException

open class NeuroSkyAttention {
    private val thinkGearSocketClient: ThinkGearSocketClient = ThinkGearSocketClient()
    var isStarted = false
        private set
    private var timer: Int

    private fun starting(): Boolean {
        if (thinkGearSocketClient.isDataAvailable) {
            while (!isStarted && timer < WAITING_TIME) {
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
        return timer < WAITING_TIME && isStarted
    }

    val attention: MutableList<Array<String>>?
        get() {
            var attention = NO_ATTENTION
            val list: MutableList<Array<String>> = ArrayList()
            return if (thinkGearSocketClient.isDataAvailable) {
                while (thinkGearSocketClient.isConnected && isStarted) {
                    val jsonString: String = thinkGearSocketClient.data
                    try {
                        val jsonObject = JSONObject(jsonString).getJSONObject("eSense")
                        attention = jsonObject.getInt("attention")
                    } catch (ignored: Exception) { }
                    val timestamp = DateTime.now()
                    val time = timestamp.toString()
                        .replace("T", " ")
                    list.add(arrayOf(time, attention.toString()))
                }
                list
            } else null
        }

    fun checkAttention(): Int {
        val thinkGearSocketClient = ThinkGearSocketClient()
        if(!thinkGearSocketClient.isConnected)
            thinkGearSocketClient.connect()

        var attention = NO_ATTENTION
        return if (thinkGearSocketClient.isDataAvailable) {
            val jsonString: String = thinkGearSocketClient.data
            try {
                val jsonObject = JSONObject(jsonString).getJSONObject("eSense")
                attention = jsonObject.getInt("attention")
            } catch (ignored: Exception) { }
            attention
        } else DATA_NOT_AVAILABLE
    }

    @Throws(IOException::class)
    fun stopConnection() {
        isStarted = false
        thinkGearSocketClient.close()
    }

    protected inner class TimerThread : Runnable {
        override fun run() {
            try {
                while (!isStarted && timer < WAITING_TIME) {
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

    companion object {
        private const val WAITING_TIME = 15

        const val DATA_NOT_AVAILABLE = -1
        const val NO_ATTENTION = 0
        const val MIN_ATTENTION = 20
        const val LOW_ATTENTION = 40
        const val MEDIUM_ATTENTION = 60
        const val HIGH_ATTENTION = 80
        const val MAX_ATTENTION = 100
    }
}