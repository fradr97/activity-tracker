package plugin.locAlgorithm.codingMode

import plugin.activityTracker.activityTrackerOutput.ProcessActivityTrackerOutput
import plugin.config.Config
import plugin.openFaceAUs.ProcessOpenFaceOutput
import plugin.utils.DateTimeUtils
import plugin.utils.FileUtils
import plugin.utils.StringUtils


class ProcessCodingModeOutput {
    private var codingModeDataset: MutableList<Array<String>>
    private val processATOutput: ProcessActivityTrackerOutput
    private lateinit var attentionValuesOutput: MutableList<Array<String>>
    private lateinit var openFaceAUsOutput: MutableList<Array<String>>
    private val stringUtils: StringUtils

    init {
        codingModeDataset = ArrayList()
        processATOutput = ProcessActivityTrackerOutput()
        stringUtils = StringUtils()
    }

    fun createOutputCodingMode(fileOnFocus: String?, openFaceOutputFolderPath: String): Int {
        val fileUtils = FileUtils()
        val processOpenFaceOutput = ProcessOpenFaceOutput(openFaceOutputFolderPath, Config.OPEN_FACE_CODING_MODE_DATASET_FILENAME)

        val trackerOutput = this.processATOutput.getCleanedATOutput(Config.ACTIVITY_TRACKER_DATASET_FILENAME, fileOnFocus!!)

        this.attentionValuesOutput = fileUtils.parseCSVFile(Config.ATTENTION_CODING_MODE_DATASET_FILENAME) as MutableList<Array<String>>
        this.openFaceAUsOutput = fileUtils.parseCSVFile(processOpenFaceOutput.getOutputFile().toString()) as MutableList<Array<String>>
        this.codingModeDataset = updateLineNumbers(trackerOutput)
        this.deleteEmptyInstructionOutput()
        this.mergeAllValues(this.attentionValuesOutput, this.openFaceAUsOutput)

        fileUtils.addCSVHeader(Config.FINAL_CODING_MODE_DATASET_FILENAME, this.codingModeDataset,
            createCodingModeHeaders() as MutableList<Array<String>>)
        return Config.OK_CODE
    }

    private fun createCodingModeHeaders(): List<Array<String>> {
        val list: MutableList<Array<String>> = java.util.ArrayList()
        val headers = arrayOf(
            "Timestamp", "EventType", "EventData", "ModifiedFile", "Line", "Column", "Instruction",
            "fileLinesNumber", "Attention", "lineOccurrences", "AU01", "AU02", "AU04", "AU05", "AU06", "AU07", "AU09",
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
            val currentLOC = list[i][Config.CURRENT_LINE_COUNT].toInt()
            var previousLOC = currentLOC

            if (i > 0) {
                previousLOC = list[i - 1][Config.CURRENT_LINE_COUNT].toInt()
            }

            val offsetLOC = currentLOC - previousLOC
            val currentNumberLine = list[i][Config.LINE].toInt()

            when (list[i][Config.EVENT]) {
                "EditorEnter" ->
                    if (isEventEndLine(list, i) || isEventEndLineMinusOne(list, i)) {
                        var k = 0
                        while (k < newOutput.size) {
                            if (newOutput[k][Config.LINE].toInt() > currentNumberLine) {
                                newLine = this.createNewLine(newOutput, k, offsetLOC)
                                newOutput[k] = newLine
                            }
                            k++
                        }
                    } else if (isEventInsideInstruction(list, i)) {
                        var k = 0
                        while (k < newOutput.size) {
                            if (newOutput[k][Config.LINE].toInt() > currentNumberLine) {
                                newLine = this.createNewLine(newOutput, k, offsetLOC)
                                newOutput[k] = newLine
                            } else if (newOutput[k][Config.LINE].toInt() == currentNumberLine) {
                                newLine = arrayOf(
                                    newOutput[k][Config.TIMESTAMP],
                                    newOutput[k][Config.EVENT_TYPE],
                                    newOutput[k][Config.EVENT],
                                    newOutput[k][Config.FILENAME],
                                    newOutput[k][Config.LINE],
                                    newOutput[k][Config.COLUMN],
                                    newOutput[k][Config.LINE_INSTRUCTION].substring(0, newOutput[k][Config.COLUMN].toInt()),
                                    newOutput[k][Config.CURRENT_LINE_COUNT]
                                )
                                newOutput[k] = newLine
                            }
                            k++
                        }
                    } else {
                        var k = 0
                        while (k < newOutput.size) {
                            if (newOutput[k][Config.LINE].toInt() >= currentNumberLine) {
                                newLine = this.createNewLine(newOutput, k, offsetLOC)
                                newOutput[k] = newLine
                            }
                            k++
                        }
                    }
                "EditorBackSpace" -> {
                    deleteEmptyInstructionLines(list[i][Config.LINE_INSTRUCTION], list[i][Config.LINE].toInt(), newOutput)
                    if (currentLOC < previousLOC) {
                        var k = 0
                        while (k < newOutput.size) {
                            if (newOutput[k][Config.LINE].toInt() >= currentNumberLine) {
                                newLine = this.createNewLine(newOutput, k, offsetLOC)
                                newOutput[k] = newLine
                            }
                            k++
                        }
                    }
                }
                else -> {
                    deleteEmptyInstructionLines(list[i][Config.LINE_INSTRUCTION], list[i][Config.LINE].toInt(), newOutput)
                }
            }
        }
        return newOutput
    }

