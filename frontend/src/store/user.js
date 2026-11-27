import { defineStore } from 'pinia'
import { login as loginApi, fetchMyMenus } from '../api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('reip_token') || '',
    userInfo: JSON.parse(localStorage.getItem('reip_user') || 'null'),
    menus: []
  }),
  actions: {
    async login(form) {
      const data = await loginApi(form)
      this.token = data.token
      this.userInfo = data
      localStorage.setItem('reip_token', data.token)
      localStorage.setItem('reip_user', JSON.stringify(data))
      return data
    },
    async loadMenus() {
      this.menus = await fetchMyMenus()
      return this.menus
    },
    logout() {
      this.token = ''
      this.userInfo = null
      this.menus = []
      localStorage.removeItem('reip_token')
      localStorage.removeItem('reip_user')
    }
  }
})
