package moe.shizuku.manager.management

import android.app.Application
import android.content.pm.PackageInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.authorization.AuthorizationManager
import rikka.lifecycle.Resource

enum class SortOrder { LAST_ADDED, ALPHABETICAL }

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext

    private val _packages = MutableLiveData<Resource<List<PackageInfo>>>()
    val packages = _packages as LiveData<Resource<List<PackageInfo>>>

    private val _grantedCount = MutableLiveData<Resource<Int>>()
    val grantedCount = _grantedCount as LiveData<Resource<Int>>

    private var fullList: List<PackageInfo> = emptyList()
    private var searchQuery: String = ""
    var sortOrder: SortOrder = SortOrder.LAST_ADDED
        private set

    fun load(onlyCount: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list: MutableList<PackageInfo> = ArrayList()
                var count = 0
                for (pi in AuthorizationManager.getPackages()) {
                    list.add(pi)
                    if (AuthorizationManager.granted(pi.packageName, pi.applicationInfo!!.uid)) count++
                }
                if (!onlyCount) {
                    fullList = list
                    _packages.postValue(Resource.success(applyFilterAndSort(fullList)))
                }
                _grantedCount.postValue(Resource.success(count))
            } catch (e: CancellationException) {

            } catch (e: Throwable) {
                _packages.postValue(Resource.error(e, null))
                _grantedCount.postValue(Resource.error(e, 0))
            }
        }
    }

    fun setSearchQuery(query: String) {
        if (searchQuery == query) return
        searchQuery = query
        viewModelScope.launch(Dispatchers.Default) {
            _packages.postValue(Resource.success(applyFilterAndSort(fullList)))
        }
    }

    fun setSortOrder(order: SortOrder) {
        if (sortOrder == order) return
        sortOrder = order
        viewModelScope.launch(Dispatchers.Default) {
            _packages.postValue(Resource.success(applyFilterAndSort(fullList)))
        }
    }

    private fun applyFilterAndSort(list: List<PackageInfo>): List<PackageInfo> {
        val pm = appContext.packageManager
        var result = if (searchQuery.isBlank()) {
            list
        } else {
            val q = searchQuery.trim()
            list.filter { pi ->
                val label = pi.applicationInfo?.loadLabel(pm)?.toString() ?: ""
                label.contains(q, ignoreCase = true) || pi.packageName.contains(q, ignoreCase = true)
            }
        }
        if (sortOrder == SortOrder.ALPHABETICAL) {
            result = result.sortedBy { pi ->
                pi.applicationInfo?.loadLabel(pm)?.toString()?.lowercase() ?: pi.packageName.lowercase()
            }
        }
        return result
    }
}
