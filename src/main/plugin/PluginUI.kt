package plugin

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
import com.intellij.openapi.ui.Messages.YesNoResult
import com.intellij.openapi.ui.Messages.showOkCancelDialog
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import plugin.Plugin.Companion.pluginId
import plugin.activityTracker.EventAnalyzer
import plugin.activityTracker.EventAnalyzer.Result.*
import plugin.activityTracker.StatsToolWindow
import plugin.activityTracker.TrackerLog
import plugin.activityTracker.liveplugin.*
import plugin.config.Config
import plugin.locAlgorithm.codingMode.LineHighlighter
import plugin.locAlgorithm.codingMode.ProcessCodingModeOutput
import plugin.locAlgorithm.comprehensionMode.ProcessComprehensionModeOutput
import plugin.neuroSkyAttention.NeuroSkyAttention
import plugin.utils.FileUtils
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.Icon
import kotlin.collections.ArrayList


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
    private val processCodingModeOutput = ProcessCodingModeOutput()
    private val processComprehensionModeOutput = ProcessComprehensionModeOutput()

    private lateinit var editor: Editor
    private lateinit var document: Document
    private var filePath: String = ""
    private var isButtonHighlightActive = true
    var comprehensionModeIsTracking = false

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

        val attentionIndicatorThread = Thread(AttentionIndicatorThread())
        attentionIndicatorThread.start()

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
                if(checkIfFocusIsOnFile == Config.OK_CODE) {
                    if (mode == Config.CODING_MODE) {  /** CODING MODE **/
                        if(state.isTracking) {
                            stopCodingModeTracking()
                        } else {
                            Messages.showInfoMessage(Config.WAITING_NEUROSKY_MESSAGE, Config.WAITING_TITLE)
                            val isStarted: Boolean = Variables.neuroSkyAttention.waitForStarting()

                            if (isStarted) {
                                Messages.showInfoMessage(Config.TRACKER_RUNNING_MESSAGE, Config.RUNNING_TITLE)
                                val attentionThread = Thread(AttentionThread())
                                attentionThread.start()

                                plugin.toggleTracking()
                                monitoringOperations(document, editor, filePath, Config.REMOVE_HIGHLIGHTED_LINES_OPERATION)
                                isButtonHighlightActive = false
                            } else {
                                Messages.showErrorDialog(Config.NEUROSKY_NOT_WORKING_MESSAGE, Config.NEUROSKY_NOT_WORKING_TITLE)
                            }
                        }
                    }
                    else {  /** COMPREHENSION MODE **/
                        if(comprehensionModeIsTracking) {
                            stopComprehensionModeTracking()
                        } else {
                            Messages.showInfoMessage(Config.WAITING_NEUROSKY_MESSAGE, Config.WAITING_TITLE)
                            val isStarted: Boolean = Variables.neuroSkyAttention.waitForStarting()

                            if (isStarted) {
                                Messages.showInfoMessage(Config.TRACKER_RUNNING_MESSAGE, Config.RUNNING_TITLE)
                                comprehensionModeIsTracking = true

                                val attentionThread = Thread(AttentionThread())
                                attentionThread.start()

                                monitoringOperations(document, editor, filePath, Config.REMOVE_HIGHLIGHTED_LINES_OPERATION)
                                isButtonHighlightActive = false
                            } else {
                                Messages.showErrorDialog(Config.NEUROSKY_NOT_WORKING_MESSAGE, Config.NEUROSKY_NOT_WORKING_TITLE)
                            }
                        }
                    }
                }
            }
            override fun update(event: AnActionEvent) {
                checkIfFocusIsOnFile = checkProjectOrFile(event)

                icon = if(checkIfFocusIsOnFile == Config.NULL_CODE) {
                    if(state.isTracking) {
                        stopCodingModeTracking()
                    }
                    if(comprehensionModeIsTracking) {
                        stopComprehensionModeTracking()
                    }
                    IconLoader.getIcon("AllIcons.Plugins.Disabled")
                } else {
                    if(state.isTracking || comprehensionModeIsTracking) {
                        IconLoader.getIcon("AllIcons.Actions.Pause")
                    } else {
                        IconLoader.getIcon("AllIcons.Debugger.Db_set_breakpoint")
                    }
                }
                event.presentation.icon = icon
            }
        }
        registerAction("Start/Stop Monitoring", "", "ToolbarRunGroup",
            "Start/Stop Monitoring", parentDisposable, buttonStartStopMonitoring)
    }

    private fun stopCodingModeTracking() {
        plugin.toggleTracking()
        Variables.neuroSkyAttention.stopConnection()

        isButtonHighlightActive = true
    }

    private fun stopComprehensionModeTracking() {
        comprehensionModeIsTracking = false
        Variables.neuroSkyAttention.stopConnection()

        Messages.showInfoMessage(Config.DATA_PROCESSING_MESSAGE, Config.WAITING_TITLE)
        Thread.sleep(Config.SLEEP_BEFORE_PROCESSING.toLong())

        monitoringOperations(document, editor, filePath, Config.CREATE_COMPREHENSION_MODE_DATASET_OPERATION)
    }

    private fun registerButtonHighlightLines(parentDisposable: Disposable) {
        var icon: Icon

        val buttonHighlight = object: AnAction("Highlight Lines"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                if(mode == Config.CODING_MODE) {
                    if (checkIfFocusIsOnFile == Config.OK_CODE) {
                        if(!state.isTracking && isButtonHighlightActive) {
                            Messages.showInfoMessage(Config.DATA_PROCESSING_MESSAGE, Config.WAITING_TITLE)
                            Thread.sleep(Config.SLEEP_BEFORE_PROCESSING.toLong())

                            val createOperation = monitoringOperations(document, editor, filePath, Config.CREATE_CODING_MODE_DATASET_OPERATION)

                            if(createOperation == Config.NULL_CODE) {
                                isButtonHighlightActive = false
                                Messages.showWarningDialog(Config.NO_TRACKING_ACTIVITY_MESSAGE, Config.NO_TRACKING_ACTIVITY_TITLE)
                            } else {
                                isButtonHighlightActive = true
                                val highlightOperation = monitoringOperations(document, editor, filePath, Config.HIGHLIGHT_LINES_OPERATION)

                                if(highlightOperation == Config.NULL_CODE)
                                    Messages.showErrorDialog(Config.NO_TRACKING_ACTIVITY_MESSAGE, Config.NO_TRACKING_ACTIVITY_TITLE)
                                else {
                                    isButtonHighlightActive = false
                                }
                            }
                        }
                        else if(!state.isTracking && !isButtonHighlightActive) {
                            val op = monitoringOperations(document, editor, filePath, Config.REMOVE_HIGHLIGHTED_LINES_OPERATION)

                            if(op == Config.NULL_CODE)
                                Messages.showErrorDialog(Config.NO_TRACKING_ACTIVITY_MESSAGE, Config.NO_TRACKING_ACTIVITY_TITLE)
                            else {
                                isButtonHighlightActive = true
                            }
                        }
                    }
                }
            }
            override fun update(event: AnActionEvent) {
                icon = if(mode == Config.CODING_MODE) {
                    if(checkIfFocusIsOnFile == Config.NULL_CODE)
                        IconLoader.getIcon("AllIcons.Plugins.Disabled")
                    else {
                        if(isButtonHighlightActive) {
                            IconLoader.getIcon("AllIcons.Actions.Lightning")
                        } else {
                            IconLoader.getIcon("AllIcons.Actions.Cancel")
                        }
                    }
                } else IconLoader.getIcon("AllIcons.Debugger.Db_disabled_exception_breakpoint")

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
                    in Config.NO_ATTENTION..Config.LOW_ATTENTION -> {
                        IconLoader.getIcon("AllIcons.Actions.IntentionBulbGrey")
                    }
                    in Config.LOW_ATTENTION + 1..Config.HIGH_ATTENTION -> {
                        IconLoader.getIcon("AllIcons.Actions.IntentionBulb")
                    }
                    in Config.HIGH_ATTENTION + 1..Config.MAX_ATTENTION -> {
                        IconLoader.getIcon("AllIcons.Actions.QuickfixOffBulb")
                    }
                    else -> {
                        IconLoader.getIcon("AllIcons.Actions.QuickfixBulb")
                    }
                }
                event.presentation.icon = icon

                if(comprehensionModeIsTracking) {
                    val attention = Variables.neuroSkyAttention.getAttention()
                    val time = Variables.neuroSkyAttention.getTimestamp()

                    if(attention < percentage(Variables.oldAttentionValue, 20)) {
                        val response = showCheckAttentionDialog()

                        if(response == Messages.YES) {
                            Variables.popupList!!.add(arrayOf(time, "Yes"))
                        }
                        else {
                            Variables.popupList!!.add(arrayOf(time, "No"))
                        }
                    }
                    Variables.oldAttentionValue = attention
                }
            }
        }
        registerAction("Attention Indicator", "", "ToolbarRunGroup",
            "Attention Indicator", parentDisposable, attentionIndicatorIcon)
    }

    @YesNoResult
    private fun showCheckAttentionDialog(): Int {
        val message = "Did you get distracted? " + Variables.checkAttentionValue + "-" + Variables.oldAttentionValue
        val title = "Attention Check"

        return Messages.showYesNoDialog(message, title, "Yes", "No",Messages.getQuestionIcon())
    }

    private fun registerSettings(parentDisposable: Disposable) {
        val buttonSettings = object: AnAction("Settings"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                if(!state.isTracking && !comprehensionModeIsTracking)
                    setOpenFaceOutputFolderAndMode()
            }
            override fun update(event: AnActionEvent) {
                event.presentation.icon = IconLoader.getIcon("AllIcons.General.Settings")
            }
        }
        registerAction("Settings", "", "ToolbarRunGroup",
            "Settings", parentDisposable, buttonSettings)
    }

    private fun setOpenFaceOutputFolderAndMode() {
        val checked = mode != Config.CODING_MODE

        val settings = Messages.showInputDialogWithCheckBox(
            Config.SETTINGS_MESSAGE, Config.SETTINGS_TITLE, Config.COMPREHENSION_MODE,
            checked, true, Messages.getInformationIcon(), openFaceOutputFolderPath, null)

        try {
            openFaceOutputFolderPath = settings.getFirst().toString()

            mode = if(settings.getSecond() == true) {
                Config.COMPREHENSION_MODE
            } else Config.CODING_MODE
        } catch (ignored: Exception) { }
    }

    private fun checkProjectOrFile(event: AnActionEvent): Int {
        val project: Project? = event.getData(PlatformDataKeys.PROJECT)

        if (project == null) {
            return Config.NULL_CODE
        } else {
            val editorManager = FileEditorManager.getInstance(project)
            val selectedEditor = editorManager.selectedTextEditor
            return if (selectedEditor == null) {
                Config.NULL_CODE
            } else {
                val selectedDocument = Objects.requireNonNull(selectedEditor).document
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(selectedDocument)
                if (psiFile == null) {
                    Config.NULL_CODE
                } else {
                    this.editor = selectedEditor
                    this.document = selectedDocument
                    this.filePath = psiFile.originalFile.virtualFile.path
                    Config.OK_CODE
                }
            }
        }
    }

    private fun monitoringOperations(document: Document, editor: Editor, fileOnFocus: String, operation: Int): Int {
        val fileUtils = FileUtils()

        when (operation) {
            Config.CREATE_CODING_MODE_DATASET_OPERATION -> {
                fileUtils.writeFile(Config.ATTENTION_CODING_MODE_DATASET_FILENAME,
                    Variables.attentionList as MutableList<Array<String>>, true)

                return processCodingModeOutput.createOutputCodingMode(fileOnFocus, openFaceOutputFolderPath)
            }
            Config.CREATE_COMPREHENSION_MODE_DATASET_OPERATION -> {
                fileUtils.writeFile(Config.ATTENTION_COMPREHENSION_MODE_DATASET_FILENAME,
                    Variables.attentionList as MutableList<Array<String>>, true)

                fileUtils.writeFile(Config.POPUP_COMPREHENSION_MODE_DATASET_FILENAME,
                    Variables.popupList as MutableList<Array<String>>, true)

                return processComprehensionModeOutput.createOutputComprehensionMode(openFaceOutputFolderPath)
            }
            Config.HIGHLIGHT_LINES_OPERATION -> {
                return processCodingModeOutput.getHighlightedAttentionLines(document, editor, fileOnFocus)
            }
            Config.REMOVE_HIGHLIGHTED_LINES_OPERATION -> {
                val lineHighlighter = LineHighlighter()
                lineHighlighter.removeAllHighlighter(editor)
                return Config.OK_CODE
            }
        }
        return Config.NULL_CODE
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
        private var checkIfFocusIsOnFile = Config.NULL_CODE
        private var openFaceOutputFolderPath: String = ""
        private var mode: String = Config.CODING_MODE

        fun percentage(value: Int, percentage: Int): Int {
            val valuePercentage: Int = (value * percentage) / 100
            return value - valuePercentage
        }
    }

    object Variables {
        val neuroSkyAttention = NeuroSkyAttention()
        var attentionList: MutableList<Array<String>>? = null
        var popupList: MutableList<Array<String>>? = ArrayList()

        var checkAttentionValue: Int = 0
        var oldAttentionValue: Int = 0
    }

    class AttentionThread : Runnable {
        override fun run() {
            Variables.attentionList = Variables.neuroSkyAttention.attention
        }
    }

    class AttentionIndicatorThread : Runnable {
        override fun run() {
            while (true) {
                Variables.checkAttentionValue = Variables.neuroSkyAttention.checkAttention()
            }
        }
    }
}