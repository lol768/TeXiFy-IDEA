package nl.rubensten.texifyidea.intentions

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import nl.rubensten.texifyidea.util.*

/**
 * @author Ruben Schellekens
 */
open class LatexLeftRightParenthesesIntention : TexifyIntentionBase("Change to \\left..\\right") {

    companion object {

        val BRACKETS = mapOf(
                "(" to ")",
                "[" to "]",
                "<" to ">"
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null || !file.isLatexFile()) {
            return false
        }

        val element = file.findElementAt(editor.caretModel.offset - 1) ?: return false
        if (!element.inMathContext() || element is PsiComment) {
            return false
        }

        val (lookbehind, lookahead) = scan(editor, file) ?: return false
        return BRACKETS.containsKeyOrValue(lookbehind) || BRACKETS.containsKeyOrValue(lookahead)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null || !file.isLatexFile()) {
            return
        }

        val caret = editor.caretModel
        val (lookbehind, lookahead) = scan(editor, file) ?: return

        if (BRACKETS.containsKey(lookahead)) {
            replaceForward(editor, caret.offset, file)
        }
        else if (BRACKETS.containsKey(lookbehind)) {
            replaceForward(editor, caret.offset - 1, file)
        }
        else if (BRACKETS.containsValue(lookbehind)) {
            replaceBackward(editor, caret.offset - 1, file)
        }
        else if (BRACKETS.containsValue(lookahead)) {
            replaceBackward(editor, caret.offset, file)
        }
    }

    private fun replaceForward(editor: Editor, offset: Int, file: PsiFile) {
        val document = editor.document
        val open = document.getText(TextRange.from(offset, 1))
        val close = BRACKETS[open]

        // Scan document.
        var current = offset
        var nested = 0
        var closeOffset: Int? = null
        while (++current < file.textLength) {
            val char = document.getText(TextRange.from(current, 1))

            // Ignore comments.
            val element = file.findElementAt(current)
            if (element is PsiComment) {
                continue
            }

            if (!element!!.inMathContext() && element !is PsiWhiteSpace) {
                break
            }

            // Open nesting
            if (char == open) {
                nested++
                continue
            }

            // Close nesting
            if (char == close && nested > 0) {
                nested--
                continue
            }

            // Whenever met at correct closure
            if (char == close && nested <= 0) {
                closeOffset = current
                break
            }
        }

        if (closeOffset == null) {
            HintManager.getInstance().showErrorHint(editor, "Could not find matching close '$close'")
            return
        }

        // Replace stuff
        runWriteAction {
            document.replaceString(closeOffset!!, closeOffset!! + 1, "\\right$close")
            document.replaceString(offset, offset + 1, "\\left$open")
            editor.caretModel.moveToOffset(offset + 6)
        }
    }

    private fun replaceBackward(editor: Editor, offset: Int, file: PsiFile) {
        val document = editor.document
        val close = document.getText(TextRange.from(offset, 1))
        val open = BRACKETS.findKeys(close).first()

        // Scan document.
        var current = offset
        var nested = 0
        var openOffset: Int? = null
        while (--current < file.textLength) {
            val char = document.getText(TextRange.from(current, 1))

            // Ignore comments.
            val element = file.findElementAt(current)
            if (element is PsiComment) {
                continue
            }

            if (!element!!.inMathContext()) {
                break
            }

            // Open nesting
            if (char == close) {
                nested++
                continue
            }

            // Close nesting
            if (char == open && nested > 0) {
                nested--
                continue
            }

            // Whenever met at correct closure
            if (char == open && nested <= 0) {
                openOffset = current
                break
            }
        }

        if (openOffset == null) {
            HintManager.getInstance().showErrorHint(editor, "Could not find matching open '$close'")
            return
        }

        // Replace stuff
        runWriteAction {
            document.replaceString(offset, offset + 1, "\\right$close")
            document.replaceString(openOffset!!, openOffset!! + 1, "\\left$open")
        }
    }

    private fun scan(editor: Editor, file: PsiFile): Pair<String, String>? {
        val caret = editor.caretModel
        val document = file.document() ?: return null

        if (caret.offset >= document.textLength) {
            return null
        }

        val lookbehind = document.getText(TextRange.from(caret.offset - 1, 1))
        val lookahead = document.getText(TextRange.from(caret.offset, 1))

        return Pair(lookbehind, lookahead)
    }
}