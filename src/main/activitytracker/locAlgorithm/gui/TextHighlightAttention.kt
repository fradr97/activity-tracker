package activitytracker.locAlgorithm.gui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font

class TextHighlightAttention {
    fun addLineHighlighter(editor: Editor, line: Int, attentionValue: Int) {
        var color: Color? = null
        color =
            if (attentionValue < LOW_ATTENTION) JBColor.RED else if (attentionValue < MEDIUM_ATTENTION) JBColor.YELLOW else JBColor.GREEN
        val hsbVals = Color.RGBtoHSB(
            color.getRed(),
            color.getGreen(),
            color.getBlue(), null
        )
        val gHue = 1 / 3f
        val highlight = Color.getHSBColor(hsbVals[0], hsbVals[1] + (attentionValue * 100 / 10), hsbVals[1])
        val shadow = Color.getHSBColor(hsbVals[0], hsbVals[1], attentionValue * 0.5f * hsbVals[2])
        val textattributes = TextAttributes(null, color, null, EffectType.LINE_UNDERSCORE, Font.PLAIN)
        editor.markupModel.addLineHighlighter(line - 1, HighlighterLayer.CARET_ROW, textattributes)
    }

    fun removeAllHighlighter(editor: Editor) {
        editor.markupModel.removeAllHighlighters()
    }

    companion object {
        private const val LOW_ATTENTION = 4
        private const val MEDIUM_ATTENTION = 7
    }
}