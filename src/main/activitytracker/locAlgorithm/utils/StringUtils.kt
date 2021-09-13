package activitytracker.locAlgorithm.utils

class StringUtils {
    fun stripLeading(string: String): String {
        var i = 0
        while (string[i].toString() == " ") {
            i++
        }
        return string.substring(i)
    }

    fun stripTrailing(string: String): String {
        var i = string.length - 1
        while (string[i].toString() == " ") {
            i--
        }
        return string.substring(0, i + 1)
    }
}