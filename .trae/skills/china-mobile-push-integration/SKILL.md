---
name: "china-mobile-push-integration"
description: "集成中国手机品牌（小米、华为、OPPO、VIVO、荣耀等）推送SDK。Invoke when user asks to integrate push notifications for Chinese Android brands, implement vendor push channels, or configure mobile push services."
---

# 中国手机品牌推送SDK集成指南

本技能用于在Android应用中集成中国主流手机品牌的推送服务，实现厂商通道推送，提升消息到达率。

## 📱 支持的手机品牌和SDK文档

### 1. 小米（MiPush）
**开发者平台**: https://dev.mi.com/xiaomihyperos/ability/mipush
**SDK下载**: https://dev.mi.com/console/appservice/push.html
**集成指南**: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1529

**特点**:
- 支持MIUI所有版本
- 消息分类必须申请（运营类/系统类）
- 需要申请AppId、AppKey、AppSecret

**Gradle依赖**:
```gradle
implementation 'com.xiaomi.push:xiaomi-push:版本号'
```

---

### 2. 华为（HMS Push）
**开发者平台**: https://developer.huawei.com/consumer/cn/
**HMS文档**: https://developer.huawei.com/consumer/cn/doc/development/HMSCore-Guides/android-integrating-sdk-0000001050040084
**推送服务**: https://developer.huawei.com/consumer/cn/doc/development/HMSCore-Guides/android-app-0000001071076052

**特点**:
- 需要在AppGallery Connect创建应用
- 提供agconnect-services.json配置文件
- 支持EMUI 4.1+设备

**Gradle依赖**:
```gradle
implementation 'com.huawei.hms:push:版本号'
```

---

### 3. OPPO（OPPO Push）
**开发者平台**: https://open.oppomobile.com/
**SDK文档**: https://open.oppomobile.com/wiki/doc#id=10453
**ColorOS推送**: 使用HeyTap Push SDK

**特点**:
- 支持ColorOS 3.1+、一加5+、realme所有机型
- 最低支持Android 4.0
- 只支持通知栏消息

**Gradle依赖**:
```gradle
implementation files('libs/OPPOPushSDK-*.aar')
```

---

### 4. VIVO（Vivo Push）
**开发者平台**: https://dev.vivo.com.cn/
**推送文档**: https://dev.vivo.com.cn/documentCenter/doc/180
**SDK下载**: 需要在VIVO开发者平台申请

**特点**:
- 支持Android 6.0+（FuntouchOS 3.0+）
- 最低支持Android 6.0
- 需要申请AppId、AppKey、AppSecret

**Gradle依赖**:
```gradle
implementation files('libs/vivo_pushsdk-*.aar')
```

---

### 5. 荣耀（Honor Push）
**开发者平台**: https://developer.hihonor.com/cn/
**推送文档**: https://developer.honor.com/cn/docs/11002/guides/sdk-data-security
**集成指南**: https://docs.rongcloud.cn/android-imkit/push/integration/honor

**特点**:
- 继承华为HMS Push技术
- 支持MagicUI 4.0+设备
- 与华为推送共用部分API

**Gradle依赖**:
```gradle
implementation 'com.hihonor.push:honor-push:版本号'
```

---

### 6. 真我（Realme/OPPO家族）
**推送通道**: 使用OPPO/一加统一推送服务
**开发者平台**: https://www.realmebbs.com/
**ColorOS适配**: 与OPPO、一加共用ColorOS推送

**特点**:
- 集成到OPPO推送SDK
- 支持realme UI所有版本
- 消息分类遵循OPPO规范

---

### 7. 一加（OnePlus）
**推送通道**: 使用OPPO ColorOS推送
**特点**:
- ColorOS 5.0+支持
- 与OPPO、realme共用推送服务
- 系统级推送权限

---

### 8. 努比亚/红魔（nubia/RedMagic）
**推送通道**: 使用系统级推送
**开发者平台**: https://www.nubia.com/
**红魔推送**: 基于Android原生FCM，需在应用市场配置

**特点**:
- 红魔9S Pro等游戏手机
- 系统推送优先级高
- 支持游戏免打扰场景

---

### 9. 魅族（Meizu）
**开发者平台**: https://open.flyme.cn/
**推送文档**: https://open.flyme.cn/service?type=push
**SDK**: Flyme推送服务

**特点**:
- 支持Flyme 4.0+设备
- 系统级推送通道
- 需在Flyme开放平台申请

**Gradle依赖**:
```gradle
implementation 'com.meizu.flyme.internet:push:版本号'
```

---

## 🏗️ 集成架构

### 方案一：各品牌SDK单独集成

**优点**:
- 直接调用厂商API
- 性能最优
- 可定制化程度高

**缺点**:
- 代码量大
- 需要维护多个SDK版本
- 兼容性处理复杂

**适用场景**: 大型应用，有专业推送团队

---

### 方案二：使用第三方推送聚合平台（推荐）

**平台选择**:
1. **极光推送**: https://www.jiguang.cn/
2. **友盟推送**: https://www.umeng.com/
3. **腾讯云推送**: https://cloud.tencent.com/document/product/548
4. **个推**: https://www.getui.com/

**优点**:
- 一套代码对接所有厂商
- 统一管理后台
- 易于维护和升级

**缺点**:
- 依赖第三方服务
- 可能影响推送到达率
- 需要付费（企业版）

**适用场景**: 中小型应用，快速上线

---

## 📋 集成检查清单

