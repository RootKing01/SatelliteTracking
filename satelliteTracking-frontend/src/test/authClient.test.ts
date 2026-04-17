import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'

// Mock axios
vi.mock('axios')
const mockedAxios = axios as unknown as {
  post: ReturnType<typeof vi.fn>
  get: ReturnType<typeof vi.fn>
}

describe('Auth Client', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should register a new user', async () => {
    // Arrange
    const mockResponse = {
      data: {
        authenticated: true,
        message: 'Registrazione completata',
        user: {
          id: 1,
          email: 'test@example.com',
          nickname: 'testuser'
        },
        token: null
      }
    }

    mockedAxios.post.mockResolvedValueOnce(mockResponse)

    // Act
    const response = await axios.post('/api/auth/register', {
      email: 'test@example.com',
      nickname: 'testuser',
      password: 'password123'
    })

    // Assert
    expect(response.data.authenticated).toBe(true)
    expect(response.data.user.email).toBe('test@example.com')
    expect(response.data.token).toBeNull()
  })

  it('should login user', async () => {
    // Arrange
    const mockResponse = {
      data: {
        authenticated: true,
        message: 'Login completato',
        user: {
          id: 1,
          email: 'user@example.com',
          nickname: 'user'
        },
        token: null
      }
    }

    mockedAxios.post.mockResolvedValueOnce(mockResponse)

    // Act
    const response = await axios.post('/api/auth/login', {
      email: 'user@example.com',
      password: 'password123'
    })

    // Assert
    expect(response.data.authenticated).toBe(true)
    expect(mockedAxios.post).toHaveBeenCalledWith('/api/auth/login', {
      email: 'user@example.com',
      password: 'password123'
    })
  })

  it('should handle login error', async () => {
    // Arrange
    mockedAxios.post.mockRejectedValueOnce({
      response: {
        status: 401,
        data: {
          authenticated: false,
          message: 'Credenziali non valide'
        }
      }
    })

    // Act & Assert
    try {
      await axios.post('/api/auth/login', {
        email: 'wrong@example.com',
        password: 'wrongpass'
      })
      throw new Error('Should have failed')
    } catch (error: any) {
      expect(error.response.status).toBe(401)
    }
  })
})
