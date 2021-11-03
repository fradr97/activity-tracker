package activitytracker.locAlgorithm

import activitytracker.locAlgorithm.activityTrackerOutput.ProcessActivityTrackerOutput
import activitytracker.locAlgorithm.openFaceOutput.ProcessOpenFaceOutput
import activitytracker.locAlgorithm.utils.DateTimeUtils
import activitytracker.locAlgorithm.utils.FileUtils
import activitytracker.locAlgorithm.utils.StringUtils
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import kotlin.math.roundToInt
import com.intellij.openapi.application.PathManager


class ProcessPluginOutput {
    private var pluginDataset: MutableList<Array<String>>
    private val processATOutput: ProcessActivityTrackerOutput
    private lateinit var attentionValuesOutput: MutableList<Array<String>>
    private lateinit var openFaceAUsOutput: MutableList<Array<String>>

    private val stringUtils: StringUtils

    fun createPluginOutput(fileOnFocus: String?, openFaceOutputFolderPath: String): Int {
        val fileUtils = FileUtils()
        val processOpenFaceOutput = ProcessOpenFaceOutput(openFaceOutputFolderPath, OPEN_FACE_DATASET_FILENAME)

        val trackerOutput = this.processATOutput.getCleanedATOutput(ACTIVITY_TRACKER_DATASET_FILENAME, fileOnFocus!!)

        this.attentionValuesOutput = fileUtils.parseCSVFile(ATTENTION_DATASET_FILENAME) as MutableList<Array<String>>
        this.openFaceAUsOutput = fileUtils.parseCSVFile(processOpenFaceOutput.getOutputFile().toString()) as MutableList<Array<String>>
        this.pluginDataset = updateLineNumbers(trackerOutput)
        this.deleteEmptyInstructionOutput()
        this.mergeAttentionAndAUsValues(this.attentionValuesOutput, this.openFaceAUsOutput)

        fileUtils.addCSVHeader(FINAL_DATASET_FILENAME, this.pluginDataset, createHeadersOutput() as MutableList<Array<String>>)
        return OK_CODE
    }

    private fun createHeadersOutput(): List<Array<String>> {
        val list: MutableList<Array<String>> = java.util.ArrayList()
        val headers = arrayOf(
            "Timestamp", "EventType", "EventData", "ModifiedFile", "Line", "Column", "Instruction",
            "LinesNumber", "Attention", "AU01", "AU02", "AU04", "AU05", "AU06", "AU07", "AU09",
            "AU10", "AU12", "AU14", "AU15", "AU17", "AU20", "AU23", "AU25", "AU26", "AU45"
        )
        list.add(headers)
        return list
    }

