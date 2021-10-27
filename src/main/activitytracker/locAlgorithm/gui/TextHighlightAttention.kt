package activitytracker.locAlgorithm.gui

import activitytracker.locAlgorithm.neuroSkyAttention.NeuroSkyAttention
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import java.awt.Font

class TextHighlightAttention {
    fun addLineHighlighter(editor: Editor, line: Int, attentionValue: Int) {
        val color: Color = if (attentionValue <= NeuroSkyAttention.MIN_ATTENTION) Color(255, 0, 0)
        else if (attentionValue <= NeuroSkyAttention.LOW_ATTENTION) Color(255, 90, 0)
        else if (attentionValue <= NeuroSkyAttention.MEDIUM_ATTENTION) Color(255, 255, 0)
        else if (attentionValue <= NeuroSkyAttention.HIGH_ATTENTION) Color(173, 255, 47)
        else Color(0, 255, 0)

        val textAttributes = TextAttributes(null, color, null, EffectType.LINE_UNDERSCORE, Font.PLAIN)
        editor.markupModel.addLineHighlighter(line - 1, HighlighterLayer.CARET_ROW, textAttributes)
    }

    fun removeAllHighlighter(editor: Editor) {
        editor.markupModel.removeAllHighlighters()
    }
}