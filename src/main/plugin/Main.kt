package plugin

import plugin.activityTracker.EventAnalyzer
import plugin.activityTracker.TrackerLog
import plugin.activityTracker.tracking.ActivityTracker
import plugin.activityTracker.tracking.CompilationTracker
import plugin.activityTracker.tracking.PsiPathProvider
import plugin.activityTracker.tracking.TaskNameProvider
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import plugin.config.Config
import java.io.File


class Main: AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        val application = ApplicationManager.getApplication()
        val pathToTrackingLogFile = Config.ACTIVITY_TRACKER_DATASET_FILENAME
        val trackerLog = TrackerLog(pathToTrackingLogFile).initWriter(parentDisposable = application, writeFrequencyMs = 10000L)
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
        createOutputDirs()
    }

    private fun createOutputDirs() {
        File(Config.CODING_MODE_PATH).mkdirs()
        File(Config.COMPREHENSION_MODE_PATH).mkdirs()
    }
}
