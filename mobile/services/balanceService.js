import AsyncStorage from '@react-native-async-storage/async-storage';
import { API_BASE_URL } from '../config';

/**
 * Get authentication headers with token
 */
const getAuthHeaders = async () => {
  const token = await AsyncStorage.getItem('authToken');
  const headers = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
};

/**
 * Fetch the current balance for a user
 * @param {string} userId - The user's ID
 * @returns {Promise<number>} - The user's current balance
 */
export const getUserBalance = async (userId) => {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 30000); // 30 second timeout
  
  try {
    const headers = await getAuthHeaders();
    const response = await fetch(`${API_BASE_URL}/balance?userId=${userId}`, {
      method: 'GET',
      headers,
      signal: controller.signal,
    });

    clearTimeout(timeoutId);
    
    if (!response.ok) {
      let errorData;
      try {
        errorData = await response.json();
      } catch (e) {
        errorData = { error: `Server error: ${response.status} ${response.statusText}` };
      }
      console.error('Error fetching balance:', errorData.error);
      return 0.0;
    }

    const data = await response.json();
    return data.balance;
  } catch (error) {
    clearTimeout(timeoutId);
    if (error.name === 'AbortError') {
      console.error('Network error fetching balance: Request timeout');
      return 0.0;
    }
    console.error('Network error fetching balance:', error);
    return 0.0;
  }
};

/**
 * Add money to user's balance (test feature)
 * @param {string} userId - The user's ID
 * @param {number} amount - Amount to add
 * @returns {Promise<Object>} - Response with success status and new balance
 */
export const addMoney = async (userId, amount) => {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 30000); // 30 second timeout
  
  try {
    const headers = await getAuthHeaders();
    const response = await fetch(`${API_BASE_URL}/balance/add?userId=${encodeURIComponent(userId)}&amount=${amount}`, {
      method: 'POST',
      headers,
      signal: controller.signal,
    });

    clearTimeout(timeoutId);
    
    if (!response.ok) {
      let errorData;
      try {
        errorData = await response.json();
      } catch (e) {
        errorData = { success: false, error: `Server error: ${response.status} ${response.statusText}` };
      }
      return errorData;
    }

    const data = await response.json();
    return data;
  } catch (error) {
    clearTimeout(timeoutId);
    if (error.name === 'AbortError') {
      console.error('Error adding money: Request timeout');
      return {
        success: false,
        error: 'Request timeout. Please check your connection.',
      };
    }
    console.error('Error adding money:', error);
    return {
      success: false,
      error: 'Network error. Please check your connection.',
    };
  }
};

/**
 * Cash out money from user's balance (test feature)
 * @param {string} userId - The user's ID
 * @param {number} amount - Amount to withdraw
 * @returns {Promise<Object>} - Response with success status and new balance
 */
export const cashOut = async (userId, amount) => {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 30000); // 30 second timeout
  
  try {
    const headers = await getAuthHeaders();
    const response = await fetch(`${API_BASE_URL}/balance/cashout?userId=${encodeURIComponent(userId)}&amount=${amount}`, {
      method: 'POST',
      headers,
      signal: controller.signal,
    });

    clearTimeout(timeoutId);
    
    if (!response.ok) {
      let errorData;
      try {
        errorData = await response.json();
      } catch (e) {
        errorData = { success: false, error: `Server error: ${response.status} ${response.statusText}` };
      }
      return errorData;
    }

    const data = await response.json();
    return data;
  } catch (error) {
    clearTimeout(timeoutId);
    if (error.name === 'AbortError') {
      console.error('Error cashing out: Request timeout');
      return {
        success: false,
        error: 'Request timeout. Please check your connection.',
      };
    }
    console.error('Error cashing out:', error);
    return {
      success: false,
      error: 'Network error. Please check your connection.',
    };
  }
};
