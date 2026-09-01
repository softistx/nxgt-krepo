package com.softistx.material.demo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.softistx.material.demo.MaterialDemo

/**
 * The Android launcher: one activity around [MaterialDemo]. On a phone the catalogue folds to a
 * single pane on its own — there is nothing here that says so.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialDemo() }
    }
}
