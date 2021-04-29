import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.actions.BaseNavigateToSourceAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import liveplugin.PluginUtil
import liveplugin.show
import java.text.DateFormat
import java.util.*

object Env {
    const val startup = true
    val resetAction get() = debugMode
    const val debugMode = true
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


throw RuntimeException("stop this")
Env.project = project!!
PluginUtil.registerAction("symbolizeKeyWords", "ctrl alt shift 0", SampleAction())

show("Current project: ${project?.name}")

class SampleAction : BaseNavigateToSourceAction(true) {
    

}