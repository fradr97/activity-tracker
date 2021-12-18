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
import plugin.locAlgorithm.codingMode.CodingModeHighlightOptions
import plugin.locAlgorithm.codingMode.EditorHighlighter
import plugin.locAlgorithm.codingMode.ProcessCodingModeOutput
import plugin.locAlgorithm.comprehensionMode.ProcessComprehensionModeOutput
import plugin.locAlgorithm.comprehensionMode.ProcessPopupQuestions
import plugin.neuroSkyAttention.NeuroSkyAttention
import plugin.utils.FileUtils
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*


class PluginUI(
    private val plugin: Plugin,
    private val trackerLog: TrackerLog,
    private val eventAnalyzer: EventAnalyzer,
    private val parentDisposable: Disposable
) {
    private val log = Logger.getInstance(PluginUI::class.java)
    private val actionGroup: DefaultActionGroup by lazy { createActionGroup() }
    private val widgetId = "Activity Tracker Widget"
    private val processCodingModeOutput = ProcessCodingModeOutput()
    private val processComprehensionModeOutput = ProcessComprehensionModeOutput()

    fun init(): PluginUI {
        plugin.setUI(this)
        registerWidget(parentDisposable)
        registerPopup(parentDisposable)
        registerButtonStartStopMonitoring(parentDisposable)
        registerButtonHighlightLines(parentDisposable)
        //registerAttentionIndicator(parentDisposable)
        registerSettings(parentDisposable)
        eventAnalyzer.runner = { task ->
            runInBackground("Analyzing activity log", task = { task() })
        }
        state.codingModeIsTracking = false
        //val attentionThread = Thread(AttentionThread())
        //attentionThread.start()

        return this
    }

    fun update(pState: Plugin.State) {
        state = pState
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
                        if(state.codingModeIsTracking) {
                            stopCodingModeTracking()
                        } else {
                            attentionList?.clear()
                            Messages.showInfoMessage(Config.WAITING_HEADSET_MESSAGE, Config.WAITING_TITLE)
                            val isStarted: Boolean = neuroSkyAttention.waitToStart()

                            if (isStarted) {
                                Messages.showInfoMessage(Config.TRACKER_RUNNING_MESSAGE, Config.RUNNING_TITLE)
                                plugin.toggleTracking()
                                monitoringOperations(document, editor, fileOnFocusPath, Config.REMOVE_HIGHLIGHTED_LINES_OPERATION)
                                isButtonHighlightActive = false
                            } else {
                                Messages.showErrorDialog(Config.HEADSET_NOT_WORKING_MESSAGE, Config.HEADSET_NOT_WORKING_TITLE)
                            }
                        }
                    }
                    else {  /** COMPREHENSION MODE **/
                        if(comprehensionModeIsTracking) {
                            stopComprehensionModeTracking()
                        } else {
                            attentionList?.clear()

                            if(openFaceOutputFolderPath == "" || questionsFileComprehensionPath == "") {
                                Messages.showWarningDialog(Config.ERROR_SET_SETTINGS_MESSAGE, Config.ERROR_SET_SETTINGS_TITLE)
                            } else {
                                Messages.showInfoMessage(Config.TRACKER_RUNNING_MESSAGE, Config.RUNNING_TITLE)
                                comprehensionModeIsTracking = true
                                monitoringOperations(document, editor, fileOnFocusPath, Config.REMOVE_HIGHLIGHTED_LINES_OPERATION)
                                isButtonHighlightActive = false

                                val popupThread = Thread(ShowPopupThread())
                                popupThread.start()
                            }
                        }
                    }
                }
            }
            override fun update(event: AnActionEvent) {
                checkIfFocusIsOnFile = checkProjectOrFile(event)

                icon = if(checkIfFocusIsOnFile == Config.NULL_CODE) {
                    if(state.codingModeIsTracking) {
                        stopCodingModeTracking()
                    }
                    if(comprehensionModeIsTracking) {
                        stopComprehensionModeTracking()
                    }
                    IconLoader.getIcon("AllIcons.Plugins.Disabled")
                } else {
                    /** If there is a problem getting attention values
                     * (e.g. headset off during tracking) */
                    /*if(neuroSkyAttention.error()) {
                        if(state.codingModeIsTracking) {
                            stopCodingModeTracking()
                        }
                    }*/

                    if(state.codingModeIsTracking || comprehensionModeIsTracking) {
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
        Messages.showInfoMessage(Config.TRACKER_STOPPING_MESSAGE, Config.STOPPING_TITLE)
        plugin.toggleTracking()
        isButtonHighlightActive = true
    }

    private fun stopComprehensionModeTracking() {
        comprehensionModeIsTracking = false
        isButtonHighlightActive = true
        Messages.showInfoMessage(Config.DATA_PROCESSING_MESSAGE, Config.WAITING_TITLE)
        Thread.sleep(Config.SLEEP_BEFORE_PROCESSING.toLong())
        monitoringOperations(document, editor, fileOnFocusPath, Config.CREATE_COMPREHENSION_MODE_DATASET_OPERATION)
    }

    private fun registerButtonHighlightLines(parentDisposable: Disposable) {
        var icon: Icon

        val buttonHighlight = object: AnAction("Highlight Lines"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                if(mode == Config.CODING_MODE) {
                    if (checkIfFocusIsOnFile == Config.OK_CODE) {
                        if (!state.codingModeIsTracking && isButtonHighlightActive) {
                            setCodingModeOptions()
                        }
                        else if(!state.codingModeIsTracking && !isButtonHighlightActive) {
                            removeHighlightedLines()
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

    private fun setCodingModeOptions() {
        val arithmeticAVGCheckBox = JRadioButton(Config.ARITHMETIC_MEAN_OPTION, true)
        //val exponentialAVGCheckBox = JRadioButton(Config.EXPONENTIAL_MEAN_OPTION)
        val lastAttentionValueCheckBox = JRadioButton(Config.LAST_ATTENTION_VALUE_OPTION)

        val buttonGroup = ButtonGroup()
        buttonGroup.add(arithmeticAVGCheckBox)
        //buttonGroup.add(exponentialAVGCheckBox)
        buttonGroup.add(lastAttentionValueCheckBox)

        val jPanel = JPanel()
        jPanel.add(arithmeticAVGCheckBox)
        //jPanel.add(exponentialAVGCheckBox)
        jPanel.add(lastAttentionValueCheckBox)

        when (codingModeOptions) {
            Config.CODING_MODE_ARITHMETIC_AVG_OPTION -> {
                arithmeticAVGCheckBox.isSelected = true
                //exponentialAVGCheckBox.isSelected = false
                lastAttentionValueCheckBox.isSelected = false
            }
            /* Exponential AVG
            Config.CODING_MODE_EXPONENTIAL_AVG_OPTION -> {
                arithmeticAVGCheckBox.isSelected = false
                exponentialAVGCheckBox.isSelected = true
                lastAttentionValueCheckBox.isSelected = false
            }*/
            Config.CODING_MODE_LAST_ATTENTION_OPTION -> {
                arithmeticAVGCheckBox.isSelected = false
                //exponentialAVGCheckBox.isSelected = false
                lastAttentionValueCheckBox.isSelected = true
            }
        }

        val settings = JOptionPane.showConfirmDialog(
            null,
            jPanel,
            Config.HIGHLIGHT_OPTIONS_TITLE,
            JOptionPane.OK_CANCEL_OPTION
        )

        if (settings == JOptionPane.OK_OPTION) {
            if(arithmeticAVGCheckBox.isSelected)
                codingModeOptions = Config.CODING_MODE_ARITHMETIC_AVG_OPTION
            /*else if(exponentialAVGCheckBox.isSelected)
                codingModeOptions = Config.CODING_MODE_EXPONENTIAL_AVG_OPTION*/
            else if(lastAttentionValueCheckBox.isSelected)
                codingModeOptions = Config.CODING_MODE_LAST_ATTENTION_OPTION

            highlightLines()
        }
    }

    private fun highlightLines() {
        Messages.showInfoMessage(Config.DATA_PROCESSING_MESSAGE, Config.WAITING_TITLE)
        Thread.sleep(Config.SLEEP_BEFORE_PROCESSING.toLong())

        val createOperation = monitoringOperations(document, editor, fileOnFocusPath, Config.CREATE_CODING_MODE_DATASET_OPERATION)

        if(createOperation == Config.NULL_CODE) {
            isButtonHighlightActive = false
            Messages.showWarningDialog(Config.NO_TRACKING_ACTIVITY_MESSAGE, Config.NO_TRACKING_ACTIVITY_TITLE)
        } else {
            isButtonHighlightActive = true
            val highlightOperation = monitoringOperations(document, editor, fileOnFocusPath, Config.HIGHLIGHT_LINES_OPERATION)

            if(highlightOperation == Config.NULL_CODE)
                Messages.showErrorDialog(Config.NO_TRACKING_ACTIVITY_MESSAGE, Config.NO_TRACKING_ACTIVITY_TITLE)
            else {
                isButtonHighlightActive = false
            }
        }
    }

    private fun removeHighlightedLines() {
        val op = monitoringOperations(document, editor, fileOnFocusPath, Config.REMOVE_HIGHLIGHTED_LINES_OPERATION)

        if(op == Config.NULL_CODE)
            Messages.showErrorDialog(Config.NO_TRACKING_ACTIVITY_MESSAGE, Config.NO_TRACKING_ACTIVITY_TITLE)
        else {
            isButtonHighlightActive = true
        }
    }

    private fun registerAttentionIndicator(parentDisposable: Disposable) {
        var icon: Icon
        val attentionIndicatorIcon = object: AnAction("Attention Indicator"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) { }
            override fun update(event: AnActionEvent) {
                icon = when (attentionIndicator) {
                    in Config.NO_ATTENTION + 1..Config.LOW_ATTENTION -> {
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
            }
        }
        registerAction("Attention Indicator", "", "ToolbarRunGroup",
            "Attention Indicator", parentDisposable, attentionIndicatorIcon)
    }

    private fun registerSettings(parentDisposable: Disposable) {
        val buttonSettings = object: AnAction("Settings"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                if(!state.codingModeIsTracking && !comprehensionModeIsTracking)
                    setSettings()
            }
            override fun update(event: AnActionEvent) {
                event.presentation.icon = IconLoader.getIcon("AllIcons.General.Settings")
            }
        }
        registerAction("Settings", "", "ToolbarRunGroup",
            "Settings", parentDisposable, buttonSettings)
    }

    private fun setSettings() {
        val ofFolderPathField = JTextField()
        val questionsFilePathField = JTextField()
        val checkBox = JCheckBox()

        ofFolderPathField.text = openFaceOutputFolderPath
        questionsFilePathField.text = questionsFileComprehensionPath
        checkBox.isSelected = mode != Config.CODING_MODE

        val inputFields = arrayOf<Any>(
            Config.SETTINGS_OF_FOLDER_MESSAGE, ofFolderPathField,
            Config.SETTINGS_QUESTIONS_FILE_MESSAGE, questionsFilePathField,
            Config.SETTINGS_CHECKBOX_MESSAGE, checkBox
        )

        val settings = JOptionPane.showConfirmDialog(
            null,
            inputFields,
            Config.SETTINGS_TITLE,
            JOptionPane.OK_CANCEL_OPTION
        )

        if (settings == JOptionPane.OK_OPTION) {
            openFaceOutputFolderPath = ofFolderPathField.text.toString()
            questionsFileComprehensionPath = questionsFilePathField.text.toString()
            mode = if(checkBox.isSelected) {
                Config.COMPREHENSION_MODE
            } else Config.CODING_MODE
        }
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
                    editor = selectedEditor
                    document = selectedDocument
                    fileOnFocusPath = psiFile.originalFile.virtualFile.path
                    Config.OK_CODE
                }
            }
        }
    }

    private fun monitoringOperations(document: Document, editor: Editor, fileOnFocus: String, operation: Int): Int {
        val fileUtils = FileUtils()
        val codingModeHighlightOptions = CodingModeHighlightOptions()

        when (operation) {
            Config.CREATE_CODING_MODE_DATASET_OPERATION -> {
                fileUtils.writeFile(Config.ATTENTION_CODING_MODE_DATASET_FILENAME,
                    attentionList as MutableList<Array<String>>, true)
                return processCodingModeOutput.createOutputCodingMode(fileOnFocus, openFaceOutputFolderPath)
            }
            Config.CREATE_COMPREHENSION_MODE_DATASET_OPERATION -> {
                fileUtils.writeFile(Config.ATTENTION_COMPREHENSION_MODE_DATASET_FILENAME,
                    attentionList as MutableList<Array<String>>, true)
                return Config.OK_CODE
                //return processComprehensionModeOutput.createOutputComprehensionMode(openFaceOutputFolderPath)
            }
            Config.HIGHLIGHT_LINES_OPERATION -> {
                return codingModeHighlightOptions.getHighlightedAttentionLines(document, editor, fileOnFocus, codingModeOptions)
            }
            Config.REMOVE_HIGHLIGHTED_LINES_OPERATION -> {
                val editorHighlighter = EditorHighlighter()
                editorHighlighter.removeAllHighlighter(editor)
                return Config.OK_CODE
            }
        }
        return Config.NULL_CODE
    }

    private fun registerWidget(parentDisposable: Disposable) {
        val presentation = object: StatusBarWidget.TextPresentation {
            override fun getText() = "Activity tracker: " + (if (state.codingModeIsTracking) "on" else "off")
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
        private lateinit var editor: Editor
        private lateinit var document: Document
        private var fileOnFocusPath: String = ""
        private var isButtonHighlightActive = true

        private var comprehensionModeIsTracking = false
        private var checkIfFocusIsOnFile = Config.NULL_CODE
        private var openFaceOutputFolderPath: String = ""
        private var mode: String = Config.CODING_MODE
        private var codingModeOptions: Int = Config.CODING_MODE_ARITHMETIC_AVG_OPTION
        private var questionsFileComprehensionPath = ""

        var state = Plugin.State.defaultValue
        val neuroSkyAttention = NeuroSkyAttention()
        var attentionList: MutableList<Array<String>>? = null
        var attentionIndicator: Int = 0

        private val popupQuestions = ProcessPopupQuestions()

        private var oldLine: Int = 0
        private var line: Int = 0
        /** Used to check if the cursor is in a particular code range*/
        private var from: Int = 0
        private var to: Int = 0

        /** Check if the cursor has left a certain range of code */
        private fun codeRangeIsChanged(): Boolean {
            line = editor.caretModel.logicalPosition.line + 1
            val allCodeRanges: MutableList<Array<Int>> = popupQuestions.getAllCodeRange(questionsFileComprehensionPath, fileOnFocusPath)
            var i = 0
            var isInRange = false

            while(i < allCodeRanges.size && !isInRange) {
                isInRange = popupQuestions.numIsInRange(line, allCodeRanges[i][0], allCodeRanges[i][1])

                if(isInRange &&
                    from != allCodeRanges[i][0] &&
                    to != allCodeRanges[i][1]) {
                    from = allCodeRanges[i][0]
                    to = allCodeRanges[i][1]
                    return true
                }
                i ++
            }
            return false
        }

        /** Return ArrayList response:
         *  the first position contains question proposed by the popup,
         *  the second position contains response given by the user
         */
        @YesNoResult
        fun showCheckAttentionDialog(line: Int): ArrayList<String> {
            val response = ArrayList<String>()
            val check: Boolean = popupQuestions.checkQuestionExists(questionsFileComprehensionPath, fileOnFocusPath, line)

            return if (check) {
                val question: String = popupQuestions.getQuestion()
                val answers = popupQuestions.getAnswers()
                val list: JList<*> = JList<Any?>(answers)

                val scrollPane = JScrollPane(list)
                scrollPane.preferredSize = Dimension(editor.component.width, editor.component.height)

                JOptionPane.showMessageDialog(
                    null, scrollPane, question, JOptionPane.PLAIN_MESSAGE
                )

                val selectedValue: String = if(list.selectedValue == null)
                    Config.NO_POPUP_RESPONSE
                else list.selectedValue.toString()

                response.add(question)
                response.add(selectedValue)
                response
            } else {
                response.add("")
                response.add("")
                response
            }
        }
    }

    class ShowPopupThread: Runnable {
        override fun run() {
            val list: MutableList<Array<String>> = ArrayList()
            var time = 0

            while(comprehensionModeIsTracking) {
                var question: String
                var userAnswer: String

                val check = codeRangeIsChanged()

                if(!check)
                    oldLine = line

                /** The popup will be shown when:
                    - the cursor exits a code block;
                    - the cursor remained in that code block for two seconds per line */
                val cursorRemainInRangeTime = (to - from) * Config.CURSOR_REMAIN_IN_RANGE_SECONDS

                if(check && time > cursorRemainInRangeTime) {
                    val popup = showCheckAttentionDialog(oldLine)
                    question = popup[Config.POPUP_QUESTION_INDEX]
                    userAnswer = popup[Config.POPUP_USER_ANSWER_INDEX]
                    val dateTimeUtils = plugin.utils.DateTimeUtils()

                    list.add(arrayOf(dateTimeUtils.getTimestamp(), question,
                        userAnswer))
                    attentionList = list

                    time = 0
                }

                if(check)
                    time = 0

                time ++
                Thread.sleep(1000)
            }
       }
    }
}