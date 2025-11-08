package com.example.taximeter

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

private const val DEFAULT_MEASUREMENT = "Km / €"
class SettingsFragment : Fragment() {

    // SharedPreferences
    private lateinit var prefs: android.content.SharedPreferences

    // Views
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var switchSound: SwitchCompat
    private lateinit var switchLocation: SwitchCompat
    private lateinit var txtLanguageValue: TextView
    private lateinit var txtThemeValue: TextView
    private lateinit var txtMeasurementValue: TextView


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize SharedPreferences
        prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)

        // Initialize views
        initializeViews(view)

        // Load saved settings
        loadSettings()

        // Setup listeners
        setupListeners(view)
    }

    private fun initializeViews(view: View) {
        switchNotifications = view.findViewById(R.id.switchNotifications)
        switchSound = view.findViewById(R.id.switchSound)
        switchLocation = view.findViewById(R.id.switchLocation)
        txtLanguageValue = view.findViewById(R.id.txtLanguageValue)
        txtThemeValue = view.findViewById(R.id.txtThemeValue)
        txtMeasurementValue = view.findViewById(R.id.txtMeasurementValue)
    }

    private fun loadSettings() {
        // Load switch states
        switchNotifications.isChecked = prefs.getBoolean("notifications", true)
        switchSound.isChecked = prefs.getBoolean("sound", true)
        switchLocation.isChecked = prefs.getBoolean("location", true)

        // Load text values
        txtLanguageValue.text = prefs.getString("language", "English")
        txtThemeValue.text = prefs.getString("theme", "Dark")
        txtMeasurementValue.text = prefs.getString("measurement",   DEFAULT_MEASUREMENT)    }

    private fun setupListeners(view: View) {
        // Notifications Switch
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
            // Handle notifications toggle
        }

        // Sound & Haptics Switch
        switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sound", isChecked).apply()
            // Handle sound toggle
        }

        // Location Services Switch
        switchLocation.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("location", isChecked).apply()
            // Handle location toggle
        }

        // Language Selection
        view.findViewById<View>(R.id.settingLanguage).setOnClickListener {
            showLanguageDialog()
        }

        // Privacy Policy
        view.findViewById<View>(R.id.settingPrivacyPolicy).setOnClickListener {
            // Navigate to privacy policy
            // You can open a WebView or another fragment
        }

        // Theme Selection
        view.findViewById<View>(R.id.settingTheme).setOnClickListener {
            showThemeDialog()
        }

        // Measurement Units
        view.findViewById<View>(R.id.settingMeasurement).setOnClickListener {
            showMeasurementDialog()
        }

        // Log Out Button
        view.findViewById<Button>(R.id.btnLogOut).setOnClickListener {
            showLogOutDialog()
        }

        // Delete Account Button
        view.findViewById<Button>(R.id.btnDeleteAccount).setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    /**
     * Show Language Selection Dialog
     */
    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Français", "العربية", "Español", "Deutsch")
        val currentLanguage = prefs.getString("language", "English")
        val currentIndex = languages.indexOf(currentLanguage)

        AlertDialog.Builder(requireContext())
            .setTitle("Select Language")
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val selectedLanguage = languages[which]
                txtLanguageValue.text = selectedLanguage
                prefs.edit().putString("language", selectedLanguage).apply()
                dialog.dismiss()
                // Apply language change here
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show Theme Selection Dialog
     */
    private fun showThemeDialog() {
        val themes = arrayOf("Light", "Dark", "Auto")
        val currentTheme = prefs.getString("theme", "Dark")
        val currentIndex = themes.indexOf(currentTheme)

        AlertDialog.Builder(requireContext())
            .setTitle("Select Theme")
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                val selectedTheme = themes[which]
                txtThemeValue.text = selectedTheme
                prefs.edit().putString("theme", selectedTheme).apply()
                dialog.dismiss()
                // Apply theme change here
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show Measurement Units Dialog
     */
    private fun showMeasurementDialog() {
        val units = arrayOf(DEFAULT_MEASUREMENT, "miles / $", "km / $", "miles / €")
        val currentUnit = prefs.getString("measurement", DEFAULT_MEASUREMENT)
        val currentIndex = units.indexOf(currentUnit)

        AlertDialog.Builder(requireContext())
            .setTitle("Select Measurement Units")
            .setSingleChoiceItems(units, currentIndex) { dialog, which ->
                val selectedUnit = units[which]
                txtMeasurementValue.text = selectedUnit
                prefs.edit().putString("measurement", selectedUnit).apply()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show Log Out Confirmation Dialog
     */
    private fun showLogOutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Log Out") { _, _ ->
                // Clear user session
                prefs.edit().clear().apply()

                // Navigate to login screen
                // val intent = Intent(requireContext(), LoginActivity::class.java)
                // startActivity(intent)
                // requireActivity().finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show Delete Account Confirmation Dialog
     */
    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                showFinalDeleteConfirmation()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show Final Delete Confirmation
     */
    private fun showFinalDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Final Confirmation")
            .setMessage("This will permanently delete your account and all associated data. This action is irreversible.")
            .setPositiveButton("Yes, Delete") { _, _ ->
                // Call API to delete account
                deleteAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Delete Account (Implement API call here)
     */
    private fun deleteAccount() {
        // TODO: Implement account deletion API call

        // Clear all data
        prefs.edit().clear().apply()

        // Navigate to login/welcome screen
        // val intent = Intent(requireContext(), WelcomeActivity::class.java)
        // intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        // startActivity(intent)
    }
}