//import com.intellij.execution.ui.ConsoleView
//import com.intellij.execution.ui.ConsoleViewContentType
//import com.intellij.lang.xml.XMLLanguage
//import com.intellij.openapi.command.WriteCommandAction
//import com.intellij.openapi.extensions.ExtensionPoint
//import com.intellij.openapi.extensions.Extensions
//import com.intellij.openapi.project.Project
//import com.intellij.openapi.vcs.CheckinProjectPanel
//import com.intellij.openapi.vcs.changes.CommitContext
//import com.intellij.openapi.vcs.checkin.CheckinHandler
//import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
//import com.intellij.psi.XmlElementFactory
//import com.intellij.psi.search.FilenameIndex
//import com.intellij.psi.search.GlobalSearchScope
//import com.intellij.psi.util.PsiTreeUtil
//import com.intellij.psi.xml.XmlComment
//import com.intellij.psi.xml.XmlFile
//import com.intellij.psi.xml.XmlTag
//import com.intellij.vcs.commit.ChangesViewCommitPanel
//import liveplugin.PluginUtil
//import liveplugin.show
//import java.text.DateFormat
//import java.time.LocalDateTime
//import java.util.*
//
//object Env {
//    const val startup = true
//    val resetAction get() = debugMode
//    private const val debugMode = true
//    private var consoleView: ConsoleView? = null
//    var project: Project? = null
//    fun println(message: Any) {
//        if (!debugMode || project == null) {
//            return
//        }
//        if (consoleView == null) {
//            consoleView = PluginUtil.showInConsole(message, project!!)
//        } else {
//            consoleView?.println(message)
//        }
//    }
//
//    fun ConsoleView.println(str: Any?) {
//        this.print(
//            "${DateFormat.getDateTimeInstance().format(Date())} : $str\n",
//            ConsoleViewContentType.LOG_INFO_OUTPUT
//        )
//    }
//}
//
//if (Env.startup || !isIdeStartup) {
//    Env.project = project
//    val extensionPoint: ExtensionPoint<CheckinHandlerFactory> =
//        Extensions.getRootArea().getExtensionPoint("com.intellij.checkinHandlerFactory")
//    extensionPoint.extensionList
//        .filter {
//            Env.println(it::class.java.name)
//            it.javaClass.name == VersionCheckFactory::class.java.name
//        }
//        .forEach {
//            Env.println("unregisterExtension" + it::class.java.name)
//            extensionPoint.unregisterExtension(it)
//        }
//
//    extensionPoint.registerExtension(VersionCheckFactory())
//    show("register VersionCheck success")
//}
//
//
//
//
//class VersionCheckHandler(private val checkinProjectPanel: CheckinProjectPanel) : CheckinHandler() {
//
//    override fun beforeCheckin(): ReturnResult {
//        val project = checkinProjectPanel.project
//        if (!(project.name.contains("pmms") && !(project.name.contains("work-flow")))) {
//            return ReturnResult.COMMIT
//        }
//
//        Env.println("before check in")
//        val updateApi = checkinProjectPanel.selectedChanges.any {
//            Env.println(it.virtualFile!!.path)
//            it.virtualFile!!.path.contains("controller")
//        }
//        Env.println("controller changed = $updateApi")
//        val result = updateVersion(updateApi, project)
//        show("before check in result = $result")
//        return result
//    }
//}
//
//class VersionCheckFactory : CheckinHandlerFactory() {
//    override fun createHandler(
//        checkinProjectPanel: CheckinProjectPanel,
//        commitContext: CommitContext
//    ): CheckinHandler {
//        return VersionCheckHandler(checkinProjectPanel)
//    }
//}
//
//
//fun updateVersion(updateApi: Boolean, project: Project): CheckinHandler.ReturnResult {
//    try {
//        val pomFiles = FilenameIndex.getFilesByName(
//            project,
//            "pom.xml",
//            GlobalSearchScope.projectScope(project)
//        )
//        if (pomFiles.size == 0) {
//            return CheckinHandler.ReturnResult.COMMIT
//        }
//        val pomFile = pomFiles[0]
//        val xmlFile = pomFile as XmlFile
//        var date = LocalDateTime.now()
//        Env.println("date date.hour ${date.hour}")
//        if (date.hour >= 18) {
//            date = date.plusDays(1)
//        }
//        val todayData = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
//        Env.println("todayData$todayData")
//
//        val document = xmlFile.document!!
//        val rootTag = document.rootTag!!
//        val versionTag = rootTag.findFirstSubTag("version")!!
//        val versionList = versionTag.value.text.split(".").toMutableList()
//        Env.println("${versionTag.value} + ${versionTag.name}")
//
//        val versionTagData = versionTag.nextSiblingInTag!!
//        Env.println("$versionTagData " + versionTagData.text.toString())
//
//        if (versionTagData is XmlComment) {
//            Env.println("${versionTagData::class.java.name} ${versionTagData.commentText}")
//            val updateData = versionTagData.commentText.trim() != todayData
//
//            // 升级 api
//            if (updateApi) {
//                //隔天的提交，修改中版本
//                if (updateData) {
//                    versionList[1] = (versionList[1].toInt() + 1).toString()
//                    versionList[2] = "0"
//                } else {
//                    if (versionList[2] == "0") {
//                        //同一天的提交，检查小版本是 0 ，说明中版本已经提升过
//                        return CheckinHandler.ReturnResult.COMMIT
//                    } else {
//                        //同一天的提交，检查小版本非 0 ，说明中版本未提升过
//                        versionList[1] = (versionList[1].toInt() + 1).toString()
//                        versionList[2] = "0"
//                    }
//                }
//            } else {
//                if (updateData) {
//                    versionList[2] = (versionList[2].toInt() + 1).toString()
//                } else {
//                    return CheckinHandler.ReturnResult.COMMIT
//                }
//            }
//
//
//            if (updateApi || updateData) {
//                Env.println("start replace version tag ${versionTagData.commentText} != $todayData ${versionTagData.commentText == todayData}")
//                //更新 version
//                WriteCommandAction.runWriteCommandAction(project) {
//                    val psiFile = rootTag.containingFile
//                    psiFile.commitAndUnblockDocument()
//                    versionTag.replace(
//                        rootTag.createChildTag(
//                            "version",
//                            null,
//                            versionList.joinToString("."),
//                            true
//                        )
//                    )
//                    versionTagData.replace(createComment(project, todayData))
//                }
//                show(" update version to ${versionList.joinToString(".")}")
//                return CheckinHandler.ReturnResult.CANCEL
//            }
//
//        } else {
//            //没加过注释，视为升级
//            if (updateApi) {
//                versionList[1] = (versionList[1].toInt() + 1).toString()
//                versionList[2] = "0"
//            } else {
//                versionList[2] = (versionList[2].toInt() + 1).toString()
//                Env.println(versionList)
//            }
//            Env.println("start insert version data")
//            WriteCommandAction.runWriteCommandAction(project) {
//                val psiFile = rootTag.containingFile
//                psiFile.commitAndUnblockDocument()
//                versionTag.addAfter(createComment(project, todayData), versionTag)
//                versionTag.replace(
//                    rootTag.createChildTag(
//                        "version",
//                        null,
//                        versionList.joinToString("."),
//                        true
//                    )
//                )
//            }
//            show(" update version to ${versionList.joinToString(".")}")
//            return CheckinHandler.ReturnResult.CANCEL
//        }
//
//        return CheckinHandler.ReturnResult.COMMIT
//    } catch (e: Exception) {
//        Env.println(e.stackTraceToString())
//        return CheckinHandler.ReturnResult.CANCEL
//    }
//}
//
//
//fun createComment(project: Project, s: String): XmlComment {
//    val element: XmlTag =
//        XmlElementFactory.getInstance(project)
//            .createTagFromText("<foo><!-- $s --></foo>", XMLLanguage.INSTANCE)
//    return PsiTreeUtil.getChildOfType(element, XmlComment::class.java)!!
//}
//
//fun com.intellij.psi.PsiFile.commitAndUnblockDocument(): Boolean {
//    val virtualFile = this.virtualFile ?: return false
//    val document =
//        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile)
//            ?: return false
//    val documentManager = com.intellij.psi.PsiDocumentManager.getInstance(project)
//    documentManager.doPostponedOperationsAndUnblockDocument(document)
//    documentManager.commitDocument(document)
//    return true
//}
