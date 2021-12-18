package plugin.locAlgorithm.comprehensionMode

import plugin.config.Config
import plugin.openFaceAUs.ProcessOpenFaceOutput
import plugin.utils.DateTimeUtils
import plugin.utils.FileUtils
import plugin.utils.StringUtils
import java.util.*
import kotlin.collections.ArrayList

class ProcessComprehensionModeOutput {
    private var comprehensionModeDataset: MutableList<Array<String>> = ArrayList()
    private lateinit var attentionValuesOutput: MutableList<Array<String>>
    private lateinit var openFaceAUsOutput: MutableList<Array<String>>
    private val stringUtils: StringUtils = StringUtils()

    fun createOutputComprehensionMode(openFaceOutputFolderPath: String): Int {
        this.comprehensionModeDataset.clear()
        val fileUtils = FileUtils()
        val processOpenFaceOutput = ProcessOpenFaceOutput(openFaceOutputFolderPath, Config.OPEN_FACE_COMPREHENSION_MODE_DATASET_FILENAME)

        this.attentionValuesOutput = fileUtils.parseCSVFile(Config.ATTENTION_COMPREHENSION_MODE_DATASET_FILENAME) as MutableList<Array<String>>
        this.openFaceAUsOutput = fileUtils.parseCSVFile(processOpenFaceOutput.getOutputFile().toString()) as MutableList<Array<String>>
        this.mergeAttentionAndAUsValues(attentionValuesOutput, openFaceAUsOutput)

        fileUtils.addCSVHeader(
            Config.FINAL_COMPREHENSION_MODE_DATASET_FILENAME,
            this.comprehensionModeDataset, createComprehensionModeHeaders() as MutableList<Array<String>>)
        return Config.OK_CODE
    }

    private fun createComprehensionModeHeaders(): List<Array<String>> {
        val list: MutableList<Array<String>> = java.util.ArrayList()
        val headers = arrayOf(
            "Timestamp", "AU01", "AU02", "AU04", "AU05", "AU06", "AU07", "AU09", "AU10", "AU12",
            "AU14", "AU15", "AU17", "AU20", "AU23", "AU25", "AU26", "AU45", "Question", "UserAnswer"
        )
        list.add(headers)
        return list
    }

    private fun indexToPopupMatch(openFaceAUsList: MutableList<Array<String>>, popupTimestamp: Date): Int {
        val dateTimeUtils = DateTimeUtils()

        for (i in 1 until openFaceAUsList.size) {
            val ofDate = dateTimeUtils.getDateFromString(
                stringUtils.replaceLastOccurrence(openFaceAUsList[i][Config.OF_TIMESTAMP], ":", "."))
            val sameDates = dateTimeUtils.checkSameDates(ofDate, popupTimestamp)

            if(sameDates && popupTimestamp.before(ofDate)) {
                return i
            }
        }
        return Config.NULL_CODE
    }

    private fun mergeAttentionAndAUsValues(attentionPopupList: MutableList<Array<String>>, openFaceAUsList: MutableList<Array<String>>) {
        val dateTimeUtils = DateTimeUtils()

        val empty = ""
        var question = empty
        var userAnswer = empty

        for (i in 1 until openFaceAUsList.size) {
            val ofDate = dateTimeUtils.getDateFromString(
                stringUtils.replaceLastOccurrence(openFaceAUsList[i][Config.OF_TIMESTAMP], ":", "."))

            for (j in attentionPopupList.indices) {
                val attentionDate = dateTimeUtils.getDateFromString(attentionPopupList[j][Config.HEADSET_TIMESTAMP])
                val sameDates = dateTimeUtils.checkSameDates(ofDate, attentionDate)

                if (sameDates && attentionDate!!.before(ofDate)) {
                    val index = indexToPopupMatch(openFaceAUsList, attentionDate)
                    if(i == index) {
                        question = attentionPopupList[j][Config.POPUP_QUESTION]
                        userAnswer = attentionPopupList[j][Config.POPUP_USER_ANSWER]
                    } else {
                        question = empty
                        userAnswer = empty
                    }
                }
            }
            val row = arrayOf(
                openFaceAUsList[i][Config.TIMESTAMP],
                openFaceAUsList[i][Config.OF_AU01],
                openFaceAUsList[i][Config.OF_AU02],
                openFaceAUsList[i][Config.OF_AU04],
                openFaceAUsList[i][Config.OF_AU05],
                openFaceAUsList[i][Config.OF_AU06],
                openFaceAUsList[i][Config.OF_AU07],
                openFaceAUsList[i][Config.OF_AU09],
                openFaceAUsList[i][Config.OF_AU10],
                openFaceAUsList[i][Config.OF_AU12],
                openFaceAUsList[i][Config.OF_AU14],
                openFaceAUsList[i][Config.OF_AU15],
                openFaceAUsList[i][Config.OF_AU17],
                openFaceAUsList[i][Config.OF_AU20],
                openFaceAUsList[i][Config.OF_AU23],
                openFaceAUsList[i][Config.OF_AU25],
                openFaceAUsList[i][Config.OF_AU26],
                openFaceAUsList[i][Config.OF_AU45],
                question,
                userAnswer
            )

            comprehensionModeDataset.add(row)

            question = empty
            userAnswer = empty
        }
    }
}