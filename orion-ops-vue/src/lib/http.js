import axios from 'axios'
import $message from 'ant-design-vue/lib/message'
import $storage from './storage'
import router from '../router/index'

const $http = axios.create({
  responseType: 'json',
  baseURL: process.env.VUE_APP_BASE_URI + process.env.VUE_APP_BASE_API,
  timeout: 10000
})

// 默认配置项
const defaultConfig = {
  // 是否需要登陆
  auth: true,
  // 超时时间
  timeout: 10000
}

/**
 * get请求
 */
const $get = (url, params = {}, config = {}) => {
  config.params = params
  return new Promise((resolve, reject) => {
    $http.get(url, fillDefaultConfig(config))
      .then(res => resolve(res))
      .catch(err => reject(err))
  })
}

/**
 * post请求
 */
const $post = (url, data = {}, config = {}) => {
  return new Promise((resolve, reject) => {
    $http.post(url, data, fillDefaultConfig(config))
      .then(res => resolve(res))
      .catch(err => reject(err))
  })
}

/**
 * http请求
 */
const $fetch = (url, method = 'get', config) => {
  return new Promise((resolve, reject) => {
    $http.request({
      url: url,
      mehod: method,
      ...fillDefaultConfig(config)
    })
      .then(res => resolve(res))
      .catch(err => reject(err))
  })
}

/**
 * 填充默认配置
 */
function fillDefaultConfig(config) {
  for (var defaultConfigKey in defaultConfig) {
    if (!(defaultConfigKey in config)) {
      config[defaultConfigKey] = defaultConfig[defaultConfigKey]
    }
  }
  return config
}

/**
 * 请求拦截器
 */
$http.interceptors.request.use(
  config => {
    const loginToken = $storage.get($storage.keys.LOGIN_TOKEN)
    // 登陆判断
    if (config.auth && !loginToken) {
      throw new RequestError(700, '用户未登录')
    }
    config.headers[$storage.keys.LOGIN_TOKEN] = loginToken
    return config
  }, err => {
    return Promise.reject(err)
  }
)

/**
 * 响应拦截器
 */
$http.interceptors.response.use(
  resp => {
    // 判断data
    var respData = resp.data
    if (!respData || !respData.code) {
      $message.warning('请求无效')
      return Promise.reject(resp)
    }
    // 判断code
    switch (respData.code) {
      case 200:
        // 正常返回
        return respData
      case 700:
        // 未登录
        $message.warning('会话过期')
        $storage.remove($storage.keys.LOGIN_TOKEN)
        router.push({ path: '/login' })
        return Promise.reject(respData)
      case 500:
        $message.error(respData.msg)
        return Promise.reject(respData)
      default:
        $message.warning(respData.msg)
        return Promise.reject(respData)
    }
  }, err => {
    let rejectWrapper
    if (err instanceof RequestError) {
      // 自定义error
      rejectWrapper = err.swap()
      if (err.code === 700) {
        rejectWrapper.tlevel('warning')
        router.push({ path: '/login' })
      }
    } else {
      // http错误
      if (!err.response || !err.response.status) {
        rejectWrapper = new RejectWrapper(0, '网络异常')
      } else {
        switch (err.response.status) {
          case 404:
            rejectWrapper = new RejectWrapper(404, '接口不存在', 'warning')
            break
          default:
            rejectWrapper = new RejectWrapper(err.response.status, '请求失败')
            break
        }
      }
    }
    rejectWrapper.tips()
    return Promise.reject(rejectWrapper)
  }
)

/**
 * 请求异常
 */
class RequestError extends Error {
  code
  msg

  constructor(code, msg) {
    super()
    this.code = code
    this.msg = msg
    Error.captureStackTrace(this, this.constructor)
  }

  swap() {
    return new RejectWrapper(this.code, this.msg)
  }
}

/**
 * reject包装
 */
class RejectWrapper {
  code
  msg
  level

  constructor(code, msg, level = 'error') {
    this.code = code
    this.msg = msg
    this.level = level
  }

  tlevel(_level = 'error') {
    this.level = _level
    return this
  }

  tips() {
    $message[this.level].call(this, this.msg)
    delete this.level
  }
}

export default {
  $get,
  $post,
  $fetch,
  BASE_URL: process.env.VUE_APP_BASE_URI,
  BASE_HOST: process.env.VUE_APP_BASE_HOST
}