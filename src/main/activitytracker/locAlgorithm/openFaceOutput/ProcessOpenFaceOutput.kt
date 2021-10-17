package activitytracker.locAlgorithm.openFaceOutput

import activitytracker.locAlgorithm.utils.FileUtils
import java.io.File
import java.util.ArrayList

class ProcessOpenFaceOutput {
    private val file: File?
    private val fileUtils: FileUtils = FileUtils()

    //position 0 is the header
    val openFaceAUs: List<Array<String>>?
        get() {
            val newOpenFaceList: MutableList<Array<String>> = ArrayList()
            var newLine: Array<String>
            return if (file == null) {
                null
            } else {
                val openFaceOutput = fileUtils.parseCSVFile(file.toString())
                for (i in 1 until openFaceOutput!!.size) {  //position 0 is the header
                    newLine = getNewOFLine(openFaceOutput, i)
                    newOpenFaceList.add(newLine)
                }
                newOpenFaceList
            }
        }

    private fun getNewOFLine(list: List<Array<String>>?, index: Int): Array<String> {
        val dateTimeProcess = DateTimeProcess(file!!)
        val dateTime = dateTimeProcess.lastModificationAddedToTimeLaps(list!![index][OF_TIMESTAMP])
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
        private const val OPEN_FACE_DATASET_DIRECTORY = "C:\\Users\\Francesco\\Desktop\\output\\"
    }

    init {
        file = fileUtils.getLastModifiedFile(OPEN_FACE_DATASET_DIRECTORY, "csv")
    }
}