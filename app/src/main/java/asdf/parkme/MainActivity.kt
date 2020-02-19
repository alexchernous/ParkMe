package asdf.parkme

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.TextView

//const val EXTRA_MESSAGE = "asdf.parkme.MESSAGE"

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun goToMaps(view : View){
        val intent = Intent(this, GoogleMaps::class.java).apply {
//            putExtra(EXTRA_MESSAGE, counterText.toString())
        }
        startActivity(intent)
    }
}
