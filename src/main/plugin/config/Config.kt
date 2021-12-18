package plugin.config

import com.intellij.openapi.application.PathManager

class Config {
    companion object {
        /** CODES */
        const val OK_CODE = 0
        const val NULL_CODE = -1

        /** OUTPUT DATASET PATHS */
        val ACTIVITY_TRACKER_DATASET_FILENAME = "${PathManager.getPluginsPath()}/activity-tracker/activity-tracker-events.csv"

        private val BASE_DATASETS_PATH = "${PathManager.getPluginsPath()}/tracker"
        val CODING_MODE_PATH = "$BASE_DATASETS_PATH/coding-mode"
        val COMPREHENSION_MODE_PATH = "$BASE_DATASETS_PATH/comprehension-mode"

        val FINAL_CODING_MODE_DATASET_FILENAME = "$CODING_MODE_PATH/final-coding-mode-events.csv"
        val ATTENTION_CODING_MODE_DATASET_FILENAME = "$CODING_MODE_PATH/coding-mode-attention.csv"
        val OPEN_FACE_CODING_MODE_DATASET_FILENAME = "$CODING_MODE_PATH/coding-mode-open-face.csv"
        val FINAL_COMPREHENSION_MODE_DATASET_FILENAME = "$COMPREHENSION_MODE_PATH/final-comprehension-mode-events.csv"
        val ATTENTION_COMPREHENSION_MODE_DATASET_FILENAME = "$COMPREHENSION_MODE_PATH/comprehension-mode-attention.csv"
        val OPEN_FACE_COMPREHENSION_MODE_DATASET_FILENAME = "$COMPREHENSION_MODE_PATH/comprehension-mode-open-face.csv"

        /** ACTIVITY TRACKER DATASET INDEXES */
        const val AT_TIMESTAMP = 0
        const val AT_EVENT_TYPE = 2
        const val AT_EVENT = 3
        const val AT_FOCUS = 5
        const val AT_FILENAME = 6
        const val AT_LINE = 8
        const val AT_COLUMN = 9
        const val AT_LINE_INSTRUCTION = 11
        const val AT_CURRENT_LINE_COUNT = 12

        /** NEW INDEXES FOR THE FINAL DATASET */
        const val TIMESTAMP = 0
        const val EVENT_TYPE = 1
        const val EVENT = 2
        const val FILENAME = 3
        const val LINE = 4
        const val COLUMN = 5
        const val LINE_INSTRUCTION = 6
        const val CURRENT_LINE_COUNT = 7
        const val ATTENTION = 8

        /** INDEXES FOR THE OPEN-FACE OUTPUT */
        const val OF_TIMESTAMP_r = 2
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

        /** INDEXES FOR THE (OLD) OPEN-FACE LIST */
        const val OF_TIMESTAMP = 0
        const val OF_AU01 = 1
        const val OF_AU02 = 2
        const val OF_AU04 = 3
        const val OF_AU05 = 4
        const val OF_AU06 = 5
        const val OF_AU07 = 6
        const val OF_AU09 = 7
        const val OF_AU10 = 8
        const val OF_AU12 = 9
        const val OF_AU14 = 10
        const val OF_AU15 = 11
        const val OF_AU17 = 12
        const val OF_AU20 = 13
        const val OF_AU23 = 14
        const val OF_AU25 = 15
        const val OF_AU26 = 16
        const val OF_AU45 = 17

        /** HEADSET VALUES (NeuroSky) */
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 13854

        const val NO_ATTENTION_VALUE_OBTAINED = -1
        const val NO_ATTENTION = 0
        const val MIN_ATTENTION = 20
        const val LOW_ATTENTION = 40
        const val MEDIUM_ATTENTION = 60
        const val HIGH_ATTENTION = 80
        const val MAX_ATTENTION = 100

        /** INDEXES FOR THE (OLD) ATTENTION LIST */
        const val HEADSET_ERROR_ATTEMPTS = 3

        const val HEADSET_TIMESTAMP = 0
        const val HEADSET_ATTENTION = 1
        const val POPUP_QUESTION = 1
        const val POPUP_USER_ANSWER = 2

        /** PARAMETERS RELATING TO THE LOSS OF ATTENTION */
        const val BUFFER_THRESHOLD = 5
        const val ATTENTION_THRESHOLD = 50

        const val CURSOR_REMAIN_IN_RANGE_SECONDS = 2

        /** PLUGIN OPERATIONS */
        const val CREATE_CODING_MODE_DATASET_OPERATION = 0
        const val CREATE_COMPREHENSION_MODE_DATASET_OPERATION = 1
        const val HIGHLIGHT_LINES_OPERATION = 2
        const val REMOVE_HIGHLIGHTED_LINES_OPERATION = 3

        const val SLEEP_BEFORE_PROCESSING = 5000

        /** PLUGIN-UI TITLES AND MESSAGES */
        const val NO_TRACKING_ACTIVITY_TITLE = "Tracker File Empty!"
        const val NO_TRACKING_ACTIVITY_MESSAGE = "No tracking activity detected for this file."
        const val WAITING_TITLE = "Please wait!"
        const val WAITING_HEADSET_MESSAGE = "Waiting headset... "
        const val RUNNING_TITLE = "Running!"
        const val TRACKER_RUNNING_MESSAGE = "The tracker is running... "
        const val STOPPING_TITLE = "Stopping!"
        const val TRACKER_STOPPING_MESSAGE = "The tracker is stopping..."
        const val HEADSET_NOT_WORKING_TITLE = "Headset Not Working!"
        const val HEADSET_NOT_WORKING_MESSAGE = "Put on the headset and check that it is on!"
        const val DATA_PROCESSING_MESSAGE = "Data processing in progress... "
        const val ERROR_SET_SETTINGS_TITLE = "Warning!"
        const val ERROR_SET_SETTINGS_MESSAGE = "Set the OpenFace output folder path and the questions file path in the settings."

        const val HIGHLIGHT_OPTIONS_TITLE = "Highlight options"
        const val ARITHMETIC_MEAN_OPTION = "Arithmetic mean of the attention values"
        const val EXPONENTIAL_MEAN_OPTION = "Exponential mean of the attention values"
        const val LAST_ATTENTION_VALUE_OPTION = "Last attention value"

        const val SETTINGS_TITLE = "Settings"
        const val SETTINGS_OF_FOLDER_MESSAGE = "Set the OpenFace Output Folder Path (e.g. C:\\Users\\OtherFolders\\FolderWithCsvFile):"
        const val SETTINGS_QUESTIONS_FILE_MESSAGE = "Set the questions File Path (e.g. C:\\Users\\OtherFolders\\QuestionsFile.json):"
        const val SETTINGS_CHECKBOX_MESSAGE = "Check for Comprehension Mode or uncheck for Coding Mode:"

        const val NO_POPUP_RESPONSE = "No response!"

        /** THE TWO MODES OF THE PLUGIN */
        const val CODING_MODE: String = "Coding Mode"
        const val COMPREHENSION_MODE: String = "Comprehension Mode"

        /** CODING MODE HIGHLIGHT OPTIONS */
        const val CODING_MODE_ARITHMETIC_AVG_OPTION = 0
        const val CODING_MODE_EXPONENTIAL_AVG_OPTION = 1
        const val CODING_MODE_LAST_ATTENTION_OPTION = 2

        /** DateTime */
        const val DEFAULT_DATE = "1900-01-01 00:00:00.000"

        /** Popup Json keys */
        const val TASKS = "tasks"
        const val TASK = "task"
        const val TASK_QUESTIONS = "taskQuestions"
        const val FROM_LINE = "fromLine"
        const val TO_LINE = "toLine"
        const val QUESTIONS = "questions"
        const val ANSWERS = "answers"
        const val QUESTION = "question"

        const val POPUP_QUESTION_INDEX = 0
        const val POPUP_USER_ANSWER_INDEX = 1
    }
}