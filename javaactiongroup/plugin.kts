import com.google.common.base.CaseFormat
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.suggested.startOffset
import com.jetbrains.rd.util.string.printToString
import liveplugin.PluginUtil
import liveplugin.currentEditor
import liveplugin.executeCommand
import liveplugin.show
import java.text.DateFormat
import java.util.*
import kotlin.reflect.KClass

// depends-on-plugin com.intellij.java

//todo order method;order filed  
object Env {

    const val startup = true

    val resetAction get() = debugMode

    const val debugMode = false

    var consoleView: ConsoleView? = null

    var project: Project? = null

    fun println(message: Any) {
        if (!debugMode || project == null) {
            return
        }
        if (consoleView == null) {
            consoleView =
                PluginUtil.showInConsole("${DateFormat.getDateTimeInstance().format(Date())} : $message \n", project!!)
        } else {
            consoleView?.println(message)
        }
    }

    fun ConsoleView.println(str: Any?) {
        this.print("${DateFormat.getDateTimeInstance().format(Date())} : $str \n", ConsoleViewContentType.NORMAL_OUTPUT)
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
        Env.println("remove $actionId")
        actionGroup.remove(actionManager.getAction(actionId))
        actionManager.unregisterAction(actionId)
    }

    val action = JavaActionGroup()
    actionManager.registerAction(actionId, action)
    actionGroup.addAction(action, Constraints.FIRST)
    action.templatePresentation.setText(displayText, false)
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
            dumbService.showDumbModeNotification("Plugin is not available during indexing")
            return getEmptyAnAction()
        }

        val psiFile = event.getData(LangDataKeys.PSI_FILE)
        if (psiFile !is PsiJavaFile) {
            show("error ! psiFile = $psiFile")
            return getEmptyAnAction()
        }

        return getMenuList()
    }

    fun checkAction(clazz: KClass<out AnAction>): AnAction {
        val configActionIdPrefix = "com.saniou.easy.code.action"
        val actionId = configActionIdPrefix + clazz.simpleName
        val actionManager: ActionManager = ActionManager.getInstance()
        val configAction: AnAction? = actionManager.getAction(actionId)

        fun registerAction(): AnAction {
            try {
                clazz.constructors.forEach { kFunction ->
                    Env.println(kFunction.parameters.joinToString { "a" + it.type })
                }
                val newAction = clazz.java.getDeclaredConstructor(Plugin::class.java).newInstance(null) as AnAction
                actionManager.registerAction(actionId, newAction)
                return newAction
            } catch (e: Exception) {
                Env.println(e.printToString())
                throw RuntimeException(e.message, e)
            }
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
            checkAction(AddJsonPropertyAction::class),
            checkAction(AddJpaColumnAction::class),
            checkAction(FormatBlankLineAction::class),
            checkAction(CopyParentProfileAction::class),
            checkAction(FoldRightProfileAction::class),
        )
        return arrayOfAnActions
    }

    private fun getEmptyAnAction(): Array<AnAction> {
        this.hide = true
        return arrayOfAnActions
    }

}

//#######################################################################################################


class FoldRightProfileAction : AnAction("FoldRightProfile") {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project!!
        val editor = project.currentEditor!!
        val psiFile = event.getData(LangDataKeys.PSI_FILE)
        Env.println("psiFile2$psiFile")
        if (psiFile !is PsiJavaFile) {
            return
        }

        editor.document.executeCommand(project, description = "Insert Hello World") {
            Env.println("psiFile : $psiFile")
            psiFile.classes.forEach { psiClass ->
                Env.println("psiClass : $psiClass")
                psiClass.fields.foldRight(0) { field, _ ->
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
            psiFile.reformatCode()
        }

    }
}

//#######################################################################################################

class CopyParentProfileAction : AnAction("CopyParentProfile") {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project!!
        val psiElement: PsiNamedElement = (event.getData(LangDataKeys.PSI_ELEMENT) as? PsiClass)
            ?: (event.getData(LangDataKeys.PSI_FILE) as? PsiJavaFile) ?: return
        Env.println("psiElement = $psiElement")

        project.currentEditor?.document?.executeCommand(project, description = "Insert Hello World") {
            val psiClasses = when (psiElement) {
                is PsiJavaFile -> psiElement.classes
                is PsiClass -> arrayOf(psiElement)
                else -> return@executeCommand
            }
            psiClasses.forEach { psiClass ->
                Env.println("psiClass : $psiClass")

                val fields = psiClass.fields
                psiClass.allFields.toMutableList()
                    .apply {
                        removeAll { pField ->
                            fields.find { it.name == pField.name } != null
                        }
                    }
                    .filter { !it.hasModifierProperty("static") }
                    .forEach { field ->
                        Env.println("field : $field")
                        val fieldz = psiClass.add(field) as PsiField

                        try {
                            fieldz.annotations.forEach {
                                it.delete()
                            }
                        } catch (e: Exception) {
                            Env.println(e)
                        }
                    }

                psiClass.extendsListTypes.forEach { parent ->
                    parent.psiContext?.also {
                        it.delete()
                    }

                }
            }
            psiElement.containingFile.reformatCode()
        }
    }

}

//#######################################################################################################
class FormatBlankLineAction : AnAction("FormatBlankLine") {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project!!
        val editor = project.currentEditor!!
        val psiFile = event.getData(LangDataKeys.PSI_FILE)
        if (psiFile !is PsiJavaFile) {
            return
        }