    private fun updateLineNumbers(list: List<Array<String>>): MutableList<Array<String>> {
        var newLine: Array<String>
        val newOutput: MutableList<Array<String>> = ArrayList()

        for (i in list.indices) {
            newLine = this.createNewLine(list, i)
            newOutput.add(newLine)
            val currentLOC = list[i][CURRENT_LINE_COUNT].toInt()
            var previousLOC = currentLOC

            if (i > 0) {
                previousLOC = list[i - 1][CURRENT_LINE_COUNT].toInt()
            }

            val offsetLOC = currentLOC - previousLOC
            val currentNumberLine = list[i][LINE].toInt()

            when (list[i][EVENT]) {
                "EditorEnter" ->
                    if (isEventEndLine(list, i) || isEventEndLineMinusOne(list, i)) {
                        var k = 0
                        while (k < newOutput.size) {
                            if (newOutput[k][LINE].toInt() > currentNumberLine) {
                                newLine = this.createNewLine(newOutput, k, offsetLOC)
                                newOutput[k] = newLine
                            }
                            k++
                        }
                    } else if (isEventInsideInstruction(list, i)) {
                        var k = 0
                        while (k < newOutput.size) {
                            if (newOutput[k][LINE].toInt() > currentNumberLine) {
                                newLine = this.createNewLine(newOutput, k, offsetLOC)
                                newOutput[k] = newLine
                            } else if (newOutput[k][LINE].toInt() == currentNumberLine) {
                                newLine = arrayOf(
                                    newOutput[k][TIMESTAMP],
                                    newOutput[k][EVENT_TYPE],
                                    newOutput[k][EVENT],
                                    newOutput[k][FILENAME],
                                    newOutput[k][LINE],
                                    newOutput[k][COLUMN],
                                    newOutput[k][LINE_INSTRUCTION].substring(0, newOutput[k][COLUMN].toInt()),
                                    newOutput[k][CURRENT_LINE_COUNT]
                                )
                                newOutput[k] = newLine
                            }
                            k++
                        }
                    } else {
                        var k = 0
                        while (k < newOutput.size) {
                            if (newOutput[k][LINE].toInt() >= currentNumberLine) {
                                newLine = this.createNewLine(newOutput, k, offsetLOC)
                                newOutput[k] = newLine
                            }
                            k++
                        }
                    }
                "EditorBackSpace" -> {
                    deleteEmptyInstructionLines(list[i][LINE_INSTRUCTION], list[i][LINE].toInt(), newOutput)
                    if (currentLOC < previousLOC) {
                        var k = 0
                        while (k < newOutput.size) {
                            if (newOutput[k][LINE].toInt() >= currentNumberLine) {
                                newLine = this.createNewLine(newOutput, k, offsetLOC)
                                newOutput[k] = newLine
                            }
                            k++
                        }
                    }
                }
                else -> {
                    deleteEmptyInstructionLines(list[i][LINE_INSTRUCTION], list[i][LINE].toInt(), newOutput)
                }
            }
        }
        return newOutput
    }

    private fun isEventEndLine(list: List<Array<String>>, index: Int): Boolean {
        val columnEvent = list[index][COLUMN].toInt()
        val lengthLineEvent: Int = stringUtils.stripTrailing(list[index][LINE_INSTRUCTION]).length
        return columnEvent >= lengthLineEvent
    }

    private fun isEventEndLineMinusOne(list: List<Array<String>>, index: Int): Boolean {
        if (list[index][LINE_INSTRUCTION].trim { it <= ' ' }.length == 1) return false
        val columnEvent = list[index][COLUMN].toInt()
        val lengthLineEvent: Int = stringUtils.stripTrailing(list[index][LINE_INSTRUCTION]).length
        return columnEvent == lengthLineEvent - 1
    }

    private fun isEventInsideInstruction(list: List<Array<String>>, index: Int): Boolean {
        if (list[index][LINE_INSTRUCTION].trim { it <= ' ' }.length <= 1) return false
        val columnEvent = list[index][COLUMN].toInt()
        val lengthLineEvent = list[index][LINE_INSTRUCTION].length
        val startInstruction: Int = lengthLineEvent - stringUtils.stripLeading(list[index][LINE_INSTRUCTION]).length
        val endInstruction: Int = stringUtils.stripTrailing(list[index][LINE_INSTRUCTION]).length
        return columnEvent in (startInstruction + 1) until endInstruction
    }

    private fun createNewLine(list: List<Array<String>>, index: Int): Array<String> {
        return arrayOf(
            list[index][TIMESTAMP],
            list[index][EVENT_TYPE],
            list[index][EVENT],
            list[index][FILENAME],
            list[index][LINE],
            list[index][COLUMN],
            list[index][LINE_INSTRUCTION],
            list[index][CURRENT_LINE_COUNT]
        )
    }

    private fun createNewLine(list: List<Array<String>>, index: Int, lineOffset: Int): Array<String> {
        return arrayOf(
            list[index][TIMESTAMP],
            list[index][EVENT_TYPE],
            list[index][EVENT],
            list[index][FILENAME],
            (list[index][LINE].toInt() + lineOffset).toString(),
            list[index][COLUMN],
            list[index][LINE_INSTRUCTION],
            list[index][CURRENT_LINE_COUNT]
        )
    }

    /* Remove lines that have become without instructions */
    private fun deleteEmptyInstructionLines(instruction: String, line: Int, newOutput: MutableList<Array<String>>) {
        if (instruction.trim { it <= ' ' } == "") {
            val toRemove: MutableList<Array<String>> = ArrayList()
            for (row in newOutput) {
                if (line == row[LINE].toInt()) {
                    toRemove.add(row)
                }
            }
            for (row in toRemove) {
                newOutput.remove(row)
            }
        }
    }

