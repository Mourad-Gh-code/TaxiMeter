package com.example.taximeter

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class ProfileFragment : Fragment() {

    // Driver Information
    private val driverName = "Mourad Ghalloumi"
    private val driverEmail = "mouradghalloumi@email.com"
    private val driverAge = 20
    private val carMatricule = "ABC-1234"
    private val rating = 5.0f // out of 5

    // Views
    private lateinit var avatarInitial: TextView
    private lateinit var userName: TextView
    private lateinit var userEmail: TextView
    private lateinit var menuMoreInfo: RelativeLayout
    private lateinit var menuShareQR: RelativeLayout

    private var qrCodeContainer: LinearLayout? = null
    private var isQRVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        avatarInitial = view.findViewById(R.id.avatarInitial)
        userName = view.findViewById(R.id.userName)
        userEmail = view.findViewById(R.id.userEmail)
        menuMoreInfo = view.findViewById(R.id.menuMoreInfo)
        menuShareQR = view.findViewById(R.id.menuShareQR)

        // Set driver info
        setupDriverInfo()

        // Setup click listeners
        menuMoreInfo.setOnClickListener {
            showDriverInfoDialog()
        }

        menuShareQR.setOnClickListener {
            toggleQRCode(view)
        }
    }

    private fun setupDriverInfo() {
        // Set avatar initial (first letter of name)
        val initial = if (driverName.isNotEmpty()) driverName.first().uppercase() else "?"
        avatarInitial.text = initial

        // Set name and email
        userName.text = driverName
        userEmail.text = driverEmail
    }

    private fun showDriverInfoDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_driver_info, null)

        // Find views in dialog
        val txtName = dialogView.findViewById<TextView>(R.id.txtDriverName)
        val txtEmail = dialogView.findViewById<TextView>(R.id.txtDriverEmail)
        val txtAge = dialogView.findViewById<TextView>(R.id.txtDriverAge)
        val txtMatricule = dialogView.findViewById<TextView>(R.id.txtCarMatricule)
        val ratingContainer = dialogView.findViewById<LinearLayout>(R.id.ratingContainer)

        // Set data
        txtName.text = driverName
        txtEmail.text = driverEmail
        txtAge.text = "$driverAge years"
        txtMatricule.text = carMatricule

        // Setup rating stars
        setupRatingStars(ratingContainer, rating)

        // Show dialog
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun setupRatingStars(container: LinearLayout, rating: Float) {
        container.removeAllViews()

        val fullStars = rating.toInt()
        val hasHalfStar = rating - fullStars >= 0.5f

        for (i in 1..5) {
            val star = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    marginEnd = 8
                }
                when {
                    i <= fullStars -> setImageResource(R.drawable.ic_star)
                    i == fullStars + 1 && hasHalfStar -> setImageResource(R.drawable.ic_star_half)
                    else -> setImageResource(R.drawable.ic_star_empty)
                }
            }
            container.addView(star)
        }
    }

    private fun toggleQRCode(parentView: View) {
        if (isQRVisible) {
            // Hide QR code
            qrCodeContainer?.visibility = View.GONE
            isQRVisible = false
        } else {
            // Show/Create QR code
            if (qrCodeContainer == null) {
                createQRCodeView(parentView)
            }
            qrCodeContainer?.visibility = View.VISIBLE
            isQRVisible = true
        }
    }

    private fun createQRCodeView(parentView: View) {
//        val scrollContent = parentView.findViewById<View>(R.id.scrollContent).parent as ViewGroup

        qrCodeContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            setBackgroundColor(Color.parseColor("#1B1F21"))

            // Title
            addView(TextView(requireContext()).apply {
                text = "Scan QR Code"
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, 16)
            })

            // QR Code Image
            val qrBitmap = generateQRCode(getDriverInfoForQR())
            addView(ImageView(requireContext()).apply {
                setImageBitmap(qrBitmap)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
                setPadding(0, 16, 0, 16)
            })

            // Info text
            addView(TextView(requireContext()).apply {
                text = "Scan this code to view driver information"
                textSize = 14f
                setTextColor(Color.parseColor("#8B9DAE"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 16, 0, 0)
            })
        }

        // Find the parent layout and add QR container
        val parentLayout = parentView.findViewById<View>(R.id.menuShareQR).parent as ViewGroup
        val index = parentLayout.indexOfChild(parentView.findViewById(R.id.menuShareQR)) + 1
        parentLayout.addView(qrCodeContainer, index)
    }

    private fun getDriverInfoForQR(): String {
        return """
            |Driver Information
            |==================
            |Name: $driverName
            |Email: $driverEmail
            |Age: $driverAge years
            |Car Matricule: $carMatricule
            |Rating: $rating / 5.0
        """.trimMargin()
    }

    private fun generateQRCode(text: String): Bitmap {
        val size = 512
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}