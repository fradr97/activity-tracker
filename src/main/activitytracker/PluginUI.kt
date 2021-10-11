package activitytracker

import activitytracker.EventAnalyzer.Result.*
import activitytracker.Plugin.Companion.pluginId
import activitytracker.liveplugin.*
import activitytracker.locAlgorithm.ProcessPluginOutput
import activitytracker.locAlgorithm.gui.TextHighlightAttention
import activitytracker.locAlgorithm.neuroSkyAttention.NeuroSkyAttention
import activitytracker.locAlgorithm.utils.FileParser
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
        eventAnalyzer.runner = { task ->
            runInBackground("Analyzing activity log", task = { task() })
        }
        return this
    }

    fun update(state: Plugin.State) {
        this.state = state
        updateWidget(widgetId)
    }

    private fun registerPopup(parentDisposable: Disposable) {
        registerAction("$pluginId-Popup", "ctrl shift alt O", "", "Activity Tracker Popup", parentDisposable, {
            val project = it.project
            if (project != null) {
                createListPopup(it.dataContext).showCenteredInCurrentWindow(project)
            }
        })
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
                        Thread.sleep(sleep.toLong())
                        plugin.toggleTracking()
                        Variables.neuroSkyAttention.stopConnection()

                        val fileParser = FileParser()
                        fileParser.writeFile("${com.intellij.openapi.application.PathManager.getPluginsPath()}/activity-tracker/attention.csv",
                            Variables.attentionList as MutableList<Array<String>>, false)

                        val op = monitoringOperations(document, editor, filePath, createAttentionDataset)

                        isButtonHighlightActive = if(op == NULL_CODE) {
                            Messages.showErrorDialog(noTrackingActivityMessage, noTrackingActivityTitle)
                            false
                        } else {
                            true
                        }
                    } else {
                        Messages.showMessageDialog("Waiting!", "Waiting Neurosky ...", null)
                        val isStarted: Boolean = Variables.neuroSkyAttention.waitForStarting()

                        if (isStarted) {
                            Messages.showMessageDialog("Running!", "Neurosky Is Running ...", null)
                            val attentionThread = Thread(AttentionThread())
                            attentionThread.start()

                            plugin.toggleTracking()
                            monitoringOperations(document, editor, filePath, removeHighlightedLines)
                            isButtonHighlightActive = false
                        } else {
                            Messages.showErrorDialog("Neurosky not working!", "Neurosky Not Working!")
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
                        val op = monitoringOperations(document, editor, filePath, highlightLines)

                        if(op == NULL_CODE)
                            Messages.showErrorDialog(noTrackingActivityMessage, noTrackingActivityTitle)
                        else {
                            isButtonHighlightActive = false
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
                    IconLoader.getIcon("AllIcons.Actions.Colors")
                } else {
                    IconLoader.getIcon("AllIcons.Actions.Cancel")
                }
                event.presentation.icon = icon
            }
        }
        registerAction("Highlight Lines", "", "ToolbarRunGroup",
            "Highlight Lines", parentDisposable, buttonHighlight)
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

    private fun monitoringOperations(document: Document, editor: Editor, path: String, operation: Int): Int {
        when (operation) {
            createAttentionDataset -> {
                return processPluginOutput.createPluginOutput(path, Variables.attentionList as MutableList<Array<String>>)
            }
            highlightLines -> {
                return processPluginOutput.getHighlightedAttentionLines(document, editor, path)
            }
            removeHighlightedLines -> {
                val textHighlightAttention = TextHighlightAttention()
                textHighlightAttention.removeAllHighlighter(editor)
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
        /*val toggleTracking = object: AnAction(), DumbAware {
            override fun actionPerformed(event: AnActionEvent) = plugin.toggleTracking()
            override fun update(event: AnActionEvent) {
                event.presentation.text = if (state.isTracking) "Stop Tracking" else "Start Tracking"
            }
        }
        val togglePollIdeState = object: CheckboxAction("Poll IDE State") {
            override fun isSelected(event: AnActionEvent) = state.pollIdeState
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enablePollIdeState(value)
        }
        val toggleTrackActions = object: CheckboxAction("Track IDE Actions") {
            override fun isSelected(event: AnActionEvent) = state.trackIdeActions
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enableTrackIdeActions(value)
        }
        val toggleTrackKeyboard = object: CheckboxAction("Track Keyboard") {
            override fun isSelected(event: AnActionEvent) = state.trackKeyboard
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enableTrackKeyboard(value)
        }
        val toggleTrackMouse = object: CheckboxAction("Track Mouse") {
            override fun isSelected(event: AnActionEvent) = state.trackMouse
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enableTrackMouse(value)
        }*/
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
                                        showNotification("There were ${result.errors.size} errors parsing log file. See IDE log for details.")
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

        //registerAction("Start/Stop Activity Tracking", action = toggleTracking)
        registerAction("Roll Tracking Log", action = rollCurrentLog)
        registerAction("Clear Tracking Log", action = clearCurrentLog)
        // TODO register other actions

        return DefaultActionGroup().apply {
            //add(toggleTracking)
            add(DefaultActionGroup("Current Log", true).apply {
                add(showStatistics)
                add(openLogInIde)
                add(openLogFolder)
                addSeparator()
                add(rollCurrentLog)
                add(clearCurrentLog)
            })
            /*addSeparator()
            add(DefaultActionGroup("Settings", true).apply {
                add(toggleTrackActions)
                add(togglePollIdeState)
                add(toggleTrackKeyboard)
                add(toggleTrackMouse)
            })*/
            add(openHelp)
        }
    }

    companion object {
        const val createAttentionDataset = 0
        const val highlightLines = 1
        const val removeHighlightedLines = 2
        const val sleep = 5000
        const val noTrackingActivityMessage = "No tracking activity detected."
        const val noTrackingActivityTitle = "Tracker File Empty!"
        const val noProjectOrFileMessage = "No project or file selected!"
        const val noProjectOrFileTitle = "Select a Project and a File."
        const val OK_CODE = 0
        const val NULL_CODE = -1
    }

    object Variables {
        val neuroSkyAttention = NeuroSkyAttention()
        var attentionList: MutableList<Array<String>>? = null
    }

    class AttentionThread : Runnable {
        override fun run() {
            Variables.attentionList = Variables.neuroSkyAttention.attention
        }
    }
}