        editor.document.executeCommand(project, description = "Insert Hello World") {
            val offset = Wrapper(0);
            psiFile.classes.forEach { psiClass ->
                handleClass(editor.document, offset, psiClass)
            }

            psiFile.reformatCode()

        }

    }

    fun handleClass(document: Document, offset: Wrapper<Int>, clazz: PsiClass) {
        Env.println("psiClass=$clazz")
        clazz.children.forEach {
            Env.println("psiClass children =${it.javaClass.canonicalName}")
            when (it) {
                is PsiClass -> {
                    handleClass(document, offset, it)
                }

                is com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl -> {
                    val nextSibling = it.nextSibling
                    if (nextSibling is PsiField) {
                        Env.println("psiClass nextSibling =$nextSibling")
                        val z = PsiParserFacade.SERVICE.getInstance(clazz.project).createWhiteSpaceFromText("\n\n")
                        it.delete()
                        clazz.addBefore(z, nextSibling)
                    }
                }
            }
        }
    }


}

//#######################################################################################################

open class AddJpaColumnAction : AnAction("AddJpaColumnAction") {


    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project!!
        val editor = project.currentEditor!!
        val psiFile = event.getData(LangDataKeys.PSI_FILE)
        if (psiFile !is PsiJavaFile) {
            return
        }

        editor.document.executeCommand(project, description = "Insert Hello World") {

            val offset = Wrapper(0)
            psiFile.classes.forEach { psiClass ->
                handleClass(editor.document, offset, psiClass)
            }

            val jsonPropertyClass = JavaPsiFacade.getInstance(project)
                .findClass("com.fasterxml.jackson.annotation.JsonProperty", GlobalSearchScope.allScope(project))!!
            psiFile.commitAndUnblockDocument()
            if (psiFile.findImportReferenceTo(jsonPropertyClass) == null) {
                psiFile.importClass(jsonPropertyClass)
            }

            psiFile.reformatCode()

        }

    }

    fun handleClass(document: Document, offset: Wrapper<Int>, clazz: PsiClass) {
        Env.println("psiClass=$clazz")
        clazz.children.forEach {
            Env.println("psiClass children =$it")
            when (it) {
                is PsiClass -> {
                    handleClass(document, offset, it)
                }

                is PsiField -> {
                    handleField(document, offset, it)
                }
            }
        }
    }

    fun handleField(document: Document, offset: Wrapper<Int>, field: PsiField) {
        field.annotations.forEach {
            Env.println("$field annotations : ${it.text}")
            if (it.text.startsWith(annotationName())) {
                return
            }
        }
        Env.println("field$field off ${field.textOffset} da${field.startOffset}")
        document.insertString(
            field.startOffset + offset.get(),
            annotationValue(field).apply { offset.set(offset.get() + length) })
    }

    fun annotationName() = "@Column"


    fun annotationValue(field: PsiField) =
        "${annotationName()}(name=\"${CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field.name)}\")\n"
}


class AddJsonPropertyAction : AnAction("AddJsonProperty") {


    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project!!
        val editor = project.currentEditor!!
        val psiFile = event.getData(LangDataKeys.PSI_FILE)
        if (psiFile !is PsiJavaFile) {
            return
        }

        editor.document.executeCommand(project, description = "Insert Hello World") {

            val offset = Wrapper(0)
            psiFile.classes.forEach { psiClass ->
                handleClass(editor.document, offset, psiClass)
            }

            val jsonPropertyClass = JavaPsiFacade.getInstance(project)
                .findClass("com.fasterxml.jackson.annotation.JsonProperty", GlobalSearchScope.allScope(project))!!
            psiFile.commitAndUnblockDocument()
            if (psiFile.findImportReferenceTo(jsonPropertyClass) == null) {
                psiFile.importClass(jsonPropertyClass)
            }

            psiFile.reformatCode()

        }

    }

    fun handleClass(document: Document, offset: Wrapper<Int>, clazz: PsiClass) {
        Env.println("psiClass=$clazz")
        clazz.children.forEach {
            Env.println("psiClass children =$it")
            when (it) {
                is PsiClass -> {
                    handleClass(document, offset, it)
                }

                is PsiField -> {
                    handleField(document, offset, it)
                }
            }
        }
    }

    fun handleField(document: Document, offset: Wrapper<Int>, field: PsiField) {
        field.annotations.forEach {
            Env.println("$field annotations : ${it.text}")
            if (it.text.startsWith(annotationName())) {
                return
            }
        }
        Env.println("field$field off ${field.textOffset} da${field.startOffset}")
        document.insertString(
            field.startOffset + offset.get(),
            annotationValue(field).apply { offset.set(offset.get() + length) })
    }

    open fun annotationName() = "@JsonProperty"

    open fun annotationValue(field: PsiField) = "${annotationName()}(\"${field.name}\")\n"

}

fun PsiFile.commitAndUnblockDocument(): Boolean {
    val virtualFile = this.virtualFile ?: return false
    val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile)
        ?: return false
    val documentManager = PsiDocumentManager.getInstance(project)
    documentManager.doPostponedOperationsAndUnblockDocument(document)
    documentManager.commitDocument(document)
    return true
}

fun PsiElement.reformatCode() {
    try {
        CodeStyleManager.getInstance(project).reformat(this)
        JavaCodeStyleManager.getInstance(project).optimizeImports(this.containingFile)
    } catch (e: Exception) {
        show(e.toString())
    }
}

class Wrapper<T>(var obj: T) {

    fun get() = obj

    fun set(t: T) {
        obj = t
    }

}
