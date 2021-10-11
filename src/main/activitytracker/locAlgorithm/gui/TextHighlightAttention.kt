package activitytracker.locAlgorithm.gui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import java.awt.Font

class TextHighlightAttention {
    fun addLineHighlighter(editor: Editor, line: Int, attentionValue: Int) {
        val color: Color = if (attentionValue <= MIN_ATTENTION) Color(150, 10, 10)
        else if (attentionValue <= LOW_ATTENTION) Color(255, 80, 0)
        else if (attentionValue <= MEDIUM_ATTENTION) Color(255, 255, 0)
        else if (attentionValue <= HIGH_ATTENTION) Color(173, 255, 47)
        else Color(0, 150, 0)

        val textAttributes = TextAttributes(null, color, null, EffectType.LINE_UNDERSCORE, Font.PLAIN)
        editor.markupModel.addLineHighlighter(line - 1, HighlighterLayer.CARET_ROW, textAttributes)
    }

    fun removeAllHighlighter(editor: Editor) {
        editor.markupModel.removeAllHighlighters()
    }

    companion object {
        private const val MIN_ATTENTION = 20
        private const val LOW_ATTENTION = 40
        private const val MEDIUM_ATTENTION = 60
        private const val HIGH_ATTENTION = 80
    }
}