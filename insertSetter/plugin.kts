import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTypesUtil
import liveplugin.PluginUtil
import liveplugin.runWriteAction
import liveplugin.show
import org.jetbrains.kotlin.idea.formatter.commitAndUnblockDocument
import org.jetbrains.kotlin.j2k.getContainingMethod
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
        intentionActions.forEach {
            Env.println("intentionActions${it.javaClass}")
            if (it.javaClass.toString().contains("GenerateSetAction")
                    || it.javaClass.toString().contains("GenerateReturnSetAction")
                    || it.javaClass.toString().contains("GenerateReturnBuilderAction")
            ) {
                Env.println("unregisterIntention${it.javaClass}")
                unregisterIntention(it)
            }
        }
        Env.println("add GenerateSetAction")
        addAction(GenerateSetAction())
        Env.println("add GenerateReturnSetAction")
        addAction(GenerateReturnSetAction())
        Env.println("add GenerateReturnBuilderAction")
        addAction(GenerateReturnBuilderAction())
    }
    show("register GenerateSetAction success")
}


//#######################################################################################################
class GenerateReturnSetAction : BaseGenerateSetAction() {
    override fun getText() = "GenerateReturnWithSet"

    override fun isAvailable(project1: Project, editor: Editor?, psiElement: PsiElement): Boolean {
        return psiElement.getContainingMethod().run {
            Env.println("isAvailable@${this?.returnType?.presentableText?.toLowerCase()}")
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

class GenerateReturnBuilderAction : BaseGenerateSetAction() {
    override fun getText() = "GenerateReturnBuilder"

    override fun isAvailable(project1: Project, editor: Editor?, psiElement: PsiElement): Boolean {
        return psiElement.getContainingMethod().run {
            Env.println("isAvailable@${this?.returnType?.presentableText?.toLowerCase()}")
            this?.returnType?.presentableText?.toLowerCase()?.equals("void")?.not() ?: false
        }
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

class GenerateSetAction : BaseGenerateSetAction() {
    override fun getText() = "GenerateAllSet"

    override fun isAvailable(project1: Project, editor: Editor?, psiElement: PsiElement): Boolean {
        Env.println("isAvailable:${psiElement.context}")
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
        val context = psiElement.context
        val psiType: PsiType
        when (context) {
            is PsiVariable -> {
                psiType = context.type
            }
            is PsiReferenceExpression -> {
                psiType = context.type!!
            }
            else -> {
                show("error with offsetElement $psiElement context $context")
                return
            }
        }

        insertSetter(psiType, editor, project, psiElement)
    }
}


abstract class BaseGenerateSetAction : PsiElementBaseIntentionAction() {

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

    override fun checkFile(file: PsiFile?): Boolean {
        //Env.println("checkFile${super.checkFile(file)}")
        return super.checkFile(file)
    }

    override fun getFamilyName(): String {
        return "GenerateSetFamily"
    }


    protected fun insertSetter(psiType: PsiType, editor: Editor, project: Project, psiElement: PsiElement, parameterName: String? = null) {

        val elementName = parameterName ?: psiElement.text!!
        val psiFile = psiElement.containingFile

        PsiTypesUtil.getPsiClass(psiType)?.let { offsetClassElement ->
            editor.document.runWriteAction(project, description = "Insert All Set", callback = {
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
            })
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

}
