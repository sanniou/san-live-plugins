import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.suggested.startOffset
import liveplugin.PluginUtil
import liveplugin.currentEditor
import liveplugin.runWriteAction
import liveplugin.show
import org.jetbrains.kotlin.idea.formatter.commitAndUnblockDocument
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
        this.print("${DateFormat.getDateTimeInstance().format(Date())} : $str \n", ConsoleViewContentType.LOG_INFO_OUTPUT)
    }
}

if (Env.startup || !isIdeStartup) {
    Env.project = project

    val actionGroupId = "GenerateGroup"
    val actionId = "com.saniou.easy.code.action.JavaActionGroup"
    val displayText = "JavaActionGroup"

    val actionManager: ActionManager = ActionManager.getInstance()
    val actionGroup = actionManager.getAction(actionGroupId) as DefaultActionGroup

    val alreadyRegistered = (actionManager.getAction(actionId) != null)
    if (alreadyRegistered) {
        actionGroup.remove(actionManager.getAction(actionId))
        actionManager.unregisterAction(actionId)
    }

    val action = JavaActionGroup()
    actionManager.registerAction(actionId, action)
    actionGroup.addAction(action, Constraints.FIRST)
    action.templatePresentation.setText(displayText, true)
    show("register java action group success")
}

//#######################################################################################################
class JavaActionGroup : ActionGroup() {

    private var hide = false
    private var arrayOfAnActions: Array<AnAction> = EMPTY_ARRAY

    override fun hideIfNoVisibleChildren(): Boolean {
        return hide
    }

    override fun isPopup(): Boolean {
        return true
    }

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        event ?: kotlin.run {
            show("event=$event")
            return getEmptyAnAction()
        }
        // 获取当前项目
        val project = getEventProject(event) ?: run {
            show("project= null")
            return getEmptyAnAction()
        }

        val dumbService = DumbService.getInstance(project)
        if (dumbService.isDumb) {
            dumbService.showDumbModeNotification("plugin is not available during indexing")
            return getEmptyAnAction()
        }

        val psiFile = event.getData(LangDataKeys.PSI_FILE)
        if (psiFile !is PsiJavaFile) {
            return getEmptyAnAction()
        }

        return getMenuList()
    }

    fun checkAction(clazz: Class<out AnAction>): AnAction {
        val configActionIdPrefix = "com.saniou.easy.code.action"
        val actionId = configActionIdPrefix + clazz.name
        val actionManager: ActionManager = ActionManager.getInstance()
        val configAction: AnAction? = actionManager.getAction(actionId)

        fun registerAction(): AnAction {
            val newAction = clazz.newInstance() as AnAction
            actionManager.registerAction(actionId, newAction)
            return newAction
        }
        if (Env.resetAction) {
            if (configAction != null) {
                actionManager.unregisterAction(actionId)
            }
            return registerAction()
        } else {
            if (configAction == null) {
                return registerAction()
            }
        }
        return configAction

    }

    private fun getMenuList(): Array<AnAction> {
        if (arrayOfAnActions.isNotEmpty()) {
            return arrayOfAnActions
        }
        arrayOfAnActions = arrayOf(
                checkAction(AddJsonPropertyAction::class.java),
                checkAction(CopyParentProfileAction::class.java),
                checkAction(FoldRightProfileAction::class.java)
        )
        return arrayOfAnActions
    }

    private fun getEmptyAnAction(): Array<AnAction> {
        this.hide = true
        return arrayOfAnActions
    }

}

//#######################################################################################################


