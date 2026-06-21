package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import com.v2ray.ang.R
import com.v2ray.ang.AppConfig
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.Utils

class SettingsActivity : AppCompatActivity() {   // เปลี่ยนจาก BaseActivity เป็น AppCompatActivity

    companion object {
        const val PREF_PER_APP_PROXY = "pref_per_app_proxy"
        const val PREF_SPEED_ENABLED = "pref_speed_enabled"
        const val PREF_SNIFFING_ENABLED = "pref_sniffing_enabled"
        const val PREF_PROXY_SHARING = "pref_proxy_sharing_enabled"
        const val PREF_LOCAL_DNS_ENABLED = "pref_local_dns_enabled"
        const val PREF_REMOTE_DNS = "pref_remote_dns"
        const val PREF_DOMESTIC_DNS = "pref_domestic_dns"
        const val PREF_ROUTING_DOMAIN_STRATEGY = "pref_routing_domain_strategy"
        const val PREF_ROUTING_MODE = "pref_routing_mode"
        const val PREF_ROUTING_CUSTOM = "pref_routing_custom"
        const val PREF_FORWARD_IPV6 = "pref_forward_ipv6"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = getString(R.string.title_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var perAppProxy: CheckBoxPreference
        private lateinit var speedEnabled: CheckBoxPreference
        private lateinit var sniffingEnabled: CheckBoxPreference
        private lateinit var proxySharing: CheckBoxPreference
        private lateinit var domainStrategy: ListPreference
        private lateinit var routingMode: ListPreference
        private lateinit var routingCustom: Preference
        private lateinit var forwardIpv6: CheckBoxPreference
        private lateinit var enableLocalDns: CheckBoxPreference
        private lateinit var domesticDns: EditTextPreference
        private lateinit var remoteDns: EditTextPreference
        private lateinit var mode: ListPreference

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.pref_settings)

            // ผูก Preference
            perAppProxy = findPreference(PREF_PER_APP_PROXY)!!
            speedEnabled = findPreference(PREF_SPEED_ENABLED)!!
            sniffingEnabled = findPreference(PREF_SNIFFING_ENABLED)!!
            proxySharing = findPreference(PREF_PROXY_SHARING)!!
            domainStrategy = findPreference(PREF_ROUTING_DOMAIN_STRATEGY)!!
            routingMode = findPreference(PREF_ROUTING_MODE)!!
            routingCustom = findPreference(PREF_ROUTING_CUSTOM)!!
            forwardIpv6 = findPreference(PREF_FORWARD_IPV6)!!
            enableLocalDns = findPreference(PREF_LOCAL_DNS_ENABLED)!!
            domesticDns = findPreference(PREF_DOMESTIC_DNS)!!
            remoteDns = findPreference(PREF_REMOTE_DNS)!!
            mode = findPreference(AppConfig.PREF_MODE)!!

            setupListeners()
        }

        private fun setupListeners() {
            perAppProxy.setOnPreferenceClickListener {
                startActivity(Intent(requireActivity(), PerAppProxyActivity::class.java))
                true
            }

            speedEnabled.setOnPreferenceClickListener { restartProxy(); true }
            sniffingEnabled.setOnPreferenceClickListener { restartProxy(); true }
            forwardIpv6.setOnPreferenceClickListener { restartProxy(); true }
            enableLocalDns.setOnPreferenceClickListener { restartProxy(); true }

            proxySharing.setOnPreferenceClickListener {
                if (proxySharing.isChecked) {
                    requireActivity().toast(R.string.toast_warning_pref_proxysharing)
                }
                restartProxy()
                true
            }

            domainStrategy.setOnPreferenceChangeListener { _, _ -> restartProxy(); true }
            routingMode.setOnPreferenceChangeListener { _, _ -> restartProxy(); true }

            routingCustom.setOnPreferenceClickListener {
                startActivity(Intent(requireActivity(), RoutingSettingsActivity::class.java))
                false
            }

            domesticDns.setOnPreferenceChangeListener { _, any ->
                val nval = any as? String ?: ""
                domesticDns.summary = if (nval.isEmpty()) AppConfig.DNS_DIRECT else nval
                restartProxy()
                true
            }

            remoteDns.setOnPreferenceChangeListener { _, any ->
                val nval = any as? String ?: ""
                remoteDns.summary = if (nval.isEmpty()) AppConfig.DNS_AGENT else nval
                restartProxy()
                true
            }

            mode.setOnPreferenceChangeListener { _, newValue ->
                updatePerAppProxy(newValue.toString())
                restartProxy()
                true
            }

            mode.dialogLayoutResource = R.layout.preference_with_help_link
        }

        override fun onStart() {
            super.onStart()
            updatePerAppProxy(mode.value)
            
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            remoteDns.summary = prefs.getString(PREF_REMOTE_DNS, AppConfig.DNS_AGENT)
            domesticDns.summary = prefs.getString(PREF_DOMESTIC_DNS, AppConfig.DNS_DIRECT)
        }

        private fun updatePerAppProxy(modeStr: String?) {
            val isVpnMode = modeStr == "VPN"
            perAppProxy.isEnabled = isVpnMode
            if (!isVpnMode) {
                perAppProxy.isChecked = false
            }
        }

        private fun restartProxy() {
            if (isRunning()) {
                Utils.stopVService(requireContext())
                Utils.startVService(requireContext(), AngConfigManager.configs.index)
            }
        }

        private fun isRunning(): Boolean {
            // TODO: เช็คสถานะจริง (ชั่วคราว)
            return false
        }
    }

    fun onModeHelpClicked(view: View) {
        Utils.openUri(this, AppConfig.v2rayNGWikiMode)
    }
}