package org.soundsync.ebook.wxapi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.tencent.mm.opensdk.constants.ConstantsAPI
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import org.soundsync.ebook.util.WeChatShareUtil

/**
 * 微信分享回调Activity
 * 必须位于应用包名.wxapi下，命名为WXEntryActivity
 */
class WXEntryActivity : Activity(), IWXAPIEventHandler {
    private lateinit var api: IWXAPI
    
    companion object {
        private const val TAG = "WXEntryActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 注册微信API
        api = WeChatShareUtil.registerWeChatAPI(this)
        
        try {
            // 处理微信回调
            api.handleIntent(intent, this)
        } catch (e: Exception) {
            Log.e(TAG, "处理微信回调失败", e)
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        setIntent(intent)
        api.handleIntent(intent, this)
    }
    
    /**
     * 处理微信请求
     */
    override fun onReq(req: BaseReq) {
        // 通常不需要处理微信发送给应用的请求
        finish()
    }
    
    /**
     * 处理微信响应
     */
    override fun onResp(resp: BaseResp) {
        // 分享结果处理
        when (resp.errCode) {
            BaseResp.ErrCode.ERR_OK -> {
                // 分享成功
                if (resp.type == ConstantsAPI.COMMAND_SENDMESSAGE_TO_WX) {
                    Toast.makeText(this, "分享成功", Toast.LENGTH_SHORT).show()
                }
            }
            BaseResp.ErrCode.ERR_USER_CANCEL -> {
                // 用户取消
                Toast.makeText(this, "分享已取消", Toast.LENGTH_SHORT).show()
            }
            BaseResp.ErrCode.ERR_AUTH_DENIED -> {
                // 认证被拒绝
                Toast.makeText(this, "分享认证被拒绝", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // 其他错误
                Toast.makeText(this, "分享失败: ${resp.errStr}", Toast.LENGTH_SHORT).show()
            }
        }
        
        finish()
    }
} 