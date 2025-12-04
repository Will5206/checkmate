import AsyncStorage from '@react-native-async-storage/async-storage';
import { API_BASE_URL } from '../config';



/**
 * Login user
 */
export async function loginUser(emailOrPhone, password) {
  console.log('🔵 [FRONTEND STEP 1/8] loginUser() called');
  console.log('🔵 [FRONTEND STEP 2/8] API_BASE_URL:', API_BASE_URL);
  console.log('🔵 [FRONTEND STEP 3/8] Preparing fetch request...');
  
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 30000); // 30 second timeout
  
  try {
    const url = `${API_BASE_URL}/auth/login`;
    const requestBody = {
      emailOrPhone,
      password,
    };
    
    console.log('🔵 [FRONTEND STEP 4/8] Making fetch request to:', url);
    console.log('🔵 [FRONTEND STEP 4/8] Request body:', JSON.stringify({ ...requestBody, password: '***' }));
    
    const fetchStartTime = Date.now();
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(requestBody),
      signal: controller.signal,
    });
    
    const fetchTime = Date.now() - fetchStartTime;
    clearTimeout(timeoutId);
    
    console.log('🔵 [FRONTEND STEP 5/8] Fetch completed in', fetchTime, 'ms');
    console.log('🔵 [FRONTEND STEP 5/8] Response status:', response.status, response.statusText);
    console.log('🔵 [FRONTEND STEP 5/8] Response ok:', response.ok);
    
    if (!response.ok) {
      console.log('🔵 [FRONTEND STEP 6/8] Response NOT ok, parsing error...');
      // Try to parse error response
      let errorData;
      try {
        errorData = await response.json();
        console.log('🔵 [FRONTEND STEP 6/8] Error data:', errorData);
      } catch (e) {
        console.error('🔵 [FRONTEND STEP 6/8] Failed to parse error JSON:', e);
        errorData = { success: false, message: `Server error: ${response.status} ${response.statusText}` };
      }
      return errorData;
    }
    
    console.log('🔵 [FRONTEND STEP 7/8] Parsing response JSON...');
    const data = await response.json();
    console.log('🔵 [FRONTEND STEP 7/8] Response data:', { ...data, token: data.token ? '***' : null });

    if (data.success) {
      console.log('🔵 [FRONTEND STEP 8/8] Login successful, storing session...');
      //store session
      await AsyncStorage.setItem('userId', data.userId);
      await AsyncStorage.setItem('userName', data.name);
      await AsyncStorage.setItem('userEmail', data.email);
      await AsyncStorage.setItem('authToken', data.token);
      await AsyncStorage.setItem('isLoggedIn', 'true');
      console.log('🔵 [FRONTEND STEP 8/8] Session stored successfully');
    } else {
      console.log('🔵 [FRONTEND STEP 8/8] Login failed:', data.message);
    }
    
    return data;
    
  } catch (error) {
    clearTimeout(timeoutId);
    console.error('🔴 [FRONTEND ERROR] Exception caught:', error.name, error.message);
    if (error.name === 'AbortError') {
      console.error('🔴 [FRONTEND ERROR] Request timeout after 30 seconds');
      return {
        success: false,
        message: 'Request timeout. Please check your connection.',
      };
    }
    console.error('🔴 [FRONTEND ERROR] Full error:', error);
    return {
      success: false,
      message: 'Network error. Please try again.',
    };
  }
}

/**
 * Sign up new user
 */

export async function signupUser(name, email, phone, password) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 30000); // 30 second timeout
  
  try {
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
      signal: controller.signal,
    });
    
    clearTimeout(timeoutId);
    
    if (!response.ok) {
      // Try to parse error response
      let errorData;
      try {
        errorData = await response.json();
      } catch (e) {
        errorData = { success: false, message: `Server error: ${response.status} ${response.statusText}` };
      }
      return errorData;
    }
    
    const data = await response.json();
    
    // If signup successful, automatically log the user in
    if (data.success && data.userId) {
      // Automatically login with the same credentials
      const loginResult = await loginUser(email, password);
      
      if (loginResult.success) {
        // Login successful - session is now stored
        return {
          success: true,
          message: 'Account created and logged in successfully',
          userId: loginResult.userId,
          name: loginResult.name,
          email: loginResult.email,
        };
      } else {
        // Signup succeeded but login failed - still return success but user needs to login manually
        return {
          success: true,
          message: 'Account created successfully. Please log in.',
          userId: data.userId,
        };
      }
    }
    
    return data;
    
  } catch (error) {
    clearTimeout(timeoutId);
    if (error.name === 'AbortError') {
      console.error('Signup error: Request timeout');
      return {
        success: false,
        message: 'Request timeout. Please check your connection.',
      };
    }
    console.error('Signup error:', error);
    return {
      success: false,
      message: 'Network error. Please try again.',
    };
  }
}

/**
 * log out user
 */
export async function logoutUser() {
  await AsyncStorage.clear();
}




/**
 * cjeck if user is logged in
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