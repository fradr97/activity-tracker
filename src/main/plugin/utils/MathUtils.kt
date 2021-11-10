package plugin.utils

import java.util.ArrayList
import kotlin.math.pow
import kotlin.math.sqrt

class MathUtils {
    fun avg(values: ArrayList<Int>): Int {
        var sum = 0
        val size = values.size
        for (value in values) {
            sum += value
        }
        return if (size > 0) sum / size else 0
    }

    fun standardDeviation(values: ArrayList<Int>, avg: Int): Int {
        val samples = ArrayList<Int>()
        var sum = 0
        for (value in values) {
            samples.add((value - avg).toDouble().pow(2).toInt())
        }
        for (sample in samples) {
            sum += sample
        }
        return if (samples.size > 0) sqrt((sum / (samples.size - 1)).toDouble()).toInt() else 0
    }

    fun percentage(value: Int, percentage: Int): Int {
        val valuePercentage: Int = (value * percentage) / 100
        return value - valuePercentage
    }
}