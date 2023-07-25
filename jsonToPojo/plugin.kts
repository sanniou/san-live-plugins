import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.internal.LazilyParsedNumber
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.fileTemplates.impl.UrlUtil
import com.intellij.ide.util.PackageUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import liveplugin.PluginUtil
import liveplugin.show
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.io.File
import java.text.DateFormat
import java.util.*
import javax.swing.AbstractAction
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea


// depends-on-plugin com.intellij.java
// add-to-classpath /home/jichang/.config/JetBrains/IntelliJIdea2022.2/live-plugins/jenkins/jenkins-client-0.3.8.jar
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

val Action = "JsonToJavaAction"

if (Env.startup || !isIdeStartup) {
    Env.project = project
    val actionGroupId = "GenerateGroup"
    val actionId = "com.saniou.action.$Action"

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
    action.templatePresentation.setText(Action, false)
    show("register Action $Action success")
}

class EasyCodeAction : AnAction(Action) {


    override fun actionPerformed(event: AnActionEvent) {
        try {

            val psiFile = event.getData(LangDataKeys.PSI_FILE)
            Env.println("PSI_ELEMENT : " + event.getData(LangDataKeys.PSI_ELEMENT))
            Env.println("SYMBOLS : " + event.getData(LangDataKeys.SYMBOLS))
            Env.println("NAVIGATABLE : " + event.getData(LangDataKeys.NAVIGATABLE))
            Env.println("LANGUAGE : " + event.getData(LangDataKeys.LANGUAGE))
            Env.println("HOST_EDITOR : " + event.getData(LangDataKeys.HOST_EDITOR))
            Env.println("CARET : " + event.getData(LangDataKeys.CARET))
            Env.println("CARET.selectedText : " + event.getData(LangDataKeys.CARET)!!.selectedText)
            Env.println("EDITOR : " + event.getData(LangDataKeys.EDITOR))

            if (psiFile is PsiJavaFile) {
                JsonInputDialog { jsonData ->
                    val editor = event.getData(PlatformDataKeys.EDITOR)
                    val project = editor?.project
                    println(project?.name)
                    val str = parseObject(psiFile, jsonData)
                    if (str.isEmpty()) {
                        Messages.showErrorDialog("输入的内容不是一个 JSON ！", "ERROR")
                    } else {
                        WriteCommandAction.runWriteCommandAction(project) {
                            editor?.document?.setText(str)
                            psiFile.reformatCode()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            show("actionPerformed Exception :\n$e")
            throw e
        }
    }
}

fun JsonInputDialog(onClickListener: (String) -> Unit) {
    val mainPanel = JPanel(FlowLayout())

    val textArea = JTextArea()

    // 设置文本区域可滚动
    textArea.isEditable = true
    textArea.lineWrap = true
    textArea.wrapStyleWord = true

    // 加上滚动面板
    val scrollPane = JScrollPane(textArea)

    // 设置首选大小
    scrollPane.preferredSize = Dimension(800, 400)

    mainPanel.add(scrollPane, BorderLayout.CENTER)

    val dialogBuilder = DialogBuilder(project)
    dialogBuilder.setTitle("MsgValue.TITLE_INFO")
    dialogBuilder.setCenterPanel(mainPanel)
    dialogBuilder.addActionDescriptor(DialogBuilder.ActionDescriptor { dialogWrapper ->
        object : AbstractAction("OK") {
            override fun actionPerformed(e: ActionEvent) {
                try {
                    onClickListener(textArea.text)
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


fun parseObject(psiFile: PsiJavaFile, json: String): String {
    try {
        val classSet = mutableSetOf<String>()
        val jsonObject = Gson().fromJson(json, JsonObject::class.java)
        val builder = StringBuilder()
        builder.append("package ${psiFile.packageName};")
        builder.append(
            """
            import com.fasterxml.jackson.annotation.JsonProperty;
            import lombok.Data;
            import java.util.List;
        """.trimIndent()
        )
        val publicClassName = psiFile.name.substring(0, psiFile.name.length - 5)
        builder.append("@Data\npublic class $publicClassName {")

        classSet.add(publicClassName)
        parseObject(null, null, jsonObject, builder, classSet)
        builder.append("}")
        return builder.toString()
    } catch (e: Exception) {
        show("parseObject Exception : $e ${e.stackTraceToString()}")
        return ""
    }
}

fun parseObject(
    name: String?,
    parentName: String?,
    jsonObject: JsonObject?,
    stringBuilder: StringBuilder,
    classSet: MutableSet<String>
): String? {
    val realName = name?.run {
        if (classSet.contains(name)) {
            parentName?.run {
                parentName + name.capitalize()
            } ?: (name + "X")
        } else {
            name
        }
    }
    realName?.run {
        classSet.add(realName)
    }
    val dataBuilder =
        StringBuilder(realName?.run { "@Data\npublic static class $realName {" } ?: "")
    val docBuilder = StringBuilder("/**\n")
    jsonObject?.entrySet()
        ?.forEach { entry ->
            val key = entry.key
            val element = entry.value
            dataBuilder.append("@JsonProperty(\"$key\")\n")
            dataBuilder.append("private ")
            val replacement = Character.toUpperCase(key[0].titlecaseChar()) + key.substring(1)
            when {
                element.isJsonObject -> {
                    docBuilder.append("* $key : {}\n")
                    val realReplacement = parseObject(
                        replacement,
                        realName,
                        element.asJsonObject,
                        stringBuilder,
                        classSet
                    )
                    dataBuilder.append(realReplacement)
                }

                element.isJsonArray -> {
                    docBuilder.append("* $key : []\n")
                    val jsonArray = element.asJsonArray
                    when {
                        jsonArray.size() == 0 -> {
                            dataBuilder.append("List<Object>")
                        }

                        jsonArray.get(0).isJsonObject -> {
                            val realReplacement = parseObject(
                                replacement,
                                realName,
                                jsonArray.get(0).asJsonObject,
                                stringBuilder,
                                classSet
                            )
                            dataBuilder.append("List<$realReplacement>")
                        }

                        else -> dataBuilder.append("List<${getJsonElementTypeName(jsonArray.get(0).asJsonPrimitive)}>")
                    }

                }

                else -> {
                    docBuilder.append("* $key : $element\n")
                    dataBuilder.append(getJsonElementTypeName(element))
                }
            }
            dataBuilder.append(" $key")
            dataBuilder.append(";\n")
        }

    realName?.run {
        dataBuilder.append("}")
    }
    stringBuilder.append(docBuilder.toString()).append("*/").append("\n")
    stringBuilder.append(dataBuilder.toString()).append("\n\n")
    return realName
}


object FileUtil {
    fun loadFile(fileName: String): File {
        return File(
            this.javaClass.getResource("").file.replace(
                "live-plugins-compiled",
                "live-plugins"
            ) + fileName
        )
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
        return File(
            this.javaClass.getResource("").file.replace(
                "live-plugins-compiled",
                "live-plugins"
            )
        )
            .listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sortedBy { it }
            ?: emptyList()
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
            ?.filter { it.isDirectory }
            ?.sortedBy { it }
            ?: emptyList()
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
    val document =
        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile)
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
    return PackageUtil.findOrCreateDirectoryForPackage(
        selectedFile.project,
        psiFile.packageName,
        null,
        true
    )!!
}

fun getJsonElementTypeName(jsonElement: JsonElement): String = when {
    jsonElement.isJsonNull -> "String"
    jsonElement.isJsonObject -> "Object"
    jsonElement.isJsonPrimitive -> getPrimitiveJsonTypeName(jsonElement.asJsonPrimitive)
    else -> throw RuntimeException("Unknown type: ${jsonElement.javaClass}")

}

private val INT_REGEX = Regex("""^-?\d+$""")
private val DOUBLE_REGEX = Regex("""^-?\d+\.\d+(?:E-?\d+)?$""")


fun getPrimitiveJsonTypeName(jsonPrimitive: JsonPrimitive): String =
    when {
        jsonPrimitive.isBoolean -> "Boolean"
        jsonPrimitive.isString -> "String"
        jsonPrimitive.isNumber -> when (jsonPrimitive.asNumber) {
            is Long -> "Long"
            is Int -> "Integer"
            is Float -> "Float"
            is Double -> "Double"
            is LazilyParsedNumber -> with(jsonPrimitive.asString ?: "") {
                when {
                    INT_REGEX.matches(this@with) -> "Integer"
                    DOUBLE_REGEX.matches(this@with) -> "Double"
                    else -> throw IllegalArgumentException("Unknown type for JSON value: ${this@with}")
                }
            }

            else -> throw RuntimeException("Unknown number type: ${jsonPrimitive.asNumber.javaClass}")
        }

        else -> throw RuntimeException("Unknown primitive type: ${jsonPrimitive.javaClass}")
    }
