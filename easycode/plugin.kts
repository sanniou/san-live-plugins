import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.fileTemplates.impl.UrlUtil
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.util.PackageUtil
import com.intellij.ide.util.TreeFileChooser
import com.intellij.ide.util.TreeFileChooserFactory
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.selected
import liveplugin.PluginUtil
import liveplugin.show
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.DateFormat
import java.util.*
import java.util.function.Consumer
import java.util.regex.Pattern
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextPane


// depends-on-plugin com.intellij.java
object Env {
    const val startup = true
    const val resetAction = true
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
    val actionGroupId = "GenerateGroup"
    val actionId = "com.saniou.easy.code.action.EasyCodeAction"

    val actionManager: ActionManager = ActionManager.getInstance()

    val actionGroup = actionManager.getAction(actionGroupId) as DefaultActionGroup

    val alreadyRegistered = (actionManager.getAction(actionId) != null)
    if (alreadyRegistered) {
        Env.println("remove $actionId")
        actionGroup.remove(actionManager.getAction(actionId))
        actionManager.unregisterAction(actionId)
    }

    val action = EasyCodeAction()
    actionManager.registerAction(actionId, action)
    actionGroup.addAction(action, Constraints.FIRST)
    action.templatePresentation.setText("EasyCodeAction", false)
    show("register EasyCodeAction success")
}

class EasyCodeAction : AnAction("EasyCodeAction") {


    override fun actionPerformed(event: AnActionEvent) {
        val psiFile = event.getData(LangDataKeys.PSI_FILE)
        Env.println(event.getData(LangDataKeys.PSI_ELEMENT) ?: "PSI_ELEMENT")
        Env.println(event.getData(LangDataKeys.SYMBOLS) ?: "PSI_ELEMENT")
        Env.println(event.getData(LangDataKeys.NAVIGATABLE) ?: "NAVIGATABLE")
        Env.println(event.getData(LangDataKeys.LANGUAGE) ?: "LANGUAGE")
        Env.println(event.getData(LangDataKeys.HOST_EDITOR) ?: "HOST_EDITOR")
        Env.println(event.getData(LangDataKeys.CARET) ?: "CARET")
        Env.println(event.getData(LangDataKeys.CARET)!!.selectedText ?: "dssssss")
        Env.println(event.getData(LangDataKeys.EDITOR) ?: "CARET")

        val element = PsiManager.getInstance(event.project!!)
            .findFile(event.getData(LangDataKeys.VIRTUAL_FILE)!!)!!
            .findElementAt(event.getData(LangDataKeys.CARET)!!.offset)
        Env.println("cursor test =$element")

        if (psiFile is PsiJavaFile) {
            generateCode(null, psiFile);
        }
    }

}


fun selectFile(): PsiJavaFile? {
    val instance = TreeFileChooserFactory.getInstance(project)
    val fileFilter = TreeFileChooser.PsiFileFilter { file ->
        file.name.endsWith(".java")
    }
    val javaFileChooser = instance.createFileChooser("java文件选择器", null, JavaFileType.INSTANCE, fileFilter)
    javaFileChooser.showDialog()
    return (javaFileChooser.selectedFile ?: return null) as PsiJavaFile
}

fun generateCode(tempFile: File? = null, currentFile: PsiJavaFile? = null) {
    Env.println("generateCode start")
    val actuallyFile = currentFile ?: selectFile()
    actuallyFile ?: return

    Env.println("generateCode actuallyFile $actuallyFile")
    val psiClass = PsiTreeUtil.findChildOfAnyType(
        actuallyFile.originalElement,
        PsiClass::class.java
    )!!

    Env.println("generateCode psiClass $psiClass")
    try {
        if (tempFile != null) {
            createFile(psiClass, actuallyFile, tempFile)
        } else {
            showSelected(psiClass, actuallyFile)
        }
    } catch (e: Throwable) {
        Env.println("generateCode Exception $e")
        show("generateCode Exception $e")
    }
}


