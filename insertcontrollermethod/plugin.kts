import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.suggested.startOffset
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
        this.print(
            "${DateFormat.getDateTimeInstance().format(Date())} : $str\n",
            ConsoleViewContentType.LOG_INFO_OUTPUT
        )
    }
}

if (Env.startup || !isIdeStartup) {
    Env.project = project
    IntentionManager.getInstance().apply {
        intentionActions.firstOrNull {
            Env.println("find unregister Intention${it.javaClass}")
            it.javaClass.toString().contains("InsertControllerMethod")
        }?.let {
            show("unregister Intention${it.javaClass}")
            unregisterIntention(it)
        }
        addAction(InsertControllerMethodAction())
        show("register InsertControllerMethodAction  success")
    }
}

class InsertControllerMethodAction : PsiElementBaseIntentionAction() {
    fun PsiElement.getContainingClass(): PsiClass? {
        var context = context
        while (context != null) {
            val _context = context
            if (_context is PsiClass) return _context
            if (_context is PsiMember) return _context.containingClass
            context = _context.context
        }
        return null
    }

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
        var contentLength = 0
        Env.println("psiClass$psiClass")
        Env.println("psiElement$psiElement")
        Env.println("psiElement size${psiClass.allFields.size}")
        psiClass.allFields.filter { it.name.endsWith("Service") || it.name.endsWith("service") }
            .forEach { serviceField ->
                Env.println("psiField$serviceField")
                PsiTypesUtil.getPsiClass(serviceField.type)
                    ?.methods
                    ?.forEach { method ->
                        Env.println("methods$method")
                        psiClass.methods.firstOrNull { psiMethod -> psiMethod.name == method.name }
                            ?: run {
                                Env.println("insertMethod=$method")
                                contentLength += insertMethod(psiElement, method, serviceField.name, editor, project)
                            }
                    }
            }
        // fixme not work
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = psiElement.containingFile
                psiFile.commitAndUnblockDocument()
                psiFile.reformatCodeRange(psiElement.startOffset, psiElement.startOffset + contentLength)
            }
        } catch (e: Exception) {
            Env.println("Exception$e")
        }
    }

    /**
     * Resolves generics on the given type and returns them (if any) or null if there are none
     */
    fun getResolvedGenerics(type: PsiType): Collection<PsiType> {
        if (type is PsiClassType) {
            return type.resolveGenerics().substitutor.substitutionMap.values
        }
        return emptyList()
    }

    fun insertMethod(
        psiElement: PsiElement,
        method: PsiMethod,
        serviceName: String,
        editor: Editor,
        project: Project
    ): Int {
        if (method.parameters.isEmpty()) {
            Env.println("not insert empty paramters method:$method")
            return 0
        }
        val returnType = method.returnType
        val requestType = method.parameters[0].type

        Env.println("insertMethod: returnType=$returnType ;requestType=$requestType")

        val psiFile = psiElement.containingFile as PsiJavaFile
        psiFile.importClass(
            JavaPsiFacade.getInstance(project)
                .findClass("com.onestep.pmms.model.message.PmmsResponse", GlobalSearchScope.allScope(project))!!
        )
        psiFile.importClass(
            JavaPsiFacade.getInstance(project)
                .findClass("org.springframework.web.bind.annotation.PostMapping", GlobalSearchScope.allScope(project))!!
        )
        psiFile.importClass(
            JavaPsiFacade.getInstance(project)
                .findClass("org.springframework.validation.annotation.Validated", GlobalSearchScope.allScope(project))!!
        )
        psiFile.importClass(
            JavaPsiFacade.getInstance(project)
                .findClass("org.springframework.web.bind.annotation.RequestBody", GlobalSearchScope.allScope(project))!!
        )
        psiFile.importClass(
            JavaPsiFacade.getInstance(project)
                .findClass("io.swagger.annotations.ApiOperation", GlobalSearchScope.allScope(project))!!
        )
        psiFile.commitAndUnblockDocument()
        if (requestType is PsiType) {
            try {
                PsiTypesUtil.getPsiClass(requestType)?.let {
                    Env.println("importClass $it")
                    psiFile.importClass(it)
                }
            } catch (e: Exception) {
                Env.println("Exception importClass $e")

            }
        }
        if (returnType is PsiType) {
            try {
                PsiTypesUtil.getPsiClass(returnType)?.let { psiClass ->
                    Env.println("importClass $psiClass")
                    psiFile.importClass(psiClass)
                }
                getResolvedGenerics(returnType).forEach {
                    psiFile.importClass(PsiTypesUtil.getPsiClass(it)!!)
                }
            } catch (e: Exception) {
                Env.println("Exception importClass $e")

            }
        }

        var contentLength: Int
        val presentableText = returnType?.presentableText
        val (returnName, tryMethodName) = if (presentableText == "void") Pair("Void", "tryConsumer") else Pair(
            presentableText,
            "tryFunction"
        )
        Env.println("insert content ")
        val methodContent =
            """
    @PostMapping("/${method.name}")
    @ApiOperation(value = "${method.name}")
    public PmmsResponse<$returnName> ${method.name}(@RequestBody @Validated ${(requestType as PsiType).presentableText} request) {
        return ${tryMethodName}(request, () -> $serviceName.${method.name}(request));
    }
        """.trimIndent().apply { contentLength = this.length }
        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.insertString(psiElement.startOffset, methodContent)
        }
        return contentLength
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