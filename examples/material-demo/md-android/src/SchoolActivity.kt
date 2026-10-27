package com.softistx.material.demo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.softistx.material.school.SchoolApp

/**
 * The second launcher icon: one activity around [SchoolApp]. It sits beside [MainActivity] so the
 * catalogue and the School app install together and are one tap apart.
 */
class SchoolActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SchoolApp() }
    }
}
