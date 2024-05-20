import com.intellij.ide.ui.LafManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import liveplugin.show
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.RoundRectangle2D
import java.io.File
import javax.swing.*
import javax.swing.plaf.ComponentUI
import javax.swing.plaf.basic.BasicProgressBarUI


// from https://github.com/batya239/NyanProgressBar

NyanApplicationComponent()


class NyanProgressBarUi : BasicProgressBarUI() {

    private val periodLength: Int
        get() = JBUI.scale(16)

    @Volatile
    private var offset = 0

    @Volatile
    private var offset2 = 0

    @Volatile
    private var velocity = 1

    companion object {
        private const val ONE_OVER_SEVEN = 1f / 7
        private val VIOLET = Color(90, 0, 157)

        @JvmStatic
        @Suppress("ACCIDENTAL_OVERRIDE")
        fun createUI(c: JComponent): ComponentUI {
            c.border = JBUI.Borders.empty().asUIResource()
            return NyanProgressBarUi()
        }

        private fun isEven(value: Int): Boolean {
            return value % 2 == 0
        }
    }

    override fun getPreferredSize(c: JComponent): Dimension {
        return Dimension(super.getPreferredSize(c).width, JBUI.scale(20))
    }

    override fun installListeners() {
        super.installListeners()
        progressBar.addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent) {
                super.componentShown(e)
            }

            override fun componentHidden(e: ComponentEvent) {
                super.componentHidden(e)
            }
        })
    }


    fun paintProgressBar(g2d: Graphics, c: JComponent, startOffset: Int, full: Boolean, icon: Icon) {
        if (g2d !is Graphics2D) {
            return
        }
        if (progressBar.orientation != SwingConstants.HORIZONTAL || !c.componentOrientation.isLeftToRight) {
            super.paintDeterminate(g2d, c)
            return
        }
        val config = GraphicsUtil.setupAAPainting(g2d)
        val b = progressBar.insets // area for border
        val w = progressBar.width
        var h = progressBar.preferredSize.height
        if (!isEven(c.height - h)) h++
        val barRectWidth = w - (b.right + b.left)
        val barRectHeight = h - (b.top + b.bottom)
        if (barRectWidth <= 0 || barRectHeight <= 0) {
            return
        }
        val amountFull = getAmountFull(b, barRectWidth, barRectHeight)
        val parent: Container? = c.parent
        val background: Color = if (parent != null) parent.background else UIUtil.getPanelBackground()
        g2d.setColor(background)
        if (c.isOpaque) {
            g2d.fillRect(0, 0, w, h)
        }
        val R: Float = JBUI.scale(8f)
        val R2: Float = JBUI.scale(9f)
        val off: Float = JBUI.scale(1f)
        g2d.translate(0, (c.height - h) / 2)
        g2d.color = progressBar.foreground
        g2d.fill(RoundRectangle2D.Float(0F, 0F, w - off, h - off, R2, R2))
        g2d.color = background
        g2d.fill(RoundRectangle2D.Float(off, off, w - 2f * off - off, h - 2f * off - off, R, R))
        g2d.paint = LinearGradientPaint(
            0F,
            JBUI.scale(2).toFloat(),
            0F,
            h - JBUI.scale(6).toFloat(),
            floatArrayOf(
                ONE_OVER_SEVEN * 1,
                ONE_OVER_SEVEN * 2,
                ONE_OVER_SEVEN * 3,
                ONE_OVER_SEVEN * 4,
                ONE_OVER_SEVEN * 5,
                ONE_OVER_SEVEN * 6,
                ONE_OVER_SEVEN * 7
            ),
            arrayOf(Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.cyan, Color.blue, VIOLET)
        )
        // diff 1
        g2d.fill(
            RoundRectangle2D.Float(
                2f * off,
                2f * off,
                if (full) w.toFloat() else amountFull - JBUI.scale(5f),
                h - JBUIScale.scale(5f),
                JBUIScale.scale(7f),
                JBUIScale.scale(7f)
            )
        )
        // diff 2
        icon.paintIcon(progressBar, g2d, (if (full) startOffset else amountFull) - JBUI.scale(10), -JBUI.scale(7))

        g2d.translate(0, -(c.height - h) / 2)
        // Deal with possible text painting
        if (progressBar.isStringPainted) {
            paintString(g2d, b.left, b.top, barRectWidth, barRectHeight, amountFull, b)
        }
        config.restore()
    }

    override fun paintIndeterminate(g2d: Graphics, c: JComponent) {
        val w = progressBar.width
        offset = (offset + 1) % periodLength
        offset2 += velocity
        if (offset2 <= 2) {
            offset2 = 2
            velocity = 1
        } else if (offset2 >= w - JBUI.scale(15)) {
            offset2 = w - JBUI.scale(15)
            velocity = -1
        }
        val scaledIcon: Icon = if (velocity > 0) NyanIcons.CAT_ICON else NyanIcons.RCAT_ICON
        paintProgressBar(g2d, c, offset2, true, scaledIcon)
    }


    override fun paintDeterminate(g2d: Graphics, c: JComponent) {
        paintProgressBar(g2d, c, 0, false, NyanIcons.CAT_ICON)
    }


    override fun getBoxLength(availableLength: Int, otherDimension: Int): Int {
        return availableLength
    }

}


object FileUtil {
    fun loadFile(fileName: String): File {
        return File(File(this.javaClass.getResource("").file).parentFile.parent + "/live-plugins" + "/progressbar" + fileName)
    }
}

object NyanIcons {

    const val imageSize = 36

    val CAT_ICON: Icon by lazy {
        val loadFile = FileUtil.loadFile("/resources/cat.png")
        ImageIcon(
            ImageIcon(loadFile.toURI().toURL())
                .image
                .getScaledInstance(
                    imageSize,
                    imageSize,
                    Image.SCALE_DEFAULT
                )
        )
    }
    val RCAT_ICON: Icon by lazy {
        val loadFile = FileUtil.loadFile("/resources/rcat.png")
        ImageIcon(
            ImageIcon(loadFile.toURI().toURL())
                .image
                .getScaledInstance(
                    imageSize,
                    imageSize,
                    Image.SCALE_DEFAULT
                )
        )
    }
}

class NyanApplicationComponent {
    private fun updateProgressBarUi() {
        UIManager.put("ProgressBarUI", NyanProgressBarUi::class.java.name)
        UIManager.getDefaults()[NyanProgressBarUi::class.java.name] = NyanProgressBarUi::class.java
    }

    init {
        LafManager.getInstance().addLafManagerListener { updateProgressBarUi() }
        updateProgressBarUi()
        show("show nyan progress")
    }
}