fun showCode(project: Project, psiFile: PsiFile) {

    // 创建编辑框
    val editorFactory: EditorFactory = EditorFactory.getInstance()
    val fileName = psiFile.name
    psiFile.reformatCode()
    val document: Document = PsiDocumentManager.getInstance(project)
        .getDocument(psiFile)!!

    val editor: Editor = editorFactory
        .createEditor(document, project, psiFile.fileType, true)
    // 配置编辑框
    val editorSettings: EditorSettings = editor.settings
    // 关闭虚拟空间
    editorSettings.isVirtualSpace = false
    // 关闭标记位置（断点位置）
    editorSettings.isLineMarkerAreaShown = false
    // 关闭缩减指南
    editorSettings.isIndentGuidesShown = false
    // 显示行号
    editorSettings.isLineNumbersShown = true
    // 支持代码折叠
    editorSettings.isFoldingOutlineShown = true
    // 附加行，附加列（提高视野）
    editorSettings.additionalColumnsCount = 3
    editorSettings.additionalLinesCount = 3
    // 不显示换行符号
    editorSettings.isCaretRowShown = false
    (editor as EditorEx).highlighter = EditorHighlighterFactory.getInstance()
        .createEditorHighlighter(project, LightVirtualFile(fileName))
    val dialogBuilder = DialogBuilder(project)
    dialogBuilder.setTitle(fileName)
    val component: JComponent = editor.getComponent()
    component.preferredSize = Dimension(800, 600)
    dialogBuilder.setCenterPanel(component)
    dialogBuilder.addCloseButton()
    dialogBuilder.addDisposable {
        editorFactory.releaseEditor(editor)
        dialogBuilder.dispose()
    }
    dialogBuilder.show()
}

fun getDefaultParam(): MutableMap<String, Any> {
    return mutableMapOf()
}


object VelocityUtils {
    /**
     * velocity配置
     */
    val INIT_PROP: Properties = Properties()


    fun generate(template: String, map: Map<String, Any>): String {
        // 每次创建一个新实例，防止velocity缓存宏定义
        val velocityEngine = VelocityEngine(INIT_PROP)
        // 创建上下文对象
        val velocityContext = VelocityContext()
        map.forEach {
            velocityContext.put(it.key, it.value)
        }
        val stringWriter = StringWriter()
        try {
            // 生成代码
            velocityEngine
                .evaluate(velocityContext, stringWriter, "Velocity Code Generate", template)
        } catch (e: Exception) {
            // 将异常全部捕获，直接返回，用于写入模板
            val builder = StringBuilder("在生成代码时，模板发生了如下语法错误：\n")
            val writer = StringWriter()
            show("Exception$builder\n${e.stackTraceToString()}")
            Env.println("Exception$builder\n${e.stackTraceToString()}")
            e.printStackTrace(PrintWriter(writer))
            builder.append(writer.toString())
            return builder.toString().replace("\r", "")
        }
        // 返回结果
        return stringWriter.toString()
    }

    init {
        // 设置初始化配置
        // 修复部分用户的velocity日志记录无权访问velocity.log文件问题
        INIT_PROP.setProperty(
            RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
            "org.apache.velocity.runtime.log.Log4JLogChute"
        )
        INIT_PROP.setProperty("runtime.log.logsystem.log4j.logger", "velocity")
    }

}


object FileUtil {
    fun loadFile(fileName: String): File {
        return File(this.javaClass.getResource("").file.replace("live-plugins-compiled", "live-plugins") + fileName)
    }

    fun loadFile(file: File): String {
        try {
            return UrlUtil.loadText(file.toURI().toURL()).replace("\r", "")
        } catch (e: Exception) {
            Env.println("loadFile IOException : $e")
        }
        return ""
    }

    fun listFile(): List<String> {
        return File(this.javaClass.getResource("").file.replace("live-plugins-compiled", "live-plugins"))
            .listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name } ?: emptyList()
    }

    fun listDirectory(): List<File> {
        return File(File(this.javaClass.getResource("").file).parentFile.parent + "/live-plugins" + "/easycode")
            .apply {
                Env.println("listDirectory : $this")
            }
            .listFiles()
            ?.apply {
                Env.println("listDirectory result : ${this.joinToString { it.toString() }}")
            }
            ?.filter { it.isDirectory } ?: emptyList()
    }
}


