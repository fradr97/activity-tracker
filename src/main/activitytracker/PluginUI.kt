package activitytracker

import activitytracker.EventAnalyzer.Result.*
import activitytracker.Plugin.Companion.pluginId
import activitytracker.liveplugin.*
import activitytracker.locAlgorithm.ProcessPluginOutput
import activitytracker.locAlgorithm.LineHighlighter
import activitytracker.locAlgorithm.neuroSkyAttention.NeuroSkyAttention
import activitytracker.locAlgorithm.utils.FileUtils
import com.intellij.CommonBundle
import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.showOkCancelDialog
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.*
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.Icon
import javax.swing.JTextField


class PluginUI(
    private val plugin: Plugin,
    private val trackerLog: TrackerLog,
    private val eventAnalyzer: EventAnalyzer,
    private val parentDisposable: Disposable
) {
    private val log = Logger.getInstance(PluginUI::class.java)
    private var state = Plugin.State.defaultValue
    private val actionGroup: DefaultActionGroup by lazy { createActionGroup() }
    private val widgetId = "Activity Tracker Widget"
    private val processPluginOutput = ProcessPluginOutput()

    private lateinit var editor: Editor
    private lateinit var document: Document
    private var filePath: String = ""
    private var isButtonHighlightActive = true

    fun init(): PluginUI {
        plugin.setUI(this)
        registerWidget(parentDisposable)
        registerPopup(parentDisposable)
        registerButtonStartStopMonitoring(parentDisposable)
        registerButtonHighlightLines(parentDisposable)
        registerAttentionIndicator(parentDisposable)
        registerSettings(parentDisposable)
        eventAnalyzer.runner = { task ->
            runInBackground("Analyzing activity log", task = { task() })
        }

        val checkAttentionThread = Thread(CheckAttentionThread())
        checkAttentionThread.start()

        return this
    }

    fun update(state: Plugin.State) {
        this.state = state
        updateWidget(widgetId)
    }

    private fun registerPopup(parentDisposable: Disposable) {
        registerAction("$pluginId-Popup", "ctrl shift alt O", "",
            "Activity Tracker Popup", parentDisposable) {
            val project = it.project
            if (project != null) {
                createListPopup(it.dataContext).showCenteredInCurrentWindow(project)
            }
        }
    }

    private fun registerButtonStartStopMonitoring(parentDisposable: Disposable) {
        var icon: Icon?

        val buttonStartStopMonitoring = object: AnAction("Start/Stop Monitoring"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                val check = checkProjectOrFile(event)

                if (check == NULL_CODE) {
                    if(state.isTracking) {
                        plugin.toggleTracking()
                        isButtonHighlightActive = true
                        Variables.neuroSkyAttention.stopConnection()
                    }
                    else
                        Messages.showErrorDialog(noProjectOrFileTitle, noProjectOrFileMessage)
                }
                else {
                    if(state.isTracking) {
                        plugin.toggleTracking()
                        Variables.neuroSkyAttention.stopConnection()

                        val fileUtils = FileUtils()
                        fileUtils.writeFile(ProcessPluginOutput.ATTENTION_DATASET_FILENAME,
                            Variables.attentionList as MutableList<Array<String>>, true)

                        isButtonHighlightActive = true
                    } else {
                        if(openFaceOutputFolderPath.trim() == "")
                            setOpenFaceOutputFolder()

                        Messages.showInfoMessage(waitingNeuroSkyMessage, waitingTitle)
                        val isStarted: Boolean = Variables.neuroSkyAttention.waitForStarting()

                        if (isStarted) {
                            Messages.showInfoMessage(activityTrackerRunningMessage, runningTitle)
                            val attentionThread = Thread(AttentionThread())
                            attentionThread.start()

                            plugin.toggleTracking()
                            monitoringOperations(document, editor, filePath, removeHighlightedLines)
                            isButtonHighlightActive = false
                        } else {
                            Messages.showErrorDialog(neuroSkyNotWorkingMessage, neuroSkyNotWorkingTitle)
                        }
                    }
                }
            }
            override fun update(event: AnActionEvent) {
                icon = if(state.isTracking) {
                    IconLoader.getIcon("AllIcons.Actions.Pause")
                } else {
                    IconLoader.getIcon("AllIcons.Debugger.Db_set_breakpoint")
                }
                event.presentation.icon = icon
            }
        }
        registerAction("Start/Stop Monitoring", "", "ToolbarRunGroup",
            "Start/Stop Monitoring", parentDisposable, buttonStartStopMonitoring)
    }

    private fun registerButtonHighlightLines(parentDisposable: Disposable) {
        var icon: Icon

        val buttonHighlight = object: AnAction("Highlight Lines"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                val check = checkProjectOrFile(event)

                if (check == NULL_CODE) {
                    Messages.showErrorDialog(noProjectOrFileTitle, noProjectOrFileMessage)
                }
                else {
                    if(!state.isTracking && isButtonHighlightActive) {
                        Messages.showInfoMessage(highlightLinesMessage, waitingTitle)
                        Thread.sleep(sleep.toLong())

                        val createOperation = monitoringOperations(document, editor, filePath, createAttentionDataset)

                        if(createOperation == NULL_CODE) {
                            isButtonHighlightActive = false
                            Messages.showWarningDialog(noTrackingActivityMessage, noTrackingActivityTitle)
                        } else {
                            isButtonHighlightActive = true
                            val highlightOperation = monitoringOperations(document, editor, filePath, highlightLines)

                            if(highlightOperation == NULL_CODE)
                                Messages.showErrorDialog(noTrackingActivityMessage, noTrackingActivityTitle)
                            else {
                                isButtonHighlightActive = false
                            }
                        }
                    }
                    else if(!state.isTracking && !isButtonHighlightActive) {
                        val op = monitoringOperations(document, editor, filePath, removeHighlightedLines)

                        if(op == NULL_CODE)
                            Messages.showErrorDialog(noTrackingActivityMessage, noTrackingActivityTitle)
                        else {
                            isButtonHighlightActive = true
                        }
                    }
                }
            }
            override fun update(event: AnActionEvent) {
                icon = if(isButtonHighlightActive) {
                    IconLoader.getIcon("AllIcons.Actions.Lightning")
                } else {
                    IconLoader.getIcon("AllIcons.Actions.Cancel")
                }
                event.presentation.icon = icon
            }
        }
        registerAction("Highlight Lines", "", "ToolbarRunGroup",
            "Highlight Lines", parentDisposable, buttonHighlight)
    }

    private fun registerAttentionIndicator(parentDisposable: Disposable) {
        var icon: Icon
        val attentionIndicatorIcon = object: AnAction("Attention Indicator"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) { }
            override fun update(event: AnActionEvent) {
                icon = when (Variables.checkAttentionValue) {
                    in NeuroSkyAttention.NO_ATTENTION..NeuroSkyAttention.LOW_ATTENTION -> {
                        IconLoader.getIcon("AllIcons.Actions.IntentionBulbGrey")
                    }
                    in NeuroSkyAttention.LOW_ATTENTION + 1..NeuroSkyAttention.HIGH_ATTENTION -> {
                        IconLoader.getIcon("AllIcons.Actions.IntentionBulb")
                    }
                    in NeuroSkyAttention.HIGH_ATTENTION + 1..NeuroSkyAttention.MAX_ATTENTION -> {
                        IconLoader.getIcon("AllIcons.Actions.QuickfixOffBulb")
                    }
                    else -> {
                        IconLoader.getIcon("AllIcons.Actions.QuickfixBulb")
                    }
                }
                event.presentation.icon = icon
            }
        }
        registerAction("Attention Indicator", "", "ToolbarRunGroup",
            "Attention Indicator", parentDisposable, attentionIndicatorIcon)
    }

    private fun registerSettings(parentDisposable: Disposable) {
        val buttonSettings = object: AnAction("Set OpenFace Output"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                setOpenFaceOutputFolder()
            }
            override fun update(event: AnActionEvent) {
                event.presentation.icon = IconLoader.getIcon("AllIcons.General.Settings")
            }
        }
        registerAction("OpenFace output", "", "ToolbarRunGroup",
            "Set OpenFace Output", parentDisposable, buttonSettings)
    }

    private fun setOpenFaceOutputFolder() {
        val jTextField = JTextField()
        Messages.showTextAreaDialog(jTextField, settingsTitle, "")
        openFaceOutputFolderPath = jTextField.text.toString()
    }

    private fun checkProjectOrFile(event: AnActionEvent): Int {
        val project: Project? = event.getData(PlatformDataKeys.PROJECT)

        if (project == null) {
            return NULL_CODE
        } else {
            val editorManager = FileEditorManager.getInstance(project)
            val selectedEditor = editorManager.selectedTextEditor
            return if (selectedEditor == null) {
                NULL_CODE
            } else {
                val selectedDocument = Objects.requireNonNull(selectedEditor).document
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(selectedDocument)
                if (psiFile == null) {
                    NULL_CODE
                } else {
                    this.editor = selectedEditor
                    this.document = selectedDocument
                    this.filePath = psiFile.originalFile.virtualFile.path
                    OK_CODE
                }
            }
        }
    }

    private fun monitoringOperations(document: Document, editor: Editor, fileOnFocus: String, operation: Int): Int {
        when (operation) {
            createAttentionDataset -> {
                return processPluginOutput.createPluginOutput(fileOnFocus, openFaceOutputFolderPath)
            }
            highlightLines -> {
                return processPluginOutput.getHighlightedAttentionLines(document, editor, fileOnFocus)
            }
            removeHighlightedLines -> {
                val lineHighlighter = LineHighlighter()
                lineHighlighter.removeAllHighlighter(editor)
                return OK_CODE
            }
        }
        return NULL_CODE
    }

    private fun registerWidget(parentDisposable: Disposable) {
        val presentation = object: StatusBarWidget.TextPresentation {
            override fun getText() = "Activity tracker: " + (if (state.isTracking) "on" else "off")
            override fun getAlignment() = Component.CENTER_ALIGNMENT
            override fun getTooltipText() = "Click to open menu"
            override fun getClickConsumer() = Consumer { mouseEvent: MouseEvent ->
                val dataContext = MapDataContext().put(PlatformDataKeys.CONTEXT_COMPONENT.name, mouseEvent.component)
                val popup = createListPopup(dataContext)
                val dimension = popup.content.preferredSize
                val point = Point(0, -dimension.height)
                popup.show(RelativePoint(mouseEvent.component, point))
            }
        }

        val extensionPoint = StatusBarWidgetFactory.EP_NAME.getPoint { Extensions.getRootArea() }
        val factory = object: StatusBarWidgetFactory {
            override fun getId() = widgetId
            override fun getDisplayName() = widgetId
            override fun isAvailable(project: Project) = true
            override fun canBeEnabledOn(statusBar: StatusBar) = true
            override fun createWidget(project: Project) = object: StatusBarWidget, StatusBarWidget.Multiframe {
                override fun ID() = widgetId
                override fun getPresentation() = presentation
                override fun install(statusBar: StatusBar) = statusBar.updateWidget(ID())
                override fun copy() = this
                override fun dispose() {}
            }
            override fun disposeWidget(widget: StatusBarWidget) {}
        }
        extensionPoint.registerExtension(factory, LoadingOrder.before("positionWidget"), parentDisposable)
    }

    private fun createListPopup(dataContext: DataContext) =
        JBPopupFactory.getInstance().createActionGroupPopup(
            "Activity Tracker",
            actionGroup,
            dataContext,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            true
        )

    private fun createActionGroup(): DefaultActionGroup {
        val openLogInIde = object: AnAction("Open in IDE"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) = plugin.openTrackingLogFile(event.project)
        }
        val openLogFolder = object: AnAction("Open in File Manager"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) = plugin.openTrackingLogFolder()
        }
        val showStatistics = object: AnAction("Show Stats"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                val project = event.project
                if (trackerLog.isTooLargeToProcess()) {
                    showNotification("Current activity log is too large to process in IDE.")
                } else if (project != null) {
                    eventAnalyzer.analyze(whenDone = { result ->
                        invokeLaterOnEDT {
                            when (result) {
                                is Ok             -> {
                                    StatsToolWindow.showIn(project, result.stats, eventAnalyzer, parentDisposable)
                                    if (result.errors.isNotEmpty()) {
                                        showNotification("There were ${result.errors.size} errors parsing log file. " +
                                                "See IDE log for details.")
                                        result.errors.forEach { log.warn(it.first, it.second) }
                                    }
                                }
                                is AlreadyRunning -> showNotification("Analysis is already running.")
                                is DataIsTooLarge -> showNotification("Activity log is too large to process in IDE.")
                            }
                        }
                    })
                }
            }
        }
        val rollCurrentLog = object: AnAction("Roll Tracking Log"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                val userAnswer = showOkCancelDialog(
                    event.project,
                    "Roll tracking log file?\nCurrent log will be moved into new file.",
                    "Activity Tracker",
                    CommonBundle.getOkButtonText(),
                    CommonBundle.getCancelButtonText(),
                    Messages.getQuestionIcon()
                )
                if (userAnswer != Messages.OK) return

                val rolledFile = trackerLog.rollLog()
                showNotification("Rolled tracking log into <a href=''>${rolledFile.name}</a>") {
                    RevealFileAction.openFile(rolledFile)
                }
            }
        }
        val clearCurrentLog = object: AnAction("Clear Tracking Log"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                val userAnswer = showOkCancelDialog(
                    event.project,
                    "Clear current tracking log file?\n(This operation cannot be undone.)",
                    "Activity Tracker",
                    CommonBundle.getOkButtonText(),
                    CommonBundle.getCancelButtonText(),
                    Messages.getQuestionIcon()
                )
                if (userAnswer != Messages.OK) return

                val wasCleared = trackerLog.clearLog()
                if (wasCleared) showNotification("Tracking log was cleared")
            }
        }
        val openHelp = object: AnAction("Help"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) = BrowserUtil.open("https://github.com/dkandalov/activity-tracker#help")
        }

        registerAction("Roll Tracking Log", action = rollCurrentLog)
        registerAction("Clear Tracking Log", action = clearCurrentLog)

        return DefaultActionGroup().apply {
            add(DefaultActionGroup("Current Log", true).apply {
                add(showStatistics)
                add(openLogInIde)
                add(openLogFolder)
                addSeparator()
                add(rollCurrentLog)
                add(clearCurrentLog)
            })
            add(openHelp)
        }
    }

    companion object {
        const val createAttentionDataset = 0
        const val highlightLines = 1
        const val removeHighlightedLines = 2
        const val sleep = 5000
        const val noTrackingActivityTitle = "Tracker File Empty!"
        const val noTrackingActivityMessage = "No tracking activity detected for this file."
        const val noProjectOrFileTitle = "No project or file selected!"
        const val noProjectOrFileMessage = "Select a Project and a File."
        const val waitingTitle = "Please wait!"
        const val waitingNeuroSkyMessage = "Waiting NeuroSky MindWave headset... "
        const val runningTitle = "Running!"
        const val activityTrackerRunningMessage = "Tracker is running... "
        const val neuroSkyNotWorkingTitle = "NeuroSky MindWave headset Not Working!"
        const val neuroSkyNotWorkingMessage = "Put on the NeuroSky MindWave headset and check that it is on!"
        const val highlightLinesMessage = "Data processing and highlighting in progress... "
        const val settingsTitle = "OpenFace Output Folder Path (e.g. C:\\Users\\OtherFolders\\FolderWithCsvFile)"

        const val OK_CODE = 0
        const val NULL_CODE = -1

        var openFaceOutputFolderPath: String = ""
    }

    object Variables {
        val neuroSkyAttention = NeuroSkyAttention()
        var attentionList: MutableList<Array<String>>? = null
        var checkAttentionValue: Int = 0
    }

    class AttentionThread : Runnable {
        override fun run() {
            Variables.attentionList = Variables.neuroSkyAttention.attention
        }
    }

    class CheckAttentionThread : Runnable {
        override fun run() {
            while (true)
                Variables.checkAttentionValue = Variables.neuroSkyAttention.checkAttention()
        }
    }
}