//import com.intellij.execution.ui.ConsoleView
//import com.intellij.execution.ui.ConsoleViewContentType
//import com.intellij.openapi.project.Project
//import com.intellij.ui.components.JBScrollPane
//import com.intellij.ui.layout.panel
//import com.intellij.ui.table.JBTable
//import com.offbytwo.jenkins.client.JenkinsHttpClient
//import liveplugin.PluginUtil
//import liveplugin.registerIdeToolWindow
//import liveplugin.show
//import org.apache.http.impl.client.HttpClientBuilder
//import java.awt.GridLayout
//import java.net.URI
//import java.text.DateFormat
//import java.util.*
//import javax.swing.*
//
//
//// ddddepends-on-plugin org.jetbrains.compose
//// depends-on-plugin com.intellij.java
//// add-to-classpath /home/jichang/.config/JetBrains/IntelliJIdea2022.2/live-plugins/jenkins/jenkins-client-0.3.8.jar
//
////todo order method;order filed
//object Env {
//
//    const val startup = true
//
//    val resetAction get() = debugMode
//
//    const val debugMode = true
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
//            consoleView =
//                PluginUtil.showInConsole("${DateFormat.getDateTimeInstance().format(Date())} : $message \n", project!!)
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
////throw RuntimeException("stop this")
//
//
//val js = com.offbytwo.jenkins.JenkinsServer(
//    JenkinsHttpClient(
//        URI.create("https://devops-cn1.api.1stepai.cn/jenkins"),
//        HttpClientBuilder
//            .create()
//            .setSSLContext(
//                org.apache.http.ssl.SSLContextBuilder()
//                    .loadTrustMaterial(null, org.apache.http.conn.ssl.TrustAllStrategy.INSTANCE).build()
//            )
//            .setSSLHostnameVerifier(org.apache.http.conn.ssl.NoopHostnameVerifier.INSTANCE),
//        "yujichang",
//        "11ae9fd6022506a67807b83cf61ee6a112"
//    )
//);
//
//
////fun createMainPanel(): JComponent {
////
////    return ComposePanel().apply {
////        setContent {
////            DropDownComponent()
////                .dropdown(topics) { topic -> listener.onTopicChanged(topic) }
////            if (topics.value.isNotEmpty()) {
////                listener.onTopicChanged(topics.value.first())
////            }
////        }
////    }
////}
//
//registerIdeToolWindow(
//    "DisposableDisposableDisposable",
//    com.intellij.openapi.wm.ToolWindowAnchor.RIGHT, {
//        val searchTextField = JTextField()
////        searchTextField.document.addDocumentListener(this)
//
//        // 声明一个JBTable
//        val table = JBTable(HomeTableModel(js.jobs.values.toTypedArray()))
//        table.getColumn("build").cellRenderer = ButtonRenderer()
//        table.preferredScrollableViewportSize = java.awt.Dimension(800, 300)
//        table.rowHeight = 36
//        table.fillsViewportHeight = true
////        table.addMouseListener(this)
//
//        val tablePanel = JPanel(GridLayout(1, 0))
//        val scrollPane = JBScrollPane(table)
//        tablePanel.add(scrollPane)
//        val r = panel {
//            row {
//                searchTextField()
//            }
//            row {
//                scrollPane()
//            }
//        }
//        r
//    });
//
//fun buildJob(s: String) {
//    var job = js.getJob(s);
//    val queueRef = job.build(true);
//
//    job = js.getJob(s);
//    var queueItem = js.getQueueItem(queueRef);
//    while (!queueItem.isCancelled() && job.isInQueue()) {
//        System.out.println("In Queue " + job.isInQueue());
//        Thread.sleep(200);
//        job = js.getJob(s);
//        queueItem = js.getQueueItem(queueRef);
//    }
//    System.out.println("ended waiting.");
//    System.out.println("cancelled:" + queueItem.isCancelled());
//    if (queueItem.isCancelled()) {
//        System.out.println("Job has been canceled.");
//    }
//}
//
//
//fun jobLast(s: String) {
//    val job = js.getJob(s);
//    var lastBuild = job.getLastBuild();
//
//    var isBuilding = lastBuild.details().isBuilding();
//    while (isBuilding) {
//        System.out.println("Is building...(" + lastBuild.getNumber() + ")");
//        Thread.sleep(200);
//        isBuilding = lastBuild.details().isBuilding();
//    }
//
//    System.out.println("Finished.");
//    System.out.println(" Result: " + lastBuild.details().getResult());
//
//}
//
//class HomeTableModel(private val repositories: Array<com.offbytwo.jenkins.model.Job>) :
//    javax.swing.table.AbstractTableModel() {
//
//    private val mColumnNames = mutableListOf("Repository", "status", "build")
//
//    override fun getRowCount(): Int {
//        return repositories.size
//    }
//
//    override fun getColumnCount(): Int {
//        return mColumnNames.size
//    }
//
//    override fun getColumnName(columnIndex: Int): String {
//        return mColumnNames[columnIndex]
//    }
//
//    override fun getColumnClass(columnIndex: Int): Class<*> {
//        return getValueAt(0, columnIndex).javaClass
//    }
//
//    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
//        return columnIndex == 0 || columnIndex == 3
//    }
//
//    private var isJava = false
//
//    fun updateLanguage(isJava: Boolean) {
//        this.isJava = isJava
//        fireTableDataChanged()
//    }
//
//    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
//        return when (columnIndex) {
//            0 -> repositories[rowIndex].name
////            1 -> repositories[rowIndex].fullName
//            2 -> "build"
//            else -> ""
//        }
//    }
//
//    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
//        when (columnIndex) {
////            0 -> repositories[rowIndex].isChoose = value as Boolean?
////            3 -> repositories[rowIndex].customVersion = value as String?
////            4 -> repositories[rowIndex].urlType = value as Int?
//        }
////        fireTableCellUpdated(rowIndex, columnIndex)
//    }
//
//}
//
//internal class ButtonRenderer : JButton(), javax.swing.table.TableCellRenderer {
//    init {
//        isOpaque = true
//    }
//
//    override fun getTableCellRendererComponent(
//        table: JTable, value: Any?,
//        isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
//    ): java.awt.Component {
//        if (isSelected) {
//            foreground = table.selectionForeground
//            background = table.selectionBackground
//        } else {
//            foreground = table.foreground
//            background = UIManager.getColor("Button.background")
//        }
//        text = value?.toString() ?: ""
//        return this
//    }
//}
//
//show("run success ")
//
//
//