### 1. 开发者平台注册
- [ ] 注册各品牌开发者账号
- [ ] 创建应用并获取AppId/AppKey/AppSecret
- [ ] 配置应用包名和签名

### 2. Gradle配置
- [ ] 添加各品牌SDK依赖
- [ ] 配置Maven仓库（华为需要配置HMS仓库）
- [ ] 设置minSdkVersion兼容性

### 3. AndroidManifest配置
- [ ] 添加AppId/AppKey元数据
- [ ] 配置推送服务Receiver
- [ ] 添加必要权限

### 4. 代码实现
- [ ] 初始化各品牌推送SDK
- [ ] 注册Token获取回调
- [ ] 实现消息接收器
- [ ] 处理点击事件

### 5. 消息分类配置
- [ ] 申请各品牌消息分类
- [ ] 配置通知渠道（Android 8.0+）
- [ ] 设置消息优先级

### 6. 测试验证
- [ ] 设备厂商推送测试
- [ ] 后台杀掉进程测试
- [ ] 网络切换测试
- [ ] 消息点击跳转测试

---

## 🔧 常用配置代码

### AndroidManifest配置示例

```xml
<!-- 小米推送配置 -->
<meta-data
    android:name="MIPUSH_APPID"
    android:value="your_mi_app_id" />
<meta-data
    android:name="MIPUSH_APPKEY"
    android:value="your_mi_app_key" />

<!-- 华为推送配置 -->
<meta-data
    android:name="com.huawei.hms.client.appid"
    android:value="appid=your_huawei_app_id" />

<!-- OPPO推送配置 -->
<meta-data
    android:name="com.heytap.mcp.appid"
    android:value="your_oppo_appid" />
<meta-data
    android:name="com.heytap.mcp.appkey"
    android:value="your_oppo_appkey" />

<!-- VIVO推送配置 -->
<meta-data
    android:name="com.vivo.push.app_id"
    android:value="your_vivo_appid" />
<meta-data
    android:name="com.vivo.push.app_key"
    android:value="your_vivo_appkey" />
```

### Kotlin初始化示例

```kotlin
object PushManager {
    
    fun initPush(context: Context) {
        // 小米推送初始化
        initMiPush(context)
        
        // 华为推送初始化
        initHMSPush(context)
        
        // OPPO推送初始化
        initOPPOush(context)
        
        // VIVO推送初始化
        initVivoPush(context)
    }
    
    private fun initMiPush(context: Context) {
        // 小米推送初始化代码
        val appId = "your_mi_app_id"
        val appKey = "your_mi_app_key"
        // MiPushSDK.register(context, appId, appKey)
    }
    
    private fun initHMSPush(context: Context) {
        // 华为推送初始化代码
        // HMSMessaging.getInstance().turnOnPush(context)
    }
    
    private fun initOPPOush(context: Context) {
        // OPPO推送初始化代码
    }
    
    private fun initVivoPush(context: Context) {
        // VIVO推送初始化代码
    }
}
```

---

## ⚠️ 注意事项

### 1. 消息分类限制
- **小米**: 必须申请消息分类，未分类消息可能被拦截
- **华为**: 分政务类、企业类、互联网类
- **OPPO**: 分私信类、资讯类、营销类
- **VIVO**: 分运营消息、系统消息

### 2. 设备兼容性
- 老旧设备可能不支持系统级推送
- 部分设备需要用户手动开启推送权限
- 海外版系统可能无法使用国内推送

### 3. 推送策略
- 优先使用系统级推送
- 降级方案使用极光/友盟等第三方推送
- 做好消息去重和优先级处理

### 4. 合规要求
- 必须在隐私政策中说明推送用途
- 提供关闭推送的选项
- 遵循各厂商推送规范

---

## 📚 参考资源

### 官方文档
1. 小米推送: https://dev.mi.com/xiaomihyperos/ability/mipush
2. 华为HMS: https://developer.huawei.com/consumer/cn/doc/development/HMSCore-Guides/android-app-0000001071076052
3. OPPO推送: https://open.oppomobile.com/
4. VIVO推送: https://dev.vivo.com.cn/
5. 荣耀推送: https://developer.honor.com/cn/
6. 魅族推送: https://open.flyme.cn/

### 第三方推送平台
1. 极光: https://www.jiguang.cn/docs/push/android/overview/
2. 友盟: https://developer.umeng.com/docs/119267
3. 腾讯云: https://cloud.tencent.com/document/product/548
4. 个推: https://www.getui.com/

### 技术社区
1. CSDN推送集成教程
2. 掘金推送专栏
3. 简书推送实践

---

## 🎯 实施建议

### 快速上线方案（1-2周）
1. 使用极光推送或腾讯云推送
2. 只需集成一个SDK
3. 后台配置各厂商通道
4. 快速上线，后期优化

### 深度定制方案（1个月+）
1. 单独集成各厂商SDK
2. 实现统一推送接口
3. 优化消息路由策略
4. 完善数据监控体系

### 企业级方案（持续维护）
1. 建立推送监控平台
2. 实现智能推送策略
3. 多维度数据埋点
4. 持续优化到达率

---

## 🔄 技能维护

**最后更新**: 2026年6月
**更新记录**:
- 2026-06: 添加荣耀、努比亚/红魔支持
- 2024-12: 更新各品牌SDK版本要求
- 2024-06: 完善消息分类配置指南

**反馈渠道**: 如有SDK文档更新或集成问题，请提交Issue
