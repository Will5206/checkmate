import AsyncStorage from '@react-native-async-storage/async-storage';
import { API_BASE_URL } from '../config';

/**
 * Create a new receipt and share it with participants
 * @param {Object} receiptData - Receipt data object
 * @param {string} receiptData.restaurant_name - Name of the restaurant/merchant
 * @param {number} receiptData.total_amount - Total amount of the receipt
 * @param {number} receiptData.tax - Tax amount
 * @param {number} receiptData.tip - Tip amount
 * @param {Array} receiptData.items - Array of items with name, price, qty
 * @param {Array} receiptData.participants - Array of participant email addresses
 * @returns {Promise<Object>} Response with success status and receipt data
 */
export async function createReceipt(receiptData) {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const url = `${API_BASE_URL}/receipts/create?userId=${encodeURIComponent(userId)}`;
    
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(receiptData),
    });

    const data = await response.json();
    return data;

  } catch (error) {
    console.error('Create receipt error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Get pending receipts for the current user
 * @returns {Promise<Object>} Response with success status and receipts array
 */
export async function getPendingReceipts() {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/receipts/pending?userId=${encodeURIComponent(userId)}`,
      {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      }
    );

    const data = await response.json();
    return data;

  } catch (error) {
    console.error('Get pending receipts error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Accept a receipt
 * @param {number} receiptId - The receipt ID to accept
 * @returns {Promise<Object>} Response with success status
 */
export async function acceptReceipt(receiptId) {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/receipts/accept?receiptId=${receiptId}&userId=${encodeURIComponent(userId)}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      }
    );

    const data = await response.json();
    return data;

  } catch (error) {
    console.error('Accept receipt error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Decline a receipt
 * @param {number} receiptId - The receipt ID to decline
 * @returns {Promise<Object>} Response with success status
 */
export async function declineReceipt(receiptId) {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/receipts/decline?receiptId=${receiptId}&userId=${encodeURIComponent(userId)}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      }
    );

    const data = await response.json();
    return data;

  } catch (error) {
    console.error('Decline receipt error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Get all receipts for the current user (activity/history)
 * Includes accepted, declined, and uploaded receipts
 * @returns {Promise<Object>} Response with success status and receipts array
 */
export async function getActivityReceipts() {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/receipts/activity?userId=${encodeURIComponent(userId)}`,
      {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      }
    );

    const data = await response.json();
    return data;

  } catch (error) {
    console.error('Get activity receipts error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Claim an item from a receipt
 * @param {number} receiptId - The receipt ID
 * @param {number} itemId - The item ID to claim
 * @param {number} quantity - Quantity to claim (default 1)
 * @returns {Promise<Object>} Response with success status and owed amount
 */
export async function claimItem(receiptId, itemId, quantity = 1) {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/receipts/items/claim?receiptId=${receiptId}&itemId=${itemId}&userId=${encodeURIComponent(userId)}&quantity=${quantity}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      }
    );

    const data = await response.json();
    return data;

  } catch (error) {
    console.error('Claim item error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Unclaim an item from a receipt
 * @param {number} receiptId - The receipt ID
 * @param {number} itemId - The item ID to unclaim
 * @returns {Promise<Object>} Response with success status and owed amount
 */
export async function unclaimItem(receiptId, itemId) {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/receipts/items/claim?receiptId=${receiptId}&itemId=${itemId}&userId=${encodeURIComponent(userId)}`,
      {
        method: 'DELETE',
        headers: {
          'Content-Type': 'application/json',
        },
      }
    );

    const data = await response.json();
    return data;

  } catch (error) {
    console.error('Unclaim item error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Get item assignments and owed amount for a receipt
 * @param {number} receiptId - The receipt ID
 * @returns {Promise<Object>} Response with assignments and owed amount
 */
export async function getItemAssignments(receiptId) {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const url = `${API_BASE_URL}/receipts/items/assignments?receiptId=${receiptId}&userId=${encodeURIComponent(userId)}`;
    console.log('Fetching item assignments from:', url);
    
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Check if response is actually JSON
    const contentType = response.headers.get('content-type');
    if (!contentType || !contentType.includes('application/json')) {
      const text = await response.text();
      console.error('Non-JSON response from server:', text.substring(0, 200));
      return {
        success: false,
        message: `Server error: ${response.status} ${response.statusText}. The endpoint may not be registered. Please restart the backend server.`,
      };
    }

    const data = await response.json();
    return data;

  } catch (error) {
    console.error('Get item assignments error:', error);
    return {
      success: false,
      message: error.message || 'Network error. Please check your connection.',
    };
  }
}

/**
 * Add participants to an existing receipt
 * @param {number} receiptId - The receipt ID
 * @param {Array<string>} participantEmails - Array of email addresses to add
 * @returns {Promise<Object>} Response with success status
 */
export async function addParticipantsToReceipt(receiptId, participantEmails) {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/receipts/add-participants?receiptId=${receiptId}&userId=${encodeURIComponent(userId)}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          participants: participantEmails,
        }),
      }
    );

    const data = await response.json();
    return data;

  } catch (error) {
    console.error('Error adding participants:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Pay for a receipt
 * @param {number} receiptId - The receipt ID
 * @returns {Promise<Object>} Response with success status and payment details
 */
export async function payReceipt(receiptId) {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/receipts/pay?receiptId=${receiptId}&userId=${encodeURIComponent(userId)}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      }
    );

    const data = await response.json();
    return data;

  } catch (error) {
    console.error('Pay receipt error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}
