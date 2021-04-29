import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.fileTemplates.impl.UrlUtil
import com.intellij.ide.util.PackageUtil
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.util.TreeFileChooser
import com.intellij.ide.util.TreeFileChooserFactory
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import  com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.actionSystem.AnActionEvent
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
import liveplugin.PluginUtil
import liveplugin.show
import liveplugin.toUrl
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.jetbrains.annotations.NotNull
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
    const val startup = false
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

    val actionGroupId = "GenerateGroup"
    val actionId = "com.saniou.easy.code.action.EasyCodeAction"
    val anchorActionId = "com.saniou.easy.code.action.JavaActionGroup"

    val actionManager: ActionManager = ActionManager.getInstance()

    val actionGroup = actionManager.getAction(actionGroupId) as com.intellij.openapi.actionSystem.DefaultActionGroup

    val alreadyRegistered = (actionManager.getAction(actionId) != null)
    if (alreadyRegistered) {
        Env.println("remove $actionId")
        actionGroup.remove(actionManager.getAction(actionId))
        actionManager.unregisterAction(actionId)
    }

    val action = EasyCodeAction()
    actionManager.registerAction(actionId, action)
    actionGroup.addAction(action, Constraints(com.intellij.openapi.actionSystem.Anchor.BEFORE, anchorActionId))
    action.templatePresentation.setText("EasyCodeAction", true)
    show("register java action group success")
}

class EasyCodeAction : AnAction("EasyCodeAction") {


    override fun actionPerformed(event: AnActionEvent) {
        val psiFile = event.getData(LangDataKeys.PSI_FILE)
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

    val actuallyFile = currentFile ?: selectFile()
    actuallyFile ?: return

    val psiClass = PsiTreeUtil.findChildOfAnyType(actuallyFile.originalElement,
            PsiClass::class.java)!!

    if (tempFile != null) {
        createFile(psiClass, actuallyFile, tempFile)
    } else {
        showSelected(psiClass, actuallyFile)
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
        INIT_PROP.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                "org.apache.velocity.runtime.log.Log4JLogChute")
        INIT_PROP.setProperty("runtime.log.logsystem.log4j.logger", "velocity")
    }

}


object FileUtil {
    fun loadFile(fileName: String): File {
        return File(this.javaClass.getResource("").file.replace("live-plugins-compiled", "live-plugins") + fileName)
    }

    fun loadFile(file: File): String {
        try {
            return UrlUtil.loadText(file.toUrl()).replace("\r", "")
        } catch (e: Exception) {
            Env.println("IOException$e")

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
        return File(this.javaClass.getResource("").file.replace("live-plugins-compiled", "live-plugins"))
                .listFiles()
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
        CodeStyleManager.getInstance(project).reformat(this)
        JavaCodeStyleManager.getInstance(project).optimizeImports(this)
    } catch (e: Exception) {
        show(e.toString())
    }
}

fun addOrReplaceFile(selectedFile: PsiFile, psiFile: PsiJavaFile) {
    WriteCommandAction.runWriteCommandAction(project) {
        try {
            val psiDirectory = findDirectory(selectedFile, psiFile)
            psiDirectory.apply {
                findFile(psiFile.name)?.run {
                    PsiDocumentManager.getInstance(project).getDocument(this)?.run {
                        replaceString(0, this.textLength, PsiDocumentManager.getInstance(project).getDocument(psiFile)!!.text)
                        reformatCode()
                    }
                } ?: run {
                    add(psiFile)
                    findFile(psiFile.name)?.reformatCode()
                }
            }
        } catch (e: Exception) {
            Env.println(e)
        }
    }
}

fun findDirectory(selectedFile: PsiFile, psiFile: PsiJavaFile): PsiDirectory {
    return PackageUtil.findOrCreateDirectoryForPackage(selectedFile.project, psiFile.packageName, null, true)!!
}

fun showSelected(psiClass: PsiClass, selectedFile: PsiJavaFile) {
    // 创建一行四列的主面板
    //val mainPanel = JPanel(GridLayout(1, 4))
    val mainPanel = JPanel(FlowLayout())

    val listPanel = FileUtil.listDirectory()
            .map {
                val templatePanel = ListCheckboxPanel(it.name, it.listFiles()?.toList()
                        ?: emptyList())
                mainPanel.add(templatePanel)
                templatePanel
            }

    // 构建dialog
    val dialogBuilder = DialogBuilder(project)
    dialogBuilder.setTitle("MsgValue.TITLE_INFO")
    dialogBuilder.setNorthPanel(MultiLineLabel("请选择要导出的code："))
    dialogBuilder.setCenterPanel(mainPanel)
    dialogBuilder.addActionDescriptor(DialogBuilder.ActionDescriptor { dialogWrapper ->
        object : AbstractAction("OK") {
            override fun actionPerformed(e: ActionEvent) {

                if (!isPanelSelected(*listPanel.toTypedArray())) {
                    Messages.showWarningDialog("至少选择一个模板！", "MsgValue.TITLE_INFO")
                    return
                }
                val selectedFiles = listPanel.flatMap {
                    it.selectedItems
                }

                selectedFiles.forEach {
                    createFile(psiClass, selectedFile, it)
                }
                // 关闭并退出
                dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)

            }

        }
    })

    dialogBuilder.show()
}

fun isPanelSelected(@NotNull vararg checkboxPanels: ListCheckboxPanel): Boolean {
    for (checkboxPanel in checkboxPanels) {
        if (checkboxPanel.selectedItems.isNotEmpty()) {
            return true
        }
    }
    return false
}

class ListCheckboxPanel(val title: String, val items: List<File>) : JPanel(VerticalFlowLayout()) {

    /**
     * 复选框列表
     */
    var checkBoxList: List<JBCheckBox>

    /**
     * 初始化操作
     */
    init {
        val textPane = JTextPane()
        textPane.isEditable = false
        textPane.text = title
        add(textPane)
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
    }

    /**
     * 获取已选中的元素
     *
     * @return 已选中的元素
     */
    val selectedItems: List<File>
        get() {
            if (checkBoxList.isNullOrEmpty()) {
                return emptyList()
            }
            val result: MutableList<File> = ArrayList()
            checkBoxList.forEach(Consumer { checkBox: JBCheckBox ->
                if (checkBox.isSelected) {
                    result.add(items.first { it.name == checkBox.text })
                }
            })
            return result
        }

}

fun createFile(psiClass: PsiClass, selectedFile: PsiJavaFile, tempfile: File) {
    // 获取默认参数
    val param: MutableMap<String, Any> = getDefaultParam()

    param["fileClass"] = psiClass.apply {
        psiClass.allFields.forEach {
            Env.println("$it ${it.javaClass} ${it.hasModifierProperty("static")} ")
        }
    }
    param["file"] = selectedFile
    param["tool"] = NameUtils
    val templateSetting = TemplateSetting()
    param["setting"] = templateSetting


    val inputString = FileUtil.loadFile(tempfile)
    val code = VelocityUtils.generate(inputString, param).trim()

    val psiFile: PsiJavaFile = PsiFileFactory.getInstance(project)
            .createFileFromText(templateSetting.fileName,
                    psiClass.language, code) as PsiJavaFile

    addOrReplaceFile(selectedFile, psiFile)
//    showCode(project!!, psiFile)
}

data class TemplateSetting(var fileName: String = "")