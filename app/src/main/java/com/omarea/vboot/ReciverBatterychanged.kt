package com.omarea.vboot

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.omarea.shared.Consts
import com.omarea.shared.SpfConfig
import com.omarea.shared.helper.KeepShell

class ReciverBatterychanged(private var service: Service) : BroadcastReceiver() {
    private var bp: Boolean = false
    private var keepShell: KeepShell? = null

    internal var context: Context? = null
    private var sharedPreferences: SharedPreferences
    private var globalSharedPreferences: SharedPreferences
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener
    private val myHandler = Handler(Looper.getMainLooper())
    private var qcLimit = 50000

    //显示文本消息
    private fun showMsg(msg: String, longMsg: Boolean) {
        if (context != null)
            myHandler.post {
                Toast.makeText(context, msg, if (longMsg) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }
    }

    //快速充电
    private fun fastCharger() {
        if (!sharedPreferences.getBoolean(SpfConfig.CHARGE_SPF_QC_BOOSTER, false))
            return

        if (globalSharedPreferences.getBoolean(SpfConfig.GLOBAL_SPF_DEBUG, false))
            showMsg(context!!.getString(R.string.power_connected), false)
        keepShell!!.doCmd(Consts.FastChangerBase)
        keepShell!!.doCmd(computeLeves(qcLimit).toString())
    }

    private fun computeLeves(qcLimit: Int): StringBuilder {
        val arr = StringBuilder()
        if (qcLimit < 500) {
        } else {
            var level = 500
            while (level < qcLimit) {
                arr.append("echo ${level}000 > /sys/class/power_supply/battery/constant_charge_current_max\n")
                arr.append("echo ${level}000 > /sys/class/power_supply/main/constant_charge_current_max\n")
                arr.append("echo ${level}000 > /sys/class/qcom-battery/restricted_current\n")
                level += 500
            }
        }
        arr.append("echo ${qcLimit}000 > /sys/class/power_supply/battery/constant_charge_current_max\n")
        arr.append("echo ${qcLimit}000 > /sys/class/power_supply/main/constant_charge_current_max\n")
        arr.append("echo ${qcLimit}000 > /sys/class/qcom-battery/restricted_current\n")
        return arr;
    }

    override fun onReceive(context: Context, intent: Intent) {
        this.context = context
        try {
            val action = intent.action
            val onChanger = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            val batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)

            //BatteryProtection
            if (sharedPreferences.getBoolean(SpfConfig.CHARGE_SPF_BP, false)) {
                if (onChanger) {
                    if (batteryLevel >= sharedPreferences.getInt(SpfConfig.CHARGE_SPF_BP_LEVEL, 85)) {
                        bp = true
                        keepShell!!.doCmd(Consts.DisableChanger)
                    } else if (batteryLevel < sharedPreferences.getInt(SpfConfig.CHARGE_SPF_BP_LEVEL, 85) - 20) {
                        resumeCharge()
                    }
                }
                //电量不足，恢复充电功能
                else if (action == Intent.ACTION_BATTERY_LOW) {
                    showMsg(context.getString(R.string.battery_low), false)
                    resumeCharge()
                } else if (bp && batteryLevel != -1 && batteryLevel < sharedPreferences.getInt(SpfConfig.CHARGE_SPF_BP_LEVEL, 85) - 20) {
                    //电量低于保护级别20
                    resumeCharge()
                }
            }

            entryFastChanger(onChanger)
        } catch (ex: Exception) {
            showMsg("充电加速服务：\n" + ex.message, true);
        }
    }

    init {
        if (keepShell == null) {
            keepShell = KeepShell(service)
        }
        sharedPreferences = service.getSharedPreferences(SpfConfig.CHARGE_SPF, Context.MODE_PRIVATE)
        listener = SharedPreferences.OnSharedPreferenceChangeListener { spf, key ->
            if (key == SpfConfig.CHARGE_SPF_QC_LIMIT) {
                qcLimit = spf.getInt(SpfConfig.CHARGE_SPF_QC_LIMIT, qcLimit)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        qcLimit = sharedPreferences.getInt(SpfConfig.CHARGE_SPF_QC_LIMIT, qcLimit)
        globalSharedPreferences = service.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
    }

    internal fun onDestroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this.listener)
        this.resumeCharge()
        keepShell!!.tryExit()
        keepShell = null
    }

    internal fun resumeCharge() {
        bp = false
        keepShell!!.doCmd(Consts.ResumeChanger)
    }

    internal fun entryFastChanger(onChanger: Boolean) {
        if (onChanger) {
            if (sharedPreferences.getBoolean(SpfConfig.CHARGE_SPF_QC_BOOSTER, false)) {
                fastCharger()
            }
        }
    }
}