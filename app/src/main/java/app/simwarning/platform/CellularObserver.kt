package app.simwarning.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.*
import androidx.core.content.ContextCompat
import app.simwarning.core.*

/** No location, identifiers, signal-strength polling, or internet/connectivity queries. */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
@SuppressLint("MissingPermission") // The service checks access; revocation is caught and re-evaluated.
class CellularObserver(private val context: Context, private val changed: (Connectivity) -> Unit) {
    private val subscriptions = context.getSystemService(SubscriptionManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private data class Registration(val token: Any, val close: () -> Unit)
    private val registrations = mutableMapOf<Int, Registration>()
    private val observations = mutableMapOf<Int, SimService>()
    private var subscribed = false
    private var closed = false
    private val subscriptionListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() { rebuild() }
    }
    fun start() {
        try {
            if (Build.VERSION.SDK_INT >= 30) subscriptions.addOnSubscriptionsChangedListener(ContextCompat.getMainExecutor(context), subscriptionListener)
            else subscriptions.addOnSubscriptionsChangedListener(subscriptionListener)
            subscribed = true
            rebuild()
        } catch (_: RuntimeException) { changed(Connectivity.UNKNOWN) }
    }
    fun rebuild() {
        if (closed) return
        try {
            val ids = subscriptions.activeSubscriptionInfoList.orEmpty().map { it.subscriptionId }.toSet()
            (registrations.keys - ids).forEach { id -> registrations.remove(id)?.close?.invoke(); observations.remove(id) }
            if (ids.isEmpty()) {
                val absent = telephony.simState in listOf(TelephonyManager.SIM_STATE_ABSENT, TelephonyManager.SIM_STATE_CARD_IO_ERROR, TelephonyManager.SIM_STATE_CARD_RESTRICTED)
                changed(aggregateServices(listOf(classifyCellular(CellularEvidence(definitelyNoSim = absent)))))
                return
            }
            for (id in ids - registrations.keys) register(id)
            changed(aggregateServices(observations.values.toList()))
        } catch (_: RuntimeException) { changed(Connectivity.UNKNOWN) }
    }
    private fun register(id: Int) {
        val manager = telephony.createForSubscriptionId(id)
        val token = Any()
        observations[id] = SimService.UNKNOWN
        fun received(state: ServiceState) {
            if (closed || registrations[id]?.token !== token) return
            observations[id] = try { mapState(state, manager) } catch (_: RuntimeException) { SimService.UNKNOWN }
            changed(aggregateServices(observations.values.toList()))
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val callback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                override fun onServiceStateChanged(serviceState: ServiceState) { received(serviceState) }
            }
            registrations[id] = Registration(token) { manager.unregisterTelephonyCallback(callback) }
            try {
                if (Build.VERSION.SDK_INT >= 33) manager.registerTelephonyCallback(TelephonyManager.INCLUDE_LOCATION_DATA_NONE, ContextCompat.getMainExecutor(context), callback)
                else manager.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
            } catch (_: RuntimeException) { registrations.remove(id); observations[id] = SimService.UNKNOWN }
        } else {
            val listener = object : PhoneStateListener() {
                override fun onServiceStateChanged(serviceState: ServiceState) { received(serviceState) }
            }
            registrations[id] = Registration(token) { manager.listen(listener, PhoneStateListener.LISTEN_NONE) }
            try { manager.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE) }
            catch (_: RuntimeException) { registrations.remove(id); observations[id] = SimService.UNKNOWN }
        }
    }
    private fun mapState(state: ServiceState, manager: TelephonyManager): SimService {
        if (Build.VERSION.SDK_INT >= 30) {
            val cellular = state.networkRegistrationInfoList.filter { it.transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN }
            val registered = cellular.any { info -> info.isRegistered && info.availableServices.any { it == NetworkRegistrationInfo.SERVICE_TYPE_VOICE || it == NetworkRegistrationInfo.SERVICE_TYPE_DATA } }
            return classifyCellular(CellularEvidence(cellular.isNotEmpty(), registered, state.state == ServiceState.STATE_EMERGENCY_ONLY))
        }
        val bearer = manager.voiceNetworkType
        return classifyCellular(CellularEvidence(legacyVoiceInService = state.state == ServiceState.STATE_IN_SERVICE,
            legacyVoiceNetworkIsCellular = bearer != TelephonyManager.NETWORK_TYPE_UNKNOWN && bearer != TelephonyManager.NETWORK_TYPE_IWLAN,
            emergencyOnly = state.state == ServiceState.STATE_EMERGENCY_ONLY))
    }
    fun close() {
        closed = true
        registrations.values.forEach { runCatching { it.close() } }
        registrations.clear(); observations.clear()
        if (subscribed) runCatching { subscriptions.removeOnSubscriptionsChangedListener(subscriptionListener) }
    }
}
