import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.util.gotoByName.*
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.CollectConsumer
import com.intellij.util.Processor
import com.intellij.util.SmartList
import com.intellij.util.SynchronizedCollectConsumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import liveplugin.PluginUtil
import liveplugin.show
import java.text.DateFormat
import java.util.*
import kotlin.collections.ArrayList


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

throw RuntimeException("stop this")

Env.project = project!!
PluginUtil.registerAction("symbolizeKeyWords", "ctrl alt shift 0", GoToRequestMappingAction())

show("Current project: ${project?.name}")

class GoToRequestMappingAction : GotoActionBase(), DumbAware {

    override fun gotoActionPerformed(e: AnActionEvent) {
        Env.println("aaa $this")
        val requestMappingModel = RequestMappingModel(e.project!!, Extensions.getExtensions())
        showNavigationPopup(e, requestMappingModel, GoToRequestMappingActionCallback(), null, true, false)
    }

    private class GoToRequestMappingActionCallback : GotoActionBase.GotoActionCallback<String>() {

        override fun elementChosen(popup: ChooseByNamePopup, element: Any) {
//            if (element is RequestMappingItem && element.canNavigate()) {
//                element.navigate(true)
//            }
        }
    }
}

object Extensions {

    private val extensionPoints = ExtensionPointName.create<ChooseByNameContributor>("")

    fun getExtensions(): List<ChooseByNameContributor> {
        Env.println("bb" + this)
        try {
            extensionPoints.extensionList
        } catch (e: Exception) {
            Env.println("bb " + e)

        }
        return ChooseByNameContributor.CLASS_EP_NAME.extensionList
    }
}


class RequestMappingModel(project: Project, contributors: List<ChooseByNameContributor>) : FilteringGotoByModel<FileType>(project, contributors), DumbAware {

    override fun getItemProvider(context: PsiElement?): ChooseByNameItemProvider {
        return RequestMappingItemProvider()
    }

    override fun filterValueFor(item: NavigationItem): FileType? = null

    override fun getPromptText(): String = "Enter mapping url"

    override fun getNotInMessage(): String = "No matches found"

    override fun getNotFoundMessage(): String = "Mapping not found"

    override fun getCheckBoxName(): String? = null

    override fun loadInitialCheckBoxState(): Boolean = false

    override fun saveInitialCheckBoxState(state: Boolean) = Unit

    override fun getSeparators(): Array<String> = emptyArray()

    override fun getFullName(element: Any): String? = getElementName(element)

    override fun willOpenEditor(): Boolean = false
}

open class RequestMappingItemProvider : ChooseByNameItemProvider {
    override fun filterElements(
            base: ChooseByNameBase,
            pattern: String,
            everywhere: Boolean,
            indicator: ProgressIndicator,
            consumer: Processor<Any>
    ): Boolean {
        Env.println("zz" + pattern + base.trimmedText)
        if (base.project != null) {
            base.project!!.putUserData(ChooseByNamePopup.CURRENT_SEARCH_PATTERN, pattern)
        }
        val idFilter: IdFilter? = null
        val searchScope = FindSymbolParameters.searchScopeFor(base.project!!, everywhere)
        val parameters = FindSymbolParameters(pattern, pattern, searchScope, idFilter)

        val namesList = getSortedResults(base, pattern, indicator, parameters)
        indicator.checkCanceled()
        return processByNames(base, everywhere, indicator, consumer, namesList, parameters)||true
    }

    override fun filterNames(base: ChooseByNameBase, names: Array<String>, pattern: String): List<String> {
        Env.println("filterNames" + names + base)
        return emptyList()
    }

    companion object {
        private fun getSortedResults(
                base: ChooseByNameViewModel,
                pattern: String,
                indicator: ProgressIndicator,
                parameters: FindSymbolParameters
        ): List<String> {
            if (pattern.isEmpty() && !base.canShowListForEmptyPattern()) {
                return emptyList()
            }
            val namesList: MutableList<String> = ArrayList()
            val collect: CollectConsumer<String> = SynchronizedCollectConsumer(namesList)
            val model = base.model
            if (model is ChooseByNameModelEx) {
                indicator.checkCanceled()
                model.processNames(
                        { sequence: String? ->
                            indicator.checkCanceled()
                            if (matches(sequence, pattern)) {
                                collect.consume(sequence)
                                return@processNames true
                            }
                            return@processNames false
                        },
                        parameters
                )
            }

//            namesList.sortWith(compareBy { PopupPath(it) })

            indicator.checkCanceled()
            return namesList
        }

        private fun processByNames(
                base: ChooseByNameViewModel,
                everywhere: Boolean,
                indicator: ProgressIndicator,
                consumer: Processor<Any>,
                namesList: List<String>,
                parameters: FindSymbolParameters
        ): Boolean {
            val sameNameElements: MutableList<Any> = SmartList()
            val qualifierMatchResults: MutableMap<Any, MatchResult> = ContainerUtil.newIdentityTroveMap()
            val model = base.model
            for (name in namesList) {
                indicator.checkCanceled()
                val elements = if (model is ContributorsBasedGotoByModel) model.getElementsByName(name, parameters, indicator) else model.getElementsByName(name, everywhere, parameters.completePattern)
                if (elements.size > 1) {
                    sameNameElements.clear()
                    qualifierMatchResults.clear()
                    for (element in elements) {
                        indicator.checkCanceled()
                        sameNameElements.add(element)
                    }
                    if (!ContainerUtil.process(sameNameElements, consumer)) return false
                } else if (elements.size == 1) {
                    if (!consumer.process(elements[0])) return false
                }
            }
            return true
        }

        fun matches(name: String?, pattern: String): Boolean {

            if (name == null) {
                return false
            }
            return try {
                if (pattern == "/") {
                    true
                } else if (!pattern.contains('/')) {
                    val (method, path) = name.split(" ", limit = 2)
                    path.contains(pattern) || method.contains(pattern, ignoreCase = true)
                } else {
                    true
                }
            } catch (e: Exception) {
                false // no matches appears valid result for "bad" pattern
            }
        }
    }
}