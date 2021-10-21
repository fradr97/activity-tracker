package activitytracker.locAlgorithm.utils

import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class DateTimeUtils(private val file: File) {
    private var sdf: SimpleDateFormat? = null

    fun lastModificationAddedToTimeLaps(timeLapse: String): String {
        val lastModificationDateTime = getLastModificationFile(file)
        val date = lastModificationDateTime.substring(0, 10)
        val lastModificationTime = lastModificationDateTime.substring(11)
        return date + " " + sumTimes(lastModificationTime, timeLapseToTime(timeLapse))
    }

    private fun getLastModificationFile(file: File): String {
        sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SS")
        return sdf!!.format(file.lastModified())
    }

    private fun sumTimes(time1: String, time2: String): String? {
        sdf = SimpleDateFormat("HH:mm:ss:SS")
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
        var timeLapse = timeLapse
        timeLapse = timeLapse.trim { it <= ' ' }.replace(".", ":")
        val occ = countOccurrence(timeLapse, ":")
        if (occ == 1) {  //sec, mill
            timeLapse = "00:00:$timeLapse"
        } else if (occ == 2) {    //min, sec, mill
            timeLapse = "00:$timeLapse"
        }
        return timeLapse
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

    @Throws(ParseException::class)
    fun getDateFromString(dateTime: String, format: String): Date? {
        val formatter = SimpleDateFormat(format)
        return formatter.parse(dateTime)
    }

    /* checks if two dates are equal accepting a 1-second time difference margin */
    @Throws(ParseException::class)
    fun checkSameDates(date1: String, date2: String): Boolean {
        return datesMilliesDiff(date1, date2) <= 1000
    }

    @Throws(ParseException::class)
    private fun datesMilliesDiff(date1: String, date2: String): Long {
        sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        sdf!!.timeZone = TimeZone.getTimeZone("UTC")

        val diff: Long
        val dt1: Date
        val dt2: Date
        try {
            dt1 = sdf!!.parse(date1)
            dt2 = sdf!!.parse(date2)
            diff = abs(dt2.time - dt1.time)
        } catch (e: ParseException) {
            return -9999    //Error
        }
        return diff
    }
}