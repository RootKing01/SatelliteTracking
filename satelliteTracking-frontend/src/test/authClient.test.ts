import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  executeLoginFlow,
  executeLogoutFlow,
  executeRegisterFlow,
  getCurrentUser,
  login,
  logout,
  register,
} from '../api/authClient'
import { httpClient } from '../api/httpClient'

vi.mock('../api/httpClient', () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

const mockedHttpClient = {
  get: vi.mocked(httpClient.get),
  post: vi.mocked(httpClient.post),
}

const authenticatedResponse = {
  authenticated: true,
  message: 'Operazione completata',
  user: {
    id: 1,
    username: 'testuser',
    email: 'test@example.com',
    role: 'USER',
  },
  token: null,
}

describe('Auth Client', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('registers a user through the application client', async () => {
    mockedHttpClient.post.mockResolvedValueOnce({ data: authenticatedResponse })

    await expect(register({
      username: 'testuser',
      email: 'test@example.com',
      password: 'password123',
    })).resolves.toEqual(authenticatedResponse)

    expect(mockedHttpClient.post).toHaveBeenCalledWith('/api/auth/register', {
      username: 'testuser',
      email: 'test@example.com',
      password: 'password123',
    })
  })

  it('logs in with username or email', async () => {
    mockedHttpClient.post.mockResolvedValueOnce({ data: authenticatedResponse })

    await expect(login({
      usernameOrEmail: 'test@example.com',
      password: 'password123',
    })).resolves.toEqual(authenticatedResponse)

    expect(mockedHttpClient.post).toHaveBeenCalledWith('/api/auth/login', {
      usernameOrEmail: 'test@example.com',
      password: 'password123',
    })
  })

  it('loads the current user with the provided abort signal', async () => {
    const signal = new AbortController().signal
    mockedHttpClient.get.mockResolvedValueOnce({ data: authenticatedResponse })

    await expect(getCurrentUser(signal)).resolves.toEqual(authenticatedResponse)
    expect(mockedHttpClient.get).toHaveBeenCalledWith('/api/auth/me', { signal })
  })

  it('logs out through the application client', async () => {
    mockedHttpClient.post.mockResolvedValueOnce({
      data: { authenticated: false, message: 'Sessione chiusa', user: null },
    })

    await expect(logout()).resolves.toEqual({
      authenticated: false,
      message: 'Sessione chiusa',
      user: null,
    })
    expect(mockedHttpClient.post).toHaveBeenCalledWith('/api/auth/logout')
  })

  it('completes the login flow after validating the session', async () => {
    mockedHttpClient.post.mockResolvedValueOnce({ data: authenticatedResponse })
    mockedHttpClient.get.mockResolvedValueOnce({ data: authenticatedResponse })

    await expect(executeLoginFlow({
      usernameOrEmail: 'test@example.com',
      password: 'password123',
    })).resolves.toEqual({
      user: authenticatedResponse.user,
      info: 'Benvenuto testuser',
      error: '',
    })
    expect(mockedHttpClient.get).toHaveBeenCalledWith('/api/auth/me', { signal: undefined })
  })

  it('does not register when passwords do not match', async () => {
    await expect(executeRegisterFlow({
      username: 'testuser',
      email: 'test@example.com',
      password: 'password123',
      passwordConfirm: 'different-password',
    })).resolves.toEqual({
      user: null,
      info: '',
      error: 'Le password non coincidono.',
    })

    expect(mockedHttpClient.post).not.toHaveBeenCalled()
  })

  it('returns the API message when login is rejected', async () => {
    mockedHttpClient.post.mockResolvedValueOnce({
      data: {
        authenticated: false,
        message: 'Credenziali non valide',
        user: null,
      },
    })

    await expect(executeLoginFlow({
      usernameOrEmail: 'wrong@example.com',
      password: 'wrongpass',
    })).resolves.toEqual({
      user: null,
      info: '',
      error: 'Credenziali non valide',
    })
  })

  it('returns an error when logout fails', async () => {
    mockedHttpClient.post.mockRejectedValueOnce(new Error('network error'))

    await expect(executeLogoutFlow()).resolves.toMatchObject({
      error: expect.any(String),
    })
  })
})
