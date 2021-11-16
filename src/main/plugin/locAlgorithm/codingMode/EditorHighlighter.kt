package plugin.locAlgorithm.codingMode

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.TextAttributes
import plugin.config.Config
import java.awt.Color
import java.awt.Font

class EditorHighlighter {
    fun addLineHighlighter(editor: Editor, line: Int, attentionValue: Int) {
        val color: Color = if (attentionValue <= Config.MIN_ATTENTION) Color(255, 0, 0)
        else if (attentionValue <= Config.LOW_ATTENTION) Color(255, 90, 0)
        else if (attentionValue <= Config.MEDIUM_ATTENTION) Color(255, 255, 0)
        else if (attentionValue <= Config.HIGH_ATTENTION) Color(173, 255, 47)
        else Color(0, 255, 0)

        val textAttributes = TextAttributes(null, color, null, EffectType.LINE_UNDERSCORE, Font.PLAIN)
        editor.markupModel.addLineHighlighter(line - 1, HighlighterLayer.CARET_ROW, textAttributes)
    }

    fun removeAllHighlighter(editor: Editor) {
        editor.markupModel.removeAllHighlighters()
    }

    fun hideLine(editor: Editor, line: Int) {
        val color = Color(0, 0, 0)
        val textAttributes = TextAttributes(color, color, null, EffectType.LINE_UNDERSCORE, Font.PLAIN)
        editor.markupModel.addLineHighlighter(line - 1, HighlighterLayer.CARET_ROW, textAttributes)
    }
}