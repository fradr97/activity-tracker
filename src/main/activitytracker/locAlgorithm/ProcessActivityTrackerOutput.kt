package activitytracker.locAlgorithm

import activitytracker.locAlgorithm.utils.FileParser
import java.lang.ArrayIndexOutOfBoundsException
import java.util.ArrayList

class ProcessActivityTrackerOutput {
    private lateinit var activityTrackerOutput: MutableList<Array<String>>
    fun getCleanedATOutput(fileOnFocus: String): List<Array<String>>? {
        val fileParser = FileParser()
        activityTrackerOutput = fileParser.parseCSVFile(TRACKER_DATASET_FILENAME) as MutableList<Array<String>>
        if (activityTrackerOutput != null) {
            getFileOnFocusEvents(fileOnFocus)
            cleanATOutput()
            return activityTrackerOutput
        }
        return null
    }

    private fun cleanATOutput() {
        deleteATInactiveEvents()
        deleteATOutsideEditorEvents()
        deleteATEventsWithoutFilepath()
        deleteATEventsStartStopTracking()
        deleteATDuplicateEvents()
    }

    private fun getFileOnFocusEvents(fileOnFocus: String) {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in activityTrackerOutput!!) {
            if (atOutput[AT_FILENAME] != fileOnFocus) {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            activityTrackerOutput.remove(row)
        }
    }

    /**
     * "Inactive" events are suppressed.
     */
    private fun deleteATInactiveEvents() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in activityTrackerOutput!!) {
            if (atOutput[AT_EVENT] == "Inactive") {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            activityTrackerOutput.remove(row)
        }
    }

    /**
     * Events where the focus is not on the Editor are removed
     */
    private fun deleteATOutsideEditorEvents() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in activityTrackerOutput!!) {
            if (atOutput[AT_FOCUS] != "Editor") {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            activityTrackerOutput.remove(row)
        }
    }

    /**
     * Events without filepath are suppressed.
     */
    private fun deleteATEventsWithoutFilepath() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in activityTrackerOutput!!) {
            try {
                if (atOutput[AT_FILENAME] == "") {
                    toRemove.add(atOutput)
                }
            } catch (ignored: ArrayIndexOutOfBoundsException) {
            }
        }
        for (row in toRemove) {
            activityTrackerOutput.remove(row)
        }
    }

    /**
     * Events named 'Start/Stop Activity Tracking' are suppressed.
     */
    private fun deleteATEventsStartStopTracking() {
        val toRemove: MutableList<Array<String>> = ArrayList()
        for (atOutput in activityTrackerOutput!!) {
            if (atOutput[AT_EVENT] == "Start/Stop Activity Tracking" ||
                atOutput[AT_EVENT] == "Start Monitoring" ||
                atOutput[AT_EVENT] == "Stop Monitoring") {
                toRemove.add(atOutput)
            }
        }
        for (row in toRemove) {
            activityTrackerOutput.remove(row)
        }
    }

    /**
     * There are "duplicate" events in Activity Tracker: e.g. if I do "Enter",
     * it generates an event with "EditorEnter" and one with the selected button (10:10:0).
     * Collapse of events of this type taking as the (correct) CURRENT_LOC that of the second event.
     */
    private fun deleteATDuplicateEvents() {
        val newList: MutableList<Array<String>> = ArrayList()
        var newLine: Array<String>
        for (i in activityTrackerOutput!!.indices) {
            newLine =
                if (activityTrackerOutput!![i][AT_EVENT] == "EditorEnter" ||
                    activityTrackerOutput!![i][AT_EVENT] == "EditorSplitLine" ||
                    activityTrackerOutput!![i][AT_EVENT] == "EditorBackSpace" ||
                    activityTrackerOutput!![i][AT_EVENT] == "EditorDelete" ||
                    activityTrackerOutput!![i][AT_EVENT] == "EditorCut" ||
                    activityTrackerOutput!![i][AT_EVENT] == "EditorPaste" ||
                    activityTrackerOutput!![i][AT_EVENT] == "EditorCopy") {
                    arrayOf(
                        activityTrackerOutput!![i][AT_TIMESTAMP],
                        activityTrackerOutput!![i][AT_EVENT_TYPE],
                        activityTrackerOutput!![i][AT_EVENT],
                        activityTrackerOutput!![i][AT_FILENAME],
                        activityTrackerOutput!![i][AT_LINE],
                        activityTrackerOutput!![i][AT_COLUMN],
                        activityTrackerOutput!![i][AT_LINE_INSTRUCTION],
                        activityTrackerOutput!![i + 1][AT_CURRENT_LINE_COUNT]
                    )
                    //i ++; //If MouseTracker and KeyTracker events are also active
                } else {
                    getNewATLine(activityTrackerOutput, i)
                }
            newList.add(newLine)
        }
        activityTrackerOutput = newList
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