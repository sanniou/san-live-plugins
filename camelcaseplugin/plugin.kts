import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.actions.EditorActionUtil
import com.intellij.openapi.editor.actions.TextComponentEditorAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.ThrowableRunnable
import liveplugin.PluginUtil
import liveplugin.registerAction
import liveplugin.show
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.text.DateFormat
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

object Env {
    const val startup = true
    const val resetAction = false
    const val debugMode = false
    var consoleView: ConsoleView? = null
    var project: Project? = null
    fun println(message: Any) {
        if (!debugMode || project == null) {
            return
        }
        if (consoleView == null) {
            consoleView = PluginUtil.showInConsole(message, project!!)
        } else {
            consoleView?.println(message)
        }
    }

    fun ConsoleView.println(str: Any?) {
        this.print("${DateFormat.getDateTimeInstance().format(Date())} : $str\n", ConsoleViewContentType.LOG_INFO_OUTPUT)
    }
}

//#######################################################################################################

registerAction(id = "cpm.san.code.ToggleCamelCase", keyStroke = "alt shift U", action = ToggleCamelCase())

show("register camelcase success")


//#######################################################################################################

class ToggleCamelCase : TextComponentEditorAction(CamelCaseEditorActionHandler<String>())

//#######################################################################################################
class CamelCaseEditorActionHandler<T> : EditorActionHandler(true) {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val additionalParameter = beforeWriteAction(editor)
        if (!additionalParameter.first) {
            return
        }
        object : EditorWriteActionHandler(false) {
            override fun executeWriteAction(editor1: Editor, @Nullable caret1: Caret?, dataContext1: DataContext?) {}
        }.doExecute(editor, caret, dataContext)
    }

    @NotNull
    private fun beforeWriteAction(editor: Editor): Pair<Boolean, T> {
        val project = editor.project!!
        val config = CamelCaseConfig()
        var text = editor.selectionModel.selectedText
        if (text == null || text.isEmpty()) {
            editor.selectionModel.selectWordAtCaret(true)
            var moveLeft = true
            var moveRight = true
            var start: Int = editor.selectionModel.selectionStart
            var end: Int = editor.selectionModel.selectionEnd
            val p: Pattern = Pattern.compile("[^A-z0-9.\\-]")

            // move caret left
            while (moveLeft && start != 0) {
                start--
                EditorActionUtil.moveCaret(editor.caretModel.currentCaret, start, true)
                val m: Matcher = p.matcher(editor.selectionModel.selectedText!!)
                if (m.find()) {
                    start++
                    moveLeft = false
                }
            }
            editor.selectionModel.setSelection(end - 1, end)

            // move caret right
            while (moveRight && end != editor.document.textLength) {
                end++
                EditorActionUtil.moveCaret(editor.caretModel.currentCaret, end, true)
                val m: Matcher = p.matcher(editor.selectionModel.selectedText!!)
                if (m.find()) {
                    end--
                    moveRight = false
                }
            }
            editor.selectionModel.setSelection(start, end)
            text = editor.selectionModel.selectedText
        }
        val newText: String
        text!!
        if (config.cb1State || config.cb2State || config.cb3State || config.cb4State || config.cb5State || config.cb6State) {
            newText = Conversion.transform(text,
                    config.cb6State,  // space case
                    config.cb1State,  // kebab case
                    config.cb2State,  // upper snake case
                    config.cb3State,  // pascal case
                    config.cb4State,  // camel case
                    config.cb5State,  // lower snake case
                    config.model)
            val runnable = Runnable { replaceText(editor, newText) }
            ApplicationManager.getApplication().runWriteAction(getRunnableWrapper(project, runnable))
        }
        return continueExecution()
    }

    fun replaceText(editor: Editor, replacement: String) {
        try {
            WriteAction.run(ThrowableRunnable<Throwable> {
                val start = editor.selectionModel.selectionStart
                EditorModificationUtil.insertStringAtCaret(editor, replacement)
                editor.selectionModel.setSelection(start, start + replacement.length)
            })
        } catch (ignored: Throwable) {
            show(ignored)
        }
    }

    private fun continueExecution(): Pair<Boolean, T> {
        return Pair<Boolean, T>(true, null)
    }

    private fun getRunnableWrapper(project: Project, runnable: Runnable): Runnable {
        return Runnable { CommandProcessor.getInstance().executeCommand(project, runnable, "CamelCase", ActionGroup.EMPTY_GROUP) }
    }

}

//#######################################################################################################
class CamelCaseConfig {
    var cb1State = true
    var cb2State = true
    var cb3State = true
    var cb4State = true
    var cb5State = true
    var cb6State = true
    var model = arrayOf(
            "kebab-case",
            "SNAKE_CASE",
            "CamelCase",
            "camelCase",
            "snake_case",
            "space case")


}

//#######################################################################################################
object Conversion {
    private const val CONVERSION_SNAKE_CASE = "snake_case"
    private const val CONVERSION_SPACE_CASE = "space case"
    private const val CONVERSION_KEBAB_CASE = "kebab-case"
    private const val CONVERSION_UPPER_SNAKE_CASE = "SNAKE_CASE"
    private const val CONVERSION_PASCAL_CASE = "CamelCase"
    private const val CONVERSION_CAMEL_CASE = "camelCase"
    private const val CONVERSION_LOWER_SNAKE_CASE = "snake_case"

