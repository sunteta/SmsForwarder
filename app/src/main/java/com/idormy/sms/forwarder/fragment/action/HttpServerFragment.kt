package com.idormy.sms.forwarder.fragment.action

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import com.google.gson.Gson
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.idormy.sms.forwarder.R
import com.idormy.sms.forwarder.core.BaseFragment
import com.idormy.sms.forwarder.databinding.FragmentTasksActionHttpServerBinding
import com.idormy.sms.forwarder.entity.action.HttpServerSetting
import com.idormy.sms.forwarder.utils.KEY_BACK_DATA_ACTION
import com.idormy.sms.forwarder.utils.KEY_BACK_DESCRIPTION_ACTION
import com.idormy.sms.forwarder.utils.KEY_EVENT_DATA_ACTION
import com.idormy.sms.forwarder.utils.KEY_TEST_ACTION
import com.idormy.sms.forwarder.utils.Log
import com.idormy.sms.forwarder.utils.SettingUtils
import com.idormy.sms.forwarder.utils.TASK_ACTION_HTTPSERVER
import com.idormy.sms.forwarder.utils.XToastUtils
import com.jeremyliao.liveeventbus.LiveEventBus
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xui.utils.CountDownButtonHelper
import com.xuexiang.xui.widget.actionbar.TitleBar

@Page(name = "HttpServer")
@Suppress("PrivatePropertyName")
class HttpServerFragment : BaseFragment<FragmentTasksActionHttpServerBinding?>(), View.OnClickListener {

    private val TAG: String = HttpServerFragment::class.java.simpleName
    private var titleBar: TitleBar? = null
    private var mCountDownHelper: CountDownButtonHelper? = null

