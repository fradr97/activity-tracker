package plugin.locAlgorithm.codingMode

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import plugin.config.Config
import plugin.utils.FileUtils
import kotlin.math.roundToInt

class CodingModeHighlightOptions {

    fun getHighlightedAttentionLines(document: Document, editor: Editor, fileOnFocus: String, highlightOption: Int): Int {
        val fileUtils = FileUtils()
        val attentionValues = fileUtils.parseCSVFile(Config.FINAL_CODING_MODE_DATASET_FILENAME) ?: return Config.NULL_CODE

        val editorHighlighter = EditorHighlighter()
        for (i in 0 until document.lineCount) {
            val editorLineNumber = i + 1
            val range = TextRange(
                editor.document.getLineStartOffset(i),
                editor.document.getLineEndOffset(i)
            )
            var attentionLine = 0

            when (highlightOption) {
                Config.CODING_MODE_ARITHMETIC_AVG_OPTION -> attentionLine =
                    getArithmeticAVGLineAttention(attentionValues, fileOnFocus, editorLineNumber)
                /*Config.CODING_MODE_EXPONENTIAL_AVG_OPTION -> attentionLine =
                    getExponentialAVGLineAttention(attentionValues, fileOnFocus, editorLineNumber)*/
                Config.CODING_MODE_LAST_ATTENTION_OPTION -> attentionLine =
                    getLastAttentionValue(attentionValues, editorLineNumber)
            }

            if (editor.document.getText(range).trim { it <= ' ' } != "") {
                if (attentionLine >= 0) editorHighlighter.addLineHighlighter(
                    editor,
                    editorLineNumber,
                    attentionLine
                )
            }
        }
        return Config.OK_CODE
    }

    private fun getArithmeticAVGLineAttention(list: List<Array<String>>, fileOnFocus: String, line: Int): Int {
        var sum = 0
        var occurrences = 0
        for (row in list) {
            if (fileOnFocus == row[Config.FILENAME] &&
                line == row[Config.LINE].toInt() &&
                row[Config.ATTENTION] != Config.NO_ATTENTION_VALUE_OBTAINED.toString()  /* excludes any errors (e.g. headset off) */
            ) {
                sum += row[Config.ATTENTION].toInt()
                occurrences++
            }
        }
        return if (occurrences == 0) Config.NULL_CODE else (sum / occurrences).toDouble().roundToInt()
    }

    //TODO: exponential AVG
    //private fun getExponentialAVGLineAttention(list: List<Array<String>>, fileOnFocus: String, line: Int): Int { }

    private fun getLastAttentionValue(list: List<Array<String>>, line: Int): Int {
        for (i in list.size - 1 downTo 1) {
            if (list[i][Config.LINE].toInt() == line) return list[i][Config.ATTENTION].toInt()
        }
        return -1
    }
}