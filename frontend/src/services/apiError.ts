/** Error code in the 401 body when the email + login provider is not on the server's allow-list. */
export const LOGIN_NOT_ALLOWED = 'login_not_allowed'

/** A failed API call. The message is for the user; status and code are for the code. */
export class ApiError extends Error {
  constructor(
    message: string,
    /** HTTP status. 0 when the server was not reachable. */
    readonly status: number,
    /** The "error" field of the response body, if any. */
    readonly code?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** A short message that tells the user what went wrong and what to do. */
export function friendlyMessage(status: number, serverMessage?: string): string {
  // Client errors from our own controllers can carry a message for the user
  if (status >= 400 && status < 500 && status !== 401 && status !== 403 && serverMessage) {
    return serverMessage
  }
  switch (status) {
    case 0:
      return 'Cannot reach Status Tacos. Check your internet connection and try again.'
    case 400:
    case 422:
      return 'Some of the data is not valid. Check your input and try again.'
    case 401:
      return 'Your session has ended. Please sign in again.'
    case 403:
      return 'You do not have permission to do this.'
    case 404:
      return 'We could not find this. Maybe somebody deleted it.'
    case 409:
      return 'This conflicts with existing data. Reload the page and try again.'
    case 413:
      return 'The data is too large.'
    case 429:
      return 'Too many requests. Wait a moment and try again.'
    case 502:
    case 503:
    case 504:
      return 'Status Tacos is not available right now. Try again in a few minutes.'
  }
  if (status >= 500) {
    return 'Something went wrong on our side. Try again later.'
  }
  return 'The request failed. Try again.'
}
