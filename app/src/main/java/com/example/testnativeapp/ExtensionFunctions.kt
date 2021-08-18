package com.example.testnativeapp

import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.android.synthetic.main.activity_usb.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/*
* @author Tkachov Vasyl
* @since 10.08.2021
*/
fun CoroutineScope.launchPeriodicAsync(repeatMillis: Long, action: () -> Unit) = this.async {
    if (repeatMillis > 0) {
        while (isActive) {
            action()
            delay(repeatMillis)
        }
    } else {
        action()
    }
}

fun TextInputEditText.getIntValue(): Int {
    return if (editableText.isNotEmpty()) {
        text.toString().toInt()
    } else {
        hint.toString().toInt()
    }
}