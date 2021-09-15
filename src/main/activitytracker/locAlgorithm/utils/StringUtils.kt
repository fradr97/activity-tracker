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
        return string.replaceFirst("\\s++$".toRegex(), "")
    }
}