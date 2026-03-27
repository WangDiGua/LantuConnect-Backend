# 后端改动同步：登录验证码改造（图形验证码）

## 改动时间
- 日期：2026-03-23
- 影响环境：后端 API（登录与验证码模块）

## 改动背景
- 原方案为滑块验证码，登录请求使用 `captchaX`。
- 现改为字母数字混合图形验证码（包含背景干扰），登录请求改为 `captchaCode`。

## 影响范围
- 验证码接口
  - `GET /captcha/generate`
  - `POST /captcha/verify`
- 登录接口
  - `POST /auth/login`

## 接口变更明细

### 1) GET `/captcha/generate`
- 旧响应（已废弃）
  - `captchaId`
  - `backgroundImage`
  - `sliderImage`
  - `x`
  - `y`
- 新响应（生效）
  - `captchaId`
  - `captchaImage`（Base64 图片，含 `data:image/png;base64,` 前缀）

### 2) POST `/captcha/verify`
- 旧参数（已废弃）
  - `captchaId`
  - `x`
- 新参数（生效）
  - `captchaId`
  - `code`
- 说明
  - 返回 `boolean`，用于联调预校验。
  - 验证码为一次性，校验后即失效。

### 3) POST `/auth/login`
- 旧请求字段（已废弃）
  - `captchaX: number`
- 新请求字段（生效）
  - `captchaCode: string`
- 仍需字段
  - `username`
  - `password`
  - `captchaId`
  - `remember?`
- 错误码
  - 验证码缺失/错误/过期：`CAPTCHA_ERROR (2010)`

## 前端改造要求
1. 登录页拉取验证码时，使用 `GET /captcha/generate` 的 `captchaImage` 渲染图片。
2. 登录请求体把 `captchaX` 改为 `captchaCode`。
3. 刷新验证码时重新获取 `captchaId` 与 `captchaImage`，不可复用旧 `captchaId`。
4. 若前端有单独“验证码预校验”逻辑，`POST /captcha/verify` 参数改为 `captchaId + code`。

## 建议联调步骤
1. 调用 `GET /captcha/generate`，确认返回 `captchaId` 与可显示的 `captchaImage`。
2. 使用错误验证码调用 `POST /captcha/verify`，期望 `false`。
3. 提交登录请求时传 `captchaCode`，验证验证码错误时返回 `CAPTCHA_ERROR (2010)`。
4. 使用正确账号 + 正确验证码，验证登录成功并返回 token。

## 相关后端改动文件
- `src/main/java/com/lantu/connect/common/captcha/CaptchaResult.java`
- `src/main/java/com/lantu/connect/common/captcha/CaptchaService.java`
- `src/main/java/com/lantu/connect/common/captcha/controller/CaptchaController.java`
- `src/main/java/com/lantu/connect/common/captcha/impl/SliderCaptchaServiceImpl.java`
- `src/main/java/com/lantu/connect/auth/dto/LoginRequest.java`
- `src/main/java/com/lantu/connect/auth/service/impl/AuthServiceImpl.java`
- `docs/frontend-backend-alignment-spec.md`
