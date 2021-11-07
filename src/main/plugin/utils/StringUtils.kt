package plugin.utils

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

    fun replaceLastOccurrence(string: String, oldOcc: String, newOcc: String): String {
        var i = string.length - 1
        if (string.contains(oldOcc)) {
            while (i > 0 && string[i].toString() != oldOcc) {
                i--
            }
            return string.substring(0, i) + newOcc + string.substring(i + 1)
        }
        return string
    }
}