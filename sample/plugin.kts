import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.fileTemplates.impl.UrlUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import liveplugin.PluginUtil
import liveplugin.show
import java.text.DateFormat
import java.util.*
import java.awt.TrayIcon.MessageType


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




throw RuntimeException("stop this")

show("Current project: ${project?.name}")
ProgressManager.getInstance().run(object : Task.Backgroundable(project, "show progress") {
    override fun run(progressIndicator: ProgressIndicator) {
        progressIndicator.isIndeterminate = false
        val currentTimeMillis = System.currentTimeMillis()
        progressIndicator.text = "ZZZ"
        progressIndicator.text2 = "AAA"
        Thread.sleep(10000)

        for (i in 1..10) {
            progressIndicator.fraction = 0.1 * i
            Thread.sleep(500)
        }

        val sb = StringBuffer()
        sb.append("show progress  [${System.currentTimeMillis() - currentTimeMillis} ms]")
        show(sb.toString())
    }
})



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