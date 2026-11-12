package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string resources from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("BinCheck", context.getString(R.string.app_name))
        assertEquals("453225005536xxx3|08|2028|113", context.getString(R.string.input_placeholder))
        assertEquals("https://t.me/drkingbd", context.getString(R.string.developer_channel_url))
    }
}
