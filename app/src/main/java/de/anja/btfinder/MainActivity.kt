package de.anja.btfinder

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.location.LocationManagerCompat

class MainActivity : Activity() {

    private val devices = LinkedHashMap<String, DeviceEntry>()
    private var visibleList: List<DeviceEntry> = emptyList()
    private var tracked: DeviceEntry? = null

    private var scanning = false
    private var adapter: BluetoothAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null

    private lateinit var statusText: TextView
    private lateinit var scanButton: Button
    private lateinit var filterEdit: EditText
    private lateinit var namedOnly: CheckBox
    private lateinit var listView: ListView
    private lateinit var listAdapter: DeviceAdapter
    private lateinit var listPane: LinearLayout
    private lateinit var trackPane: LinearLayout
    private lateinit var trackName: TextView
    private lateinit var trackInfo: TextView
    private lateinit var trackRssi: TextView
    private lateinit var trackBar: SignalBar
    private lateinit var beepBox: CheckBox

    // ---------- Scan ----------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val rec = result.scanRecord
            val mfrData = rec?.manufacturerSpecificData
            val mfr = if (mfrData != null && mfrData.size() > 0) mfrData.keyAt(0) else null
            val entry = devices.getOrPut(result.device.address) { DeviceEntry(result.device.address) }
            entry.update(rec?.deviceName, mfr, result.rssi)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            statusText.text = "Scan fehlgeschlagen (Code $errorCode)"
            scanButton.text = "Scan starten"
        }
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasPermissions() = requiredPermissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startScan() {
        if (!hasPermissions()) {
            requestPermissions(requiredPermissions(), REQ_PERM)
            return
        }
        val a = adapter
        if (a == null || !a.isEnabled) {
            statusText.text = "Bluetooth ist aus. Bitte einschalten."
            return
        }
        if (Build.VERSION.SDK_INT < 31) {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!LocationManagerCompat.isLocationEnabled(lm)) {
                statusText.text = "Android 8-11 braucht eingeschaltete Standortdienste für BLE-Scans."
                return
            }
        }
        val scanner = a.bluetoothLeScanner ?: run {
            statusText.text = "Kein BLE-Scanner verfügbar."
            return
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            statusText.text = "Berechtigung fehlt."
            return
        }
        scanning = true
        scanButton.text = "Scan stoppen"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun stopScan() {
        if (scanning) {
            try {
                adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: SecurityException) {
            }
        }
        scanning = false
        scanButton.text = "Scan starten"
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        if (code == REQ_PERM) {
            if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) startScan()
            else statusText.text = "Ohne Berechtigung kann ich nicht scannen."
        }
    }

    // ---------- Periodische UI-Updates ----------

    private val uiTick = object : Runnable {
        override fun run() {
            refreshList()
            refreshTracking()
            handler.postDelayed(this, 250)
        }
    }

    private fun refreshList() {
        if (trackPane.visibility == View.VISIBLE) return
        devices.values.removeAll { it.ageMs() > DROP_AFTER_MS && it !== tracked }
        val q = filterEdit.text.toString().trim().lowercase()
        visibleList = devices.values
            .filter { !namedOnly.isChecked || !it.name.isNullOrBlank() }
            .filter {
                q.isEmpty() || (it.name?.lowercase()?.contains(q) == true) ||
                    it.address.lowercase().contains(q)
            }
            .sortedByDescending { it.rssi }
        listAdapter.notifyDataSetChanged()
        if (scanning) statusText.text = "${visibleList.size} Geräte (${devices.size} gesehen)"
    }

    private var nextBeepAt = 0L

    private fun refreshTracking() {
        val d = tracked ?: return
        trackName.text = d.name ?: "(ohne Namen)"
        trackInfo.text = listOfNotNull(d.address, d.manufacturerLabel()).joinToString(" · ")
        val lost = d.ageMs() > LOST_AFTER_MS
        if (lost) {
            trackRssi.text = "Signal weg"
            trackRssi.setTextColor(Color.GRAY)
            trackBar.level = 0f
            return
        }
        trackRssi.text = "${d.rssi.toInt()} dBm"
        trackRssi.setTextColor(Color.WHITE)
        val level = ((d.rssi + 100f) / 60f).coerceIn(0f, 1f) // -100 dBm = 0, -40 dBm = voll
        trackBar.level = level
        if (beepBox.isChecked) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now >= nextBeepAt) {
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                nextBeepAt = now + (1000 - 880 * level).toLong() // nah = schnell
            }
        }
    }

    private fun track(d: DeviceEntry) {
        tracked = d
        nextBeepAt = 0L
        listPane.visibility = View.GONE
        trackPane.visibility = View.VISIBLE
    }

    private fun leaveTracking() {
        tracked = null
        trackPane.visibility = View.GONE
        listPane.visibility = View.VISIBLE
    }

    // ---------- Lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        tone = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (_: RuntimeException) {
            null
        }
        buildUi()
        handler.post(uiTick)
    }

    override fun onPause() {
        super.onPause()
        stopScan()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tone?.release()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (trackPane.visibility == View.VISIBLE) leaveTracking() else super.onBackPressed()
    }

    // ---------- UI (programmatisch, keine Layout-XMLs) ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setPadding(dp(16), dp(36), dp(16), dp(16))

        // Liste
        listPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scanButton = Button(this).apply {
            text = "Scan starten"
            setOnClickListener { if (scanning) stopScan() else startScan() }
        }
        statusText = text(14f, Color.LTGRAY).apply { setPadding(0, dp(8), 0, dp(8)) }
        statusText.text = if (adapter == null) "Dieses Gerät hat kein Bluetooth." else "Bereit."
        filterEdit = EditText(this).apply {
            hint = "Filter: Name oder Adresse"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setSingleLine()
        }
        namedOnly = CheckBox(this).apply {
            text = "Nur Geräte mit Namen"
            setTextColor(Color.LTGRAY)
        }
        listAdapter = DeviceAdapter()
        listView = ListView(this).apply {
            divider = null
            this.adapter = listAdapter
            setOnItemClickListener { _, _, pos, _ -> visibleList.getOrNull(pos)?.let { track(it) } }
        }
        listPane.addView(scanButton)
        listPane.addView(statusText)
        listPane.addView(filterEdit)
        listPane.addView(namedOnly)
        listPane.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))

        // Such-Ansicht
        trackPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
        }
        trackName = text(24f, bold = true).apply { gravity = Gravity.CENTER }
        trackInfo = text(13f, Color.GRAY).apply { gravity = Gravity.CENTER }
        trackRssi = text(64f, bold = true).apply { gravity = Gravity.CENTER }
        trackBar = SignalBar(this)
        beepBox = CheckBox(this).apply {
            text = "Piepton (schneller = näher)"
            setTextColor(Color.LTGRAY)
            isChecked = true
        }
        val back = Button(this).apply {
            text = "Zurück zur Liste"
            setOnClickListener { leaveTracking() }
        }
        trackPane.addView(trackName)
        trackPane.addView(trackInfo)
        trackPane.addView(trackRssi, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(24) })
        trackPane.addView(trackBar, LinearLayout.LayoutParams(-1, dp(32)))
        trackPane.addView(beepBox, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(24) })
        trackPane.addView(back, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        root.addView(listPane, ViewGroup.LayoutParams(-1, -1))
        root.addView(trackPane, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)
    }

    private inner class DeviceAdapter : BaseAdapter() {
        override fun getCount() = visibleList.size
        override fun getItem(i: Int) = visibleList[i]
        override fun getItemId(i: Int) = i.toLong()

        override fun getView(i: Int, convert: View?, parent: ViewGroup): View {
            val row = (convert as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
                val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                col.addView(text(17f, bold = true).apply { tag = "t" })
                col.addView(text(12f, Color.GRAY).apply { tag = "s" })
                addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                addView(text(20f, bold = true).apply { tag = "r" })
            }
            val d = visibleList[i]
            val stale = d.ageMs() > 8_000
            val title = row.findViewWithTag<TextView>("t")
            val sub = row.findViewWithTag<TextView>("s")
            val rssi = row.findViewWithTag<TextView>("r")
            title.text = d.name ?: "(ohne Namen)"
            title.setTextColor(if (stale) Color.GRAY else Color.WHITE)
            sub.text = listOfNotNull(d.address, d.manufacturerLabel(), "vor ${d.ageMs() / 1000}s")
                .joinToString(" · ")
            rssi.text = "${d.rssi.toInt()}"
            rssi.setTextColor(if (stale) Color.GRAY else Color.HSVToColor(floatArrayOf(
                (((d.rssi + 100f) / 60f).coerceIn(0f, 1f)) * 120f, 0.7f, 0.95f)))
            return row
        }
    }

    companion object {
        private const val REQ_PERM = 1
        private const val LOST_AFTER_MS = 5_000L
        private const val DROP_AFTER_MS = 120_000L
    }
}
