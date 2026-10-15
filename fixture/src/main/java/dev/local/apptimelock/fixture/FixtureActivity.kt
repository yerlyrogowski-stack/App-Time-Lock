package dev.local.apptimelock.fixture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

open class FixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val pad = (28 * density).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad); setBackgroundColor(Color.rgb(244, 247, 240))
        }
        body.addView(TextView(this).apply {
            text = if (this@FixtureActivity is SecondActivity) "同一应用 · 第二页" else "时间锁测试页"
            textSize = 28f; gravity = Gravity.CENTER
        })
        body.addView(TextView(this).apply {
            text = "这是专用测试应用，没有账号或个人内容。"; textSize = 15f
            setPadding(0, pad, 0, pad); gravity = Gravity.CENTER
        })
        body.addView(EditText(this).apply { hint = "点击这里测试键盘"; setSingleLine(true) })
        body.addView(Button(this).apply {
            text = "切换应用内页面"
            setOnClickListener { startActivity(Intent(this@FixtureActivity, if (this@FixtureActivity is SecondActivity) FixtureActivity::class.java else SecondActivity::class.java)) }
        })
        setContentView(body)
    }
}
class SecondActivity : FixtureActivity()
