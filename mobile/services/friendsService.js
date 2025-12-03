import AsyncStorage from '@react-native-async-storage/async-storage';
import { API_BASE_URL } from '../config';

/**
 * Add friend by email
 * @param {string} email - Friend's email address
 * @returns {Promise<Object>} Response with success status and friend data
 */
export async function addFriendByEmail(email) {
  try {
    const userId = await AsyncStorage.getItem('userId');
    console.log('userId from AsyncStorage:', userId);

    if (!userId) {
      console.log('No userId found - user not logged in');
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const url = `${API_BASE_URL}/friends/add-by-email?userId=${userId}&email=${encodeURIComponent(email)}`;
    console.log('Making request to:', url);

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    console.log('Response status:', response.status);
    const data = await response.json();
    console.log('Response data:', data);
    return data;

  } catch (error) {
    console.error('Add friend error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Get list of user's friends with full details
 * @returns {Promise<Object>} Response with friends array
 */
export async function getFriendsList() {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/friends/list?userId=${userId}`,
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
    console.error('Get friends list error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Get pending friend requests for the current user
 * @returns {Promise<Object>} Response with pendingRequests array
 */
export async function getPendingFriendRequests() {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/friends/pending?userId=${userId}`,
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
    console.error('Get pending friend requests error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Accept a friend request
 * @param {string} friendId - Friend's user ID
 * @returns {Promise<Object>} Response with success status
 */
export async function acceptFriendRequest(friendId) {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/friends/accept?userId=${userId}&friendId=${encodeURIComponent(friendId)}`,
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
    console.error('Accept friend request error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Decline a friend request
 * @param {string} friendId - Friend's user ID
 * @returns {Promise<Object>} Response with success status
 */
export async function declineFriendRequest(friendId) {
  try {
    const userId = await AsyncStorage.getItem('userId');

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const response = await fetch(
      `${API_BASE_URL}/friends/decline?userId=${userId}&friendId=${encodeURIComponent(friendId)}`,
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
    console.error('Decline friend request error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}

/**
 * Remove friend
 * @param {string} friendId - Friend's user ID
 * @returns {Promise<Object>} Response with success status
 */
export async function removeFriend(friendId) {
  try {
    console.log('🔴 [2/8] removeFriend service: friendId=', friendId);
    const userId = await AsyncStorage.getItem('userId');
    console.log('🔴 [2.5/8] removeFriend service: userId=', userId);

    if (!userId) {
      return {
        success: false,
        message: 'User not logged in',
      };
    }

    const url = `${API_BASE_URL}/friends/remove?userId=${userId}&friendId=${friendId}`;
    console.log('🔴 [3/8] Making remove friend request to:', url);

    const response = await fetch(
      url,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      }
    );

    console.log('🔴 [7.5/8] Remove friend response status:', response.status);
    const data = await response.json();
    console.log('🔴 [8/8] Remove friend response data:', data);
    return data;

  } catch (error) {
    console.error('Remove friend error:', error);
    return {
      success: false,
      message: 'Network error. Please check your connection.',
    };
  }
}
