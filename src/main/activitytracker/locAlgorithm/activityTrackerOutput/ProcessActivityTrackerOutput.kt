package activitytracker.locAlgorithm.activityTrackerOutput

import activitytracker.locAlgorithm.utils.FileParser
import java.lang.ArrayIndexOutOfBoundsException
import java.util.ArrayList

class ProcessActivityTrackerOutput {
    private lateinit var activityTrackerOutput: MutableList<Array<String>>

    fun getCleanedATOutput(fileOnFocus: String): List<Array<String>>? {
        val fileParser = FileParser()
        this.activityTrackerOutput = fileParser.parseCSVFile(TRACKER_DATASET_FILENAME) as MutableList<Array<String>>
        getFileOnFocusEvents(fileOnFocus)
        cleanATOutput()
        return this.activityTrackerOutput
    }

    private fun cleanATOutput() {
        deleteATInactiveEvents()
        deleteATOutsideEditorEvents()
        deleteATEventsWithoutFilepath()
        deleteATEventsStartStopTracking()
        manageSpecialEvents()
    }

    private fun getFileOnFocusEvents(fileOnFocus: String) {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in this.activityTrackerOutput!!) {
            if (atOutput[AT_FILENAME] != fileOnFocus) {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            this.activityTrackerOutput.remove(row)
        }
    }

    /**
     * "Inactive" events are suppressed.
     */
    private fun deleteATInactiveEvents() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in this.activityTrackerOutput!!) {
            if (atOutput[AT_EVENT] == "Inactive") {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            this.activityTrackerOutput.remove(row)
        }
    }

    /**
     * Events where the focus is not on the Editor are removed
     */
    private fun deleteATOutsideEditorEvents() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in this.activityTrackerOutput!!) {
            if (atOutput[AT_FOCUS] != "Editor") {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            this.activityTrackerOutput.remove(row)
        }
    }

    /**
     * Events without filepath are suppressed.
     */
    private fun deleteATEventsWithoutFilepath() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in this.activityTrackerOutput!!) {
            try {
                if (atOutput[AT_FILENAME] == "") {
                    toRemove.add(atOutput)
                }
            } catch (ignored: ArrayIndexOutOfBoundsException) {
            }
        }
        for (row in toRemove) {
            this.activityTrackerOutput.remove(row)
        }
    }

    /**
     * Events named 'Start/Stop Activity Tracking' are suppressed.
     */
    private fun deleteATEventsStartStopTracking() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in this.activityTrackerOutput!!) {
            if (atOutput[AT_EVENT] == "Start/Stop Activity Tracking" ||
                atOutput[AT_EVENT] == "Start/Stop Monitoring") {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            this.activityTrackerOutput.remove(row)
        }
    }

    /**
     * If I do "Enter" or "BackSpace", this method takes the next event as CURRENT_LOC (correct).
     */
    private fun manageSpecialEvents() {
        val newList: MutableList<Array<String>> = ArrayList()
        var newLine: Array<String>
        for (i in this.activityTrackerOutput!!.indices) {
            newLine =
                if (this.activityTrackerOutput!![i][AT_EVENT] == "EditorEnter" ||
                    this.activityTrackerOutput!![i][AT_EVENT] == "EditorBackSpace") {
                    arrayOf(
                        this.activityTrackerOutput!![i][AT_TIMESTAMP],
                        this.activityTrackerOutput!![i][AT_EVENT_TYPE],
                        this.activityTrackerOutput!![i][AT_EVENT],
                        this.activityTrackerOutput!![i][AT_FILENAME],
                        this.activityTrackerOutput!![i][AT_LINE],
                        this.activityTrackerOutput!![i][AT_COLUMN],
                        this.activityTrackerOutput!![i][AT_LINE_INSTRUCTION],
                        this.activityTrackerOutput!![i + 1][AT_CURRENT_LINE_COUNT]
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
            list!![index][AT_TIMESTAMP],
            list[index][AT_EVENT_TYPE],
            list[index][AT_EVENT],
            list[index][AT_FILENAME],
            list[index][AT_LINE],
            list[index][AT_COLUMN],
            list[index][AT_LINE_INSTRUCTION],
            list[index][AT_CURRENT_LINE_COUNT]
        )
    }

    companion object {
        private val TRACKER_DATASET_FILENAME = "${com.intellij.openapi.application.PathManager.getPluginsPath()}/activity-tracker/ide-events.csv"

        /* Activity Tracker dataset indexes */
        private const val AT_TIMESTAMP = 0
        private const val AT_EVENT_TYPE = 2
        private const val AT_EVENT = 3
        private const val AT_FOCUS = 5
        private const val AT_FILENAME = 6
        private const val AT_LINE = 8
        private const val AT_COLUMN = 9
        private const val AT_LINE_INSTRUCTION = 11
        private const val AT_CURRENT_LINE_COUNT = 12
    }
}