package plugin.locAlgorithm.comprehensionMode

import plugin.config.Config
import plugin.openFaceAUs.ProcessOpenFaceOutput
import plugin.utils.DateTimeUtils
import plugin.utils.FileUtils
import plugin.utils.StringUtils

//TODO: se riprendo il monitoraggio, non salva nulla
class ProcessComprehensionModeOutput {
    private var comprehensionModeDataset: MutableList<Array<String>> = ArrayList()
    private lateinit var attentionValuesOutput: MutableList<Array<String>>
    private lateinit var openFaceAUsOutput: MutableList<Array<String>>
    private lateinit var popupOutput: MutableList<Array<String>>

    private val stringUtils: StringUtils = StringUtils()

    fun createOutputComprehensionMode(openFaceOutputFolderPath: String): Int {
        val fileUtils = FileUtils()
        val processOpenFaceOutput = ProcessOpenFaceOutput(openFaceOutputFolderPath, Config.OPEN_FACE_COMPREHENSION_MODE_DATASET_FILENAME)

        this.attentionValuesOutput = fileUtils.parseCSVFile(Config.ATTENTION_COMPREHENSION_MODE_DATASET_FILENAME) as MutableList<Array<String>>
        this.openFaceAUsOutput = fileUtils.parseCSVFile(processOpenFaceOutput.getOutputFile().toString()) as MutableList<Array<String>>
        this.popupOutput = fileUtils.parseCSVFile(Config.POPUP_COMPREHENSION_MODE_DATASET_FILENAME) as MutableList<Array<String>>

        val attentionPopupValues: MutableList<Array<String>> = this.mergeAttentionAndPopupValues(this.attentionValuesOutput, this.popupOutput)
        this.mergeAttentionAndAUsValues(attentionPopupValues, this.openFaceAUsOutput)

        fileUtils.addCSVHeader(
            Config.FINAL_COMPREHENSION_MODE_DATASET_FILENAME,
            this.comprehensionModeDataset, createComprehensionModeHeaders() as MutableList<Array<String>>)
        return Config.OK_CODE
    }

    private fun createComprehensionModeHeaders(): List<Array<String>> {
        val list: MutableList<Array<String>> = java.util.ArrayList()
        val headers = arrayOf(
            "Timestamp", "Attention", "AU01", "AU02", "AU04", "AU05", "AU06", "AU07", "AU09",
            "AU10", "AU12", "AU14", "AU15", "AU17", "AU20", "AU23", "AU25", "AU26", "AU45", "PopupResponse"
        )
        list.add(headers)
        return list
    }

    private fun mergeAttentionAndPopupValues(attentionList: MutableList<Array<String>>, popupList: MutableList<Array<String>>): MutableList<Array<String>> {
        val attentionPopupList: MutableList<Array<String>> = ArrayList()

        val empty = ""
        var response = empty

        for (i in attentionList.indices) {
            val attentionDate = attentionList[i][Config.NEUROSKY_TIMESTAMP]

            for (j in popupList.indices) {
                val popupDate = popupList[j][Config.POPUP_TIMESTAMP]

                if (attentionDate == popupDate)
                    response = popupList[j][Config.POPUP_RESPONSE]
            }

            val row = arrayOf(
                attentionList[i][Config.NEUROSKY_TIMESTAMP],
                attentionList[i][Config.NEUROSKY_ATTENTION],
                response
            )

            attentionPopupList.add(row)
            response = empty
        }
        return attentionPopupList
    }

    //TODO: i valori delle risposte si duplicano causa margine di 1 seconda tra date!
    private fun mergeAttentionAndAUsValues(attentionPopupList: MutableList<Array<String>>, openFaceAUsList: MutableList<Array<String>>) {
        val dateTimeUtils = DateTimeUtils()

        val empty = ""
        var attention = empty
        var popupResponse = empty

        for (i in 1 until openFaceAUsList.size) {
            val ofDate = dateTimeUtils.getDateFromString(
                stringUtils.replaceLastOccurrence(openFaceAUsList[i][Config.OF_TIMESTAMP], ":", "."))

            for (j in attentionPopupList.indices) {
                val attentionDate = dateTimeUtils.getDateFromString(attentionPopupList[j][Config.NEUROSKY_TIMESTAMP])
                val sameDates = dateTimeUtils.checkSameDates(ofDate, attentionDate)

                if (sameDates && attentionDate!!.before(ofDate)) {
                    attention = attentionPopupList[j][Config.NEUROSKY_ATTENTION]
                    popupResponse = attentionPopupList[j][Config.NEW_POPUP_RESPONSE]
                }
            }

            val row = arrayOf(
                openFaceAUsList[i][Config.TIMESTAMP],
                attention,
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
                popupResponse
            )

            if(attention != empty)
                comprehensionModeDataset.add(row)

            attention = empty
            popupResponse = empty
        }
    }
}