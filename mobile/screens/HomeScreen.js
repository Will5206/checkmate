// app/screens/ScanReceipt.js
import React, { useState, useEffect } from 'react';
import { View, Text, Image, TouchableOpacity, ActivityIndicator, StyleSheet, Alert } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { useNavigation, useFocusEffect } from '@react-navigation/native';
import AsyncStorage from '@react-native-async-storage/async-storage';
// bringing in nav bar
import BottomNavBar from '../components/BottomNavBar';

// Use same IP as authService - update this to your computer's IP address
// Import from config.js for consistency
import { API_BASE_URL } from '../config';

// create string that is mainColor for main color text or buttons - good for testing
const mainColor = 'blue';
export default function HomeScreen() {
  const [imageUri, setImageUri] = useState(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const navigation = useNavigation();

  // Load saved image URI when screen comes into focus
  useFocusEffect(
    React.useCallback(() => {
      const loadSavedImage = async () => {
        try {
          const savedUri = await AsyncStorage.getItem('scanReceiptImageUri');
          if (savedUri) {
            setImageUri(savedUri);
          }
        } catch (error) {
          console.error('Error loading saved image:', error);
        }
      };
      loadSavedImage();
    }, [])
  );

  // Save image URI when it changes
  useEffect(() => {
    if (imageUri) {
      AsyncStorage.setItem('scanReceiptImageUri', imageUri);
    } else {
      AsyncStorage.removeItem('scanReceiptImageUri');
    }
  }, [imageUri]);

  const handlePickImage = async () => {
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: 'images',
        quality: 0.8,
      });
      if (!result.canceled && result.assets && result.assets.length > 0) {
        setImageUri(result.assets[0].uri);
      }
    } catch (error) {
      console.error('Error picking image:', error);
      Alert.alert('Error', 'Failed to open image picker');
    }
  };

  const handleProcess = async () => {
    if (!imageUri) {
      Alert.alert('No image selected', 'Please choose a receipt photo.');
      return;
    }
    setIsProcessing(true);
    
    try {
      console.log('Starting receipt processing...');
      console.log('API URL:', `${API_BASE_URL}/receipt/parse`);
      
      // Convert image URI to blob for upload
      console.log('Converting image to blob...');
      const response = await fetch(imageUri);
      const blob = await response.blob();
      console.log('Blob size:', blob.size, 'bytes');
      
      // Send image to backend parser (120 second timeout - OpenAI API can take 15-30 seconds)
      console.log('Sending request to backend...');
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 120000); // 120 seconds
      
      let parseResponse;
      try {
        parseResponse = await fetch(`${API_BASE_URL}/receipt/parse`, {
          method: 'POST',
          body: blob,
          headers: {
            'Content-Type': 'image/jpeg',
          },
          signal: controller.signal,
        });
        clearTimeout(timeoutId);
      } catch (error) {
        clearTimeout(timeoutId);
        if (error.name === 'AbortError') {
          throw new Error('Request timed out. Receipt parsing usually takes 15-30 seconds. Please try again.');
        }
        throw error;
      }
      
      console.log('Response status:', parseResponse.status);

      const receiptData = await parseResponse.json();
      console.log('Receipt data response:', JSON.stringify(receiptData, null, 2));

      if (!parseResponse.ok) {
        const errorMsg = receiptData.message || `Server error: ${parseResponse.status}`;
        console.error('Receipt parsing failed:', errorMsg);
        throw new Error(errorMsg);
      }
      
      if (!receiptData.success) {
        const errorMsg = receiptData.message || 'Failed to parse receipt';
        console.error('Receipt parsing unsuccessful:', errorMsg);
        throw new Error(errorMsg);
      }

      setIsProcessing(false);
      
      // Navigate to BillReview with parsed data
      navigation.navigate('BillReview', {
        data: {
          merchant: receiptData.merchant,
          total: receiptData.total,
          subtotal: receiptData.subtotal,
          tax: receiptData.tax,
          tip: 0, // Backend doesn't extract tip, will be calculated
          items: receiptData.items || [],
        },
      });
    } catch (error) {
      setIsProcessing(false);
      console.error('Receipt processing error:', error);
      console.error('Error name:', error.name);
      console.error('Error message:', error.message);
      console.error('Error stack:', error.stack);
      
      // Determine error message - use the actual error message from backend if available
      let errorMessage = error.message || 'We couldn\'t read your receipt. Please try taking the photo again.';
      
      // Enhance error messages for common cases
      if (error.name === 'AbortError' || error.message.includes('timeout')) {
        errorMessage = 'The request took too long. The receipt might be complex or the server is busy. Please try again.';
      } else if (error.message.includes('Network') || error.message.includes('Failed to fetch') || error.message.includes('Network request failed')) {
        errorMessage = `Network error. Cannot reach server at ${API_BASE_URL}. Please check:\n1. Server is running\n2. IP address is correct\n3. Phone and computer are on same network`;
      } else if (error.message.includes('Python script failed')) {
        // The backend should have included detailed error, but if not, show generic message
        errorMessage = error.message + '\n\nCheck backend terminal logs for detailed Python error output.';
      } else if (error.message.includes('billing') || error.message.includes('Billing')) {
        // OpenAI billing error - make it more user-friendly
        errorMessage = error.message + '\n\nTo fix this:\n1. Go to https://platform.openai.com/account/billing\n2. Add a payment method\n3. Ensure billing is active\n4. Try again';
      }
      
      // Show error alert with retry option
      Alert.alert(
        'Error Reading Receipt',
        errorMessage,
        [
          {
            text: 'Retake Photo',
            onPress: () => {
              setImageUri(null);
              AsyncStorage.removeItem('scanReceiptImageUri');
              handlePickImage();
            },
          },
          {
            text: 'Cancel',
            style: 'cancel',
          },
        ]
      );
    }
  };

  return (
    <View style={styles.wrapper}>
      <View style={styles.container}>
        {!imageUri ? (
          <>
            <Text style={styles.title}>Scan Receipt</Text>
            <TouchableOpacity style={styles.button} onPress={handlePickImage}>
              <Text style={styles.buttonText}>Choose Receipt Photo</Text>
            </TouchableOpacity>
          </>
        ) : (
          <>
            <Image source={{ uri: imageUri }} style={styles.preview} />
            <TouchableOpacity style={styles.button} onPress={handleProcess}>
              {isProcessing ? (
                <ActivityIndicator color="white" />
              ) : (
                <Text style={styles.buttonText}>Process & Split Bill</Text>
              )}
            </TouchableOpacity>
            <TouchableOpacity onPress={() => {
              setImageUri(null);
              AsyncStorage.removeItem('scanReceiptImageUri');
            }}>
              <Text style={styles.reset}>Reset</Text>
            </TouchableOpacity>
          </>
        )}
      </View>
      <BottomNavBar />
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    flex: 1,
    backgroundColor: '#f9fafb',
  },
  container: { 
    flex: 1, 
    justifyContent: 'center', 
    alignItems: 'center', 
    padding: 20, 
    paddingBottom: 100, // Space for bottom nav bar
    backgroundColor: '#f9fafb' 
  },
  title: { fontSize: 24, fontWeight: '700', marginBottom: 20 },
  button: { backgroundColor: '#059669', paddingVertical: 12, paddingHorizontal: 20, borderRadius: 8 },
  buttonText: { color: 'white', fontWeight: '600' },
  preview: { width: 250, height: 300, borderRadius: 12, marginBottom: 20 },
  reset: { color: '#2563eb', marginTop: 10 },
});
