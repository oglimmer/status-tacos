import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { User, UserManager, WebStorageStateStore } from 'oidc-client-ts'
import { apiService, type CurrentUser } from '../services/api'

const oidcConfig = {
  authority: 'https://id.oglimmer.de/realms/status-tacos',
  client_id: 'status-tacos-frontend',
  redirect_uri: window.location.origin + '/callback',
  response_type: 'code',
  scope: 'openid profile email offline_access',
  post_logout_redirect_uri: window.location.origin,
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  useRefreshToken: true,
  automaticSilentRenew: true,
  checkSessionInterval: 3000,
  monitorSession: true,
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const isAuthenticated = computed(() => !!user.value && !user.value.expired)
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const userManager = new UserManager(oidcConfig)

  const initAuth = async () => {
    isLoading.value = true
    error.value = null

    try {
      const existingUser = await userManager.getUser()
      console.log('[Auth] Existing user from OIDC:', existingUser)
      console.log('[Auth] User expired?', existingUser?.expired)

      if (existingUser && !existingUser.expired) {
        console.log('[Auth] Using existing non-expired user')
        user.value = existingUser
      } else if (existingUser && existingUser.expired) {
        console.log('[Auth] User is expired, attempting refresh')
        try {
          const refreshedUser = await userManager.signinSilent()
          console.log('[Auth] Successfully refreshed user token')
          user.value = refreshedUser
        } catch {
          console.log('[Auth] Refresh failed, clearing user')
          await userManager.removeUser()
          user.value = null
        }
      } else {
        console.log('[Auth] No valid authentication found')
      }
    } catch (err) {
      error.value = 'Failed to initialize authentication'
      console.error('Auth initialization error:', err)
    } finally {
      isLoading.value = false
    }
  }

  const login = async () => {
    error.value = null
    try {
      await userManager.signinRedirect()
    } catch (err) {
      error.value = 'Login failed'
      console.error('Login error:', err)
    }
  }

  const handleCallback = async () => {
    console.log('Handling authentication callback...')
    isLoading.value = true
    error.value = null

    try {
      const callbackUser = await userManager.signinRedirectCallback()
      user.value = callbackUser
      console.log('[Auth] Callback user:', callbackUser)

      // Automatically fetch user data from the API
      const userData = await fetchCurrentUser()
      console.log('User data from API:', userData)

      return callbackUser
    } catch (err) {
      error.value = 'Authentication callback failed'
      console.error('Callback error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  const logout = async () => {
    error.value = null
    try {
      await userManager.signoutRedirect()
      user.value = null
      localStorage.removeItem('status-tacos-user-config')
    } catch (err) {
      error.value = 'Logout failed'
      console.error('Logout error:', err)
    }
  }

  const renewToken = async () => {
    try {
      const renewedUser = await userManager.signinSilent()
      user.value = renewedUser
      return renewedUser
    } catch (err) {
      console.error('Token renewal failed:', err)
      user.value = null
      throw err
    }
  }

  const fetchCurrentUser = async (): Promise<CurrentUser> => {
    try {
      return await apiService.get<CurrentUser>('/users/me', user.value)
    } catch (err) {
      console.error('Failed to fetch current user:', err)
      throw err
    }
  }


  userManager.events.addUserLoaded((loadedUser) => {
    user.value = loadedUser
  })

  userManager.events.addUserUnloaded(() => {
    user.value = null
  })

  userManager.events.addAccessTokenExpired(() => {
    user.value = null
  })

  return {
    user: computed(() => user.value),
    isAuthenticated,
    isLoading: computed(() => isLoading.value),
    error: computed(() => error.value),
    initAuth,
    login,
    logout,
    handleCallback,
    renewToken,
    fetchCurrentUser,
  }
})
