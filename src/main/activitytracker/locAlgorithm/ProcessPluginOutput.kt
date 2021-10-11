package activitytracker.locAlgorithm

import activitytracker.locAlgorithm.activityTrackerOutput.ProcessActivityTrackerOutput
import activitytracker.locAlgorithm.gui.TextHighlightAttention
import activitytracker.locAlgorithm.utils.FileParser
import activitytracker.locAlgorithm.utils.StringUtils
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class ProcessPluginOutput {
    private var pluginDataset: MutableList<Array<String>>
    private val processATOutput: ProcessActivityTrackerOutput
    private lateinit var attentionValuesOutput: MutableList<Array<String>>
    private val stringUtils: StringUtils

    fun createPluginOutput(fileOnFocus: String?): Int {
        val fileParser = FileParser()
        val trackerOutput = this.processATOutput.getCleanedATOutput(ACTIVITY_TRACKER_DATASET_FILENAME, fileOnFocus!!)

        this.attentionValuesOutput = fileParser.parseCSVFile(ATTENTION_DATASET_FILENAME) as MutableList<Array<String>>
        this.pluginDataset = updateLineNumbers(trackerOutput)
        this.deleteEmptyInstructionOutput()
        this.mergeAttentionValues(this.attentionValuesOutput)
        fileParser.writeFile(FINAL_DATASET_FILENAME, pluginDataset, false)
        return OK_CODE
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
        return columnEvent > startInstruction && columnEvent < endInstruction
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
            list[index][FILENAME], (list[index][LINE].toInt() + lineOffset).toString(),
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
        val fileParser = FileParser()
        val attentionValues = fileParser.parseCSVFile(FINAL_DATASET_FILENAME) ?: return NULL_CODE

        val textHighlightAttention = TextHighlightAttention()
        for (i in 0 until document.lineCount) {
            val editorLineNumber = i + 1
            val range = TextRange(
                editor.document.getLineStartOffset(i),
                editor.document.getLineEndOffset(i)
            )
            if (editor.document.getText(range).trim { it <= ' ' } != "") {
                val meanAttentionLine = getMeanLineAttention(attentionValues, fileOnFocus, editorLineNumber)
                if (meanAttentionLine >= 0) textHighlightAttention.addLineHighlighter(
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
                line == row[LINE].toInt() &&
                row[ATTENTION].toInt() != NEUROSKY_ATTENTION_ERROR
            ) {
                sum += row[ATTENTION].toInt()
                occurrences++
            }
        }
        return if (occurrences == 0) NULL_CODE else sum / occurrences
    }

    @Throws(ParseException::class)
    private fun getDateFromString(dateTime: String): Date? {
        val formatter = SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS")
        return formatter.parse(dateTime)
    }

    private fun mergeAttentionValues(attentionList: MutableList<Array<String>>) {
        var attention = "-1"
        for (i in 0 until pluginDataset.size) {
            for (j in attentionList.indices) {
                if (getDateFromString(attentionList[j][NEUROSKY_TIMESTAMP])!!
                        .before(getDateFromString(pluginDataset[i][TIMESTAMP])) ||
                    getDateFromString(attentionList[j][NEUROSKY_TIMESTAMP]) == getDateFromString(
                        pluginDataset[i][TIMESTAMP]))
                    attention = attentionList[j][NEUROSKY_ATTENTION]
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
                attention
            )
            pluginDataset[i] = row
        }
    }

    companion object {
        private val FINAL_DATASET_FILENAME = "${com.intellij.openapi.application.PathManager.getPluginsPath()}/activity-tracker/ide-events-attention.csv"
        private val ACTIVITY_TRACKER_DATASET_FILENAME = "${com.intellij.openapi.application.PathManager.getPluginsPath()}/activity-tracker/ide-events.csv"
        private val ATTENTION_DATASET_FILENAME = "${com.intellij.openapi.application.PathManager.getPluginsPath()}/activity-tracker/attention.csv"

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

        /* Indexes for the attention list */
        const val NEUROSKY_TIMESTAMP = 0
        const val NEUROSKY_ATTENTION = 1

        const val NEUROSKY_ATTENTION_ERROR = -1
    }

    init {
        pluginDataset = ArrayList()
        processATOutput = ProcessActivityTrackerOutput()
        stringUtils = StringUtils()
    }
}