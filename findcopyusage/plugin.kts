//import com.intellij.execution.ui.ConsoleView
//import com.intellij.execution.ui.ConsoleViewContentType
//import com.intellij.find.actions.FindUsagesAction
//import com.intellij.find.findUsages.*
//import com.intellij.openapi.actionSystem.AnAction
//import com.intellij.openapi.actionSystem.AnActionEvent
//import com.intellij.openapi.actionSystem.LangDataKeys
//import com.intellij.openapi.project.Project
//import com.intellij.psi.PsiExpressionList
//import com.intellij.psi.PsiField
//import com.intellij.psi.PsiJavaFile
//import com.intellij.psi.PsiReferenceContributor
//import com.intellij.psi.search.GlobalSearchScope
//import com.intellij.psi.search.PsiReferenceProcessor
//import com.intellij.psi.search.PsiShortNamesCache
//import com.intellij.psi.search.searches.ReferencesSearch
//import com.intellij.refactoring.inline.InlineMethodDialog
//import com.intellij.refactoring.psi.MethodInheritanceUtils
//import com.intellij.usages.UsageViewManager
//import com.intellij.usages.UsageViewPresentation
//import com.intellij.usages.impl.UsageViewFactory
//import com.intellij.util.containers.toArray
//import liveplugin.PluginUtil
//import liveplugin.currentEditor
//import liveplugin.registerAction
//import liveplugin.show
//import org.jetbrains.kotlin.idea.codeInsight.RestoreReferencesDialog
//import org.jetbrains.kotlin.idea.core.util.getLineNumber
//import org.jetbrains.kotlin.idea.findUsages.ReferencesSearchScopeHelper
//import org.jetbrains.kotlin.idea.refactoring.showWithTransaction
//import java.text.DateFormat
//import java.util.*
//
//// depends-on-plugin com.intellij.java
//
//object Env {
//
//    const val startup = false
//
//    val resetAction get() = debugMode
//
//    const val debugMode = false
//
//    var consoleView: ConsoleView? = null
//
//    var project: Project? = null
//
//    fun println(message: Any) {
//        if (!debugMode || project == null//import com.intellij.execution.ui.ConsoleView
//import com.intellij.execution.ui.ConsoleViewContentType
//import com.intellij.find.actions.FindUsagesAction
//import com.intellij.find.findUsages.*
//import com.intellij.openapi.actionSystem.AnAction
//import com.intellij.openapi.actionSystem.AnActionEvent
//import com.intellij.openapi.actionSystem.LangDataKeys
//import com.intellij.openapi.project.Project
//import com.intellij.psi.PsiExpressionList
//import com.intellij.psi.PsiField
//import com.intellij.psi.PsiJavaFile
//import com.intellij.psi.PsiReferenceContributor
//import com.intellij.psi.search.GlobalSearchScope
//import com.intellij.psi.search.PsiReferenceProcessor
//import com.intellij.psi.search.PsiShortNamesCache
//import com.intellij.psi.search.searches.ReferencesSearch
//import com.intellij.refactoring.inline.InlineMethodDialog
//import com.intellij.refactoring.psi.MethodInheritanceUtils
//import com.intellij.usages.UsageViewManager
//import com.intellij.usages.UsageViewPresentation
//import com.intellij.usages.impl.UsageViewFactory
//import com.intellij.util.containers.toArray
//import liveplugin.PluginUtil
//import liveplugin.currentEditor
//import liveplugin.registerAction
//import liveplugin.show
//import org.jetbrains.kotlin.idea.codeInsight.RestoreReferencesDialog
//import org.jetbrains.kotlin.idea.core.util.getLineNumber
//import org.jetbrains.kotlin.idea.findUsages.ReferencesSearchScopeHelper
//import org.jetbrains.kotlin.idea.refactoring.showWithTransaction
//import java.text.DateFormat
//import java.util.*
//
//// depends-on-plugin com.intellij.java
//
//object Env {
//
//    const val startup = false
//
//    val resetAction get() = debugMode
//
//    const val debugMode = false
//
//    var consoleView: ConsoleView? = null
//
//    var project: Project? = null
//
//    fun println(message: Any) {
//        if (!debugMode || project == null) {
//            return
//        }
//        if (consoleView == null) {
//            consoleView = PluginUtil.showInConsole("${DateFormat.getDateTimeInstance().format(Date())} : $message \n", project!!)
//        } else {
//            consoleView?.println(message)
//        }
//    }
//
//    fun ConsoleView.println(str: Any?) {
//        this.print("${DateFormat.getDateTimeInstance().format(Date())} : $str \n", ConsoleViewContentType.NORMAL_OUTPUT)
//    }
//}
//
////#######################################################################################################
//if (Env.startup || !isIdeStartup) {
//    Env.project = project
//
//    registerAction(id = "cpm.san.code.FindCopyUsage", keyStroke = "alt shift Z", action = FindCopyUsage())
//
//    show("register FindCopyUsage success")
//}
//
//
////#######################################################################################################
//
//
//class FindCopyUsage : AnAction("FindCopyUsage") {
//
//    override fun actionPerformed(event: AnActionEvent) {
//        val project = event.project!!
//        val editor = project.currentEditor!!
//        val psiFile = event.getData(LangDataKeys.PSI_FILE) as PsiJavaFile
//        val psiElement = event.getData(LangDataKeys.PSI_ELEMENT)!!
//        Env.println("psiFile$psiFile")
//        Env.println("psiElement $psiElement")
//        if (psiElement !is PsiField) {
//            return
//        }
//        val containingClass = psiElement.containingFile as PsiJavaFile
//        Env.println("containingClass $containingClass")
//        val me = PsiShortNamesCache.getInstance(project).getMethodsByName("copyProperties", GlobalSearchScope.allScope(project))[0]
//
//        ReferencesSearch.search(me, me.useScope)
//                .filter {
//                    val expressionList = it.element.nextSibling as PsiExpressionList
//                    expressionList.expressionTypes
//                            .find { psiType ->
//                                "${psiType.canonicalText}.java" == "${containingClass.packageName}.${containingClass.name}"
//                            }
//                            .run {
//                                this != null
//                            }
//                }
//                .forEach { psiReference ->
//                    Env.println("${psiReference.element.containingFile}  \n+ " +
//                            "${psiReference.element.textRange}  \n+ " +
//                            "${psiReference.element.getLineNumber()}  \n+ "
//                    )
//                }
//
//
//    }
//
//}) {
//            return
//        }
//        if (consoleView == null) {
//            consoleView = PluginUtil.showInConsole("${DateFormat.getDateTimeInstance().format(Date())} : $message \n", project!!)
//        } else {
//            consoleView?.println(message)
//        }
//    }
//
//    fun ConsoleView.println(str: Any?) {
//        this.print("${DateFormat.getDateTimeInstance().format(Date())} : $str \n", ConsoleViewContentType.NORMAL_OUTPUT)
//    }
//}
//
////#######################################################################################################
//if (Env.startup || !isIdeStartup) {
//    Env.project = project
//
//    registerAction(id = "cpm.san.code.FindCopyUsage", keyStroke = "alt shift Z", action = FindCopyUsage())
//
//    show("register FindCopyUsage success")
//}
//
//
////#######################################################################################################
//
//
//class FindCopyUsage : AnAction("FindCopyUsage") {
//
//    override fun actionPerformed(event: AnActionEvent) {
//        val project = event.project!!
//        val editor = project.currentEditor!!
//        val psiFile = event.getData(LangDataKeys.PSI_FILE) as PsiJavaFile
//        val psiElement = event.getData(LangDataKeys.PSI_ELEMENT)!!
//        Env.println("psiFile$psiFile")
//        Env.println("psiElement $psiElement")
//        if (psiElement !is PsiField) {
//            return
//        }
//        val containingClass = psiElement.containingFile as PsiJavaFile
//        Env.println("containingClass $containingClass")
//        val me = PsiShortNamesCache.getInstance(project).getMethodsByName("copyProperties", GlobalSearchScope.allScope(project))[0]
//
//        ReferencesSearch.search(me, me.useScope)
//                .filter {
//                    val expressionList = it.element.nextSibling as PsiExpressionList
//                    expressionList.expressionTypes
//                            .find { psiType ->
//                                "${psiType.canonicalText}.java" == "${containingClass.packageName}.${containingClass.name}"
//                            }
//                            .run {
//                                this != null
//                            }
//                }
//                .forEach { psiReference ->
//                    Env.println("${psiReference.element.containingFile}  \n+ " +
//                            "${psiReference.element.textRange}  \n+ " +
//                            "${psiReference.element.getLineNumber()}  \n+ "
//                    )
//                }
//
//
//    }
//
//}