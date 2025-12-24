/**
 * Custom hook for managing bill review data
 * Handles loading receipt data from route params or AsyncStorage
 */

import { useState, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { transformReceiptData } from '../utils/receiptDataTransform';

const defaultBillData = {
  restaurant_name: "Mario's Italian Kitchen",
  date: "Today, 7:30 PM",
  items: [
    { id: 1, name: "Caesar Salad", price: 12.99 },
    { id: 2, name: "Margherita Pizza", price: 18.50 },
    { id: 3, name: "Spaghetti Carbonara", price: 16.75 },
    { id: 4, name: "Chicken Parmesan", price: 22.95 },
    { id: 5, name: "Tiramisu", price: 8.50 },
    { id: 6, name: "House Wine (2 glasses)", price: 24.00 }
  ],
  tax: 10.37,
  tip: 20.00,
  total: 133.06
};

export function useBillReviewData(route) {
  const [billData, setBillData] = useState(defaultBillData);
  const [isFromCamera, setIsFromCamera] = useState(false);
  const [isFromActivity, setIsFromActivity] = useState(false);
  const [receiptId, setReceiptId] = useState(null);
  const [uploadedBy, setUploadedBy] = useState(null);

  useEffect(() => {
    // First check route params (from HomeScreen navigation or Activity)
    const routeData = route.params?.data;
    const routeReceiptId = route.params?.receiptId;
    const routeIsFromActivity = route.params?.isFromActivity;
    const routeUploadedBy = route.params?.uploadedBy;
    
    if (routeReceiptId) {
      setReceiptId(routeReceiptId);
    }
    
    if (routeUploadedBy) {
      setUploadedBy(routeUploadedBy);
    }
    
    if (routeIsFromActivity) {
      setIsFromActivity(true);
      setIsFromCamera(false);
    }
    
    if (routeData) {
      try {
        console.log('BillReview: Received routeData:', {
          isFromActivity: routeIsFromActivity,
          itemsCount: routeData.items ? routeData.items.length : 0,
          items: routeData.items,
          receiptId: routeReceiptId,
        });
        
        // Transform backend data to match our format
        const transformedData = transformReceiptData(routeData);
        
        console.log('BillReview: Transformed data:', {
          itemsCount: transformedData.items.length,
          items: transformedData.items,
        });
        
        setBillData(transformedData);
        if (!routeIsFromActivity) {
          setIsFromCamera(true);
        }
      } catch (error) {
        console.error('Error processing route data:', error);
      }
    } else {
      // Check AsyncStorage as fallback
      AsyncStorage.getItem('parsedReceiptData').then((parsedData) => {
        if (parsedData) {
          try {
            const receiptData = JSON.parse(parsedData);
            const transformedData = transformReceiptData(receiptData);
            setBillData(transformedData);
            setIsFromCamera(true);
            // Clear the storage after using it
            AsyncStorage.removeItem('parsedReceiptData');
          } catch (error) {
            console.error('Error parsing receipt data:', error);
          }
        }
      });
    }
  }, [route.params]);

  return {
    billData,
    setBillData,
    isFromCamera,
    isFromActivity,
    receiptId,
    uploadedBy,
  };
}






