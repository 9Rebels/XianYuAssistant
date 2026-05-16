import { request } from '@/utils/request'

export interface LoginDevice {
  id: number
  deviceName: string
  browserName: string
  osName: string
  loginIp: string
  loginTime: string
  lastActiveTime: string
  expireTime: string
  status: number
  current: boolean
}

/** 获取当前用户信息 */
export function getCurrentUser() {
  return request<{ username: string; lastLoginTime: string }>({
    url: '/system/currentUser',
    method: 'post'
  })
}

/** 修改密码 */
export function changePassword(data: { oldPassword: string; newPassword: string; confirmPassword: string }) {
  return request<null>({
    url: '/system/changePassword',
    method: 'post',
    data
  })
}

/** 登录设备列表 */
export function listLoginDevices() {
  return request<LoginDevice[]>({
    url: '/system/loginDevices',
    method: 'post'
  })
}

/** 踢出登录设备 */
export function kickLoginDevice(tokenId: number) {
  return request<null>({
    url: '/system/kickLoginDevice',
    method: 'post',
    data: { tokenId }
  })
}
