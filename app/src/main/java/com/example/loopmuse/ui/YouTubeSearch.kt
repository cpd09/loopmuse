package com.example.loopmuse.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Open the same YouTube search link from Lounge posts and app recommendations. */
internal fun openYouTubeSearch(context: Context, artist: String, title: String): Boolean {
    val uri = Uri.parse("https://www.youtube.com/results")
        .buildUpon()
        .appendQueryParameter("search_query", artist.trim() + " " + title.trim())
        .build()
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "웹 링크를 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        false
    }
}
