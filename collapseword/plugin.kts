import com.intellij.codeInsight.folding.impl.EditorFoldingInfo
import com.intellij.codeInsight.folding.impl.FoldingUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import liveplugin.PluginUtil.*
import java.util.regex.Pattern

// This is a micro-plugin to collapse Java keywords into shorter symbols.
// (looks better if folded text background is the same as normal text; Settings -> Editor -> Colors & Fonts)
// (Note that it can only be executed within this plugin https://github.com/dkandalov/live-plugin)

registerAction("symbolizeKeyWords", "ctrl alt shift 0", object : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor = currentEditorIn(event.project!!)!!
        collapseIn(editor, "(\\.stream()\n?\\s+)\\s") { "" }
        collapseIn(editor, "(@Override\n?\\s+)\\s") { "↑" }
        collapseIn(editor, "(public\n?\\s*?)\\s") { "●" }
        collapseIn(editor, "(void\n?\\s*?)\\s") { "" }
        collapseIn(editor, "(private\n?\\s*?)\\s") { "○" }
        collapseIn(editor, "(StringUtils.isEmpty)") { "isEmpty" }
        collapseIn(editor, "(CollectionUtils.isEmpty)") { "isEmpty" }
        collapseIn(editor, "(protected\n?\\s*?)\\s") { "□" }
        collapseIn(editor, "(static\n?\\s*?)\\s") { "◆" }
        collapseIn(editor, "(final\n?\\s*?)\\s") { "ƒ" }
        collapseIn(editor, "( extends )") { " ← " }
        collapseIn(editor, "( implements )") { " ⇠ " }
        collapseIn(editor, "\\S(.;\n)") { it.replace(";", "") }
        collapseIn(editor, "(\\(\\))") { "" }
        collapseIn(editor, "(!=)") { "≠" }
        collapseIn(editor, "(return)") { "↵" }
        // replace "." with "->" if you really miss C++
        //collapseIn(editor, "(\\S\\.)", { it.replace(".", "->") })
    }

})

if (!isIdeStartup) show("Loaded symbolizeKeywords action. Use Ctrl+Alt+Shift+0 to run it.")


fun collapseIn(editor: Editor, regExp: String, replacementFor: (String) -> String) {
    val matches = mutableListOf<Match>()
    val matcher = Pattern.compile(regExp).matcher(editor.document.charsSequence)
    while (matcher.find()) {
        matches.add(Match(matcher.start(1), matcher.end(1), matcher.group(1)))
    }

    editor.foldingModel.runBatchFoldingOperation {
        matches.forEach { foldText(it.start, it.end, replacementFor(it.text), editor) }
    }

}

data class Match(val start: Int, val end: Int, val text: String)

/**
 * Originally copied from com.intellij.codeInsight.folding.impl.CollapseSelectionHandler
 */
fun foldText(start: Int, end: Int, placeHolderText: String, editor: Editor) {
    if (start + 1 >= end) return
    val realEnd = if (start < end && editor.document.charsSequence[end - 1] == '\n') end - 1 else end

    var region: FoldRegion? = FoldingUtil.findFoldRegion(editor, start, realEnd)
    if (region != null) {
        val info = EditorFoldingInfo.get(editor)
        if (info.getPsiElement(region) == null) {
            editor.foldingModel.removeFoldRegion(region)
            info.removeRegion(region)
        }
    } else {
        region = (editor.foldingModel).addFoldRegion(start, realEnd, placeHolderText)
        if (region == null) {
            return
        }
        region.isExpanded = false
    }
}
