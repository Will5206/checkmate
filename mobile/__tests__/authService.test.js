/**
 * Unit tests for authService
 * Tests the login, logout, and authentication checking logic
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { loginUser, logoutUser, isUserLoggedIn, getCurrentUser } from '../services/authService';

// Mock AsyncStorage
jest.mock('@react-native-async-storage/async-storage', () => ({
  setItem: jest.fn(),
  getItem: jest.fn(),
  clear: jest.fn(),
}));

// Mock fetch
global.fetch = jest.fn();

describe('authService', () => {
  beforeEach(() => {
    // Clear all mocks before each test
    jest.clearAllMocks();
  });

  describe('loginUser', () => {
    test('should return success and store user data on successful login', async () => {
      // Mock successful API response
      const mockResponse = {
        success: true,
        userId: '123',
        name: 'Test User',
        email: 'test@example.com',
        token: 'fake-token',
      };

      global.fetch.mockResolvedValueOnce({
        json: async () => mockResponse,
      });

      const result = await loginUser('test@example.com', 'password123');

      // Verify the result
      expect(result.success).toBe(true);
      expect(result.userId).toBe('123');

      // Verify AsyncStorage was called to store user data
      expect(AsyncStorage.setItem).toHaveBeenCalledWith('userId', '123');
      expect(AsyncStorage.setItem).toHaveBeenCalledWith('userName', 'Test User');
      expect(AsyncStorage.setItem).toHaveBeenCalledWith('userEmail', 'test@example.com');
      expect(AsyncStorage.setItem).toHaveBeenCalledWith('authToken', 'fake-token');
      expect(AsyncStorage.setItem).toHaveBeenCalledWith('isLoggedIn', 'true');
    });

    test('should return error message on failed login (invalid credentials)', async () => {
      // Mock failed API response
      const mockResponse = {
        success: false,
        message: 'Invalid credentials',
      };

      global.fetch.mockResolvedValueOnce({
        json: async () => mockResponse,
      });

      const result = await loginUser('wrong@example.com', 'wrongpassword');

      // Verify the result shows failure
      expect(result.success).toBe(false);
      expect(result.message).toBe('Invalid credentials');

      // Verify AsyncStorage was NOT called to store user data
      expect(AsyncStorage.setItem).not.toHaveBeenCalled();
    });

    test('should handle network errors gracefully', async () => {
      // Mock network error
      global.fetch.mockRejectedValueOnce(new Error('Network error'));

      const result = await loginUser('test@example.com', 'password123');

      // Verify error handling
      expect(result.success).toBe(false);
      expect(result.message).toBe('Network error. Please try again.');
    });

    test('should handle empty credentials', async () => {
      const mockResponse = {
        success: false,
        message: 'Email and password are required',
      };

      global.fetch.mockResolvedValueOnce({
        json: async () => mockResponse,
      });

      const result = await loginUser('', '');

      expect(result.success).toBe(false);
    });
  });

  describe('logoutUser', () => {
    test('should clear all stored data from AsyncStorage', async () => {
      await logoutUser();

      // Verify AsyncStorage.clear was called
      expect(AsyncStorage.clear).toHaveBeenCalledTimes(1);
    });
  });

  describe('isUserLoggedIn', () => {
    test('should return true when user is logged in', async () => {
      // Mock AsyncStorage to return 'true'
      AsyncStorage.getItem.mockResolvedValueOnce('true');

      const result = await isUserLoggedIn();

      expect(result).toBe(true);
      expect(AsyncStorage.getItem).toHaveBeenCalledWith('isLoggedIn');
    });

    test('should return false when user is not logged in', async () => {
      // Mock AsyncStorage to return null
      AsyncStorage.getItem.mockResolvedValueOnce(null);

      const result = await isUserLoggedIn();

      expect(result).toBe(false);
    });

    test('should return false when isLoggedIn is "false"', async () => {
      // Mock AsyncStorage to return 'false'
      AsyncStorage.getItem.mockResolvedValueOnce('false');

      const result = await isUserLoggedIn();

      expect(result).toBe(false);
    });
  });

  describe('getCurrentUser', () => {
    test('should return user data when logged in', async () => {
      // Mock AsyncStorage to return user data
      AsyncStorage.getItem
        .mockResolvedValueOnce('true') // isLoggedIn
        .mockResolvedValueOnce('123') // userId
        .mockResolvedValueOnce('Test User') // userName
        .mockResolvedValueOnce('test@example.com'); // userEmail

      const result = await getCurrentUser();

      expect(result).toEqual({
        userId: '123',
        name: 'Test User',
        email: 'test@example.com',
      });
    });

    test('should return null when not logged in', async () => {
      // Mock AsyncStorage to return null for isLoggedIn
      AsyncStorage.getItem.mockResolvedValueOnce(null);

      const result = await getCurrentUser();

      expect(result).toBe(null);
    });
  });
});
