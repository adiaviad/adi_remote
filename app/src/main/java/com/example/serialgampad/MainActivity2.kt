package com.example.serialgampad

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class MainActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        val mTextViewAngleRight=findViewById<TextView>(R.id.textView_angle_right)
        val mTextViewStrengthRight=findViewById<TextView>(R.id.textView_strength_right)
        val mTextViewCoordinateRight=findViewById<TextView>(R.id.textView_coordinate_right)

        val joystickRight = findViewById<JoystickView>(R.id.joystickView_right)
        joystickRight.setOnMoveListener(50, JoystickView.OnMoveListener { angle, strength ->
            mTextViewAngleRight.text = "${angle}°";
            mTextViewStrengthRight.text = "${strength}%";
            mTextViewCoordinateRight.text = String.format("x%03d:y%03d",
                joystickRight.getNormalizedX(),
                joystickRight.getNormalizedY());
        })

        val mTextViewAngleLeft=findViewById<TextView>(R.id.textView_angle_left)
        val mTextViewStrengthLeft=findViewById<TextView>(R.id.textView_strength_left)
        val mTextViewCoordinateLeft=findViewById<TextView>(R.id.textView_coordinate_left)

        val joystickLeft = findViewById<JoystickView>(R.id.joystickView_left)
        joystickLeft.setOnMoveListener(50, JoystickView.OnMoveListener { angle, strength ->
            mTextViewAngleLeft.text = "${angle}°";
            mTextViewStrengthLeft.text = "${strength}%";
            mTextViewCoordinateLeft.text = String.format("x%03d:y%03d",
                joystickLeft.getNormalizedX(),
                joystickLeft.getNormalizedY());
        })
    }
}