    private fun isEventEndLine(list: List<Array<String>>, index: Int): Boolean {
        val columnEvent = list[index][Config.COLUMN].toInt()
        val lengthLineEvent: Int = stringUtils.stripTrailing(list[index][Config.LINE_INSTRUCTION]).length
        return columnEvent >= lengthLineEvent
    }

    private fun isEventEndLineMinusOne(list: List<Array<String>>, index: Int): Boolean {
        if (list[index][Config.LINE_INSTRUCTION].trim { it <= ' ' }.length == 1) return false
        val columnEvent = list[index][Config.COLUMN].toInt()
        val lengthLineEvent: Int = stringUtils.stripTrailing(list[index][Config.LINE_INSTRUCTION]).length
        return columnEvent == lengthLineEvent - 1
    }

    private fun isEventInsideInstruction(list: List<Array<String>>, index: Int): Boolean {
        if (list[index][Config.LINE_INSTRUCTION].trim { it <= ' ' }.length <= 1) return false
        val columnEvent = list[index][Config.COLUMN].toInt()
        val lengthLineEvent = list[index][Config.LINE_INSTRUCTION].length
        val startInstruction: Int = lengthLineEvent - stringUtils.stripLeading(list[index][Config.LINE_INSTRUCTION]).length
        val endInstruction: Int = stringUtils.stripTrailing(list[index][Config.LINE_INSTRUCTION]).length
        return columnEvent in (startInstruction + 1) until endInstruction
    }

    private fun createNewLine(list: List<Array<String>>, index: Int): Array<String> {
        return arrayOf(
            list[index][Config.TIMESTAMP],
            list[index][Config.EVENT_TYPE],
            list[index][Config.EVENT],
            list[index][Config.FILENAME],
            list[index][Config.LINE],
            list[index][Config.COLUMN],
            list[index][Config.LINE_INSTRUCTION],
            list[index][Config.CURRENT_LINE_COUNT]
        )
    }

    private fun createNewLine(list: List<Array<String>>, index: Int, lineOffset: Int): Array<String> {
        return arrayOf(
            list[index][Config.TIMESTAMP],
            list[index][Config.EVENT_TYPE],
            list[index][Config.EVENT],
            list[index][Config.FILENAME],
            (list[index][Config.LINE].toInt() + lineOffset).toString(),
            list[index][Config.COLUMN],
            list[index][Config.LINE_INSTRUCTION],
            list[index][Config.CURRENT_LINE_COUNT]
        )
    }

