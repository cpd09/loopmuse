package com.example.loopmuse.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SelectionItem(
    val path: String,
    val isFolder: Boolean,
    val includeSubfolders: Boolean = true
) : Parcelable