    @JvmField
    @AutoWired(name = KEY_EVENT_DATA_ACTION)
    var eventData: String? = null

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentTasksActionHttpServerBinding {
        return FragmentTasksActionHttpServerBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false).setTitle(R.string.task_http_server)
        return titleBar
    }

    /**
     * 初始化控件
     */
    override fun initViews() {
        //测试按钮增加倒计时，避免重复点击
        mCountDownHelper = CountDownButtonHelper(binding!!.btnTest, 3)
        mCountDownHelper!!.setOnCountDownListener(object : CountDownButtonHelper.OnCountDownListener {
            override fun onCountDown(time: Int) {
                binding!!.btnTest.text = String.format(getString(R.string.seconds_n), time)
            }

            override fun onFinished() {
                binding!!.btnTest.text = getString(R.string.test)
            }
        })

        Log.d(TAG, "initViews eventData:$eventData")
        var settingVo = HttpServerSetting(getString(R.string.task_http_server_tips))
        if (eventData != null) {
            settingVo = Gson().fromJson(eventData, HttpServerSetting::class.java)
            Log.d(TAG, "initViews settingVo:$settingVo")
        }
        binding!!.rgHttpServerState.check(if (settingVo.action == "start") R.id.rb_start_server else R.id.rb_stop_server)
        binding!!.sbApiClone.isChecked = settingVo.enableApiClone
        binding!!.sbApiQuerySms.isChecked = settingVo.enableApiSmsQuery
        binding!!.sbApiSendSms.isChecked = settingVo.enableApiSmsSend
        binding!!.sbApiQueryCall.isChecked = settingVo.enableApiCallQuery
        binding!!.sbApiQueryContacts.isChecked = settingVo.enableApiContactQuery
        binding!!.sbApiAddContacts.isChecked = settingVo.enableApiContactAdd
        binding!!.sbApiWol.isChecked = settingVo.enableApiWol
        binding!!.sbApiLocation.isChecked = settingVo.enableApiLocation
        binding!!.sbApiQueryBattery.isChecked = settingVo.enableApiBatteryQuery
    }

    @SuppressLint("SetTextI18n")
    override fun initListeners() {
        binding!!.btnTest.setOnClickListener(this)
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
        LiveEventBus.get(KEY_TEST_ACTION, String::class.java).observe(this) {
            mCountDownHelper?.finish()

            if (it == "success") {
                XToastUtils.success("测试通过", 30000)
            } else {
                XToastUtils.error(it, 30000)
            }
        }

        binding!!.sbApiSendSms.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) checkSendSmsPermission()
        }

        binding!!.sbApiQuerySms.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) checkReadSmsPermission()
        }

        binding!!.sbApiQueryCall.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) checkCallPermission()
        }

        binding!!.sbApiQueryContacts.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) checkContactsPermission()
        }

        binding!!.sbApiAddContacts.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) checkContactsPermission()
        }

        binding!!.sbApiLocation.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked && !SettingUtils.enableLocation) {
                XToastUtils.error(getString(R.string.api_location_permission_tips))
                binding!!.sbApiLocation.isChecked = false
                return@setOnCheckedChangeListener
            }
        }
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            when (v.id) {
                R.id.btn_test -> {
                    mCountDownHelper?.start()
                    Thread {
                        try {
                            val settingVo = checkSetting()
                            Log.d(TAG, settingVo.toString())
                            LiveEventBus.get(KEY_TEST_ACTION, String::class.java).post("success")
                        } catch (e: Exception) {
                            LiveEventBus.get(KEY_TEST_ACTION, String::class.java).post(e.message.toString())
                            e.printStackTrace()
                            Log.e(TAG, "onClick error: ${e.message}")
                        }
                    }.start()
                    return
                }

                R.id.btn_del -> {
                    popToBack()
                    return
                }

                R.id.btn_save -> {
                    val settingVo = checkSetting()
                    val intent = Intent()
                    intent.putExtra(KEY_BACK_DESCRIPTION_ACTION, settingVo.description)
                    intent.putExtra(KEY_BACK_DATA_ACTION, Gson().toJson(settingVo))
                    setFragmentResult(TASK_ACTION_HTTPSERVER, intent)
                    popToBack()
                    return
                }
            }
        } catch (e: Exception) {
            XToastUtils.error(e.message.toString(), 30000)
            e.printStackTrace()
            Log.e(TAG, "onClick error: ${e.message}")
        }
    }

    //检查设置
    @SuppressLint("SetTextI18n")
    private fun checkSetting(): HttpServerSetting {
        val enableList = mutableListOf<String>()
        val disableList = mutableListOf<String>()

        val enableApiClone = binding!!.sbApiClone.isChecked
        if (enableApiClone) enableList.add(getString(R.string.api_clone)) else disableList.add(getString(R.string.api_clone))

        val enableApiSmsSend = binding!!.sbApiSendSms.isChecked
        if (enableApiSmsSend) enableList.add(getString(R.string.api_sms_query)) else disableList.add(getString(R.string.api_sms_query))

        val enableApiSmsQuery = binding!!.sbApiQuerySms.isChecked
        if (enableApiSmsQuery) enableList.add(getString(R.string.api_sms_send)) else disableList.add(getString(R.string.api_sms_send))

        val enableApiCallQuery = binding!!.sbApiQueryCall.isChecked
        if (enableApiCallQuery) enableList.add(getString(R.string.api_call_query)) else disableList.add(getString(R.string.api_call_query))

        val enableApiContactQuery = binding!!.sbApiQueryContacts.isChecked
        if (enableApiContactQuery) enableList.add(getString(R.string.api_contact_query)) else disableList.add(getString(R.string.api_contact_query))

        val enableApiContactAdd = binding!!.sbApiAddContacts.isChecked
        if (enableApiContactAdd) enableList.add(getString(R.string.api_contact_add)) else disableList.add(getString(R.string.api_contact_add))

        val enableApiWol = binding!!.sbApiWol.isChecked
        if (enableApiWol) enableList.add(getString(R.string.api_wol)) else disableList.add(getString(R.string.api_wol))

        val enableApiLocation = binding!!.sbApiLocation.isChecked
        if (enableApiLocation) enableList.add(getString(R.string.api_location)) else disableList.add(getString(R.string.api_location))

        val enableApiBatteryQuery = binding!!.sbApiQueryBattery.isChecked
        if (enableApiBatteryQuery) enableList.add(getString(R.string.api_battery_query)) else disableList.add(getString(R.string.api_battery_query))

        val description = StringBuilder()
        val action = if (binding!!.rgHttpServerState.checkedRadioButtonId == R.id.rb_start_server) {
            description.append(getString(R.string.start_server))
            if (enableList.isNotEmpty()) {
                description.append(", ").append(getString(R.string.enable_function)).append(": ").append(enableList.joinToString("/"))
            }
            if (disableList.isNotEmpty()) {
                description.append(", ").append(getString(R.string.disable_function)).append(": ").append(disableList.joinToString("/"))
            }
            "start"
        } else {
            description.append(getString(R.string.stop_server))
            "stop"
        }

        return HttpServerSetting(description.toString(), action, enableApiClone, enableApiSmsSend, enableApiSmsQuery, enableApiCallQuery, enableApiContactQuery, enableApiContactAdd, enableApiWol, enableApiLocation, enableApiBatteryQuery)
    }

    //发送短信权限
    private fun checkSendSmsPermission() {
        XXPermissions.with(this)
            // 发送短信
            .permission(Permission.SEND_SMS).request(object : OnPermissionCallback {
                override fun onGranted(permissions: List<String>, all: Boolean) {
                }

                override fun onDenied(permissions: List<String>, never: Boolean) {
                    if (never) {
                        XToastUtils.error(R.string.toast_denied_never)
                        // 如果是被永久拒绝就跳转到应用权限系统设置页面
                        XXPermissions.startPermissionActivity(requireContext(), permissions)
                    } else {
                        XToastUtils.error(R.string.toast_denied)
                    }
                    binding!!.sbApiSendSms.isChecked = false
                }
            })
    }

    //读取短信权限
    private fun checkReadSmsPermission() {
        XXPermissions.with(this)
            // 接收短信
            .permission(Permission.RECEIVE_SMS)
            // 发送短信
            .permission(Permission.SEND_SMS)
            // 读取短信
            .permission(Permission.READ_SMS).request(object : OnPermissionCallback {
                override fun onGranted(permissions: List<String>, all: Boolean) {
                }

                override fun onDenied(permissions: List<String>, never: Boolean) {
                    if (never) {
                        XToastUtils.error(R.string.toast_denied_never)
                        // 如果是被永久拒绝就跳转到应用权限系统设置页面
                        XXPermissions.startPermissionActivity(requireContext(), permissions)
                    } else {
                        XToastUtils.error(R.string.toast_denied)
                    }
                    binding!!.sbApiQuerySms.isChecked = false
                }
            })
    }

    //电话权限
    private fun checkCallPermission() {
        XXPermissions.with(this)
            // 读取电话状态
            .permission(Permission.READ_PHONE_STATE)
            // 读取手机号码
            .permission(Permission.READ_PHONE_NUMBERS)
            // 读取通话记录
            .permission(Permission.READ_CALL_LOG).request(object : OnPermissionCallback {
                override fun onGranted(permissions: List<String>, all: Boolean) {
                }

                override fun onDenied(permissions: List<String>, never: Boolean) {
                    if (never) {
                        XToastUtils.error(R.string.toast_denied_never)
                        // 如果是被永久拒绝就跳转到应用权限系统设置页面
                        XXPermissions.startPermissionActivity(requireContext(), permissions)
                    } else {
                        XToastUtils.error(R.string.toast_denied)
                    }
                    binding!!.sbApiQueryCall.isChecked = false
                }
            })
    }

    //联系人权限
    private fun checkContactsPermission() {
        XXPermissions.with(this).permission(*Permission.Group.CONTACTS).request(object : OnPermissionCallback {
            override fun onGranted(permissions: List<String>, all: Boolean) {
            }

            override fun onDenied(permissions: List<String>, never: Boolean) {
                if (never) {
                    XToastUtils.error(R.string.toast_denied_never)
                    // 如果是被永久拒绝就跳转到应用权限系统设置页面
                    XXPermissions.startPermissionActivity(requireContext(), permissions)
                } else {
                    XToastUtils.error(R.string.toast_denied)
                }
                binding!!.sbApiQueryContacts.isChecked = false
                binding!!.sbApiAddContacts.isChecked = false
            }
        })
    }

}