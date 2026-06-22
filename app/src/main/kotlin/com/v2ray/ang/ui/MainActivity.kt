package com.v2ray.ang.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.helper.ItemTouchHelper
import com.google.android.material.navigation.NavigationView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.extension.toast
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.V2rayConfigUtil
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import java.net.URL

import androidx.recyclerview.widget.helper.ItemTouchHelper


class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityMainBinding
    private val adapter by lazy { MainRecyclerAdapter(this) }
    private var mItemTouchHelper: ItemTouchHelper? = null
    private val mainViewModel: MainViewModel by lazy { ViewModelProvider(this)[MainViewModel::class.java] }

    // ตัวแปรเก็บ requestCode และ Uri ขณะรอ permission
    private var pendingRequestCode: Int = -1
    private var pendingUri: Uri? = null

    // Launcher สำหรับขอสิทธิ์กล้อง (CAMERA)
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // เปิด ScannerActivity ด้วย requestCode ที่เก็บไว้
            startActivityForResult(Intent(this, ScannerActivity::class.java), pendingRequestCode)
        } else {
            toast(R.string.toast_permission_denied)
        }
    }

    // Launcher สำหรับขอสิทธิ์อ่าน Storage (READ_EXTERNAL_STORAGE)
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingUri?.let { uri ->
                try {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        importCustomizeConfig(reader.readText())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(R.string.toast_failure)
                }
            }
        } else {
            toast(R.string.toast_permission_denied)
        }
    }

    companion object {
        private const val REQUEST_CODE_VPN_PREPARE = 0
        private const val REQUEST_SCAN = 1
        private const val REQUEST_FILE_CHOOSER = 2
        private const val REQUEST_SCAN_URL = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        title = getString(R.string.title_server)
        setSupportActionBar(binding.toolbar)

        binding.fab.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                Utils.stopVService(this)
            } else if (defaultDPreference.getPrefString(AppConfig.PREF_MODE, "VPN") == "VPN") {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    startActivityForResult(intent, REQUEST_CODE_VPN_PREPARE)
                }
            } else {
                startV2Ray()
            }
        }
        
        binding.layoutTest.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                binding.tvTestState.text = getString(R.string.connection_test_testing)
                mainViewModel.testCurrentServerRealPing()
            }
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        binding.navView.setNavigationItemSelectedListener(this)
        binding.version.text = "v${BuildConfig.VERSION_NAME} (${Libv2ray.checkVersionX()})"

        setupViewModelObserver()
    }

    private fun setupViewModelObserver() {
        mainViewModel.updateListAction.observe(this) { index ->
            if (index != null && index >= 0) adapter.updateSelectedItem(index) 
            else adapter.updateConfigList()
        }
        mainViewModel.updateTestResultAction.observe(this) { binding.tvTestState.text = it }
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.changeable = !(isRunning ?: false)
            if (isRunning == true) {
                binding.fab.setImageResource(R.drawable.ic_v)
                binding.tvTestState.text = getString(R.string.connection_connected)
            } else {
                binding.fab.setImageResource(R.drawable.ic_v_idle)
                binding.tvTestState.text = getString(R.string.connection_not_connected)
            }
        }
        mainViewModel.startListenBroadcast()
    }

    fun startV2Ray() {
        if (AngConfigManager.configs.index < 0) return
        if (!Utils.startVService(this, AngConfigManager.configs.index)) { }
    }

    override fun onResume() {
        super.onResume()
        adapter.updateConfigList()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_VPN_PREPARE -> if (resultCode == RESULT_OK) startV2Ray()
            REQUEST_SCAN -> if (resultCode == RESULT_OK) importBatchConfig(data?.getStringExtra("SCAN_RESULT"))
            REQUEST_FILE_CHOOSER -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) readContentFromUri(uri)
            }
            REQUEST_SCAN_URL -> if (resultCode == RESULT_OK) importConfigCustomUrl(data?.getStringExtra("SCAN_RESULT"))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> { importQRcode(REQUEST_SCAN); true }
        R.id.import_clipboard -> { importClipboard(); true }
        R.id.import_manually_vmess -> { startActivity(Intent(this, ServerActivity::class.java).putExtra("position", -1)); adapter.updateConfigList(); true }
        R.id.import_manually_ss -> { startActivity(Intent(this, Server3Activity::class.java).putExtra("position", -1)); adapter.updateConfigList(); true }
        R.id.import_manually_socks -> { startActivity(Intent(this, Server4Activity::class.java).putExtra("position", -1)); adapter.updateConfigList(); true }
        R.id.import_config_custom_clipboard -> { importConfigCustomClipboard(); true }
        R.id.import_config_custom_local -> { importConfigCustomLocal(); true }
        R.id.import_config_custom_url -> { importConfigCustomUrlClipboard(); true }
        R.id.import_config_custom_url_scan -> { importQRcode(REQUEST_SCAN_URL); true }
        R.id.sub_update -> { importConfigViaSub(); true }
        R.id.export_all -> { if (AngConfigManager.shareAll2Clipboard() != 0) toast(R.string.toast_failure); true }
        R.id.ping_all -> { mainViewModel.testAllTcping(); true }
        else -> super.onOptionsItemSelected(item)
    }

    // เปลี่ยนจากใช้ RxPermissions เป็น cameraPermissionLauncher
    fun importQRcode(requestCode: Int): Boolean {
        pendingRequestCode = requestCode
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        return true
    }

    fun importClipboard(): Boolean {
        return try { importBatchConfig(Utils.getClipboard(this)); true } catch (e: Exception) { e.printStackTrace(); false }
    }

    fun importBatchConfig(server: String?, subid: String = "") {
        if (AngConfigManager.importBatchConfig(server, subid) > 0) {
            toast(R.string.toast_success); adapter.updateConfigList()
        } else toast(R.string.toast_failure)
    }

    fun importConfigCustomClipboard(): Boolean {
        val configText = Utils.getClipboard(this)
        if (TextUtils.isEmpty(configText)) { toast(R.string.toast_none_data_clipboard); return false }
        importCustomizeConfig(configText); return true
    }

    fun importConfigCustomLocal(): Boolean = try { showFileChooser(); true } catch (e: Exception) { false }

    fun importConfigCustomUrlClipboard(): Boolean {
        val url = Utils.getClipboard(this)
        if (TextUtils.isEmpty(url)) { toast(R.string.toast_none_data_clipboard); return false }
        return importConfigCustomUrl(url)
    }

    fun importConfigCustomUrl(url: String?): Boolean {
        if (!Utils.isValidUrl(url)) { toast(R.string.toast_invalid_url); return false }
        GlobalScope.launch(Dispatchers.IO) {
            val configText = try { URL(url).readText() } catch (e: Exception) { "" }
            launch(Dispatchers.Main) { importCustomizeConfig(configText) }
        }
        return true
    }

    fun importConfigViaSub(): Boolean {
        toast(R.string.title_sub_update)
        AngConfigManager.configs.subItem.forEach { sub ->
            if (!TextUtils.isEmpty(sub.url) && Utils.isValidUrl(sub.url)) {
                GlobalScope.launch(Dispatchers.IO) {
                    val configText = try { URL(sub.url).readText() } catch (e: Exception) { "" }
                    launch(Dispatchers.Main) { importBatchConfig(Utils.decode(configText), sub.id) }
                }
            }
        }
        return true
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        try { startActivityForResult(Intent.createChooser(intent, getString(R.string.title_file_chooser)), REQUEST_FILE_CHOOSER) }
        catch (ex: Exception) { toast(R.string.toast_require_file_manager) }
    }

    // เปลี่ยนจากใช้ RxPermissions เป็น storagePermissionLauncher
    private fun readContentFromUri(uri: Uri) {
        pendingUri = uri
        storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun importCustomizeConfig(server: String?) {
        if (server == null || !V2rayConfigUtil.isValidConfig(server)) { toast(R.string.toast_config_file_invalid); return }
        if (AngConfigManager.importCustomizeConfig(server) > 0) toast(R.string.toast_config_file_invalid)
        else { toast(R.string.toast_success); adapter.updateConfigList() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { moveTaskToBack(false); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) binding.drawerLayout.closeDrawer(GravityCompat.START)
        else super.onBackPressed()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sub_setting -> startActivity(Intent(this, SubSettingActivity::class.java))
            R.id.settings -> startActivity(Intent(this, SettingsActivity::class.java).putExtra("isRunning", mainViewModel.isRunning.value == true))
            R.id.feedback -> Utils.openUri(this, AppConfig.v2rayNGIssues)
            R.id.promotion -> Utils.openUri(this, AppConfig.promotionUrl)
            R.id.logcat -> startActivity(Intent(this, LogcatActivity::class.java))
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
