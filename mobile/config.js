// API Configuration
// This file can be customized per developer without affecting git
// 
// IMPORTANT: 192.168.0.193 is a LOCAL NETWORK IP (private IP)
// - It's only accessible on your local WiFi network
// - It's NOT your public IP address
// - Teammates cannot access it (they need their own IP)
// - Safe to commit to GitHub (it's a private network address)
//
// For local development, use your computer's IP address
// Find your IP: Mac/Linux: `ifconfig` | Windows: `ipconfig`
// Look for inet/inet addr under en0/wlan0/eth0
//
// Each developer should update this to their own computer's IP
// when testing on physical devices or simulators

const getLocalIP = () => {
  // You can set this via environment variable
  if (typeof process !== 'undefined' && process.env.EXPO_PUBLIC_API_URL) {
    return process.env.EXPO_PUBLIC_API_URL;
  }
  
  // For device/simulator testing: use your computer's local IP
  // For web testing: use localhost
  // 
  // TODO: Update this to YOUR computer's IP address
  // Example: 'http://192.168.0.193:8080/api' (current developer's IP)
  // Example: 'http://192.168.1.100:8080/api' (teammate's IP would be different)
  
  if (__DEV__) {
    // Using IP for device/simulator access (update to your IP)
    // For web, you can use 'http://localhost:8080/api'
    return 'http://192.168.0.193:8080/api';
  }
  
  return 'http://localhost:8080/api';
};

export const API_BASE_URL = getLocalIP();
