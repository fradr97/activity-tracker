package activitytracker.locAlgorithm

import activitytracker.locAlgorithm.gui.TextHighlightAttention
import activitytracker.locAlgorithm.utils.FileParser
import activitytracker.locAlgorithm.utils.StringUtils
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import java.util.*

class ProcessPluginOutput {
    private var pluginDataset: MutableList<Array<String>>
    private val processATOutput: ProcessActivityTrackerOutput
    private val stringUtils: StringUtils
    fun createPluginOutput(fileOnFocus: String?) {
        val fileParser = FileParser()
        val trackerOutput = processATOutput.getCleanedATOutput(
            fileOnFocus!!
        )
        if (trackerOutput != null) {
            pluginDataset = updateLineNumbers(trackerOutput)
            deleteEmptyInstructionOutput()
            mergeAttentionValues()
            fileParser.writeFile(DATASET_FILENAME, pluginDataset, false)
        } else {
            val report = "No tracking activity detected."
            val title = "Tracker File Empty!"
            Messages.showInfoMessage(report, title)
        }
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
                "EditorEnter", "EditorSplitLine" ->
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
                "EditorDelete" -> if (currentLOC < previousLOC) {
                    var k = 0
                    while (k < newOutput.size) {
                        if (newOutput[k][LINE].toInt() > currentNumberLine) {
                            newLine = this.createNewLine(newOutput, k, offsetLOC)
                            newOutput[k] = newLine
                        }
                        k++
                    }
                }
                else -> {
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
            list[index][CURRENT_LINE_COUNT] /*,
                list.get(index)[ATTENTION]*/
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
            list[index][CURRENT_LINE_COUNT] /*,
                list.get(index)[ATTENTION]*/
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

    /**
     * Lines without instruction are removed from plugin dataset.
     */
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

    fun getHighlightedAttentionLines(document: Document, editor: Editor, fileOnFocus: String) {
        val textHighlightAttention = TextHighlightAttention()
        for (i in 0 until document.lineCount) {
            val editorLineNumber = i + 1
            val range = TextRange(
                editor.document.getLineStartOffset(i),
                editor.document.getLineEndOffset(i)
            )
            if (editor.document.getText(range).trim { it <= ' ' } != "") {
                val meanAttentionLine = getMeanLineAttention(fileOnFocus, editorLineNumber)
                if (meanAttentionLine > 0) textHighlightAttention.addLineHighlighter(
                    editor,
                    editorLineNumber,
                    meanAttentionLine
                )
            }
        }
    }

    private fun getMeanLineAttention(fileOnFocus: String, line: Int): Int {
        var sum = 0
        var occurrences = 0
        for (row in pluginDataset) {
            if (fileOnFocus == row[FILENAME] &&
                line == row[LINE].toInt()
            ) {
                sum += row[ATTENTION].toInt()
                occurrences++
            }
        }
        return if (occurrences == 0) 0 else sum / occurrences
    }

    /* Metodo provvisorio */
    private fun mergeAttentionValues() {
        for (i in pluginDataset.indices) {
            val row = arrayOf(
                pluginDataset[i][TIMESTAMP],
                pluginDataset[i][EVENT_TYPE],
                pluginDataset[i][EVENT],
                pluginDataset[i][FILENAME],
                pluginDataset[i][LINE],
                pluginDataset[i][COLUMN],
                pluginDataset[i][LINE_INSTRUCTION],
                pluginDataset[i][CURRENT_LINE_COUNT], randomAttention.toString()
            )
            pluginDataset[i] = row
        }
    }

    /* Metodo provvisorio */
    private val randomAttention: Int
        private get() {
            val r = Random()
            return r.nextInt(10) + 1
        }

    companion object {
        private val DATASET_FILENAME = "${com.intellij.openapi.application.PathManager.getPluginsPath()}/activity-tracker/ide-events-attention.csv"

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
    }

    init {
        pluginDataset = ArrayList()
        processATOutput = ProcessActivityTrackerOutput()
        stringUtils = StringUtils()
    }
}