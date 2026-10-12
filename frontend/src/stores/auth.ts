import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { User, UserManager, WebStorageStateStore } from 'oidc-client-ts'
import { apiService, type AccountDeletionResult, type CurrentUser } from '../services/api'
import { LOGIN_NOT_ALLOWED, type ApiError } from '../services/apiError'

const OIDC_AUTHORITY = 'https://id.oglimmer.de/realms/status-tacos'

/** Keycloak's own account console: password, sessions and linked logins. */
export const ACCOUNT_CONSOLE_URL = `${OIDC_AUTHORITY}/account`

const oidcConfig = {
  authority: OIDC_AUTHORITY,
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

const SIGN_IN_NOTICE_KEY = 'status-tacos-sign-in-notice'

/** Why the app signed the user out. The landing page shows it after the redirect. */
export interface SignInNotice {
  reason: 'not_allowed' | 'session_ended'
  email?: string
}

/** Returns the notice once, then forgets it. */
export function takeSignInNotice(): SignInNotice | null {
  try {
    const stored = sessionStorage.getItem(SIGN_IN_NOTICE_KEY)
    sessionStorage.removeItem(SIGN_IN_NOTICE_KEY)
    return stored ? (JSON.parse(stored) as SignInNotice) : null
  } catch {
    return null
  }
}

function saveSignInNotice(notice: SignInNotice) {
  try {
    sessionStorage.setItem(SIGN_IN_NOTICE_KEY, JSON.stringify(notice))
  } catch {
    // Without storage the landing page shows no notice
  }
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

  /**
   * Deletes the account on the server, then forgets the session locally. No Keycloak logout
   * redirect: the Keycloak user and its sessions are gone already.
   */
  const deleteAccount = async (): Promise<AccountDeletionResult> => {
    const result = await apiService.deleteAccount(user.value)
    await userManager.removeUser()
    user.value = null
    localStorage.removeItem('status-tacos-user-config')
    return result
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


  let isSigningOut = false

  /**
   * The server refused the token (HTTP 401). Signs the user out and sends them to the landing page,
   * which tells them why. Parallel requests all fail with 401, so only the first one acts.
   */
  const handleUnauthorized = async (apiError: ApiError) => {
    if (isSigningOut) {
      return
    }
    isSigningOut = true
    const notAllowed = apiError.code === LOGIN_NOT_ALLOWED
    saveSignInNotice({
      reason: notAllowed ? 'not_allowed' : 'session_ended',
      email: user.value?.profile?.email,
    })
    localStorage.removeItem('status-tacos-user-config')

    if (notAllowed) {
      // End the session at the identity provider too. Else the next sign-in silently uses the same
      // account again, and the user cannot pick another one.
      try {
        await userManager.signoutRedirect()
        return
      } catch (err) {
        console.error('Logout of a not allowed user failed:', err)
      }
    }

    await userManager.removeUser()
    user.value = null
    window.location.assign('/')
  }

  apiService.setUnauthorizedHandler(handleUnauthorized)

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
    deleteAccount,
    handleCallback,
    renewToken,
    fetchCurrentUser,
  }
})
