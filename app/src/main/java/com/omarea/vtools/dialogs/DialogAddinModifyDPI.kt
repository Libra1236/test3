package com.omarea.vtools.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.support.v7.app.AlertDialog
import android.util.DisplayMetrics
import android.view.Display
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import com.omarea.shared.CommonCmds
import com.omarea.shared.MagiskExtend
import com.omarea.shared.SpfConfig
import com.omarea.shell.KeepShellPublic
import com.omarea.vtools.R

/**
 * Created by Hello on 2017/12/03.
 */

class DialogAddinModifyDPI(var context: Context) {
    private val BACKUP_KEY:String = "screen_ratio"
    private val DEFAULT_RATIO:Float = 16 / 9f

    @SuppressLint("ApplySharedPref")
    private fun backupDisplay(metrics: DisplayMetrics, context: Context) {
        val spf = context.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE);
        if (!spf.contains(BACKUP_KEY)) {
            spf.edit().putFloat(BACKUP_KEY, metrics.heightPixels /  metrics.widthPixels.toFloat()).commit()
        }
    }

    private fun getHeightScaleValue(width: Int): Int {
        val spf = context.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE);
        return (width * spf.getFloat(BACKUP_KEY, DEFAULT_RATIO)).toInt()
    }

    fun modifyDPI(display: Display, context: Context) {
        val layoutInflater = LayoutInflater.from(context)
        val dialog = layoutInflater.inflate(R.layout.dialog_addin_dpi, null)
        val dpiInput = dialog.findViewById(R.id.dialog_addin_dpi_dpiinput) as EditText
        val widthInput = dialog.findViewById(R.id.dialog_addin_dpi_width) as EditText
        val heightInput = dialog.findViewById(R.id.dialog_addin_dpi_height) as EditText
        val quickChange = dialog.findViewById(R.id.dialog_addin_dpi_quickchange) as CheckBox

        val dm = DisplayMetrics()
        display.getMetrics(dm)
        val point = Point()
        display.getRealSize(point)

        backupDisplay(dm, context);

        dpiInput.setText(dm.densityDpi.toString())
        widthInput.setText(point.x.toString())
        heightInput.setText(point.y.toString())
        if (Build.VERSION.SDK_INT >= 24) {
            quickChange.isChecked = true
        }

        val rate = dm.heightPixels / 1.0 / dm.widthPixels
        dialog.findViewById<Button>(R.id.dialog_dpi_720).setOnClickListener({
            widthInput.setText("720")
            val height = getHeightScaleValue(720)
            heightInput.setText(height.toString())
            if (height > 1280) {
                dpiInput.setText("280")
            } else {
                dpiInput.setText("320")
            }
        })
        dialog.findViewById<Button>(R.id.dialog_dpi_1080).setOnClickListener({
            widthInput.setText("1080")
            val height = getHeightScaleValue(1080)
            heightInput.setText(height.toString())
            if (height > 1920) {
                dpiInput.setText("440")
            } else {
                dpiInput.setText("480")
            }
        })
        dialog.findViewById<Button>(R.id.dialog_dpi_2k).setOnClickListener({
            widthInput.setText("1440")
            val height = getHeightScaleValue(1440)
            heightInput.setText(height.toString())
            if (height > 2560) {
                dpiInput.setText("560")
            } else {
                dpiInput.setText("640")
            }
        })
        dialog.findViewById<Button>(R.id.dialog_dpi_4k).setOnClickListener({
            widthInput.setText("2160")
            heightInput.setText(getHeightScaleValue(2160).toString())
            dpiInput.setText("960")
        })
        dialog.findViewById<Button>(R.id.dialog_dpi_reset).setOnClickListener({
            val cmd = StringBuilder()
            cmd.append("wm size reset\n")
            cmd.append("wm density reset\n")
            cmd.append("wm overscan reset\n")
            KeepShellPublic.doCmdSync(cmd.toString())
        })

        val dialogInstance = AlertDialog.Builder(context).setTitle("DPI、分辨率").setView(dialog).setNegativeButton("确定", { _, _ ->
            val dpi = if (dpiInput.text.isNotEmpty()) (dpiInput.text.toString().toInt()) else (0)
            val width = if (widthInput.text.isNotEmpty()) (widthInput.text.toString().toInt()) else (0)
            val height = if (heightInput.text.isNotEmpty()) (heightInput.text.toString().toInt()) else (0)
            val qc = quickChange.isChecked

            val cmd = StringBuilder()
            if (width >= 320 && height >= 480) {
                cmd.append("wm size ${width}x$height")
                cmd.append("\n")
            }
            if (dpi >= 96) {
                cmd.append("wm density $dpi")
                cmd.append("\n")
            }
            if (!qc && dpi >= 96) {
                if (MagiskExtend.moduleInstalled()) {
                    KeepShellPublic.doCmdSync("wm density reset");
                    MagiskExtend.setSystemProp("ro.sf.lcd_density", dpi.toString());
                    MagiskExtend.setSystemProp("vendor.display.lcd_density", dpi.toString());
                    Toast.makeText(context, "已通过Magisk更改参数，请重启手机~", Toast.LENGTH_SHORT).show()
                } else {
                    cmd.append(CommonCmds.MountSystemRW)
                    cmd.append("wm density reset\n")
                    cmd.append("sed '/ro.sf.lcd_density=/'d /system/build.prop > /data/build.prop\n")
                    cmd.append("sed '\$aro.sf.lcd_density=$dpi' /data/build.prop > /data/build2.prop\n")
                    cmd.append("cp /system/build.prop /system/build.prop.dpi_bak\n")
                    cmd.append("cp /data/build2.prop /system/build.prop\n")
                    cmd.append("rm /data/build.prop\n")
                    cmd.append("rm /data/build2.prop\n")
                    cmd.append("chmod 0755 /system/build.prop\n")
                    cmd.append("sync\n")
                    cmd.append("reboot\n")
                }
            }
            if (cmd.isNotEmpty())
                KeepShellPublic.doCmdSync(cmd.toString())
        }).create()

        dialogInstance.window!!.setWindowAnimations(R.style.windowAnim)
        dialogInstance.show()
    }
}