class FoldRightProfileAction : AnAction("FoldRightProfileAction") {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project!!
        val editor = project.currentEditor!!
        val psiFile = event.getData(LangDataKeys.PSI_FILE)
        Env.println("psiFile2$psiFile")
        if (psiFile !is PsiJavaFile) {
            return
        }
        editor.document.runWriteAction(project, description = "Insert JsonProperty", callback = {
            Env.println("psiFile : $psiFile")
            psiFile.classes.forEach { psiClass ->
                Env.println("psiClass : $psiClass")
                psiClass.fields.let { fields ->
                    fields.foldRight(0) { field, _ ->
                        Env.println("field : $field")
                        try {
                            psiClass.add(field)
                            field.delete()
                        } catch (e: Exception) {
                            Env.println("Exception : $e")
                        }
                        0
                    }
                }
            }
            psiFile.reformatCode()
        })

    }


    fun PsiFile.reformatCode() {
        try {
            CodeStyleManager.getInstance(project).reformat(this)
            JavaCodeStyleManager.getInstance(project).optimizeImports(this)
        } catch (e: Exception) {
            show(e.toString())
        }
    }

}

//#######################################################################################################

class CopyParentProfileAction : AnAction("CopyParentProfileAction") {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project!!
        val editor = project.currentEditor!!
        val psiFile = event.getData(LangDataKeys.PSI_FILE)
        Env.println("psiFile2$psiFile")
        if (psiFile !is PsiJavaFile) {
            return
        }
        editor.document.runWriteAction(project, description = "Insert JsonProperty", callback = {
            Env.println("psiFile : $psiFile")
            psiFile.classes.forEach { psiClass ->
                Env.println("psiClass : $psiClass")
                psiClass.allFields.forEach { field ->
                    Env.println("field : $field")
                    try {
                        psiClass.fields.firstOrNull {
                            it.name == field.name
                        } ?: run {
                            psiClass.add(field)
                        }
                    } catch (e: Exception) {
                        Env.println("Exception : $e")
                    }
                }
            }
            editor.document.text.replace(" = null;", ";").let {
                return@let if (it.contains(" implements ")) {
                    Env.println("replace until implements ")
                    it.replace(Regex("extends[\\.A-Z a-z\\s]+ implements"), " implements")
                } else {
                    Env.println("replace until \"{\" ")
                    it.replace(Regex("extends[\\.A-Z a-z\\s]+ \\{"), " {")
                }
            }.run {
                editor.document.setText(this)
            }
            psiFile.commitAndUnblockDocument()
            psiFile.reformatCode()
        })

    }


    fun PsiFile.reformatCode() {
        try {
            CodeStyleManager.getInstance(project).reformat(this)
            JavaCodeStyleManager.getInstance(project).optimizeImports(this)
        } catch (e: Exception) {
            show(e.toString())
        }
    }

}

//#######################################################################################################
class AddJsonPropertyAction : AnAction("AddJsonProperty") {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project!!
        val editor = project.currentEditor!!
        val psiFile = event.getData(LangDataKeys.PSI_FILE)
        if (psiFile !is PsiJavaFile) {
            return
        }
        editor.document.runWriteAction(project, description = "Insert JsonProperty", callback = {
            psiFile.classes.forEach { psiClass ->
                psiClass.fields.let { fields ->
                    fields.foldRight(0) { field, _ ->
                        field.annotations.forEach {
                            if (it.text.contains("JsonProperty")) {
                                return@foldRight 0
                            }
                        }
                        it.insertString(field.startOffset, "@JsonProperty(\"${field.name}\")\n")
                        0
                    }
                }
            }

            val jsonPropertyClass = JavaPsiFacade.getInstance(project).findClass("com.fasterxml.jackson.annotation.JsonProperty", GlobalSearchScope.allScope(project))!!
            psiFile.commitAndUnblockDocument()
            if (psiFile.findImportReferenceTo(jsonPropertyClass) == null) {
                psiFile.importClass(jsonPropertyClass)
            }

            psiFile.reformatCode()
        })

    }


    fun PsiFile.reformatCode() {
        try {
            CodeStyleManager.getInstance(project).reformat(this)
//            JavaCodeStyleManager.getInstance(project).optimizeImports(this)
        } catch (e: Exception) {
            show(e.toString())
        }
    }

}

//// 编辑文件需要开启线程
//WriteCommandAction.Simple(psiMethod.getProject(), psiMethod.getContainingFile()) {
//    @Override
//    protected void run() throws Throwable {

//    }
//}.execute();
