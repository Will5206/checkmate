// app/screens/ScanReceipt.js
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { View, Text, Image, TouchableOpacity, ActivityIndicator, StyleSheet, Alert } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { useNavigation, useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
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
  
  // Background parsing state
  const [parsingStatus, setParsingStatus] = useState('idle'); // 'idle' | 'parsing' | 'completed' | 'error'
  const [parsedResult, setParsedResult] = useState(null);
  const [parsingError, setParsingError] = useState(null);
  const abortControllerRef = useRef(null);

  // Request camera permissions
  const requestCameraPermission = async () => {
    const { status } = await ImagePicker.requestCameraPermissionsAsync();
    if (status !== 'granted') {
      Alert.alert(
        'Camera Permission Required',
        'Please allow camera access to take photos of receipts.',
        [{ text: 'OK' }]
      );
      return false;
    }
    return true;
  };

  const handleTakePhoto = async () => {
    try {
      const hasPermission = await requestCameraPermission();
      if (!hasPermission) return;

      const result = await ImagePicker.launchCameraAsync({
        mediaTypes: 'images',
        quality: 0.8,
        allowsEditing: false,
      });
      
      if (!result.canceled && result.assets && result.assets.length > 0) {
        // Reset parsing state when new image is selected
        setParsingStatus('idle');
        setParsedResult(null);
        setParsingError(null);
        setImageUri(result.assets[0].uri);
      }
    } catch (error) {
      console.error('Error taking photo:', error);
      Alert.alert('Error', 'Failed to open camera');
    }
  };

  const handlePickImage = async () => {
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: 'images',
        quality: 0.8,
      });
      if (!result.canceled && result.assets && result.assets.length > 0) {
        // Reset parsing state when new image is selected
        setParsingStatus('idle');
        setParsedResult(null);
        setParsingError(null);
        setImageUri(result.assets[0].uri);
      }
    } catch (error) {
      console.error('Error picking image:', error);
      Alert.alert('Error', 'Failed to open image picker');
    }
  };

  // Background parsing: start parsing immediately when image is uploaded
  useEffect(() => {
    if (!imageUri) {
      // Reset state when image is cleared
      setParsingStatus('idle');
      setParsedResult(null);
      setParsingError(null);
      return;
    }

    // Cancel any previous parsing request
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    // Start background parsing
    const parseReceipt = async () => {
      // Create new AbortController for this request
      const abortController = new AbortController();
      abortControllerRef.current = abortController;

      setParsingStatus('parsing');
      setParsingError(null);

      try {
        console.log('[Background] Starting receipt parsing...');
        
        // Convert image URI to blob for upload
        const response = await fetch(imageUri);
        const blob = await response.blob();
        
        // Send image to backend parser
        const parseResponse = await fetch(`${API_BASE_URL}/receipt/parse`, {
          method: 'POST',
          body: blob,
          headers: {
            'Content-Type': 'image/jpeg',
          },
          signal: abortController.signal, // Allow cancellation
        });

        // Check if request was aborted
        if (abortController.signal.aborted) {
          console.log('[Background] Parsing was aborted');
          return;
        }

        const receiptData = await parseResponse.json();

        if (!parseResponse.ok) {
          throw new Error(receiptData.message || `Server error: ${parseResponse.status}`);
        }

        if (!receiptData.success) {
          throw new Error(receiptData.message || 'Failed to parse receipt');
        }

        // Success: store result
        console.log('[Background] Receipt parsing completed successfully');
        setParsedResult({
          merchant: receiptData.merchant,
          total: receiptData.total,
          subtotal: receiptData.subtotal,
          tax: receiptData.tax,
          tip: 0, // Backend doesn't extract tip, will be calculated
          items: receiptData.items || [],
        });
        setParsingStatus('completed');
      } catch (error) {
        // Check if error was due to abort
        if (error.name === 'AbortError' || abortController.signal.aborted) {
          console.log('[Background] Parsing was aborted');
          return;
        }

        // Store error for later display
        console.error('[Background] Receipt parsing error:', error);
        setParsingError(error);
        setParsingStatus('error');
      }
    };

    parseReceipt();

    // Cleanup: abort request if component unmounts or image changes
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
  }, [imageUri]);

  const showErrorAlert = useCallback((error, allowRetry = false) => {
    let errorMessage = 'We couldn\'t read your receipt. Please try taking the photo again.';
    if (error?.name === 'AbortError' || error?.message?.includes('timeout')) {
      errorMessage = 'The request took too long. The receipt might be complex or the server is busy. Please try again.';
    } else if (error?.message?.includes('Network') || error?.message?.includes('Failed to fetch') || error?.message?.includes('Network request failed')) {
      errorMessage = `Network error. Cannot reach server at ${API_BASE_URL}. Please check:\n1. Server is running\n2. IP address is correct\n3. Phone and computer are on same network`;
    } else if (error?.message) {
      errorMessage = error.message;
    }

    const buttons = [
      {
        text: 'Retry',
        onPress: () => {
          // Reset parsing state to trigger new background parse
          setParsingStatus('idle');
          setParsedResult(null);
          setParsingError(null);
        },
      },
      {
        text: 'Cancel',
        style: 'cancel',
      },
    ];

    if (!allowRetry) {
      buttons.unshift({
        text: 'Retake Photo',
        onPress: () => {
          setImageUri(null);
          handleTakePhoto();
        },
      });
    }

    Alert.alert('Error Reading Receipt', errorMessage, buttons);
  }, []);

  // Watch for parsing completion when user is waiting (isProcessing is true)
  useEffect(() => {
    if (isProcessing && parsingStatus === 'completed' && parsedResult) {
      // Parsing completed while user was waiting - navigate immediately
      setIsProcessing(false);
      navigation.navigate('BillReview', {
        data: parsedResult,
      });
    } else if (isProcessing && parsingStatus === 'error') {
      // Parsing failed while user was waiting - show error
      setIsProcessing(false);
      showErrorAlert(parsingError);
    }
  }, [isProcessing, parsingStatus, parsedResult, parsingError, navigation, showErrorAlert]);

  const handleProcess = async () => {
    if (!imageUri) {
      Alert.alert('No image selected', 'Please choose a receipt photo.');
      return;
    }

    // Check parsing status and handle accordingly
    if (parsingStatus === 'completed' && parsedResult) {
      // Parsing already completed - navigate immediately with cached result
      console.log('[Process] Using cached parsing result');
      navigation.navigate('BillReview', {
        data: parsedResult,
      });
      return;
    }

    if (parsingStatus === 'parsing') {
      // Parsing in progress - show loading and wait for completion
      // The useEffect above will handle navigation when parsing completes
      console.log('[Process] Waiting for background parsing to complete...');
      setIsProcessing(true);
      return;
    }

    if (parsingStatus === 'error') {
      // Parsing failed - show error and allow retry
      showErrorAlert(parsingError, true);
      return;
    }

    // Fallback: parsing status is 'idle' (shouldn't happen, but handle it)
    console.log('[Process] Starting parsing (fallback case)');
    setIsProcessing(true);

    try {
      const response = await fetch(imageUri);
      const blob = await response.blob();

      const parseResponse = await fetch(`${API_BASE_URL}/receipt/parse`, {
        method: 'POST',
        body: blob,
        headers: {
          'Content-Type': 'image/jpeg',
        },
      });

      const receiptData = await parseResponse.json();

      if (!parseResponse.ok) {
        throw new Error(receiptData.message || `Server error: ${parseResponse.status}`);
      }

      if (!receiptData.success) {
        throw new Error(receiptData.message || 'Failed to parse receipt');
      }

      setIsProcessing(false);

      navigation.navigate('BillReview', {
        data: {
          merchant: receiptData.merchant,
          total: receiptData.total,
          subtotal: receiptData.subtotal,
          tax: receiptData.tax,
          tip: 0,
          items: receiptData.items || [],
        },
      });
    } catch (error) {
      setIsProcessing(false);
      showErrorAlert(error);
    }
  };


  return (
    <View style={styles.wrapper}>
      <View style={styles.container}>
        {!imageUri ? (
          <>
            <Text style={styles.title}>Scan Receipt</Text>
            <View style={styles.buttonContainer}>
              <TouchableOpacity style={[styles.button, styles.cameraButton]} onPress={handleTakePhoto}>
                <Ionicons name="camera" size={24} color="white" style={styles.buttonIcon} />
                <Text style={styles.buttonText}>Take Photo</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.button, styles.libraryButton]} onPress={handlePickImage}>
                <Ionicons name="images" size={24} color="white" style={styles.buttonIcon} />
                <Text style={styles.buttonText}>Choose from Library</Text>
              </TouchableOpacity>
            </View>
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
              <Text style={styles.reset}>Retake</Text>
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
  title: { fontSize: 24, fontWeight: '700', marginBottom: 30 },
  buttonContainer: {
    width: '100%',
    maxWidth: 300,
    gap: 12,
  },
  button: { 
    backgroundColor: '#0d9488', 
    paddingVertical: 14, 
    paddingHorizontal: 20, 
    borderRadius: 8,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  cameraButton: {
    backgroundColor: '#0d9488',
  },
  libraryButton: {
    backgroundColor: '#059669',
  },
  buttonIcon: {
    marginRight: 4,
  },
  buttonText: { color: mainColor, fontWeight: '600', fontSize: 16 },
  preview: { width: 250, height: 300, borderRadius: 12, marginBottom: 20 },
  reset: { color: '#2563eb', marginTop: 10 },
});

