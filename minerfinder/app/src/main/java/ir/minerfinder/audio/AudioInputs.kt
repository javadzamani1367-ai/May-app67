package ir.minerfinder.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import ir.minerfinder.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An input the user can choose. [id] 0 stands for "the phone's default". */
data class AudioInput(val id: Int, val name: String, val isUsb: Boolean)

/** Lists the audio inputs and follows plugging and unplugging (USB microphone, USB sound card, headset). */
class AudioInputs(private val context: Context) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _inputs = MutableStateFlow(read())
    val inputs: StateFlow<List<AudioInput>> = _inputs.asStateFlow()

    init {
        manager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { _inputs.value = read() }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { _inputs.value = read() }
        }, Handler(Looper.getMainLooper()))
    }

    fun device(id: Int): AudioDeviceInfo? =
        if (id == 0) null else manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == id }

    private fun read(): List<AudioInput> {
        val devices = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type in SUPPORTED }
            .map { d ->
                val kind = context.getString(typeLabel(d.type))
                val product = d.productName?.toString().orEmpty()
                val name = if (product.isBlank() || d.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) kind else "$kind — $product"
                AudioInput(d.id, name, d.type in USB)
            }
        return listOf(AudioInput(0, context.getString(R.string.input_default), false)) + devices
    }

    private fun typeLabel(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> R.string.input_builtin
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> R.string.input_usb
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> R.string.input_wired
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> R.string.input_bluetooth
        else -> R.string.input_other
    }

    private companion object {
        val USB = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY)
        val SUPPORTED = USB + setOf(
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        )
    }
}
