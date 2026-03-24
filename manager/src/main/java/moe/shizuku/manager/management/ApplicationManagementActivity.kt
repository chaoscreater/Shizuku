package moe.shizuku.manager.management

import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.AppsActivityBinding
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.lifecycle.Status
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.fixEdgeEffect
import java.util.*

class ApplicationManagementActivity : AppBarActivity() {

    private val viewModel: AppsViewModel by viewModels()
    private val adapter = AppsAdapter()

    private val stateListener: (ShizukuStateMachine.State) -> Unit = {
        if (ShizukuStateMachine.isDead() && !isFinishing)
            finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ShizukuStateMachine.isRunning()) {
            finish()
            return
        }

        val binding = AppsActivityBinding.inflate(layoutInflater, rootView, true)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel.packages.observe(this) {
            when (it.status) {
                Status.SUCCESS -> {
                    adapter.updateData(it.data)
                }
                Status.ERROR -> {
                    finish()
                    val tr = it.error
                    Toast.makeText(this, Objects.toString(tr, "unknown"), Toast.LENGTH_SHORT).show()
                    tr.printStackTrace()
                }
                Status.LOADING -> {

                }
            }
        }
        viewModel.load()

        val recyclerView = binding.list
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()
        recyclerView.addEdgeSpacing(top = 8f, bottom = 8f, unit = TypedValue.COMPLEX_UNIT_DIP)

        adapter.registerAdapterDataObserver(object : AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                viewModel.load(true)
            }
        })

        binding.searchInput.addTextChangedListener { editable ->
            viewModel.setSearchQuery(editable?.toString() ?: "")
        }

        ShizukuStateMachine.addListener(stateListener)
    }

    override fun onDestroy() {
        ShizukuStateMachine.removeListener(stateListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.apps_management, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val sortOrder = when (item.itemId) {
            R.id.action_sort_last_added -> SortOrder.LAST_ADDED
            R.id.action_sort_alphabetical -> SortOrder.ALPHABETICAL
            else -> return super.onOptionsItemSelected(item)
        }
        item.isChecked = true
        viewModel.setSortOrder(sortOrder)
        return true
    }
}
