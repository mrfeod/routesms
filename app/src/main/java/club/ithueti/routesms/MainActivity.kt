package club.ithueti.routesms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import club.ithueti.routesms.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var routeAdapter: RouteAdapter
    private var routes: List<RouteRecord> = emptyList()
    private var selectedRouteId: String = MappingStore.DEFAULT_ID
    private var bindingFields = false
    private var savePending = false
    private val saveHandler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { saveCurrentFields() }
    private val smsActivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshSmsActivity()
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        getSharedPreferences("permissions", MODE_PRIVATE).edit().putBoolean("requested", true).apply()
        updatePermissionButton()
        if (allPermissionsGranted()) {
            Toast.makeText(this, "Все разрешения предоставлены", Toast.LENGTH_SHORT).show()
            refreshSims()
        } else {
            Toast.makeText(this, "Предоставлены не все разрешения", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        routeAdapter = RouteAdapter()
        binding.spinnerRoutes.adapter = routeAdapter
        binding.spinnerHeartbeatHours.apply {
            minValue = HealthSettings.MIN_INTERVAL_HOURS
            maxValue = HealthSettings.MAX_INTERVAL_HOURS
            wrapSelectorWheel = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setOnValueChangedListener { _, _, newValue ->
                if (bindingFields || selectedRouteId != MappingStore.SERVICE_ID) return@setOnValueChangedListener
                val previousHours = HealthSettings.intervalHours(this@MainActivity)
                val savedHours = HealthSettings.setIntervalHours(this@MainActivity, newValue)
                if (savedHours != previousHours) {
                    App.scheduleHealthCheck(this@MainActivity)
                    binding.textSaveState.text = if (savedHours == 0) "Heartbeat выключен" else "Сохранено"
                }
            }
        }
        binding.spinnerRoutes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val nextId = routes.getOrNull(position)?.id ?: return
                if (nextId == selectedRouteId) return
                flushPendingSave()
                selectedRouteId = nextId
                showSelectedRoute()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (bindingFields) return
                binding.textSaveState.text = "Сохранение…"
                saveHandler.removeCallbacks(saveRunnable)
                savePending = true
                saveHandler.postDelayed(saveRunnable, 500)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        binding.editAlias.addTextChangedListener(watcher)
        binding.editPhone.addTextChangedListener(watcher)
        binding.editBotToken.addTextChangedListener(watcher)
        binding.editChatId.addTextChangedListener(watcher)

        binding.btnRefresh.setOnClickListener { refreshSims() }
        binding.btnRequestPerm.setOnClickListener { handlePermissionClick() }
        binding.btnTest.setOnClickListener { sendTestMessage() }

        loadRoutes()
        updatePermissionButton()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButton()
        if (hasPhonePermission()) refreshSims()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            smsActivityReceiver,
            IntentFilter(MappingStore.ACTION_SMS_ACTIVITY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(smsActivityReceiver)
        super.onStop()
    }

    override fun onPause() {
        flushPendingSave()
        super.onPause()
    }

    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(Manifest.permission.READ_PHONE_NUMBERS)
    }

    private fun missingPermissions() = requiredPermissions().filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun allPermissionsGranted() = missingPermissions().isEmpty()

    private fun handlePermissionClick() {
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            updatePermissionButton()
            return
        }
        val requestedBefore = getSharedPreferences("permissions", MODE_PRIVATE).getBoolean("requested", false)
        val permanentlyDenied = requestedBefore && missing.none { shouldShowRequestPermissionRationale(it) }
        if (permanentlyDenied) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun updatePermissionButton() {
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            binding.btnRequestPerm.isEnabled = false
            binding.btnRequestPerm.text = "✓ Разрешения есть"
            return
        }
        binding.btnRequestPerm.isEnabled = true
        val requestedBefore = getSharedPreferences("permissions", MODE_PRIVATE).getBoolean("requested", false)
        val permanentlyDenied = requestedBefore && missing.none { shouldShowRequestPermissionRationale(it) }
        binding.btnRequestPerm.text = if (permanentlyDenied) "Открыть настройки разрешений" else "Разрешить доступ"
    }

    private fun hasPhonePermission() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED

    private fun refreshSims() {
        if (!hasPhonePermission()) {
            handlePermissionClick()
            return
        }
        flushPendingSave()
        SimRepository.refresh(this)
            .onSuccess { loadRoutes() }
            .onFailure { Toast.makeText(this, it.message ?: "Не удалось обновить SIM", Toast.LENGTH_LONG).show() }
    }

    private fun loadRoutes() {
        routes = MappingStore.allRoutes(this)
        if (routes.none { it.id == selectedRouteId }) selectedRouteId = routes.firstOrNull()?.id ?: MappingStore.DEFAULT_ID
        routeAdapter.notifyDataSetChanged()
        val selectedIndex = routes.indexOfFirst { it.id == selectedRouteId }
        if (selectedIndex >= 0 && binding.spinnerRoutes.selectedItemPosition != selectedIndex) {
            binding.spinnerRoutes.setSelection(selectedIndex, false)
        }
        showSelectedRoute()
    }

    private fun showSelectedRoute() {
        val route = routes.firstOrNull { it.id == selectedRouteId } ?: return
        bindingFields = true
        binding.textTitle.text = route.displayName()
        binding.layoutAlias.isVisible = route.kind == RouteKind.SIM
        binding.editAlias.setText(route.alias)
        binding.editPhone.setText(route.effectivePhoneNumber)
        binding.editBotToken.setText(route.botToken)
        binding.editChatId.setText(route.chatId)
        binding.spinnerHeartbeatHours.value = HealthSettings.intervalHours(this)
        binding.textSaveState.text = if (
            route.kind == RouteKind.SERVICE && HealthSettings.intervalHours(this) == 0
        ) "Heartbeat выключен" else "Сохранено"

        val isSim = route.kind == RouteKind.SIM
        val isService = route.kind == RouteKind.SERVICE
        binding.textStatus.isVisible = isSim
        binding.textDescription.isVisible = !isSim
        binding.layoutHeartbeat.isVisible = isService
        binding.layoutPhone.isVisible = isSim
        binding.textSlot.isVisible = isSim
        binding.textOperator.isVisible = isSim
        binding.textSubscriptionId.isVisible = isSim
        if (isSim) {
            binding.textStatus.text = if (route.active) "● Активна" else "● Неактивна"
            binding.textStatus.setTextColor(Color.parseColor(if (route.active) "#55B878" else "#6F7782"))
            binding.textSlot.text = "Слот: ${route.slotIndex?.plus(1)?.toString() ?: "—"}"
            binding.textOperator.text = "Оператор: ${route.carrierName.ifBlank { "—" }}"
            binding.textSubscriptionId.text = "Subscription ID: ${route.subscriptionId ?: "—"}"
        } else {
            binding.textDescription.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.textDescription.text = if (route.kind == RouteKind.DEFAULT) {
                "Используется, если SIM входящего SMS определить не удалось"
            } else {
                "Heartbeat, ошибки и служебные события"
            }
        }
        if (isService) binding.textSmsActivity.text = smsActivityText()
        bindingFields = false
    }

    private fun flushPendingSave() {
        if (savePending) {
            saveHandler.removeCallbacks(saveRunnable)
            saveCurrentFields()
        }
    }

    private fun saveCurrentFields() {
        if (bindingFields) return
        savePending = false
        val current = routes.firstOrNull { it.id == selectedRouteId } ?: return
        val updated = current.copy(
            alias = if (current.kind == RouteKind.SIM) binding.editAlias.text?.toString()?.trim().orEmpty() else current.alias,
            manualPhoneNumber = if (current.kind == RouteKind.SIM) {
                binding.editPhone.text?.toString()?.trim().orEmpty().takeUnless { it == current.phoneNumber }.orEmpty()
            } else current.manualPhoneNumber,
            botToken = binding.editBotToken.text?.toString()?.trim().orEmpty(),
            chatId = binding.editChatId.text?.toString()?.trim().orEmpty()
        )
        MappingStore.saveRoute(this, updated)
        routes = routes.map { if (it.id == updated.id) updated else it }
        binding.textSaveState.text = "Сохранено"
        routeAdapter.notifyDataSetChanged()
        binding.textTitle.text = updated.displayName()
    }

    private fun smsActivityText(): String {
        val sims = routes.filter { it.kind == RouteKind.SIM }
        if (sims.isEmpty()) return "SIM-карты ещё не обнаружены"
        val formatter = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT
        )
        return sims.joinToString("\n") { sim ->
            val time = if (sim.lastSmsAt == 0L) "ещё не было" else formatter.format(java.util.Date(sim.lastSmsAt))
            "${if (sim.active) "●" else "○"} ${sim.displayName()} — $time"
        }
    }

    private fun refreshSmsActivity() {
        val storedById = MappingStore.allRoutes(this).associateBy { it.id }
        routes = routes.map { route ->
            storedById[route.id]?.let { stored -> route.copy(lastSmsAt = stored.lastSmsAt) } ?: route
        }
        if (selectedRouteId == MappingStore.SERVICE_ID) {
            binding.textSmsActivity.text = smsActivityText()
        }
    }

    private fun sendTestMessage() {
        flushPendingSave()
        val route = MappingStore.loadRoute(this, selectedRouteId)
        if (!route.config.isConfigured) {
            Toast.makeText(this, "Заполните Bot Token и Chat ID", Toast.LENGTH_LONG).show()
            return
        }
        binding.btnTest.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching {
                TelegramClient(route.botToken).sendMessage(
                    route.chatId,
                    when (route.kind) {
                        RouteKind.SERVICE -> HealthReport.build(this@MainActivity)
                        RouteKind.SIM -> HealthReport.simInfo(route)
                        RouteKind.DEFAULT -> "↪️ Route SMS — резервный маршрут\nИспользуется, если SIM входящего SMS определить не удалось."
                    }
                )
            }
            binding.btnTest.isEnabled = true
            Toast.makeText(
                this@MainActivity,
                if (result.isSuccess) "Тестовое сообщение отправлено" else "Ошибка: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private inner class RouteAdapter : BaseAdapter() {
        override fun getCount() = routes.size
        override fun getItem(position: Int) = routes[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@MainActivity).inflate(R.layout.item_route, parent, false)
            val route = getItem(position)
            val active = route.kind != RouteKind.SIM || route.active
            view.findViewById<TextView>(R.id.textName).apply {
                text = route.displayName()
                alpha = if (active) 1f else 0.45f
            }
            view.findViewById<TextView>(R.id.textDetails).apply {
                text = when (route.kind) {
                    RouteKind.SIM -> listOfNotNull(
                        route.effectivePhoneNumber.takeIf { route.alias.isNotBlank() && it.isNotBlank() },
                        route.slotIndex?.let { "слот ${it + 1}" },
                        route.carrierName.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    RouteKind.DEFAULT -> "резервный маршрут"
                    RouteKind.SERVICE -> "системные сообщения"
                }
                alpha = if (active) 0.75f else 0.35f
            }
            view.findViewById<TextView>(R.id.textIndicator).apply {
                text = "●"
                setTextColor(Color.parseColor(when {
                    route.kind != RouteKind.SIM -> "#5E82AA"
                    route.active -> "#55B878"
                    else -> "#6F7782"
                }))
            }
            view.setBackgroundColor(Color.TRANSPARENT)
            return view
        }
    }
}
