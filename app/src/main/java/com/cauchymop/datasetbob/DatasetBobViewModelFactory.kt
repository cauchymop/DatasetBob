package com.cauchymop.datasetbob

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File

class DatasetBobViewModelFactory(val root: File) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return DatasetBobViewModel(root) as T
    }
}