package plugin.activityTracker.activityTrackerOutput

import plugin.config.Config
import plugin.utils.FileUtils
import java.lang.ArrayIndexOutOfBoundsException
import java.util.ArrayList

class ProcessActivityTrackerOutput {
    private lateinit var activityTrackerOutput: MutableList<Array<String>>

    fun getCleanedATOutput(activityTrackerDateset: String, fileOnFocus: String): List<Array<String>> {
        val fileUtils = FileUtils()
        this.activityTrackerOutput = fileUtils.parseCSVFile(activityTrackerDateset) as MutableList<Array<String>>
        this.cleanATOutput(fileOnFocus)
        return this.activityTrackerOutput
    }

    private fun cleanATOutput(fileOnFocus: String) {
        getFileOnFocusEvents(fileOnFocus)
        deleteATInactiveEvents()
        deleteATOutsideEditorEvents()
        deleteATEventsWithoutFilepath()
        deleteATEventsStartStopTracking()
        manageSpecialEvents()
    }

    private fun getFileOnFocusEvents(fileOnFocus: String) {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in this.activityTrackerOutput) {
            if (atOutput[Config.AT_FILENAME] != fileOnFocus) {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            this.activityTrackerOutput.remove(row)
        }
    }

    /* "Inactive" events are removed */
    private fun deleteATInactiveEvents() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in this.activityTrackerOutput) {
            if (atOutput[Config.AT_EVENT] == "Inactive") {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            this.activityTrackerOutput.remove(row)
        }
    }

    /* Events where the focus is not on the Editor are removed */
    private fun deleteATOutsideEditorEvents() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in this.activityTrackerOutput) {
            if (atOutput[Config.AT_FOCUS] != "Editor") {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            this.activityTrackerOutput.remove(row)
        }
    }

    /* Events without filepath are removed */
    private fun deleteATEventsWithoutFilepath() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in this.activityTrackerOutput) {
            try {
                if (atOutput[Config.AT_FILENAME] == "") {
                    toRemove.add(atOutput)
                }
            } catch (ignored: ArrayIndexOutOfBoundsException) {
            }
        }
        for (row in toRemove) {
            this.activityTrackerOutput.remove(row)
        }
    }

    /* Events named 'Start/Stop Activity Tracking' and 'Start/Stop Monitoring' are removed */
    private fun deleteATEventsStartStopTracking() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in this.activityTrackerOutput) {
            if (atOutput[Config.AT_EVENT] == "Start/Stop Activity Tracking" ||
                atOutput[Config.AT_EVENT] == "Start/Stop Monitoring") {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            this.activityTrackerOutput.remove(row)
        }
    }

    /* If I do "Enter" or "BackSpace", this method takes the next event as CURRENT_LOC (correct) */
    private fun manageSpecialEvents() {
        val newList: MutableList<Array<String>> = ArrayList()
        var newLine: Array<String>
        for (i in this.activityTrackerOutput.indices) {
            newLine =
                if (this.activityTrackerOutput[i][Config.AT_EVENT] == "EditorEnter" ||
                    this.activityTrackerOutput[i][Config.AT_EVENT] == "EditorBackSpace") {
                    arrayOf(
                        this.activityTrackerOutput[i][Config.AT_TIMESTAMP],
                        this.activityTrackerOutput[i][Config.AT_EVENT_TYPE],
                        this.activityTrackerOutput[i][Config.AT_EVENT],
                        this.activityTrackerOutput[i][Config.AT_FILENAME],
                        this.activityTrackerOutput[i][Config.AT_LINE],
                        this.activityTrackerOutput[i][Config.AT_COLUMN],
                        this.activityTrackerOutput[i][Config.AT_LINE_INSTRUCTION],
                        this.activityTrackerOutput[i + 1][Config.AT_CURRENT_LINE_COUNT]
                    )
                } else {
                    getNewATLine(this.activityTrackerOutput, i)
                }
            newList.add(newLine)
        }
        this.activityTrackerOutput = newList
    }

    private fun getNewATLine(list: List<Array<String>>?, index: Int): Array<String> {
        return arrayOf(
            list!![index][Config.AT_TIMESTAMP],
            list[index][Config.AT_EVENT_TYPE],
            list[index][Config.AT_EVENT],
            list[index][Config.AT_FILENAME],
            list[index][Config.AT_LINE],
            list[index][Config.AT_COLUMN],
            list[index][Config.AT_LINE_INSTRUCTION],
            list[index][Config.AT_CURRENT_LINE_COUNT]
        )
    }
}