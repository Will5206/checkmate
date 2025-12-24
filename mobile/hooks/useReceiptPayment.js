/**
 * Custom hook for managing receipt payment state and operations
 * Handles payment logic, status tracking, and item assignments loading
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Alert } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { getItemAssignments, payReceipt, getReceiptDetails } from '../services/receiptsService';

export function useReceiptPayment(receiptId, isFromActivity, uploadedBy, billData, itemAssignments, setItemAssignments) {
  const route = useRoute();
  const navigation = useNavigation();
  const [itemPaymentInfo, setItemPaymentInfo] = useState({}); // itemId -> {paidBy, payerName, paidAt}
  const [owedAmount, setOwedAmount] = useState(0);
  const [owedAmountExcludingPaid, setOwedAmountExcludingPaid] = useState(0);
  const [userHasPaid, setUserHasPaid] = useState(false);
  const [isReceiptComplete, setIsReceiptComplete] = useState(false);
  const [hasCheckedCompleteStatus, setHasCheckedCompleteStatus] = useState(false);
  const [isLoadingAssignments, setIsLoadingAssignments] = useState(false);
  const [isUploader, setIsUploader] = useState(false);

  // Check if user is uploader
  useEffect(() => {
    const checkIfUploader = async () => {
      if (uploadedBy) {
        const userId = await AsyncStorage.getItem('userId');
        setIsUploader(userId === uploadedBy);
      }
    };
    checkIfUploader();
  }, [uploadedBy]);

  // Initialize userHasPaid and isReceiptComplete from route params
  useEffect(() => {
    const routeUserHasPaid = route.params?.userHasPaid;
    if (routeUserHasPaid === true) {
      setUserHasPaid(true);
    } else {
      setUserHasPaid(false);
    }
    
    const routeIsReceiptComplete = route.params?.isReceiptComplete;
    if (routeIsReceiptComplete !== undefined) {
      setIsReceiptComplete(routeIsReceiptComplete);
      setHasCheckedCompleteStatus(true);
      console.log('BillReview: Set isReceiptComplete from route params:', routeIsReceiptComplete);
    }
  }, [route.params]);

  // Function to check if receipt is complete
  const checkReceiptCompleteStatus = useCallback(async () => {
    if (!receiptId) return;
    
    try {
      const response = await getReceiptDetails(receiptId);
      if (response.success && response.receipt) {
        const isComplete = response.receipt.complete === true || response.receipt.complete === 1 || response.receipt.complete === '1';
        setIsReceiptComplete(isComplete);
        setHasCheckedCompleteStatus(true);
        console.log('Receipt complete status:', isComplete, 'for receiptId:', receiptId);
      }
    } catch (error) {
      console.error('Error checking receipt complete status:', error);
    }
  }, [receiptId]);

  // Load item assignments if viewing from Activity
  const loadItemAssignments = useCallback(async () => {
    if (!receiptId) return;
    
    setIsLoadingAssignments(true);
    try {
      const response = await getItemAssignments(receiptId);
      if (response.success) {
        const backendAssignments = response.assignments || {};
        const expandedAssignments = {};
        
        // Map backend assignments (by original itemId) to expanded itemIds
        billData.items.forEach((item) => {
          const expandedItemId = item.itemId || item.id;
          const originalItemId = item.originalItemId || expandedItemId;
          
          // If this is an expanded item, check the original itemId's assignment
          if (item.originalItemId) {
            const claimedQty = backendAssignments[originalItemId] || 0;
            const instanceMatch = expandedItemId.match(/_(\d+)$/);
            const instanceIndex = instanceMatch ? parseInt(instanceMatch[1]) : 0;
            
            // Mark as claimed if this instance is within the claimed quantity
            if (instanceIndex < claimedQty) {
              expandedAssignments[expandedItemId] = 1;
            }
          } else {
            // Not an expanded item, use assignment directly
            if (backendAssignments[originalItemId]) {
              expandedAssignments[expandedItemId] = backendAssignments[originalItemId];
            }
          }
        });
        
        setItemAssignments(expandedAssignments);
        const newOwedAmount = response.owedAmount || 0;
        const newOwedAmountExcludingPaid = response.owedAmountExcludingPaid || 0;
        setOwedAmount(newOwedAmount);
        setOwedAmountExcludingPaid(newOwedAmountExcludingPaid);
        
        // Store payment info for all items
        const backendPaymentInfo = response.itemPaymentInfo || {};
        const expandedPaymentInfo = {};
        
        billData.items.forEach((item) => {
          const expandedItemId = item.itemId || item.id;
          const originalItemId = item.originalItemId || expandedItemId;
          const paymentInfo = backendPaymentInfo[String(originalItemId)] || backendPaymentInfo[originalItemId];
          
          if (paymentInfo) {
            expandedPaymentInfo[expandedItemId] = paymentInfo;
          }
        });
        
        setItemPaymentInfo(expandedPaymentInfo);
        
        // Check if user has actually paid
        const hasItemsClaimed = Object.keys(response.assignments || {}).length > 0;
        const hasPaymentInfo = Object.keys(expandedPaymentInfo).length > 0;
        const hasNoOwedAmount = newOwedAmount <= 0.01;
        
        const hasPaid = hasItemsClaimed && hasNoOwedAmount && hasPaymentInfo;
        setUserHasPaid(hasPaid);
      } else {
        console.error('Failed to load item assignments:', response.message);
      }
    } catch (error) {
      console.error('Error loading item assignments:', error);
    } finally {
      setIsLoadingAssignments(false);
    }
  }, [receiptId, billData.items, setItemAssignments]);

  // OPTIMIZED: Load item assignments when viewing from Activity (only once, with debouncing)
  const hasLoadedRef = useRef(false);
  const lastReceiptIdRef = useRef(null);
  
  useEffect(() => {
    if (isFromActivity && receiptId) {
      // Only load if receiptId changed or hasn't been loaded yet
      if (receiptId !== lastReceiptIdRef.current || !hasLoadedRef.current) {
        console.log('Loading item assignments and payment info for receiptId:', receiptId);
        lastReceiptIdRef.current = receiptId;
        hasLoadedRef.current = true;
        loadItemAssignments();
        if (!hasCheckedCompleteStatus) {
          checkReceiptCompleteStatus();
        }
      }
    } else {
      // Reset when not from activity
      hasLoadedRef.current = false;
      lastReceiptIdRef.current = null;
    }
  }, [isFromActivity, receiptId, hasCheckedCompleteStatus, loadItemAssignments, checkReceiptCompleteStatus]);

  // OPTIMIZED: Reload when screen comes into focus (with debouncing to prevent rapid reloads)
  const lastFocusTimeRef = useRef(0);
  useEffect(() => {
    const unsubscribe = navigation.addListener('focus', () => {
      if (isFromActivity && receiptId) {
        const now = Date.now();
        // Only reload if it's been more than 1 second since last reload
        if (now - lastFocusTimeRef.current > 1000) {
          console.log('BillReview screen focused - reloading payment info and item assignments');
          lastFocusTimeRef.current = now;
          loadItemAssignments();
          checkReceiptCompleteStatus();
        } else {
          console.log('BillReview screen focused - skipping reload (too soon after last reload)');
        }
      }
    });
    return unsubscribe;
  }, [navigation, isFromActivity, receiptId, loadItemAssignments, checkReceiptCompleteStatus]);

  // Handle payment
  const handlePay = useCallback(async () => {
    if (!receiptId) return;
    
    return new Promise((resolve) => {
      Alert.alert(
        'Confirm Payment',
        `Pay $${owedAmountExcludingPaid.toFixed(2)} for your portion of this receipt?`,
        [
          { text: 'Cancel', style: 'cancel', onPress: () => resolve(null) },
          {
            text: 'Pay',
            onPress: async () => {
              try {
                const response = await payReceipt(receiptId);
                
                if (response && response.success) {
                  // Update payment info first
                  if (response.itemPaymentInfo) {
                    setItemPaymentInfo(prev => ({
                      ...prev,
                      ...response.itemPaymentInfo
                    }));
                  }
                  
                  // Update owed amounts
                  setOwedAmount(response.owedAmount || 0);
                  setOwedAmountExcludingPaid(response.owedAmountExcludingPaid || 0);
                  
                  // Reload assignments to get fresh data
                  await loadItemAssignments();
                  
                  // Set user has paid after successful payment
                  setUserHasPaid(true);
                  
                  resolve(response);
                } else {
                  // Payment failed - show error message
                  setUserHasPaid(false);
                  const errorMessage = response?.message || 'Payment failed. Please try again.';
                  Alert.alert('Payment Failed', errorMessage);
                  resolve(response); // Return response so caller can handle it
                }
              } catch (error) {
                console.error('Error processing payment:', error);
                Alert.alert('Payment Error', 'An error occurred while processing your payment. Please try again.');
                resolve({ success: false, message: error.message || 'Network error' });
              }
            },
          },
        ]
      );
    });
  }, [receiptId, owedAmountExcludingPaid, loadItemAssignments]);

  // Expose function to update owed amounts (called when item is claimed)
  const updateOwedAmounts = useCallback((owedAmountValue, owedAmountExcludingPaidValue) => {
    setOwedAmount(owedAmountValue);
    setOwedAmountExcludingPaid(owedAmountExcludingPaidValue);
  }, []);

  return {
    itemPaymentInfo,
    owedAmount,
    owedAmountExcludingPaid,
    userHasPaid,
    setUserHasPaid,
    isReceiptComplete,
    isLoadingAssignments,
    isUploader,
    loadItemAssignments,
    handlePay,
    updateOwedAmounts,
  };
}