    @NotNull
    fun transform(text: String,
                  useSpaceCase: Boolean,
                  useKebabCase: Boolean,
                  useUpperSnakeCase: Boolean,
                  usePascalCase: Boolean,
                  useCamelCase: Boolean,
                  useLowerSnakeCase: Boolean,
                  conversionList: Array<String>): String {
        var text = text
        var newText: String
        var appendText = ""
        var repeat: Boolean
        var iterations = 0
        var next: String? = null
        val p = Pattern.compile("^\\W+")
        val m = p.matcher(text)
        if (m.find()) {
            appendText = m.group(0)
        }
        //remove all special chars
        text = text.replace("^\\W+".toRegex(), "")
        do {
            newText = text
            val isLowerCase = text == text.toLowerCase()
            val isUpperCase = text == text.toUpperCase()
            if (isLowerCase && text.contains("_")) {
                // snake_case to space case
                if (next == null) {
                    next = getNext(CONVERSION_SNAKE_CASE, conversionList)
                    repeat = true
                } else {
                    if (next == CONVERSION_SPACE_CASE) {
                        repeat = !useSpaceCase
                        next = getNext(CONVERSION_SPACE_CASE, conversionList)
                    } else {
                        repeat = true
                    }
                }
                newText = text.replace('_', ' ')
            } else if (isLowerCase && text.contains(" ")) {
                // space case to kebab-case
                if (next == null) {
                    next = getNext(CONVERSION_SPACE_CASE, conversionList)
                    repeat = true
                } else {
                    newText = text.replace(' ', '-')
                    if (next == CONVERSION_KEBAB_CASE) {
                        repeat = !useKebabCase
                        next = getNext(CONVERSION_KEBAB_CASE, conversionList)
                    } else {
                        repeat = true
                    }
                }
            } else if (isLowerCase && text.contains("-")) {
                // kebab-case to SNAKE_CASE
                if (next == null) {
                    next = getNext(CONVERSION_KEBAB_CASE, conversionList)
                    repeat = true
                } else {
                    newText = text.replace('-', '_').toUpperCase()
                    if (next == CONVERSION_UPPER_SNAKE_CASE) {
                        repeat = !useUpperSnakeCase
                        next = getNext(CONVERSION_UPPER_SNAKE_CASE, conversionList)
                    } else {
                        repeat = true
                    }
                }
            } else if (isUpperCase && text.contains("_") || isLowerCase && !text.contains("_") && !text.contains(" ")) {
                // SNAKE_CASE to PascalCase
                if (next == null) {
                    next = getNext(CONVERSION_UPPER_SNAKE_CASE, conversionList)
                    repeat = true
                } else {
                    newText = toCamelCase(text.toLowerCase())
                    if (next == CONVERSION_PASCAL_CASE) {
                        repeat = !usePascalCase
                        next = getNext(CONVERSION_PASCAL_CASE, conversionList)
                    } else {
                        repeat = true
                    }
                }
            } else if (!isUpperCase && text.substring(0, 1) == text.substring(0, 1).toUpperCase() && !text.contains("_")) {
                // PascalCase to camelCase
                if (next == null) {
                    next = getNext(CONVERSION_PASCAL_CASE, conversionList)
                    repeat = true
                } else {
                    newText = text.substring(0, 1).toLowerCase() + text.substring(1)
                    if (next == CONVERSION_CAMEL_CASE) {
                        repeat = !useCamelCase
                        next = getNext(CONVERSION_CAMEL_CASE, conversionList)
                    } else {
                        repeat = true
                    }
                }
            } else {
                // camelCase to snake_case
                if (next == null) {
                    next = getNext(CONVERSION_CAMEL_CASE, conversionList)
                    repeat = true
                } else {
                    newText = toSnakeCase(text)
                    if (next == CONVERSION_LOWER_SNAKE_CASE) {
                        repeat = !useLowerSnakeCase
                        next = getNext(CONVERSION_LOWER_SNAKE_CASE, conversionList)
                    } else {
                        repeat = true
                    }
                }
            }
            if (iterations++ > 10) {
                repeat = false
            }
            text = newText
        } while (repeat)
        return appendText + newText
    }

    /**
     * Return next conversion (or wrap to first)
     *
     * @param conversion  String
     * @param conversions Array of strings
     * @return next conversion
     */
    private fun getNext(conversion: String, conversions: Array<String>): String {
        val index = conversions.indexOf(conversion) + 1
        return if (index < conversions.size) {
            conversions[index]
        } else {
            conversions[0]
        }
    }

    /**
     * Convert a string (CamelCase) to snake_case
     *
     * @param in CamelCase string
     * @return snake_case String
     */
    private fun toSnakeCase(`in`: String): String {
        var inStr = `in`
        inStr = inStr.replace(" +".toRegex(), "")
        val result = StringBuilder("" + Character.toLowerCase(inStr[0]))
        for (i in 1 until inStr.length) {
            val c = inStr[i]
            if (Character.isUpperCase(c)) {
                result.append("_").append(Character.toLowerCase(c))
            } else {
                result.append(c)
            }
        }
        return result.toString()
    }

    /**
     * Convert a string (snake_case) to CamelCase
     *
     * @param in snake_case String
     * @return CamelCase string
     */
    private fun toCamelCase(`in`: String): String {
        val camelCased = StringBuilder()
        val tokens = `in`.split("_").toTypedArray()
        for (token in tokens) {
            if (token.isNotEmpty()) {
                camelCased.append(token.substring(0, 1).toUpperCase()).append(token.substring(1))
            } else {
                camelCased.append("_")
            }
        }
        return camelCased.toString()
    }
}