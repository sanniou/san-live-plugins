import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.fileTemplates.impl.UrlUtil
import com.intellij.openapi.project.Project
import liveplugin.PluginUtil
import liveplugin.show
import java.text.DateFormat
import java.util.*

object Env {
    const val startup = false
    const val resetAction = false
    const val debugMode = false
    var consoleView: ConsoleView? = null
    var project: Project? = null
    fun println(message: Any) {
        if (!debugMode && project != null) {
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

if (!isIdeStartup) {
    show("Current project: ${project?.name}")
    var path = "/template/groupName/templateName.vm"
}


object FileUtil {
    fun loadFile(fileName: String): String {
        fileName.run {

        }
        val url = java.net.URL(this.javaClass.getResource("").toString().replace("live-plugins-compiled", "live-plugins") + fileName)
        show(this.javaClass.getResource("").toString())
        try {
            return UrlUtil.loadText(url).replace("\r", "")
        } catch (e: Exception) {
            show("IOException$e")

        }
        return ""
    }
}