object NameUtils {

    /**
     * 转驼峰命名正则匹配规则
     */
    val TO_HUMP_PATTERN = Pattern.compile("[-_]([a-z0-9])")
    val TO_LINE_PATTERN = Pattern.compile("[A-Z]+")

    fun capitalize(name: String) = name.capitalize()

    fun decapitalize(name: String) = name.decapitalize()

    fun uppercase(name: String) = name.uppercase()

    fun lowercase(name: String) = name.lowercase()

    fun packageName(name: String) = name.lowercase().replace("relation", "")

    fun controllerApiName(name: String) = name.decapitalize().replace("Controller", "").replace("Relation", "")

    fun match(name: String, regex: String) = regex.toRegex().matches(name)

    fun contains(str: String, text: CharSequence) = str.contains(text)

    fun hump2Underline(str: String): String {
        if (str.isEmpty()) {
            return str
        }
        val matcher = TO_LINE_PATTERN.matcher(str)
        val buffer = StringBuffer()
        while (matcher.find()) {
            if (matcher.start() > 0) {
                matcher.appendReplacement(buffer, "_" + matcher.group(0).toLowerCase())
            } else {
                matcher.appendReplacement(buffer, matcher.group(0).toLowerCase())
            }
        }
        matcher.appendTail(buffer)
        return buffer.toString()
    }

    fun getClsNameByFullName(fullName: String): String {
        return fullName.substring(fullName.lastIndexOf('.') + 1)
    }

    fun getJavaName(name: String): String {
        if (name.isEmpty()) {
            return name
        }
        val localName = name.toLowerCase()
        val matcher = TO_HUMP_PATTERN.matcher(localName.toLowerCase())
        val buffer = StringBuffer()
        while (matcher.find()) {
            matcher.appendReplacement(buffer, matcher.group(1).toUpperCase())
        }
        matcher.appendTail(buffer)
        return buffer.toString()
    }

    fun append(vararg objects: Any): String {
        if (objects.isEmpty()) {
            return ""
        }
        val builder = StringBuilder()
        for (s in objects) {
            builder.append(s)
        }
        return builder.toString()
    }

}


