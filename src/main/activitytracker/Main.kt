package activitytracker

import activitytracker.tracking.ActivityTracker
import activitytracker.tracking.CompilationTracker
import activitytracker.tracking.PsiPathProvider
import activitytracker.tracking.TaskNameProvider
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption


class Main: AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        val application = ApplicationManager.getApplication()
        val pathToTrackingLogFile = "${PathManager.getPluginsPath()}/activity-tracker/ide-events.csv"
        val pathToMonitoringAttentionLogFile = "${PathManager.getPluginsPath()}/activity-tracker/ide-events-attention.csv"
        val trackerLog = TrackerLog(pathToTrackingLogFile, pathToMonitoringAttentionLogFile).initWriter(parentDisposable = application, writeFrequencyMs = 10000L)
        val tracker = ActivityTracker(
            CompilationTracker.instance,
            PsiPathProvider.instance,
            TaskNameProvider.instance,
            trackerLog,
            parentDisposable = application,
            logTrackerCallDuration = false
        )
        val plugin = Plugin(tracker, trackerLog, PropertiesComponent.getInstance()).init()
        val eventAnalyzer = EventAnalyzer(trackerLog)
        PluginUI(plugin, trackerLog, eventAnalyzer, parentDisposable = application).init()
    }

    private fun importSettings() {
        val sourceCustomSettingsFile: InputStream = javaClass.getResourceAsStream("/META-INF/options/keymap.xml")
        val sourceKeymapsFile: InputStream = javaClass.getResourceAsStream("/META-INF/keymaps/Windows copy.xml")
        File(PathManager.getConfigPath() + "/keymaps/").mkdirs()

        val targetCustomSettingsFile = PathManager.getConfigPath() + "/options/keymap.xml"
        val targetKeymapsFile = PathManager.getConfigPath() + "/keymaps/Windows copy.xml"

        Files.copy(sourceCustomSettingsFile, Paths.get(targetCustomSettingsFile), StandardCopyOption.REPLACE_EXISTING)
        Files.copy(sourceKeymapsFile, Paths.get(targetKeymapsFile), StandardCopyOption.REPLACE_EXISTING)
    }
}
