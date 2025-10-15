import AsyncStorage from '@react-native-async-storage/async-storage';

// --->>>>update this to your computer's IP address when testing on phone <<-- ---
// find your IP: Mac/Linux: `ifconfig` | Windows: `ipconfig`
const API_BASE_URL = 'http://localhost:8080/api';



/**
 * Login user
 */
export async function loginUser(emailOrPhone, password) {
  try {
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
      //store session
      await AsyncStorage.setItem('userId', data.userId);
      await AsyncStorage.setItem('userName', data.name);
      await AsyncStorage.setItem('userEmail', data.email);
      await AsyncStorage.setItem('authToken', data.token);

      await AsyncStorage.setItem('isLoggedIn', 'true');
    }
    
    return data;
    
  } catch (error) {
    console.error('Login error:', error);
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
    });
    
    const data = await response.json();
    return data;
    
  } catch (error) {
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