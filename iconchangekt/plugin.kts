import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.impl.IdeFrameImpl
import liveplugin.PluginUtil.registerProjectListener
import liveplugin.PluginUtil.show
import java.awt.Color
import java.awt.Font
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage


//ApplicationComponent()

object Constants {
    const val ideaIconWithBar_base64_28px = "iVBORw0KGgoAAAANSUhEUgAAABwAAAAcCAYAAAByDd+UAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsQAAA7EAZUrDhsAAAASdEVYdFNvZnR3YXJlAEdyZWVuc2hvdF5VCAUAAANSSURBVEhLtVZBaxNBFP5md5ukSdqKjUhLpV4FsRepHix4EEEPgt4sHrwoHoqIgqAULx4EqT/BPyFaFA+ilyJFLHjQQotCtQ1FbZs0abPZ2fF7s5tYZbWXzYMvM3n7Zr/33rydeWp+9KLJOC52iiG0UoSDkAgImcsYItIL5nqG8KR0hBpAnpj4meFasQupDzmX//xBGDahPh+/ZIZzReH5Q1qLd5L9PQpm+w7iWekwtp2uiIQ6IdF2HhNTJ+/bqizTsVhCY+CHIRqhtvDDAE3dRCCgZ3YktIUfj02MrC3iwspbeM0tGOqNbsAEDShBcxuKOofvi3KDKMID2QK2jcYZMx+xpyzFkUeAl0NtsxxFKLne0gFzHHYEJtiCw4BcZjFOqUHT8AEVnYBi6h2+3+G8vYciSd6lAdm/39ghScZpwEbWQsxla0hr3REIUbR/JJdvqeF48Ikk4zQQRRal02lkgIbrWiSlIw24LEohrfLTcOpjc1g9PYevJxcSvUsDYIVWSFZ1siRmmHLs2PMzwTgN1LwsfnhFeySS0B44EnSicRooZ3rhc8vahFKhnYzQFqQExMPcEraQZJwG7O3B6LRyfxNKWoMg6Agisr8i1MZLNE4DcjcKoWxdu2hWy4cS05EKSNa6uEkYfRa1yv5E79JAq2BkVG8mj5psfgDv5k7g9vR90gODg4OYmpqy83/J+Ph4PNtdeq/MQuX7EVTLUK8nR029MYovCwdx68U9azA0NIT5+f/f/oVCIZ7tLj3X3seEK1CvJo+Z2uYYVj7vx8TzSWswPDxso/yfzMzMxLPdpXfiA5AvQQvhyztjprp2CuvL3bg6fTc2SVd6bnwiYb8ldHy/gOrGgC2cpJM+FSjPtooC9fTmWfNz+Ty0X0elUWMzxRaQpWukd6UTtortSaHYe3pYyhexWOxDNcMuzC2gjhx8k4Unl4LYiuMSFu0NG2yT4V53l2CyPQgr36AeX75usttHSMIzj99MwKtEFlgyWcRRyFaLGXzcuwffu9hSIsP7M4NNVcQm8uxjs3CF0Nr/udawQTZdOTvqjSWoB+ce0r/Ys3ZUrQOd3XJBY2Wfg6qbw7qXR83kbVR1EtVMN2roZtMc3XPtdSQTab3D3vdUqdDHLz+773FTc2IpAAAAAElFTkSuQmCC"
    val identLettersSplitRegexes = arrayOf("[a-z0-9_-]+", "[0-9_-]+")

}

class ApplicationComponent {
    init {
        ApplicationManager.getApplication().invokeLater {
            IdeFrameImpl.getFrames()
                    .forEach { frame ->
                        if (frame is IdeFrameImpl) {
                            val project = frame.project
                            setIdeaWindowIcon(project!!, frame, getIdentifyingLetters(project))
                        }
                    }

            registerProjectListener("icon-changer-live", object : ProjectManagerListener {
                override fun projectOpened(project: Project) {
                    ApplicationManager.getApplication().invokeLater {
                        setIdeaWindowIcon(project, overlayText = getIdentifyingLetters(project))
                    }
                }
            })
        }
    }

    fun setIdeaWindowIcon(project: Project, frame: IdeFrameImpl? = findFrameForProject(project), overlayText: String) {
        if (frame == null) {
            return
        }
        show("setIdeaWindowIcon $frame $project")
        val moreThanTwoChars = overlayText.length > 2
        val img = toBufferedImage(frame.iconImage)
        val g = img.createGraphics()
        g.color = Color.BLACK
        g.fillRect(5, 5, 20, 16)
        g.color = Color.WHITE
        g.font = Font("Consoles", Font.BOLD, if (moreThanTwoChars) 13 else 15)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.drawString(overlayText, if (moreThanTwoChars) 5 else 6,
                if (moreThanTwoChars) 14 else 16)
        frame.iconImage = img
    }

    fun getIdentifyingLetters(project: Project?, i: Int = 0): String {
        if (project == null) {
            return "???"
        }
        val prjName = project.name
        if (i >= Constants.identLettersSplitRegexes.size) {
            return prjName.substring(0, Math.min(3, prjName.length))
        }
        val regex = Constants.identLettersSplitRegexes[i]
        val split = prjName.split(regex) - ""
        if (split.size >= 3) {
            return split[0].substring(0, 1) + split[1].substring(0, 1) + split[2].substring(0, 1)
        } else if (split.size == 2) {
            return split[0].substring(0, 1) + split[1].substring(0, 1)
//    } else if (split.length == 1) {
//        return split[0].substring(0, 1)
        } else {
            return getIdentifyingLetters(project, i + 1)
        }
    }

    fun findFrameForProject(project: Project): IdeFrameImpl? {
        IdeFrameImpl.getFrames()
                .forEach { frame ->
                    if (frame is IdeFrameImpl && frame.project == project) {
                        return frame
                    }
                }
        return null
    }

    /**
     * Converts a given Image into a BufferedImage
     *
     * @param img The Image to be converted
     * @return The converted BufferedImage
     */
    fun toBufferedImage(img: Image): BufferedImage {
        if (img is BufferedImage) {
            return img
        }

        // Create a buffered image with transparency
        val bimage = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        val bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();

        // Return the buffered image
        return bimage;
    }

}


