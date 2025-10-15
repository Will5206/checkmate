import AsyncStorage from '@react-native-async-storage/async-storage';

// for this sprint (oct 14,2025) - Mock API
// next sprint will connect to our Java backend

const API_BASE_URL = 'http://localhost:8080/api'; //update for our backend


/**
 * login user
 */
export async function loginUser(emailOrPhone, password) {
  try {



    await new Promise(resolve => setTimeout(resolve, 1000));
    
    // mock validation for this sprint
    if (emailOrPhone === 'test@checkmate.com' && password === 'password123') {
      //store session
      await AsyncStorage.setItem('userId', 'user_123');
      await AsyncStorage.setItem('userName', 'Test User');
      await AsyncStorage.setItem('userEmail', emailOrPhone);
      await AsyncStorage.setItem('isLoggedIn', 'true');
      
      return {
        success: true,
        userId: 'user_123',
        name: 'Test User',
        email: emailOrPhone,
      };
    }
    
    return {
      success: false,
      message: 'Invalid email or password',
    };
    
    /* next sprint - real implementation:
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        emailOrPhone,
        password,
      }),
    });
    
    const data = await response.json();
    
    if (data.success) {
      await AsyncStorage.setItem('userId', data.userId);
      await AsyncStorage.setItem('userName', data.name);
      await AsyncStorage.setItem('authToken', data.token);
      await AsyncStorage.setItem('isLoggedIn', 'true');
    }
    
    return data;
    */
    
  } catch (error) {
    console.error('Login error:', error);
    return {
      success: false,
      message: 'Network error. Please try again.',
    };
  }
}





/**
 * sign up new user
 */

export async function signupUser(name, email, phone, password) {
  try {
    await new Promise(resolve => setTimeout(resolve, 1500));
    
    // mock validation
    if (!name || !email || !phone || !password) {
      return {
        success: false,
        message: 'All fields are required',
      };
    }
    
    if (password.length < 8) {
      return {
        success: false,
        message: 'Password must be at least 8 characters',
      };
    }
    
    //mock success
    return {
      success: true,
      message: 'Account created successfully!',
      userId: 'user_' + Date.now(),
    };
    
    /* next sprint -real implementation:
    const response = await fetch(`${API_BASE_URL}/auth/signup`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        name,
        email,
        phoneNumber: phone,
        password,
      }),
    });
    
    const data = await response.json();
    return data;
    */
    
  } catch (error) {
    console.error('Signup error:', error);
    return {
      success: false,
      message: 'Network error. Please try again.',
    };
  }
}

/**
 * logout user
 */

export async function logoutUser() {
  await AsyncStorage.clear();
}

/**
 * check if user is logged in
 */
export async function isUserLoggedIn() {
  const isLoggedIn = await AsyncStorage.getItem('isLoggedIn');

  return isLoggedIn === 'true';
}





/**
 * get current user info
 */
export async function getCurrentUser() {
  const isLoggedIn = await isUserLoggedIn();
  if (!isLoggedIn) return null;
  

  return {
    userId: await AsyncStorage.getItem('userId'),
    name: await AsyncStorage.getItem('userName'),
    email: await AsyncStorage.getItem('userEmail'),
  };
}