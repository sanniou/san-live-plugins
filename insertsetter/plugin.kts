import Plugin.ValueProvider
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTypesUtil
import liveplugin.PluginUtil
import liveplugin.show
import java.text.DateFormat
import java.util.*

// depends-on-plugin com.intellij.java
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

    val actionsClasses =
            arrayOf(GenerateSetAction::class.java,
                    GenerateSetWithParamAction::class.java,
                    GenerateBuilderAction::class.java,
                    GenerateSetWithDefaultAction::class.java,
                    GenerateReturnSetAction::class.java,
                    GenerateReturnBuilderAction::class.java)

    IntentionManager.getInstance().apply {
        intentionActions.forEach {
            Env.println("intentionActions${it.javaClass}")
            actionsClasses.find { clazz ->
                it.javaClass.name == clazz.name
            }?.run {
                Env.println("unregisterIntention${it.javaClass}")
                unregisterIntention(it)
            }
        }

        actionsClasses.forEach {
            Env.println("add action ${it.simpleName}")
            addAction(it.newInstance())
        }
    }
    show("register GenerateSetAction success")
}

//#######################################################################################################
class GenerateReturnSetAction : BaseGenerateSetAction() {

    override fun getText() = "GenerateReturnWithSet"

    override fun isAvailable(project1: Project, editor: Editor?, psiElement: PsiElement): Boolean {
        return psiElement.getContainingMethod().run {
            Env.println("$this +  isAvailable ${this?.returnType?.presentableText?.toLowerCase()}")
            this?.returnType?.presentableText?.toLowerCase()?.equals("void")?.not() ?: false
        }
    }

    override fun invoke(project: Project, editor: Editor?, psiElement: PsiElement) {
        editor ?: return
        val psiMethod = psiElement.getContainingMethod()!!
        val psiType: PsiType = psiMethod.returnType!!
        val parameterName = JavaCodeStyleManager.getInstance(project).suggestCompiledParameterName(psiType)!!
        insertSetter(psiType, editor, project, psiElement, parameterName)
    }

    override fun beforeSetter(parameterName: String, offset: Int, editor: Editor, psiType: PsiType): String {
        return parameterName.run {
            val constructor = "${psiType.presentableText} $parameterName = new ${psiType.presentableText}();"
            editor.document.insertString(offset, constructor)
            constructor
        }
    }

}


open class GenerateSetAction : BaseGenerateSetAction() {
    override fun getText() = "GenerateAllSet"

    override fun isAvailable(project1: Project, editor: Editor?, psiElement: PsiElement): Boolean {
        Env.println("$this + isAvailable:${psiElement.context}")
        return when (psiElement.context) {
            is PsiVariable -> {
                true
            }
            is PsiReferenceExpression -> {
                true
            }
            else -> {
                false
            }
        }
    }


    override fun invoke(project: Project, editor: Editor?, psiElement: PsiElement) {
        Env.println("invoke")
        editor ?: return
        val psiType = when (val context = psiElement.context) {
            is PsiVariable -> {
                context.type
            }
            is PsiReferenceExpression -> {
                context.type!!
            }
            else -> {
                show("error with offsetElement $psiElement context $context")
                return
            }
        }

        insertSetter(psiType, editor, project, psiElement)
    }
}

open class GenerateBuilderAction : BaseGenerateSetAction() {
    override fun getText() = "GenerateAllBuilder"

    override fun isAvailable(project1: Project, editor: Editor?, psiElement: PsiElement): Boolean {
        val context = psiElement.context
        Env.println("$this+isAvailable:$context")
        return when (context) {
            is PsiReferenceExpression -> {
                context.text.endsWith("builder")
            }
            else -> {
                false
            }
        }
    }


    override fun invoke(project: Project, editor: Editor?, psiElement: PsiElement) {
        Env.println("$this+invoke")
        editor ?: return
        val psiType = when (val context = psiElement.context) {
            is PsiReferenceExpression -> {
                PsiTypesUtil.getClassType(PsiTypesUtil.getPsiClass(context.type)!!.containingClass!!)
            }
            else -> {
                show("error with offsetElement $psiElement context $context")
                return
            }
        }

        insertSetter(psiType, editor, project, psiElement)
    }

    override fun getSetterMethodStr(elementName: String, method: PsiMethod) = "\n.${method.name.substring(3).decapitalize()}(null)"
}

class GenerateSetWithDefaultAction : GenerateSetAction() {
    override fun getText() = "GenerateAllSetWithDefault"

    override fun getSetterMethodStr(elementName: String, method: PsiMethod): String {
        val parameterName = method.parameters[0].name!!
        val parameterType = (method.parameters[0].type as PsiClassReferenceType).className

        val provider = DefaultTemplate.typeValueProvider.getOrElse(parameterType) { null }



        provider?.forEach { (t, u) ->
            if (parameterName.contains(t)) {
                return "\n$elementName.${method.name}(${u()});"
            }
        }

        return "\n$elementName.${method.name}(null);"

    }
}

class GenerateSetWithParamAction : GenerateSetAction() {

    private var currentParameters = emptyArray<PsiParameter>()

    private val methodMap = mutableMapOf<String, String>()
    override fun getText() = "GenerateSetWithParam"

    override fun isAvailable(project1: Project, editor: Editor?, psiElement: PsiElement): Boolean {
        methodMap.clear()
        val psiMethod = psiElement.getContainingMethod()
        currentParameters = psiMethod?.parameterList?.parameters ?: emptyArray()
        currentParameters.forEach { parameter ->
            PsiTypesUtil.getPsiClass(parameter.type)!!.allMethods
                    .filter { it.name.startsWith("get") }
                    .forEach {
                        methodMap[it.name.substring(3)] = parameter.name
                    }
        }
        return currentParameters.isNotEmpty() && super.isAvailable(project1, editor, psiElement)
    }

