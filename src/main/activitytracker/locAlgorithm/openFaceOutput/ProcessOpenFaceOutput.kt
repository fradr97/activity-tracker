package activitytracker.locAlgorithm.openFaceOutput

import activitytracker.locAlgorithm.utils.DateTimeUtils
import activitytracker.locAlgorithm.utils.FileUtils
import com.intellij.openapi.application.PathManager
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.collections.ArrayList

class ProcessOpenFaceOutput(outputFilePath: String?) {
    private var file: File?
    private val fileUtils: FileUtils = FileUtils()

    val openFaceAUs: List<Array<String>>
        get() {
            val newOpenFaceList: MutableList<Array<String>> = ArrayList()
            var newLine: Array<String>
            val openFaceOutput = fileUtils.parseCSVFile(file.toString())
            for (i in 1 until openFaceOutput!!.size) {  //position 0 is the header
                newLine = getNewOFLine(openFaceOutput, i)
                newOpenFaceList.add(newLine)
            }
            return newOpenFaceList
        }

    private fun getNewOFLine(list: List<Array<String>>?, index: Int): Array<String> {
        val dateTimeUtils = DateTimeUtils()
        val dateTime = dateTimeUtils.dataCreationAddedToTimeLaps(file!!, list!![index][OF_TIMESTAMP])
        return arrayOf(
            dateTime,
            list[index][OF_AU01_r],
            list[index][OF_AU02_r],
            list[index][OF_AU04_r],
            list[index][OF_AU05_r],
            list[index][OF_AU05_r],
            list[index][OF_AU06_r],
            list[index][OF_AU07_r],
            list[index][OF_AU09_r],
            list[index][OF_AU10_r],
            list[index][OF_AU12_r],
            list[index][OF_AU14_r],
            list[index][OF_AU15_r],
            list[index][OF_AU17_r],
            list[index][OF_AU20_r],
            list[index][OF_AU23_r],
            list[index][OF_AU25_r],
            list[index][OF_AU26_r],
            list[index][OF_AU45_r]
        )
    }

    private fun defaultOpenFaceOutput(): File {
        val defaultOFOutputSource: InputStream = javaClass.getResourceAsStream("/open-face-output-stub/open-face-output-stub.csv")
        File(PathManager.getPluginsPath() + "/open-face-output-stub/").mkdirs()
        val defaultOFOutputTarget = PathManager.getPluginsPath() + "/open-face-output-stub/open-face-output-stub.csv"

        Files.copy(defaultOFOutputSource, Paths.get(defaultOFOutputTarget), StandardCopyOption.REPLACE_EXISTING)
        return File(defaultOFOutputTarget)
    }

    companion object {
        const val OF_TIMESTAMP = 2
        const val OF_AU01_r = 679
        const val OF_AU02_r = 680
        const val OF_AU04_r = 681
        const val OF_AU05_r = 682
        const val OF_AU06_r = 683
        const val OF_AU07_r = 684
        const val OF_AU09_r = 685
        const val OF_AU10_r = 686
        const val OF_AU12_r = 687
        const val OF_AU14_r = 688
        const val OF_AU15_r = 689
        const val OF_AU17_r = 690
        const val OF_AU20_r = 691
        const val OF_AU23_r = 692
        const val OF_AU25_r = 693
        const val OF_AU26_r = 694
        const val OF_AU45_r = 695
    }

    init {
        file = fileUtils.getLastModifiedFile(outputFilePath, "csv")

        if(file == null)
            file = defaultOpenFaceOutput()
    }
}