package com.wanderreads.ebook.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage
import com.tencent.mm.opensdk.modelmsg.WXTextObject
import com.tencent.mm.opensdk.modelmsg.WXWebpageObject
import com.tencent.mm.opensdk.modelmsg.WXFileObject
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import com.wanderreads.ebook.domain.model.Book
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 微信分享工具类
 */
class WeChatShareUtil {
    companion object {
        private const val TAG = "WeChatShareUtil"
        
        // 微信开发者ID，需要替换为实际的AppID
        // 开发者需要在微信开放平台申请并配置
        private const val APP_ID = "wx_app_id_placeholder"
        
        // 缩略图最大尺寸
        private const val THUMB_SIZE = 120
        
        /**
         * 注册微信API
         */
        fun registerWeChatAPI(context: Context): IWXAPI {
            val api = WXAPIFactory.createWXAPI(context, APP_ID, true)
            api.registerApp(APP_ID)
            return api
        }
        
        /**
         * 检查微信是否已安装
         */
        fun isWeChatInstalled(context: Context): Boolean {
            val api = registerWeChatAPI(context)
            return api.isWXAppInstalled
        }
        
        /**
         * 分享文件到微信
         * 注意：微信对分享文件有大小限制，一般不超过10MB
         */
        fun shareFileToWeChat(
            context: Context,
            filePath: String,
            fileName: String,
            thumbnailBitmap: Bitmap? = null,
            scene: Int = SendMessageToWX.Req.WXSceneSession
        ): Boolean {
            try {
                val file = File(filePath)
                
                // 检查文件是否存在
                if (!file.exists()) {
                    Log.e(TAG, "文件不存在: $filePath")
                    return false
                }
                
                // 检查文件大小是否超过微信限制（不超过10MB）
                if (file.length() > 10 * 1024 * 1024) {
                    Log.e(TAG, "文件大小超过10MB，微信不支持分享")
                    return false
                }
                
                val api = registerWeChatAPI(context)
                
                // 创建文件对象
                val fileObj = WXFileObject()
                fileObj.filePath = filePath
                
                // 创建多媒体消息
                val msg = WXMediaMessage()
                msg.mediaObject = fileObj
                msg.title = fileName
                msg.description = "分享电子书文件"
                
                // 设置缩略图
                if (thumbnailBitmap != null) {
                    msg.thumbData = bitmapToByteArray(thumbnailBitmap)
                }
                
                // 发送请求
                val req = SendMessageToWX.Req()
                req.transaction = buildTransaction("file")
                req.message = msg
                req.scene = scene
                
                return api.sendReq(req)
            } catch (e: Exception) {
                Log.e(TAG, "分享文件到微信失败", e)
                return false
            }
        }
        
        /**
         * 分享电子书文件到微信
         */
        fun shareBookFileToWeChat(
            context: Context,
            book: Book,
            scene: Int = SendMessageToWX.Req.WXSceneSession
        ): Boolean {
            // 获取电子书文件路径
            val filePath = book.filePath ?: return false
            
            try {
                val file = File(filePath)
                
                // 检查文件是否存在
                if (!file.exists()) {
                    Log.e(TAG, "电子书文件不存在: $filePath")
                    return false
                }
                
                // 获取文件名
                val fileName = file.name
                
                // 获取封面缩略图
                var thumbnailBitmap: Bitmap? = null
                if (book.coverPath != null) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(book.coverPath)
                        thumbnailBitmap = compressBitmap(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "加载封面图片失败", e)
                    }
                }
                
                // 分享文件
                return shareFileToWeChat(context, filePath, fileName, thumbnailBitmap, scene)
            } catch (e: Exception) {
                Log.e(TAG, "分享电子书文件到微信失败", e)
                return false
            }
        }
        
        /**
         * 分享文本消息到微信
         */
        fun shareTextToWeChat(context: Context, text: String, scene: Int = SendMessageToWX.Req.WXSceneSession): Boolean {
            try {
                val api = registerWeChatAPI(context)
                
                // 创建文本对象
                val textObj = WXTextObject()
                textObj.text = text
                
                // 创建多媒体消息
                val msg = WXMediaMessage()
                msg.mediaObject = textObj
                msg.description = text
                
                // 发送请求
                val req = SendMessageToWX.Req()
                req.transaction = buildTransaction("text")
                req.message = msg
                req.scene = scene
                
                return api.sendReq(req)
            } catch (e: Exception) {
                Log.e(TAG, "分享文本到微信失败", e)
                return false
            }
        }
        
        /**
         * 分享书籍信息到微信
         */
        fun shareBookToWeChat(
            context: Context, 
            book: Book,
            thumbnailBitmap: Bitmap? = null,
            scene: Int = SendMessageToWX.Req.WXSceneSession
        ): Boolean {
            try {
                val api = registerWeChatAPI(context)
                
                // 创建网页对象
                val webpage = WXWebpageObject()
                // 使用通用分享链接，实际应用中应替换为书籍详情页URL
                webpage.webpageUrl = "https://wanderreads.com/books/${book.id}"
                
                // 创建多媒体消息
                val msg = WXMediaMessage(webpage)
                msg.title = book.title
                msg.description = "《${book.title}》${if (book.author.isNotEmpty()) "by ${book.author}" else ""}"
                
                // 设置缩略图
                if (thumbnailBitmap != null) {
                    msg.thumbData = bitmapToByteArray(thumbnailBitmap)
                } else if (book.coverPath != null) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(book.coverPath)
                        val thumbBitmap = compressBitmap(bitmap)
                        msg.thumbData = bitmapToByteArray(thumbBitmap)
                        thumbBitmap.recycle()
                        bitmap.recycle()
                    } catch (e: Exception) {
                        Log.e(TAG, "加载封面图片失败", e)
                    }
                }
                
                // 发送请求
                val req = SendMessageToWX.Req()
                req.transaction = buildTransaction("webpage")
                req.message = msg
                req.scene = scene
                
                return api.sendReq(req)
            } catch (e: Exception) {
                Log.e(TAG, "分享书籍到微信失败", e)
                return false
            }
        }
        
        /**
         * 分享电子书阅读进度到微信
         */
        fun shareReadingProgressToWeChat(
            context: Context,
            book: Book,
            currentChapter: String,
            progress: Float,
            thumbnailBitmap: Bitmap? = null,
            scene: Int = SendMessageToWX.Req.WXSceneSession
        ): Boolean {
            val progressPercent = (progress * 100).toInt()
            val shareText = "我正在阅读《${book.title}》${if (book.author.isNotEmpty()) "(${book.author})" else ""}，" +
                    "当前章节：${currentChapter}，已读完${progressPercent}%"
            
            // 创建网页对象
            try {
                val api = registerWeChatAPI(context)
                
                // 创建网页对象
                val webpage = WXWebpageObject()
                // 使用通用分享链接，实际应用中应替换为书籍详情页URL
                webpage.webpageUrl = "https://wanderreads.com/books/${book.id}"
                
                // 创建多媒体消息
                val msg = WXMediaMessage(webpage)
                msg.title = "分享阅读进度：《${book.title}》"
                msg.description = shareText
                
                // 设置缩略图
                if (thumbnailBitmap != null) {
                    msg.thumbData = bitmapToByteArray(thumbnailBitmap)
                } else if (book.coverPath != null) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(book.coverPath)
                        val thumbBitmap = compressBitmap(bitmap)
                        msg.thumbData = bitmapToByteArray(thumbBitmap)
                        thumbBitmap.recycle()
                        bitmap.recycle()
                    } catch (e: Exception) {
                        Log.e(TAG, "加载封面图片失败", e)
                    }
                }
                
                // 发送请求
                val req = SendMessageToWX.Req()
                req.transaction = buildTransaction("webpage")
                req.message = msg
                req.scene = scene
                
                return api.sendReq(req)
            } catch (e: Exception) {
                Log.e(TAG, "分享阅读进度到微信失败", e)
                return false
            }
        }
        
        /**
         * 构建事务ID
         */
        private fun buildTransaction(type: String): String {
            return "${type}${System.currentTimeMillis()}"
        }
        
        /**
         * 压缩位图到指定大小
         */
        private fun compressBitmap(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            
            // 计算比例
            val scale = if (width > height) {
                THUMB_SIZE.toFloat() / width
            } else {
                THUMB_SIZE.toFloat() / height
            }
            
            // 创建压缩后的位图
            return Bitmap.createScaledBitmap(
                bitmap,
                (width * scale).toInt(),
                (height * scale).toInt(),
                true
            )
        }
        
        /**
         * 将位图转换为字节数组
         */
        private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            return stream.toByteArray()
        }
    }
} 