    override fun getSetterMethodStr(elementName: String, method: PsiMethod): String {
        val fieldName = method.name.substring(3)
        if (methodMap.contains(fieldName)) {
            return "\n$elementName.set${fieldName}(${methodMap[fieldName]}.get${fieldName}());"
        }
        return "\n$elementName.set${fieldName}(null);"

    }
}

class GenerateReturnBuilderAction : BaseGenerateSetAction() {


    override fun getText() = "GenerateReturnBuilder"

    override fun isAvailable(project: Project, editor: Editor?, psiElement: PsiElement): Boolean {

        return psiElement.getContainingMethod()
                ?.returnType
                ?.let { type ->

                    Env.println("$this + isAvailable ${type.presentableText.toLowerCase()}")
                    (type.presentableText.toLowerCase() == "void").not()
                            .and(PsiTypesUtil.getPsiClass(type)
                                    ?.children
                                    ?.filterIsInstance<PsiModifierList>()
                                    ?.flatMap { it.annotations.toList() }
                                    ?.firstOrNull { it.qualifiedName == "lombok.Builder" }
                                    != null)
                } ?: false
    }

    override fun invoke(project: Project, editor: Editor?, psiElement: PsiElement) {
        editor ?: return
        val psiMethod = psiElement.getContainingMethod()!!
        val psiType: PsiType = psiMethod.returnType!!
        Env.println("p22siType:$psiType")
        val parameterName = JavaCodeStyleManager.getInstance(project).suggestCompiledParameterName(psiType)!!
        insertSetter(psiType, editor, project, psiElement, parameterName)
    }

    override fun getSetterMethodStr(elementName: String, method: PsiMethod) = "\n.${method.name.substring(3).decapitalize()}()"


    override fun beforeSetter(parameterName: String, offset: Int, editor: Editor, psiType: PsiType): String {
        psiType as PsiClassType
        return parameterName.run {

            val sb = StringBuilder()

            psiType.resolveGenerics().substitutor.substitutionMap.run {
                if (isNotEmpty()) {
                    sb.append("<")
                    forEach { (_, v) ->
                        sb.append(v.presentableText)
                    }
                    sb.append(">")
                }
            }
            val constructor = "${psiType.className}.${sb}builder()"
            editor.document.insertString(offset, constructor)
            constructor
        }
    }

    override fun afterSetter(parameterName: String, offset: Int, editor: Editor, psiType: PsiType): String {
        return parameterName.run {
            val constructor = "\n.build();"
            editor.document.insertString(offset, constructor)
            constructor
        }
    }
}

abstract class BaseGenerateSetAction : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String {
        return "GenerateSetFamily"
    }


    protected fun insertSetter(psiType: PsiType, editor: Editor, project: Project, psiElement: PsiElement, parameterName: String? = null) {

        val elementName = parameterName ?: psiElement.text!!
        val psiFile = psiElement.containingFile

        PsiTypesUtil.getPsiClass(psiType)?.let { offsetClassElement ->
            WriteCommandAction.runWriteCommandAction(project) {
                Env.println("$parameterName")
                val currentLine = editor.caretModel.logicalPosition.line
                val lineEndOffset = editor.document.getLineEndOffset(currentLine)
                val beforeCode = beforeSetter(elementName, lineEndOffset, editor, psiType)

                val fold = offsetClassElement.allMethods
                        .filter { it.name.startsWith("set") }
                        .fold(lineEndOffset + beforeCode.length) { offset, method ->
                            val str = getSetterMethodStr(elementName, method)
                            editor.document.insertString(offset, str)
                            offset + str.length
                        }

                val afterCode = afterSetter(elementName, fold, editor, psiType)

                editor.caretModel.moveCaretRelatively(100, 1, false, false, true)

                psiFile.commitAndUnblockDocument()
                psiFile.reformatCodeRange(lineEndOffset, fold + afterCode.length)
            }
        }
    }


    protected open fun beforeSetter(parameterName: String, offset: Int, editor: Editor, psiType: PsiType): String {
        return ""
    }

    protected open fun afterSetter(parameterName: String, offset: Int, editor: Editor, psiType: PsiType): String {
        return ""
    }

    protected open fun getSetterMethodStr(elementName: String, method: PsiMethod) =
            "\n$elementName.${method.name}();"


    fun PsiFile.reformatCode() {
        try {
            CodeStyleManager.getInstance(project).reformat(this)
        } catch (e: Exception) {
            show(e.toString())
        }
    }

    fun PsiFile.reformatCodeRange(rangeStart: Int, rangeEnd: Int) {
        try {
            CodeStyleManager.getInstance(project).reformatRange(this, rangeStart, rangeEnd)
        } catch (e: Exception) {
            show(e.toString())
        }
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

    fun PsiElement.getContainingMethod(): PsiMethod? {
        var context = context
        while (context != null) {
            val _context = context
            if (_context is PsiMethod) return _context
            context = _context.context
        }
        return null
    }

}


typealias ValueProvider = () -> String

object DefaultTemplate {

    val typeValueProvider = mapOf<String, Map<String, ValueProvider>>(
            "String" to mapOf("Id" to { "\"${UUID.randomUUID()}\"" },
                    "Name" to { "\"UserName\"" },
                    "Code" to { "\"KLSCODE001\"" },
                    "Phone" to { "\"13901010203\"" }
            ),
            "Long" to mapOf("time" to { "${System.currentTimeMillis()}L" },
                    "Time" to { "${System.currentTimeMillis()}L" },
                    "date" to { "${System.currentTimeMillis()}L" },
                    "Date" to { "${System.currentTimeMillis()}L" })

    )

}
