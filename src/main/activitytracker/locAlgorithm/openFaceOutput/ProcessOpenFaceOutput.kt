package activitytracker.locAlgorithm.openFaceOutput

import activitytracker.locAlgorithm.utils.DateTimeUtils
import activitytracker.locAlgorithm.utils.FileUtils
import com.intellij.openapi.application.PathManager
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class ProcessOpenFaceOutput(outputFolderPath: String, openFaceDatasetFilename: String) {
    private val outputFile: File
    private val fileUtils: FileUtils = FileUtils()

    fun getOutputFile(): File {
        return this.outputFile
    }

    private fun getNewOFLine(file: File, list: List<Array<String>>?, index: Int): Array<String> {
        val dateTimeUtils = DateTimeUtils()
        val dateTime = dateTimeUtils.dataCreationAddedToTimeLaps(file, list!![index][OF_TIMESTAMP])
        return arrayOf(
            dateTime,
            list[index][OF_AU01_r],
            list[index][OF_AU02_r],
            list[index][OF_AU04_r],
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
        val defaultOFOutput = PathManager.getPluginsPath() + "/open-face-output-stub/open-face-output-stub.csv"

        Files.copy(defaultOFOutputSource, Paths.get(defaultOFOutput), StandardCopyOption.REPLACE_EXISTING)
        return File(defaultOFOutput)
    }

    private fun mergeOpenFaceOutputs(outputFolderPath: String): MutableList<Array<String>>? {
        val files: Array<File>? = fileUtils.getFilesFromFolder(outputFolderPath, "csv")
        if (files != null) {
            val newOpenFaceList: MutableList<Array<String>> = java.util.ArrayList()
            var newLine: Array<String>

            val headers = arrayOf(
                "Timestamp", "AU01", "AU02", "AU04", "AU05", "AU06", "AU07", "AU09",
                "AU10", "AU12", "AU14", "AU15", "AU17", "AU20", "AU23", "AU25", "AU26", "AU45"
            )
            newOpenFaceList.add(headers)

            for (openFaceFile in files) {
                val list: MutableList<Array<String>> = fileUtils.parseCSVFile(openFaceFile.toString()) as MutableList<Array<String>>
                for (i in 1 until list.size) {  //position 0 is the header
                    newLine = getNewOFLine(openFaceFile, list, i)
                    newOpenFaceList.add(newLine)
                }
            }
            return newOpenFaceList
        }
        return null
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
        //TODO: check if openFace folder output is empty
        val dir = File(outputFolderPath)
        if(outputFolderPath.trim() != "" && dir.exists()) {
            val openFaceOutput: MutableList<Array<String>> = this.mergeOpenFaceOutputs(outputFolderPath) as MutableList<Array<String>>
            fileUtils.writeFile(openFaceDatasetFilename, openFaceOutput, false)
        }

        var fileTemp = File(openFaceDatasetFilename)

        if (!fileTemp.exists()) fileTemp = defaultOpenFaceOutput()

        outputFile = fileTemp
    }
}