fun PsiFile.reformatCode() {
    try {
        this.commitAndUnblockDocument()
        JavaCodeStyleManager.getInstance(project).optimizeImports(this)
        CodeStyleManager.getInstance(project).reformat(this)
    } catch (e: Exception) {
        show("reformatCode Exception :\n$e")
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

fun addOrReplaceFile(selectedFile: PsiFile, psiFile: PsiJavaFile) {
    WriteCommandAction.runWriteCommandAction(project) {
        try {
            val psiDirectory = findDirectory(selectedFile, psiFile)
            Env.println("psiDirectory $psiDirectory ")
            psiDirectory.apply {
                findFile(psiFile.name)?.run {
                    PsiDocumentManager.getInstance(project).getDocument(this)?.run {
                        replaceString(
                            0,
                            this.textLength,
                            PsiDocumentManager.getInstance(project).getDocument(psiFile)!!.text
                        )
                        reformatCode()
                    }
                } ?: run {
                    add(psiFile)
                    findFile(psiFile.name)?.reformatCode()
                }
            }
        } catch (e: Throwable) {
            Env.println("addOrReplaceFile : " + e)
        }
    }
}

fun findDirectory(selectedFile: PsiFile, psiFile: PsiJavaFile): PsiDirectory {
    return PackageUtil.findOrCreateDirectoryForPackage(selectedFile.project, psiFile.packageName, null, true)!!
}

fun showSelected(psiClass: PsiClass, selectedFile: PsiJavaFile) {
    // 创建一行四列的主面板
    val mainPanel = JPanel(FlowLayout())

    val listPanel = FileUtil.listDirectory()
        .map {
            val templatePanel = ListCheckboxPanel(
                it.name,
                it.listFiles()?.toList() ?: emptyList()
            )
            mainPanel.add(templatePanel)
            templatePanel
        }.apply {
            Env.println("listPanel : $this")
        }
    // 构建dialog
    Env.println("dialogBuilder : $project")
    val dialogBuilder = DialogBuilder(project)
    Env.println("dialogBuilder 2 : $dialogBuilder")
    dialogBuilder.setTitle("MsgValue.TITLE_INFO")
    dialogBuilder.setNorthPanel(MultiLineLabel("请选择要导出的code："))
    dialogBuilder.setCenterPanel(mainPanel)
    dialogBuilder.addActionDescriptor(DialogBuilder.ActionDescriptor { dialogWrapper ->
        object : AbstractAction("OK") {
            override fun actionPerformed(e: ActionEvent) {
                try {
                    if (!isPanelSelected(*listPanel.toTypedArray())) {
                        Messages.showWarningDialog("至少选择一个模板！", "MsgValue.TITLE_INFO")
                        return
                    }
                    val selectedFiles = listPanel.flatMap {
                        it.selectedItems
                    }

//                    show("generateCode selectedFiles $selectedFiles")
                    selectedFiles.forEach {
                        createFile(psiClass, selectedFile, it)
                    }
                    // 关闭并退出
                    dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
                } catch (e: Exception) {
                    show("generateCode Exception ${e.stackTraceToString()}")
                }

            }

        }
    })

    dialogBuilder.show()
}

fun isPanelSelected(vararg checkboxPanels: ListCheckboxPanel): Boolean {
    for (checkboxPanel in checkboxPanels) {
        if (checkboxPanel.selectedItems.isNotEmpty()) {
            return true
        }
    }
    return false
}

class ListCheckboxPanel(title: String, private val items: List<File>) : JPanel(VerticalFlowLayout()) {

    /**
     * 复选框列表
     */
    private var checkBoxList: List<JBCheckBox>

    /**
     * 初始化操作
     */
    init {
        val textPane = JBCheckBox(title)
        add(textPane)
        add(com.intellij.ui.components.JBLabel("-----------"))
        checkBoxList = if (items.isNotEmpty()) {
            ArrayList<JBCheckBox>(items.size)
                .also {
                    for (item in items) {
                        val checkBox = JBCheckBox(item.name)
                        it.add(checkBox)
                        add(checkBox)
                    }
                }

        } else {
            emptyList()
        }

        textPane.addItemListener {
            checkBoxList.forEach { cb ->
                cb.isSelected = it.stateChange == java.awt.event.ItemEvent.SELECTED
            }
        }
    }

    /**
     * 获取已选中的元素
     *
     * @return 已选中的元素
     */
    val selectedItems: List<File>
        get() {
            return checkBoxList.filter {
                it.isSelected
            }.map { checkBox ->
                items.first { it.name == checkBox.text }
            }
        }

}

fun createFile(psiClass: PsiClass, selectedFile: PsiJavaFile, tempfile: File) {
    // 获取默认参数
    val param: MutableMap<String, Any> = getDefaultParam()
    param["fileClass"] = psiClass.apply {
        psiClass.allFields.forEach {
            Env.println("createFile $it ; javaClass: ${it.javaClass} ; static : ${it.hasModifierProperty("static")} ")
        }
    }
    param["packageName"] = (psiClass.containingFile as PsiJavaFile).packageName
    param["file"] = selectedFile
    param["tool"] = NameUtils
    val templateSetting = TemplateSetting()
    param["setting"] = templateSetting


    val inputString = FileUtil.loadFile(tempfile)
    val code = VelocityUtils.generate(inputString, param).trim()

    val psiFile: PsiJavaFile = PsiFileFactory.getInstance(selectedFile.project)
        .createFileFromText(
            templateSetting.fileName,
            psiClass.language, code
        ) as PsiJavaFile

//    show("createFile selectedFile $selectedFile")
    addOrReplaceFile(selectedFile, psiFile)
//    showCode(project!!, psiFile)
}

data class TemplateSetting(var fileName: String = "")