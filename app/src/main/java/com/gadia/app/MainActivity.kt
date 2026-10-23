
package com.gadia.app

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val prefs by lazy { getSharedPreferences("gadia_data", MODE_PRIVATE) }
    private val records = mutableListOf<JSONObject>()
    private var filter = "सभी"
    private val categories = listOf(
        "गाड़ी की कमाई", "पेट्रोल", "CNG", "मेंटेनेंस",
        "ड्राइवर सैलरी", "एडवांस", "टोल", "पार्किंग", "अन्य खर्च"
    )

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        showHome()
    }

    private fun load() {
        records.clear()
        try {
            val a = JSONArray(prefs.getString("records", "[]"))
            for (i in 0 until a.length()) records.add(a.getJSONObject(i))
        } catch (_: Exception) {}
    }

    private fun save() {
        val a = JSONArray()
        records.forEach { a.put(it) }
        prefs.edit().putString("records", a.toString()).apply()
    }

    private fun text(s: String, size: Float = 16f): TextView {
        return TextView(this).apply {
            text = s
            textSize = size
            setTextColor(Color.rgb(35, 40, 48))
            setPadding(12, 10, 12, 10)
        }
    }

    private fun button(s: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = s
            setOnClickListener { action() }
        }
    }

    private fun showHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 16)
            setBackgroundColor(Color.rgb(245, 247, 250))
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        val income = records.filter { it.optString("category") == "गाड़ी की कमाई" }
            .sumOf { it.optDouble("amount") }
        val expenses = records.filter { it.optString("category") != "गाड़ी की कमाई" }
            .sumOf { it.optDouble("amount") }

        root.addView(text("🚕 GADIA हिसाब-किताब", 25f))
        root.addView(text("आज की तारीख: ${today()}"))
        root.addView(text("कुल कमाई: ₹${income.toLong()}", 20f))
        root.addView(text("कुल खर्च: ₹${expenses.toLong()}", 20f))
        root.addView(text("प्रॉफिट / लॉस: ₹${(income - expenses).toLong()}", 22f))

        root.addView(button("➕ नई कमाई दर्ज करें") {
            recordDialog("गाड़ी की कमाई")
        })
        root.addView(button("➕ नया खर्च दर्ज करें") {
            categoryDialog()
        })
        root.addView(button("👨‍✈️ ड्राइवर और सैलरी सेटिंग") {
            driverSettings()
        })
        root.addView(button("📅 तारीख चुनें") {
            val c = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                filter = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                showHome()
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)).show()
        })
        root.addView(button("🧮 कैलकुलेटर") { calculator() })
        root.addView(button("📋 सभी रिकॉर्ड दिखाएँ") {
            filter = "सभी"
            showHome()
        })

        root.addView(text("खर्च की श्रेणियाँ", 20f))
        categories.filter { it != "गाड़ी की कमाई" }.forEach { cat ->
            root.addView(button(cat) {
                filter = cat
                showHome()
                recordDialog(cat)
            })
        }

        root.addView(text("रिकॉर्ड ${if (filter == "सभी") "(सभी)" else "($filter)"}", 20f))
        val shown = records.filter {
            filter == "सभी" || it.optString("category") == filter ||
                it.optString("date") == filter
        }.reversed()

        if (shown.isEmpty()) root.addView(text("अभी कोई रिकॉर्ड नहीं है।"))

        shown.forEach { r ->
            val line = "${r.optString("date")} | ${r.optString("category")}\n" +
                "${r.optString("note")} — ₹${r.optDouble("amount").toLong()}"
            root.addView(button(line) { editRecord(r) })
        }

        root.addView(button("🗑️ सभी रिकॉर्ड मिटाएँ") {
            AlertDialog.Builder(this)
                .setTitle("सभी रिकॉर्ड मिटाएँ?")
                .setMessage("यह कार्रवाई वापस नहीं होगी।")
                .setNegativeButton("नहीं", null)
                .setPositiveButton("हाँ, मिटाएँ") { _, _ ->
                    records.clear()
                    save()
                    filter = "सभी"
                    showHome()
                }.show()
        })
    }

    private fun categoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("खर्च का प्रकार चुनें")
            .setItems(categories.filter { it != "गाड़ी की कमाई" }.toTypedArray()) { _, which ->
                val list = categories.filter { it != "गाड़ी की कमाई" }
                recordDialog(list[which])
            }.show()
    }

    private fun recordDialog(category: String, old: JSONObject? = null) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
        }
        val date = EditText(this).apply {
            hint = "तारीख (YYYY-MM-DD)"
            setText(old?.optString("date") ?: today())
        }
        val amount = EditText(this).apply {
            hint = "रकम रुपये में"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (old != null) old.optDouble("amount").toString() else "")
        }
        val note = EditText(this).apply {
            hint = "विवरण / किसलिए खर्च हुआ"
            setText(old?.optString("note") ?: "")
        }
        box.addView(text("श्रेणी: $category"))
        box.addView(date)
        box.addView(amount)
        box.addView(note)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (old == null) "नया रिकॉर्ड" else "रिकॉर्ड बदलें")
            .setView(box)
            .setNegativeButton("रद्द", null)
            .setPositiveButton("सेव", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = amount.text.toString().toDoubleOrNull()
                if (value == null || value < 0 || date.text.isBlank()) {
                    Toast.makeText(this, "सही तारीख और रकम डालें", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val r = old ?: JSONObject()
                r.put("date", date.text.toString())
                r.put("category", category)
                r.put("amount", value)
                r.put("note", note.text.toString())
                if (old == null) records.add(r)
                save()
                dialog.dismiss()
                showHome()
            }
        }
        dialog.show()
    }

    private fun editRecord(r: JSONObject) {
        val opts = arrayOf("बदलें", "मिटाएँ", "रद्द")
        AlertDialog.Builder(this)
            .setTitle(r.optString("category"))
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> recordDialog(r.optString("category"), r)
                    1 -> AlertDialog.Builder(this)
                        .setMessage("यह रिकॉर्ड मिटाएँ?")
                        .setNegativeButton("नहीं", null)
                        .setPositiveButton("मिटाएँ") { _, _ ->
                            records.remove(r)
                            save()
                            showHome()
                        }.show()
                }
            }.show()
    }

    private fun driverSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
        }
        val name = EditText(this).apply {
            hint = "ड्राइवर का नाम"
            setText(prefs.getString("driver", ""))
        }
        val phone = EditText(this).apply {
            hint = "मोबाइल नंबर"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(prefs.getString("phone", ""))
        }
        val salary = EditText(this).apply {
            hint = "मासिक तय सैलरी"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(prefs.getString("salary", ""))
        }
        box.addView(name)
        box.addView(phone)
        box.addView(salary)
        AlertDialog.Builder(this)
            .setTitle("ड्राइवर की जानकारी")
            .setView(box)
            .setNegativeButton("रद्द", null)
            .setPositiveButton("सेव") { _, _ ->
                prefs.edit()
                    .putString("driver", name.text.toString())
                    .putString("phone", phone.text.toString())
                    .putString("salary", salary.text.toString())
                    .apply()
                showHome()
            }
            .show()
    }

    private fun calculator() {
        val input = EditText(this).apply {
            hint = "जैसे 250+100-50"
        }
        AlertDialog.Builder(this)
            .setTitle("कैलकुलेटर")
            .setView(input)
            .setPositiveButton("जवाब") { _, _ ->
                try {
                    val expr = input.text.toString().replace(" ", "")
                    val parts = Regex("(?<=[+-])|(?=[+-])").split(expr)
                    var result = 0.0
                    var op = '+'
                    for (p in parts) {
                        if (p == "+" || p == "-") op = p[0]
                        else if (p.isNotBlank()) {
                            val n = p.toDouble()
                            result = if (op == '+') result + n else result - n
                        }
                    }
                    AlertDialog.Builder(this)
                        .setMessage("जवाब: $result")
                        .setPositiveButton("ठीक", null).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "उदाहरण: 250+100-50", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("बंद", null)
            .show()
    }
}
