// app/screens/ScanReceipt.js
import React, { useState } from 'react';
import { View, Text, Image, TouchableOpacity, ActivityIndicator, StyleSheet, Alert } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { useNavigation } from '@react-navigation/native';
// bringing in nav bar
import BottomNavBar from '../components/BottomNavBar';

export default function HomeScreen() {
  const [imageUri, setImageUri] = useState(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const navigation = useNavigation();

  const handlePickImage = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.8,
    });
    if (!result.canceled) {
      setImageUri(result.assets[0].uri);
    }
  };

  const handleProcess = async () => {
    if (!imageUri) {
      Alert.alert('No image selected', 'Please choose a receipt photo.');
      return;
    }
    setIsProcessing(true);
    // fake parse (simulate backend)
    setTimeout(() => {
      setIsProcessing(false);
      navigation.navigate('BillReview', {
        data: {
          merchant: 'Demo Store',
          total: 11.0,
          items: [
            { name: 'Taco', qty: 2, price: 3.5 },
            { name: 'Soda', qty: 1, price: 1.75 },
          ],
        },
      });
    }, 1000);
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
  button: { backgroundColor: '#059669', paddingVertical: 12, paddingHorizontal: 20, borderRadius: 8 },
  buttonText: { color: 'white', fontWeight: '600' },
  preview: { width: 250, height: 300, borderRadius: 12, marginBottom: 20 },
  reset: { color: '#2563eb', marginTop: 10 },
});
