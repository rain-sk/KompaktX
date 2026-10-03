package com.noti.restore.ui

import android.Manifest
import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noti.restore.R
import com.noti.restore.service.KompaktXListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inbox — a pull-based viewer for the system's active notifications.
 * No overlays, no accessibility service: notification access only.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var permissionView: View
    private lateinit var inboxView: View
    private lateinit var countLabel: TextView
    private lateinit var persistentSwitch: Switch
    private val adapter = InboxAdapter()

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            InboxPersistent.update(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.list)
        emptyView = findViewById(R.id.empty)
        permissionView = findViewById(R.id.permission_gate)
        inboxView = findViewById(R.id.inbox)
        countLabel = findViewById(R.id.count)
        persistentSwitch = findViewById(R.id.persistent_switch)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btn_clear_all).setOnClickListener {
            KompaktXListener.clearAll()
            InboxPersistent.update(this)
            refresh()
        }

        findViewById<Button>(R.id.btn_grant).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "Open Settings → Apps → Special app access → Notification access, or run:\n" +
                        "adb shell cmd notification allow_listener $packageName/${packageName}.service.KompaktXListener",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        persistentSwitch.setOnCheckedChangeListener { _, on ->
            getSharedPreferences("inbox", MODE_PRIVATE).edit().putBoolean("persistent", on).apply()
            InboxPersistent.update(this)
        }

        // Swipe a row left/right to dismiss that notification.
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.items.getOrNull(vh.bindingAdapterPosition) ?: return
                KompaktXListener.dismiss(item.key)
                InboxPersistent.update(this@MainActivity)
                refresh()
            }
        }).attachToRecyclerView(recycler)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        persistentSwitch.isChecked =
            getSharedPreferences("inbox", MODE_PRIVATE).getBoolean("persistent", true)
        refresh()
    }

    private fun refresh() {
        val granted = KompaktXListener.isEnabled(this)
        permissionView.visibility = if (granted) View.GONE else View.VISIBLE
        inboxView.visibility = if (granted) View.VISIBLE else View.GONE
        if (!granted) return

        KompaktXListener.refresh()
        val items = KompaktXListener.notifications.value
        adapter.items = items
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        countLabel.text = resources.getQuantityString(R.plurals.unread, items.size, items.size)
        InboxPersistent.update(this)
    }

    private inner class InboxAdapter : RecyclerView.Adapter<RowHolder>() {
        var items: List<android.service.notification.StatusBarNotification> = emptyList()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = RowHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.row_notification, parent, false)
        )
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: RowHolder, position: Int) = holder.bind(items[position])
    }

    private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val app: TextView = view.findViewById(R.id.row_app)
        private val title: TextView = view.findViewById(R.id.row_title)
        private val text: TextView = view.findViewById(R.id.row_text)
        private val time: TextView = view.findViewById(R.id.row_time)
        private val dismiss: ImageButton = view.findViewById(R.id.row_dismiss)

        fun bind(item: android.service.notification.StatusBarNotification) {
            val n = item.notification
            val rawTitle = try {
                n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            } catch (_: Exception) { "" }
            val sender = KompaktXListener.latestMessageSender(n)
            val body = KompaktXListener.latestMessageText(n)

            app.text = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(item.packageName, 0)
                )
            } catch (_: Exception) { item.packageName }

            title.text = when {
                rawTitle.isNotBlank() && !sender.isNullOrBlank() &&
                    !sender.equals(rawTitle, ignoreCase = true) -> "$sender · $rawTitle"
                rawTitle.isNotBlank() -> rawTitle
                else -> sender ?: ""
            }
            title.visibility = if (title.text.isNullOrBlank()) View.GONE else View.VISIBLE
            text.text = body
            text.visibility = if (body.isBlank()) View.GONE else View.VISIBLE
            time.text = formatTime(item.postTime)

            itemView.setOnClickListener { KompaktXListener.open(this@MainActivity, item) }
            dismiss.setOnClickListener {
                KompaktXListener.dismiss(item.key)
                InboxPersistent.update(this@MainActivity)
                refresh()
            }
        }

        private fun formatTime(millis: Long): String {
            val day = SimpleDateFormat("yyyyMMdd", Locale.US)
            return if (day.format(Date(System.currentTimeMillis())) == day.format(Date(millis)))
                SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
            else SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(millis))
        }
    }
}
