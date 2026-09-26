package com.rayka.smsforwarder

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.concurrent.Executors

class LogFragment : Fragment(R.layout.fragment_log) {

    private val uiHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var adapter: LogAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyView: View

    private val autoRefresh = object : Runnable {
        override fun run() {
            loadData()
            uiHandler.postDelayed(this, 3000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerLog)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        emptyView = view.findViewById(R.id.txtEmptyLog)

        adapter = LogAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        swipeRefresh.setOnRefreshListener { loadData() }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(autoRefresh)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(autoRefresh)
    }

    private fun loadData() {
        executor.execute {
            val data = DbHelper.get(requireContext()).getAll()
            uiHandler.post {
                if (!isAdded) return@post
                adapter.submit(data)
                swipeRefresh.isRefreshing = false
                emptyView.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
