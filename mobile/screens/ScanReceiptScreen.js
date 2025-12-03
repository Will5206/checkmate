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
const mainColor = 'white';
export default function ScanReceiptScreen() {
  const [imageUri, setImageUri] = useState(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const navigation = useNavigation();

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
      
      // Send image to backend parser (no timeout - let it complete)
      console.log('Sending request to backend...');
      const parseResponse = await fetch(`${API_BASE_URL}/receipt/parse`, {
        method: 'POST',
        body: blob,
        headers: {
          'Content-Type': 'image/jpeg',
        },
      });
      
      console.log('Response status:', parseResponse.status);

      const receiptData = await parseResponse.json();

      if (!parseResponse.ok) {
        throw new Error(receiptData.message || `Server error: ${parseResponse.status}`);
      }
      
      if (!receiptData.success) {
        throw new Error(receiptData.message || 'Failed to parse receipt');
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
      
      // Determine error message
      let errorMessage = 'We couldn\'t read your receipt. Please try taking the photo again.';
      if (error.name === 'AbortError' || error.message.includes('timeout')) {
        errorMessage = 'The request took too long. The receipt might be complex or the server is busy. Please try again.';
      } else if (error.message.includes('Network') || error.message.includes('Failed to fetch') || error.message.includes('Network request failed')) {
        errorMessage = `Network error. Cannot reach server at ${API_BASE_URL}. Please check:\n1. Server is running\n2. IP address is correct\n3. Phone and computer are on same network`;
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
            <TouchableOpacity onPress={() => setImageUri(null)}>
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
  button: { backgroundColor: '#0d9488', paddingVertical: 12, paddingHorizontal: 20, borderRadius: 8 },
  buttonText: { color: mainColor, fontWeight: '600' },
  preview: { width: 250, height: 300, borderRadius: 12, marginBottom: 20 },
  reset: { color: '#2563eb', marginTop: 10 },
});

