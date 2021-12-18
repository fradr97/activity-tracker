package plugin.locAlgorithm.comprehensionMode

import org.json.JSONArray
import org.json.JSONObject
import plugin.config.Config
import plugin.utils.FileUtils
import java.util.*
import kotlin.collections.ArrayList


class ProcessPopupQuestions {
    private lateinit var tasks: JSONArray
    private lateinit var taskQuestions: JSONArray
    private lateinit var questions: JSONArray
    private lateinit var question: String
    private lateinit var answers: Array<String?>
    private val alreadyAsked: ArrayList<Int> = ArrayList()

    fun getQuestion(): String {
        return question
    }

    fun getAnswers(): Array<String?> {
        return answers
    }

    fun checkQuestionExists(filePath: String, javaClass: String, line: Int): Boolean {
        val taskIndex = getTaskIndex(filePath, javaClass)
        if (taskIndex != Config.NULL_CODE) {
            val taskQuestionIndex = getTaskQuestionIndex(taskIndex, line)
            if (taskQuestionIndex != Config.NULL_CODE) {
                val questionAnswersIndex = getQuestionAnswersIndex(taskQuestionIndex)
                if (questionAnswersIndex != Config.NULL_CODE) {
                    updateAlreadyAskedQuestions(questionAnswersIndex)
                    question = questions.getJSONObject(questionAnswersIndex).getString(Config.QUESTION)
                    val ans = questions.getJSONObject(questionAnswersIndex).getJSONArray(Config.ANSWERS)
                    answers = arrayOfNulls(ans.length())

                    for (k in 0 until ans.length()) {
                        answers[k] = ans[k].toString()
                    }
                    return true
                }
                return false
            }
            return false
        }
        return false
    }

    private fun getTaskIndex(jsonFilePath: String, focusFile: String): Int {
        val fileUtils = FileUtils()
        val content: String = fileUtils.readFileContent(jsonFilePath)
        try {
            val jsonContent = JSONObject(content)
            tasks = jsonContent.getJSONArray(Config.TASKS)
            for (i in 0 until tasks.length()) {
                val task = tasks.getJSONObject(i).getString(Config.TASK)
                if (focusFile.contains(task)) {    //if (task == javaClass) {
                    return i
                }
            }
        } catch (e: Exception) {
            return Config.NULL_CODE
        }
        return Config.NULL_CODE
    }

    private fun getTaskQuestionIndex(taskIndex: Int, line: Int): Int {
        try {
            taskQuestions = tasks.getJSONObject(taskIndex).getJSONArray(Config.TASK_QUESTIONS)
            for (j in 0 until taskQuestions.length()) {
                val from = taskQuestions.getJSONObject(j).getInt(Config.FROM_LINE)
                val to = taskQuestions.getJSONObject(j).getInt(Config.TO_LINE)
                val isInRange = numIsInRange(line, from, to)
                if (isInRange) {
                    return j
                }
            }
        } catch (e: Exception) {
            return Config.NULL_CODE
        }
        return Config.NULL_CODE
    }

    fun getAllCodeRange(filePath: String, javaClass: String): MutableList<Array<Int>> {
        val ranges: MutableList<Array<Int>> = java.util.ArrayList()
        val taskIndex = getTaskIndex(filePath, javaClass)
        taskQuestions = tasks.getJSONObject(taskIndex).getJSONArray(Config.TASK_QUESTIONS)

        if (taskIndex != Config.NULL_CODE) {
            for (j in 0 until taskQuestions.length()) {
                val from = taskQuestions.getJSONObject(j).getInt(Config.FROM_LINE)
                val to = taskQuestions.getJSONObject(j).getInt(Config.TO_LINE)

                val range = arrayOf(from, to)
                ranges.add(range)
            }
        }
        return ranges
    }

    private fun getQuestionAnswersIndex(taskQuestionIndex: Int): Int {
        return try {
            questions = taskQuestions.getJSONObject(taskQuestionIndex).getJSONArray(Config.QUESTIONS)
            val r = Random()
            var rIndex = r.nextInt(questions.length())

            /* if all the questions have already been asked,
                 clear the index array and propose them again */
            if (questions.length() == alreadyAsked.size) {
                alreadyAsked.clear()
            } else {
                while (isAlreadyAsked(rIndex)) {
                    rIndex = r.nextInt(questions.length())
                }
            }
            rIndex
        } catch (ex: Exception) {
            Config.NULL_CODE
        }
    }

    private fun isAlreadyAsked(index: Int): Boolean {
        for (i in alreadyAsked) {
            if (i == index) return true
        }
        return false
    }

    private fun updateAlreadyAskedQuestions(index: Int) {
        alreadyAsked.add(index)
    }

    fun numIsInRange(num: Int, from: Int, to: Int): Boolean {
        return num in from..to
    }
}
