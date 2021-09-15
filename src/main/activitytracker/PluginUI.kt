package activitytracker

import activitytracker.EventAnalyzer.Result.*
import activitytracker.Plugin.Companion.pluginId
import activitytracker.liveplugin.*
import activitytracker.locAlgorithm.ProcessPluginOutput
import activitytracker.locAlgorithm.gui.TextHighlightAttention
import com.intellij.CommonBundle
import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.diagnostic.Logger
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

    private var isMonitoring = false
    private var isButtonHighlightActive = false

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
                plugin.toggleTracking()
                if(isMonitoring) {
                    Thread.sleep(5000)
                    check(event, 0)
                    isMonitoring = false
                    isButtonHighlightActive = true
                }
                else {
                    check(event, 2)
                    isMonitoring = true
                    isButtonHighlightActive = false
                }
            }
            override fun update(event: AnActionEvent) {
                icon = if(isMonitoring) {
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
                if(!isMonitoring && isButtonHighlightActive) {
                    check(event, 1)
                    isButtonHighlightActive = false
                }
                else if(!isMonitoring && !isButtonHighlightActive) {
                    check(event, 2)
                    isButtonHighlightActive = true
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

    private fun check(event: AnActionEvent, operation: Int) {
        val project: Project? = event.getData(PlatformDataKeys.PROJECT)

        val messageTitle = "No project or file selected!"
        val messageContent = "Select a Project and a File."

        if (project == null) {
            Messages.showInfoMessage(messageTitle, messageContent)
        } else {
            val editorManager = FileEditorManager.getInstance(project)
            val editor = editorManager.selectedTextEditor
            if (editor == null) {
                Messages.showInfoMessage(messageTitle, messageContent)
            } else {
                val document = Objects.requireNonNull(editor).document
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
                if (psiFile == null) {
                    Messages.showInfoMessage(messageTitle, messageContent)
                } else {
                    val virtualFile = psiFile.originalFile.virtualFile
                    val path = virtualFile.path

                    when (operation) {
                        0 -> {
                            processPluginOutput.createPluginOutput(path)
                        }
                        1 -> {
                            processPluginOutput.getHighlightedAttentionLines(document, editor, path)
                        }
                        2 -> {
                            val textHighlightAttention = TextHighlightAttention()
                            textHighlightAttention.removeAllHighlighter(editor)
                        }
                    }
                }
            }
        }
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
        val toggleTracking = object: AnAction(), DumbAware {
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
        }
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
}