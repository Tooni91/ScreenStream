package info.dvkr.screenstream.mjpeg

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.elvishew.xlog.XLog
import info.dvkr.screenstream.common.StreamingModule
import info.dvkr.screenstream.common.getLog
import info.dvkr.screenstream.mjpeg.internal.MjpegEvent
import info.dvkr.screenstream.mjpeg.internal.MjpegState
import info.dvkr.screenstream.mjpeg.internal.MjpegStreamingService
import info.dvkr.screenstream.mjpeg.ui.MjpegStreamingFragment
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.koin.core.parameter.parametersOf
import org.koin.core.scope.Scope

@Single
@Named(MjpegKoinQualifier)
public class MjpegStreamingModule : StreamingModule {

    internal companion object {
        internal val Id: StreamingModule.Id = StreamingModule.Id("MJPEG")
    }

    override val id: StreamingModule.Id = Id

    override val priority: Int = 20

    private val _streamingServiceIsReady: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val streamingServiceIsReady: StateFlow<Boolean>
        get() = _streamingServiceIsReady.asStateFlow()

    private val _mjpegStateFlow: MutableStateFlow<MjpegState?> = MutableStateFlow(null)
    internal val mjpegStateFlow: StateFlow<MjpegState?>
        get() = _mjpegStateFlow.asStateFlow()

    @MainThread
    override fun getName(context: Context): String {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }
        return context.getString(R.string.mjpeg_stream_fragment_mode_local)
    }

    @MainThread
    override fun getContentDescription(context: Context): String {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }
        return context.getString(R.string.mjpeg_stream_fragment_mode_local_description)
    }

    @MainThread
    override fun showDescriptionDialog(context: Context, lifecycleOwner: LifecycleOwner) {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

        MaterialDialog(context).show {
            lifecycleOwner(lifecycleOwner)
            icon(R.drawable.mjpeg_ic_permission_dialog_24dp)
            title(R.string.mjpeg_stream_fragment_mode_local)
            message(R.string.mjpeg_stream_fragment_mode_local_details)
            positiveButton(android.R.string.ok)
        }
    }

    @MainThread
    override fun getFragmentClass(): Class<out Fragment> = MjpegStreamingFragment::class.java

    internal val mjpegSettings: MjpegSettings
        get() = requireNotNull(_scope).get()

    private var _scope: Scope? = null

    @MainThread
    override fun createStreamingService(context: Context) {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

        if (_streamingServiceIsReady.value) {
            XLog.e(getLog("createStreamingService", "Already ready"), IllegalStateException("Already ready"))
            return
        }

        XLog.d(getLog("createStreamingService"))

        MjpegService.startService(context, MjpegEvent.Intentable.StartService.toIntent(context))
    }

    @MainThread
    override fun sendEvent(event: StreamingModule.AppEvent) {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }
        XLog.d(getLog("sendEvent", "Event $event"))

        when (event) {
            is StreamingModule.AppEvent.StartStream -> sendEvent(MjpegStreamingService.InternalEvent.StartStream)
            is StreamingModule.AppEvent.StopStream -> sendEvent(MjpegEvent.Intentable.StopStream("User action: Button"))
        }
    }

    @MainThread
    internal fun sendEvent(event: MjpegEvent) {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }
        XLog.d(getLog("sendEvent", "Event $event"))

        when (event) {
            is MjpegEvent.CreateStreamingService -> if (_streamingServiceIsReady.value) {
                XLog.e(getLog("sendEvent", "Service already started. Ignoring"), IllegalStateException("Service already started. Ignoring"))
                checkNotNull(_scope)
            } else {
                check(_scope == null)

                val scope = MjpegKoinScope().scope
                _scope = scope
                _mjpegStateFlow.value = MjpegState()
                scope.get<MjpegStreamingService> { parametersOf(event.service, _mjpegStateFlow) }.start()
                _streamingServiceIsReady.value = true
            }

            else -> if (_streamingServiceIsReady.value)
                requireNotNull(_scope).get<MjpegStreamingService>().sendEvent(event)
            else
                XLog.e(getLog("sendEvent", "Module not active. Ignoring"), IllegalStateException("$event: Module not active. Ignoring"))
        }
    }

    @MainThread
    override suspend fun destroyStreamingService() {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }
        XLog.d(getLog("destroyStreamingService"))

        _scope?.let { scope ->
            withContext(NonCancellable) { scope.get<MjpegStreamingService>().destroyService() }
            _mjpegStateFlow.value = null
            scope.close()
            _scope = null
            _streamingServiceIsReady.value = false
        } ?: XLog.i(getLog("destroyStreamingService", "Scope is null"))

        XLog.d(getLog("destroyStreamingService", "Done"))
    }
}