export const REQUEST_FAILED_CODE = "REQUEST_FAILED";
export const REQUEST_FAILED_STATUS = 0;
export const EMPTY_ERROR_MESSAGE = "";

export const REQUEST_FAILED_MESSAGE = "请求失败，请稍后重试";
export const CLOUD_LOGIN_FAILED_MESSAGE = "登录失败，请稍后重试";
export const CLOUD_CAPTCHA_FAILED_MESSAGE = "验证码获取失败，请重试";

export const ERROR_CODE_REQUEST_VALIDATION = "REQUEST_VALIDATION";
export const ERROR_CODE_LOCAL_TOKEN_INVALID = "LOCAL_TOKEN_INVALID";
export const ERROR_CODE_LOCAL_API_INTERNAL = "LOCAL_API_INTERNAL";
export const ERROR_CODE_ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
export const ERROR_CODE_ACCOUNT_DUPLICATE = "ACCOUNT_DUPLICATE";
export const ERROR_CODE_ACCOUNT_STATE_CONFLICT = "ACCOUNT_STATE_CONFLICT";
export const ERROR_CODE_CREDENTIAL_UNAVAILABLE = "CREDENTIAL_UNAVAILABLE";
export const ERROR_CODE_NOT_LOGGED_IN = "NOT_LOGGED_IN";
export const ERROR_CODE_CLOUD_FORBIDDEN = "CLOUD_FORBIDDEN";
export const ERROR_CODE_CLOUD_REPORT_FAILED = "CLOUD_REPORT_FAILED";
export const ERROR_CODE_CLOUD_ITEM_SYNC_FAILED = "CLOUD_ITEM_SYNC_FAILED";
export const ERROR_CODE_ITEM_SYNC_IN_PROGRESS = "ITEM_SYNC_IN_PROGRESS";
export const ERROR_CODE_XIANYU_COOKIE_INVALID = "XIANYU_COOKIE_INVALID";
export const ERROR_CODE_XIANYU_COOKIE_MISMATCH = "XIANYU_COOKIE_MISMATCH";
export const ERROR_CODE_XIANYU_RISK_CONTROL = "XIANYU_RISK_CONTROL";
export const ERROR_CODE_XIANYU_NETWORK = "XIANYU_NETWORK";
export const ERROR_CODE_XIANYU_PROTOCOL = "XIANYU_PROTOCOL";

/**
 * Operator-facing hint for each stable backend error code.
 *
 * Keeping the mapping here means the local service can keep short, stable
 * messages while the renderer decides how much guidance to show.
 */
export const ERROR_CODE_MESSAGES: Record<string, string> = {
  [ERROR_CODE_REQUEST_VALIDATION]: "提交内容不合法，请检查后重试",
  [ERROR_CODE_LOCAL_TOKEN_INVALID]: "本地服务鉴权失败，请重启应用",
  [ERROR_CODE_LOCAL_API_INTERNAL]: "本地服务内部错误，请查看本地日志",
  [ERROR_CODE_ACCOUNT_NOT_FOUND]: "账号不存在，请刷新后重试",
  [ERROR_CODE_ACCOUNT_DUPLICATE]: "该闲鱼账号已添加，请勿重复添加",
  [ERROR_CODE_ACCOUNT_STATE_CONFLICT]: "账号当前状态不允许该操作",
  [ERROR_CODE_CREDENTIAL_UNAVAILABLE]: "本地 Cookie 凭据不可用，请重新录入 Cookie",
  [ERROR_CODE_NOT_LOGGED_IN]: "登录状态已失效，请重新登录",
  [ERROR_CODE_CLOUD_FORBIDDEN]: "授权已过期，请联系管理员",
  [ERROR_CODE_CLOUD_REPORT_FAILED]: "账号信息上报云端失败，请检查网络后重试",
  [ERROR_CODE_CLOUD_ITEM_SYNC_FAILED]: "商品同步失败，请稍后重试",
  [ERROR_CODE_ITEM_SYNC_IN_PROGRESS]: "该账号正在同步商品，请稍后再试",
  [ERROR_CODE_XIANYU_COOKIE_INVALID]: "Cookie 已失效，请重新从浏览器复制完整 Cookie",
  [ERROR_CODE_XIANYU_COOKIE_MISMATCH]: "该 Cookie 属于其他闲鱼账号，请确认后重试",
  [ERROR_CODE_XIANYU_RISK_CONTROL]: "闲鱼要求人工验证，请用该账号在闲鱼网页版完成验证后重试",
  [ERROR_CODE_XIANYU_NETWORK]: "无法连接闲鱼，请检查网络或代理后重试",
  [ERROR_CODE_XIANYU_PROTOCOL]: "闲鱼返回了无法识别的响应，请稍后重试；若持续失败请重新复制 Cookie"
};
