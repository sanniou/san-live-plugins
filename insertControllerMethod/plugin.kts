import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import liveplugin.PluginUtil
import liveplugin.runWriteAction
import liveplugin.show
import org.jetbrains.kotlin.idea.formatter.commitAndUnblockDocument
import org.jetbrains.kotlin.j2k.getContainingClass
import java.text.DateFormat
import java.util.*

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

if (Env.startup || !isIdeStartup) {
    Env.project = project
    IntentionManager.getInstance().apply {
        intentionActions.firstOrNull {
            it.javaClass.toString().contains("InsertControllerMethod")
        }?.let {
            Env.println("unregisterIntention${it.javaClass}")
            unregisterIntention(it)
        }
        addAction(InsertControllerMethodAction())
        show("register InsertControllerMethodAction  success")
    }
}

class InsertControllerMethodAction : PsiElementBaseIntentionAction() {
    override fun getText(): String {
        return "InsertServiceMethod"
    }

    fun PsiFile.reformatCodeRange(rangeStart: Int, rangeEnd: Int) {
        try {
            CodeStyleManager.getInstance(project).reformatRange(this, rangeStart, rangeEnd)
        } catch (e: Exception) {
            show(e.toString())
        }
    }

    override fun checkFile(file: PsiFile?): Boolean {
        //Env.println("checkFile${super.checkFile(file)}")
        return super.checkFile(file)
    }

    override fun getFamilyName(): String {
        return "InsertControllerMethodActionFamily"
    }

    override fun isAvailable(project1: Project, editor: Editor?, psiElement: PsiElement): Boolean {
        return psiElement.getContainingClass().run {
            Env.println("isAvailable@${this?.name}")
            this?.name?.contains("Controller") ?: false
        }
    }

    override fun invoke(project: Project, editor: Editor?, psiElement: PsiElement) {
        editor ?: return
        val psiClass = psiElement.getContainingClass()!!
        Env.println("psiClass$psiClass")
        Env.println("psiElement$psiElement")
        Env.println("psiElement size${psiClass.allFields.size}")
        psiClass.allFields.filter { it.name.endsWith("Service") || it.name.endsWith("service") }
                .forEach { serviceField ->
                    Env.println("psiField$serviceField")
                    PsiTypesUtil.getPsiClass(serviceField.type)?.methods?.forEach { method ->
                        Env.println("methods$method")
                        psiClass.methods.firstOrNull { psiMethod -> psiMethod.name == method.name }
                                ?.run {
                                    Env.println("firstOrNull$this")

                                } ?: run {
                            Env.println("insertMethod$method")
                            insertMethod(psiElement, method, serviceField.name, editor, project)
                        }
                    }
                }
    }

    fun insertMethod(psiElement: PsiElement, method: PsiMethod, serviceName: String, editor: Editor, project: Project) {
        if(method.parameters.isEmpty()){
            return
        }
        editor.document.runWriteAction(project, description = "insertMethod", callback = {
            val psiFile = psiElement.containingFile
            val contentLength: Int;
            editor.document.insertString(psiElement.startOffset, """
    @PostMapping("/${method.name}")
    @ApiOperation(value = "${method.name}")
    public PmmsResponse<${method.returnType?.presentableText}> ${method.name}(
            @RequestBody @Validated ${(method.parameters[0].type as PsiType).presentableText} request) {
        return tryFunction(request, () -> $serviceName.${method.name}(request));
    }
    """.trimIndent().apply { contentLength = this.length })
            // fixme not work
            psiFile.commitAndUnblockDocument()
            psiFile.reformatCodeRange(psiElement.startOffset, psiElement.startOffset + contentLength)
        })
    }

}
