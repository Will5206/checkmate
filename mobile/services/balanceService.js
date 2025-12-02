import { API_BASE_URL } from '../config';

/**
 * Fetch the current balance for a user
 * @param {string} userId - The user's ID
 * @returns {Promise<number>} - The user's current balance
 */
export const getUserBalance = async (userId) => {
  try {
    const response = await fetch(`${API_BASE_URL}/balance?userId=${userId}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    const data = await response.json();

    if (response.ok) {
      return data.balance;
    } else {
      console.error('Error fetching balance:', data.error);
      return 0.0;
    }
  } catch (error) {
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
  try {
    const response = await fetch(`${API_BASE_URL}/balance/add?userId=${encodeURIComponent(userId)}&amount=${amount}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    const data = await response.json();
    return data;
  } catch (error) {
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
  try {
    const response = await fetch(`${API_BASE_URL}/balance/cashout?userId=${encodeURIComponent(userId)}&amount=${amount}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    const data = await response.json();
    return data;
  } catch (error) {
    console.error('Error cashing out:', error);
    return {
      success: false,
      error: 'Network error. Please check your connection.',
    };
  }
};
