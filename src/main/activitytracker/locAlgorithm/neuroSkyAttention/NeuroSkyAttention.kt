package activitytracker.locAlgorithm.neuroSkyAttention

import org.joda.time.DateTime
import org.json.JSONObject
import java.io.IOException
import java.util.*

open class NeuroSkyAttention {
    private val thinkGearSocketClient: ThinkGearSocketClient = ThinkGearSocketClient()
    var isStarted = false
        private set
    private var timer: Int

    private fun starting(): Boolean {
        isStarted = false
        if (thinkGearSocketClient.isDataAvailable) {
            while (!isStarted && timer < WAITING_TIME) {
                val jsonString: String = thinkGearSocketClient.data
                try {
                    val jsonObject = JSONObject(jsonString).getJSONObject("eSense")
                    if (jsonObject != null) isStarted = true
                } catch (ignored: Exception) {
                }
            }
        }
        return isStarted
    }

    fun waitForStarting(): Boolean {
        val timerThread = Thread(TimerThread())
        timerThread.start()
        starting()
        return timer < WAITING_TIME && isStarted
    }

    val attention: List<Array<String>>?
        get() {
            var attention: Int
            val list: MutableList<Array<String>> = ArrayList()
            return if (thinkGearSocketClient.isDataAvailable) {
                while (thinkGearSocketClient.isConnected && isStarted) {
                    val jsonString: String = thinkGearSocketClient.data
                    attention = try {
                        val jsonObject = JSONObject(jsonString).getJSONObject("eSense")
                        jsonObject.getInt("attention")
                    } catch (ex: Exception) {
                        -1
                    }
                    val timestamp = DateTime.now()
                    val time = timestamp.toString().replace("T", " ").replace("+02:00", "")
                    list.add(arrayOf(time, attention.toString()))
                }
                list
            } else null
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
                    println(timer)
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
        private const val WAITING_TIME = 20
    }
}