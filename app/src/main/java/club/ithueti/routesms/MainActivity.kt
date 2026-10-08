package club.ithueti.routesms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import club.ithueti.routesms.databinding.ActivityMainBinding

private const val REQ_PERM_SMS = 1001

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, MappingStore.allKeys(this).toMutableList())
        binding.listMappings.adapter = adapter

        binding.btnSave.setOnClickListener {
            val key = binding.editKey.text.toString().trim()
            val token = binding.editBotToken.text.toString().trim()
            val chat = binding.editChatId.text.toString().trim()
            if (key.isNotEmpty() && token.isNotEmpty() && chat.isNotEmpty()) {
                MappingStore.saveMapping(this, key, BotConfig(token, chat))
                refreshList()
            }
        }

        binding.btnRemove.setOnClickListener {
            val key = binding.editKey.text.toString().trim()
            if (key.isNotEmpty()) {
                MappingStore.removeMapping(this, key)
                refreshList()
            }
        }

        binding.btnRequestPerm.setOnClickListener { requestSmsPermissionsIfNeeded() }
        binding.btnRunHealthNow.setOnClickListener {
            WorkManager.getInstance(this).enqueue(OneTimeWorkRequest.from(HealthCheckWorker::class.java))
        }

        requestSmsPermissionsIfNeeded()
    }

    private fun refreshList() {
        val keys = MappingStore.allKeys(this).toMutableList()
        adapter.clear()
        adapter.addAll(keys)
        adapter.notifyDataSetChanged()
    }

    private fun requestSmsPermissionsIfNeeded() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.READ_SMS)
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), REQ_PERM_SMS)
        }
    }
}
