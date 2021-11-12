package plugin.utils

import org.joda.time.DateTime
import plugin.config.Config
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class DateTimeUtils {
    private var sdf: SimpleDateFormat? = null

    fun dataCreationAddedToTimeLaps(file: File, timeLapse: String): String {
        val dataCreationDateTime = getDataCreationFile(file)
        val date = dataCreationDateTime!!.substring(0, 10).trim()
        val time = dataCreationDateTime.substring(11).trim()
        return date + " " + sumTimes(time, timeLapseToTime(timeLapse))
    }

    private fun getDataCreationFile(file: File): String? {
        val attrs: BasicFileAttributes
        return try {
            attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            val time = attrs.creationTime()
            val pattern = "yyyy-MM-dd HH:mm:ss:SSS"
            val simpleDateFormat = SimpleDateFormat(pattern)
            simpleDateFormat.format(Date(time.toMillis()))
        } catch (e: IOException) {
            Config.DEFAULT_DATE
        }
    }

    private fun sumTimes(time1: String, time2: String): String? {
        sdf = SimpleDateFormat("HH:mm:ss:SSS")
        sdf!!.timeZone = TimeZone.getTimeZone("UTC")
        val sum: Long
        val date1: Date
        val date2: Date
        try {
            date1 = sdf!!.parse(time1)
            date2 = sdf!!.parse(time2)
            sum = date1.time + date2.time
        } catch (e: ParseException) {
            return null
        }
        return sdf!!.format(Date(sum))
    }

    private fun timeLapseToTime(timeLapse: String): String {
        var time = timeLapse
        time = time.trim { it <= ' ' }.replace(".", ":")
        val occ = countOccurrence(time, ":")
        if (occ == 1) {  //sec, mill
            time = "00:00:$time"
        } else if (occ == 2) {    //min, sec, mill
            time = "00:$time"
        }
        return time
    }

    private fun countOccurrence(string: String, type: String): Int {
        var occ = 0
        for (element in string) {
            if (element.toString() + "" == type) {
                occ++
            }
        }
        return occ
    }

    fun getDateFromString(dateTime: String?): Date? {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        val date: Date = try {
            formatter.parse(dateTime)
        } catch (e: ParseException) {
            return getDateFromString(Config.DEFAULT_DATE)
        }
        return date
    }

    /* checks if two dates are equal accepting a 1-second time difference margin */
    fun checkSameDates(date1: Date?, date2: Date?): Boolean {
        return datesMilliesDiff(date1!!, date2!!) <= ERROR_MARGIN_BETWEEN_DATES
    }

    fun datesMilliesDiff(pluginDate: Date?, ofDate: Date?): Long {
        return abs(pluginDate!!.time - ofDate!!.time)
    }

    fun getTimestamp(): String {
        val timestamp = DateTime.now()
        return timestamp.toString().replace("T", " ")
    }

    companion object {
        const val ERROR_MARGIN_BETWEEN_DATES: Long = 1000
    }
}