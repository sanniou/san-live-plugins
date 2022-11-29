import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
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


//throw RuntimeException("stop this")

// show BaseNavigateToSourceAction
/*
Env.project = project!!
PluginUtil.registerAction("symbolizeKeyWords", "ctrl alt shift 0", SampleAction())

show("Current project: ${project?.name}")

class SampleAction : BaseNavigateToSourceAction(true) {


}*/

show("run success ")
