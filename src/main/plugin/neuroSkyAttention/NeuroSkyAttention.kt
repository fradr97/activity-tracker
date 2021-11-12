package plugin.neuroSkyAttention

import org.json.JSONObject
import plugin.config.Config
import java.util.ArrayList
import kotlin.math.abs

open class NeuroSkyAttention {
    private val thinkGearSocketClient: ThinkGearSocketClient = ThinkGearSocketClient()
    private var attention: Int = 0
    private var error = 0

    init {
        thinkGearSocketClient.connect()
    }

    fun waitToStart(): Boolean {
        if(!isConnected())
            thinkGearSocketClient.connect()
        return isConnected()
    }

    fun isConnected(): Boolean {
        return thinkGearSocketClient.isConnected
    }

    fun monitorAttention() {
        val jsonString: String = thinkGearSocketClient.data
        try {
            if(thinkGearSocketClient.isDataAvailable) {
                val jsonObject = JSONObject(jsonString).getJSONObject("eSense")
                attention = jsonObject.getInt("attention")
                error = 0
            }
        } catch (ex: Exception) {
            error += 1
        }
    }

    fun isAttentionDropped(newVarianceAttention: Int, oldVarianceAttention: Int): Boolean {
        val diff = newVarianceAttention - oldVarianceAttention
        return if (diff < 0) {
            abs(diff) > Config.ATTENTION_THRESHOLD
        } else false
    }

    fun updateBuffer(buffer: ArrayList<Int>, element: Int) {
        if (buffer.size == Config.BUFFER_THRESHOLD) buffer.removeAt(0)
        buffer.add(element)
    }

    fun getAttentionValue(): Int {
        return this.attention
    }

    fun error(): Boolean {
        if(this.error >= Config.NEUROSKY_ERROR_ATTEMPTS) {
            attention = Config.NO_ATTENTION_VALUE_OBTAINED
            return true
        }
        return false
    }

    fun stopConnection() {
        thinkGearSocketClient.close()
    }
}