    /* Lines without instruction are removed from plugin dataset */
    private fun deleteEmptyInstructionOutput() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in pluginDataset) {
            if (atOutput[LINE_INSTRUCTION].trim { it <= ' ' } == "") {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            pluginDataset.remove(row)
        }
    }

    fun getHighlightedAttentionLines(document: Document, editor: Editor, fileOnFocus: String): Int {
        val fileUtils = FileUtils()
        val attentionValues = fileUtils.parseCSVFile(FINAL_DATASET_FILENAME) ?: return NULL_CODE

        val lineHighlighter = LineHighlighter()
        for (i in 0 until document.lineCount) {
            val editorLineNumber = i + 1
            val range = TextRange(
                editor.document.getLineStartOffset(i),
                editor.document.getLineEndOffset(i)
            )
            if (editor.document.getText(range).trim { it <= ' ' } != "") {
                val meanAttentionLine = getMeanLineAttention(attentionValues, fileOnFocus, editorLineNumber)
                if (meanAttentionLine >= 0) lineHighlighter.addLineHighlighter(
                    editor,
                    editorLineNumber,
                    meanAttentionLine
                )
            }
        }
        return OK_CODE
    }

    private fun getMeanLineAttention(list: List<Array<String>>, fileOnFocus: String, line: Int): Int {
        var sum = 0
        var occurrences = 0
        for (row in list) {
            if (fileOnFocus == row[FILENAME] &&
                line == row[LINE].toInt()
            ) {
                sum += row[ATTENTION].toInt()
                occurrences++
            }
        }
        return if (occurrences == 0) NULL_CODE else (sum / occurrences).toDouble().roundToInt()
    }

    private fun mergeAttentionAndAUsValues(attentionList: MutableList<Array<String>>, openFaceAUsList: MutableList<Array<String>>) {
        val dateTimeUtils = DateTimeUtils()

        var attention = "0"
        val defaultAUsDensity = "0.00"

        var au01 = defaultAUsDensity
        var au02 = defaultAUsDensity
        var au04 = defaultAUsDensity
        var au05 = defaultAUsDensity
        var au06 = defaultAUsDensity
        var au07 = defaultAUsDensity
        var au09 = defaultAUsDensity
        var au10 = defaultAUsDensity
        var au12 = defaultAUsDensity
        var au14 = defaultAUsDensity
        var au15 = defaultAUsDensity
        var au17 = defaultAUsDensity
        var au20 = defaultAUsDensity
        var au23 = defaultAUsDensity
        var au25 = defaultAUsDensity
        var au26 = defaultAUsDensity
        var au45 = defaultAUsDensity

        for (i in 0 until pluginDataset.size) {
            val pluginDate = dateTimeUtils.getDateFromString(pluginDataset[i][TIMESTAMP])

            for (j in attentionList.indices) {
                val attentionDate = dateTimeUtils.getDateFromString(attentionList[j][NEUROSKY_TIMESTAMP])
                val sameDates = dateTimeUtils.checkSameDates(pluginDate, attentionDate)

                if (sameDates && attentionDate!!.before(pluginDate))
                    attention = attentionList[j][NEUROSKY_ATTENTION]
            }
            for (k in 1 until openFaceAUsList.size) {
                val ofDate = dateTimeUtils.getDateFromString(
                    stringUtils.replaceLastOccurrence(openFaceAUsList[k][OF_TIMESTAMP], ":", "."))
                val sameDates = dateTimeUtils.checkSameDates(pluginDate, ofDate)

                if (sameDates && ofDate!!.before(pluginDate)) {
                    au01 = openFaceAUsList[k][OF_AU01_r]
                    au02 = openFaceAUsList[k][OF_AU02_r]
                    au04 = openFaceAUsList[k][OF_AU04_r]
                    au05 = openFaceAUsList[k][OF_AU05_r]
                    au06 = openFaceAUsList[k][OF_AU06_r]
                    au07 = openFaceAUsList[k][OF_AU07_r]
                    au09 = openFaceAUsList[k][OF_AU09_r]
                    au10 = openFaceAUsList[k][OF_AU10_r]
                    au12 = openFaceAUsList[k][OF_AU12_r]
                    au14 = openFaceAUsList[k][OF_AU14_r]
                    au15 = openFaceAUsList[k][OF_AU15_r]
                    au17 = openFaceAUsList[k][OF_AU17_r]
                    au20 = openFaceAUsList[k][OF_AU20_r]
                    au23 = openFaceAUsList[k][OF_AU23_r]
                    au25 = openFaceAUsList[k][OF_AU25_r]
                    au26 = openFaceAUsList[k][OF_AU26_r]
                    au45 = openFaceAUsList[k][OF_AU45_r]
                }
            }

            val row = arrayOf(
                pluginDataset[i][TIMESTAMP],
                pluginDataset[i][EVENT_TYPE],
                pluginDataset[i][EVENT],
                pluginDataset[i][FILENAME],
                pluginDataset[i][LINE],
                pluginDataset[i][COLUMN],
                pluginDataset[i][LINE_INSTRUCTION],
                pluginDataset[i][CURRENT_LINE_COUNT],
                attention,
                au01,
                au02,
                au04,
                au05,
                au06,
                au07,
                au09,
                au10,
                au12,
                au14,
                au15,
                au17,
                au20,
                au23,
                au25,
                au26,
                au45
            )

            pluginDataset[i] = row

            attention = "0"
            au01 = defaultAUsDensity
            au02 = defaultAUsDensity
            au04 = defaultAUsDensity
            au05 = defaultAUsDensity
            au06 = defaultAUsDensity
            au07 = defaultAUsDensity
            au09 = defaultAUsDensity
            au10 = defaultAUsDensity
            au12 = defaultAUsDensity
            au14 = defaultAUsDensity
            au15 = defaultAUsDensity
            au17 = defaultAUsDensity
            au20 = defaultAUsDensity
            au23 = defaultAUsDensity
            au25 = defaultAUsDensity
            au26 = defaultAUsDensity
            au45 = defaultAUsDensity
        }
    }

    companion object {
        private val BASE_DATASETS_PATH = "${PathManager.getPluginsPath()}/activity-tracker"
        val FINAL_DATASET_FILENAME = "$BASE_DATASETS_PATH/ide-events-attention.csv"
        val ACTIVITY_TRACKER_DATASET_FILENAME = "$BASE_DATASETS_PATH/ide-events.csv"
        val ATTENTION_DATASET_FILENAME = "$BASE_DATASETS_PATH/attention.csv"
        val OPEN_FACE_DATASET_FILENAME = "$BASE_DATASETS_PATH/open-face.csv"

        const val OK_CODE = 0
        const val NULL_CODE = -1

        /* New indexes for the plugin dataset */
        const val TIMESTAMP = 0
        const val EVENT_TYPE = 1
        const val EVENT = 2
        const val FILENAME = 3
        const val LINE = 4
        const val COLUMN = 5
        const val LINE_INSTRUCTION = 6
        const val CURRENT_LINE_COUNT = 7
        const val ATTENTION = 8

        /* Indexes for the attention list (old) */
        const val NEUROSKY_TIMESTAMP = 0
        const val NEUROSKY_ATTENTION = 1

        /* Indexes for the OpenFace list (old) */
        const val OF_TIMESTAMP = 0
        const val OF_AU01_r = 1
        const val OF_AU02_r = 2
        const val OF_AU04_r = 3
        const val OF_AU05_r = 4
        const val OF_AU06_r = 5
        const val OF_AU07_r = 6
        const val OF_AU09_r = 7
        const val OF_AU10_r = 8
        const val OF_AU12_r = 9
        const val OF_AU14_r = 10
        const val OF_AU15_r = 11
        const val OF_AU17_r = 12
        const val OF_AU20_r = 13
        const val OF_AU23_r = 14
        const val OF_AU25_r = 15
        const val OF_AU26_r = 16
        const val OF_AU45_r = 17
    }

    init {
        pluginDataset = ArrayList()
        processATOutput = ProcessActivityTrackerOutput()
        stringUtils = StringUtils()
    }
}