    /* Remove lines that have become without instructions */
    private fun deleteEmptyInstructionLines(instruction: String, line: Int, newOutput: MutableList<Array<String>>) {
        if (instruction.trim { it <= ' ' } == "") {
            val toRemove: MutableList<Array<String>> = ArrayList()
            for (row in newOutput) {
                if (line == row[Config.LINE].toInt()) {
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
        for (atOutput in codingModeDataset) {
            if (atOutput[Config.LINE_INSTRUCTION].trim { it <= ' ' } == "") {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            codingModeDataset.remove(row)
        }
    }

    private fun mergeAllValues(attentionList: MutableList<Array<String>>, openFaceAUsList: MutableList<Array<String>>) {
        val dateTimeUtils = DateTimeUtils()

        var attention = Config.NO_ATTENTION_VALUE_OBTAINED.toString()
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

        for (i in 0 until codingModeDataset.size) {
            val pluginDate = dateTimeUtils.getDateFromString(codingModeDataset[i][Config.TIMESTAMP])

            for (j in attentionList.indices) {
                val attentionDate = dateTimeUtils.getDateFromString(attentionList[j][Config.HEADSET_TIMESTAMP])
                val sameDates = dateTimeUtils.checkSameDates(pluginDate, attentionDate)

                if (sameDates && attentionDate!!.before(pluginDate)) {
                    attention = attentionList[j][Config.HEADSET_ATTENTION]
                }
            }
            for (k in 1 until openFaceAUsList.size) {
                val ofDate = dateTimeUtils.getDateFromString(
                    stringUtils.replaceLastOccurrence(openFaceAUsList[k][Config.OF_TIMESTAMP], ":", "."))
                val sameDates = dateTimeUtils.checkSameDates(pluginDate, ofDate)

                if (sameDates && ofDate!!.before(pluginDate)) {
                    au01 = openFaceAUsList[k][Config.OF_AU01]
                    au02 = openFaceAUsList[k][Config.OF_AU02]
                    au04 = openFaceAUsList[k][Config.OF_AU04]
                    au05 = openFaceAUsList[k][Config.OF_AU05]
                    au06 = openFaceAUsList[k][Config.OF_AU06]
                    au07 = openFaceAUsList[k][Config.OF_AU07]
                    au09 = openFaceAUsList[k][Config.OF_AU09]
                    au10 = openFaceAUsList[k][Config.OF_AU10]
                    au12 = openFaceAUsList[k][Config.OF_AU12]
                    au14 = openFaceAUsList[k][Config.OF_AU14]
                    au15 = openFaceAUsList[k][Config.OF_AU15]
                    au17 = openFaceAUsList[k][Config.OF_AU17]
                    au20 = openFaceAUsList[k][Config.OF_AU20]
                    au23 = openFaceAUsList[k][Config.OF_AU23]
                    au25 = openFaceAUsList[k][Config.OF_AU25]
                    au26 = openFaceAUsList[k][Config.OF_AU26]
                    au45 = openFaceAUsList[k][Config.OF_AU45]
                }
            }

            val row = arrayOf(
                codingModeDataset[i][Config.TIMESTAMP],
                codingModeDataset[i][Config.EVENT_TYPE],
                codingModeDataset[i][Config.EVENT],
                codingModeDataset[i][Config.FILENAME],
                codingModeDataset[i][Config.LINE],
                codingModeDataset[i][Config.COLUMN],
                codingModeDataset[i][Config.LINE_INSTRUCTION],
                codingModeDataset[i][Config.CURRENT_LINE_COUNT],
                attention,
                countLineOccurrence(i, codingModeDataset[i][Config.LINE].toInt()).toString(),
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
            codingModeDataset[i] = row

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

    private fun countLineOccurrence(index: Int, line: Int): Int {
        var count = 0
        for (i in 0 until index) {
            if (codingModeDataset[i][Config.LINE].toInt() == line) {
                count++
            }
        }
        return count + 1
    }
}