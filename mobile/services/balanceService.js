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
