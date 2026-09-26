package com.rayka.smsforwarder

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.Executors

class BuoyFragment : Fragment(R.layout.fragment_buoy) {

    private val uiHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var editNewNumber: TextInputEditText
    private lateinit var numbersContainer: LinearLayout
    private lateinit var editKeyword: TextInputEditText
    private lateinit var editOutMainUrl: TextInputEditText
    private lateinit var editOutLocalUrl: TextInputEditText
    private lateinit var outgoingAdapter: OutgoingAdapter
    private lateinit var txtEmptyOutgoing: View

    private val autoRefresh = object : Runnable {
        override fun run() {
            loadOutgoingHistory()
            uiHandler.postDelayed(this, 4000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Prefs.init(requireContext())

        editNewNumber = view.findViewById(R.id.editNewNumber)
        numbersContainer = view.findViewById(R.id.numbersContainer)
        editKeyword = view.findViewById(R.id.editKeyword)
        editOutMainUrl = view.findViewById(R.id.editOutMainUrl)
        editOutLocalUrl = view.findViewById(R.id.editOutLocalUrl)
        txtEmptyOutgoing = view.findViewById(R.id.txtEmptyOutgoing)

        editKeyword.setText(Prefs.incomingKeyword)
        editOutMainUrl.setText(Prefs.outgoingMainUrl)
        editOutLocalUrl.setText(Prefs.outgoingLocalUrl)

        view.findViewById<MaterialButton>(R.id.btnAddNumber).setOnClickListener { addNumber() }
        view.findViewById<MaterialButton>(R.id.btnSaveKeyword).setOnClickListener {
            Prefs.incomingKeyword = editKeyword.text?.toString() ?: "RAYKA"
            Toast.makeText(requireContext(), "پیشوند ذخیره شد", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.btnSaveOutgoing).setOnClickListener {
            Prefs.outgoingMainUrl = editOutMainUrl.text?.toString()?.trim() ?: ""
            Prefs.outgoingLocalUrl = editOutLocalUrl.text?.toString()?.trim() ?: ""
            Toast.makeText(requireContext(), "تنظیمات ارسال ذخیره شد", Toast.LENGTH_SHORT).show()
        }

        val recyclerOutgoing = view.findViewById<RecyclerView>(R.id.recyclerOutgoing)
        outgoingAdapter = OutgoingAdapter()
        recyclerOutgoing.layoutManager = LinearLayoutManager(requireContext())
        recyclerOutgoing.isNestedScrollingEnabled = false
        recyclerOutgoing.adapter = outgoingAdapter

        renderNumbers()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(autoRefresh)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(autoRefresh)
    }

    private fun addNumber() {
        val number = editNewNumber.text?.toString()?.trim() ?: ""
        if (number.isEmpty()) return
        Prefs.addAllowedNumber(number)
        editNewNumber.setText("")
        renderNumbers()
        Toast.makeText(requireContext(), "شماره اضافه شد", Toast.LENGTH_SHORT).show()
    }

    private fun renderNumbers() {
        numbersContainer.removeAllViews()
        val numbers = Prefs.allowedNumbersList()
        if (numbers.isEmpty()) return
        val inflater = LayoutInflater.from(requireContext())
        for (number in numbers) {
            val row = inflater.inflate(R.layout.item_number, numbersContainer, false)
            row.findViewById<android.widget.TextView>(R.id.txtNumber).text = number
            row.findViewById<android.widget.TextView>(R.id.btnRemoveNumber).setOnClickListener {
                Prefs.removeAllowedNumber(number)
                renderNumbers()
            }
            numbersContainer.addView(row)
        }
    }

    private fun loadOutgoingHistory() {
        executor.execute {
            val data = DbHelper.get(requireContext()).getAllOutgoing()
            uiHandler.post {
                if (!isAdded) return@post
                outgoingAdapter.submit(data)
                txtEmptyOutgoing.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
