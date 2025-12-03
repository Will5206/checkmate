import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import AsyncStorage from '@react-native-async-storage/async-storage';
import BottomNavBar from '../components/BottomNavBar';
import { colors, spacing, typography } from '../styles/theme';
import { createReceipt, claimItem, unclaimItem, getItemAssignments, payReceipt, addParticipantsToReceipt } from '../services/receiptsService';

export default function BillReview() {
  const navigation = useNavigation();
  const route = useRoute();
  
  // Default mock data - will be replaced by parsed receipt data
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

  const [billData, setBillData] = useState(defaultBillData);
  const [friendsEmails, setFriendsEmails] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [isFromCamera, setIsFromCamera] = useState(false);
  const [isFromActivity, setIsFromActivity] = useState(false);
  const [receiptId, setReceiptId] = useState(null);
  const [uploadedBy, setUploadedBy] = useState(null);
  const [isUploader, setIsUploader] = useState(false);
  const [itemAssignments, setItemAssignments] = useState({}); // itemId -> quantity
  const [itemPaymentInfo, setItemPaymentInfo] = useState({}); // itemId -> {paidBy, payerName, paidAt}
  const [owedAmount, setOwedAmount] = useState(0);
  const [userHasPaid, setUserHasPaid] = useState(false); // Boolean: true only after payment is fully processed
  const [isLoadingAssignments, setIsLoadingAssignments] = useState(false);
  const [claimingItems, setClaimingItems] = useState(new Set()); // Track items being claimed to prevent double-clicks
  const scrollViewRef = useRef(null);
  const emailInputRef = useRef(null);

  // Check for parsed receipt data from route params or AsyncStorage
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
    
    // Initialize userHasPaid from route params if provided
    // Only set to true if explicitly passed and true (after actual payment)
    const routeUserHasPaid = route.params?.userHasPaid;
    if (routeUserHasPaid === true) {
      setUserHasPaid(true);
    } else {
      // Default to false - only set to true after successful payment
      setUserHasPaid(false);
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

  // Check if user is uploader and load item assignments if viewing from Activity
  useEffect(() => {
    const checkIfUploader = async () => {
      if (uploadedBy) {
        const userId = await AsyncStorage.getItem('userId');
        setIsUploader(userId === uploadedBy);
      }
    };
    checkIfUploader();
  }, [uploadedBy]);

  // Load item assignments if viewing from Activity
  // This loads payment info from the database so we can show "Paid by [name]" for paid items
  useEffect(() => {
    console.log('BillReview useEffect - isFromActivity:', isFromActivity, 'receiptId:', receiptId);
    if (isFromActivity && receiptId) {
      console.log('Loading item assignments and payment info for receiptId:', receiptId);
      loadItemAssignments();
    }
  }, [isFromActivity, receiptId]);
  
  // Also reload when screen comes into focus to get latest payment status
  useEffect(() => {
    const unsubscribe = navigation.addListener('focus', () => {
      if (isFromActivity && receiptId) {
        console.log('BillReview screen focused - reloading payment info');
        loadItemAssignments();
      }
    });
    return unsubscribe;
  }, [navigation, isFromActivity, receiptId]);

  const loadItemAssignments = async () => {
    if (!receiptId) return;
    
    setIsLoadingAssignments(true);
    try {
      const response = await getItemAssignments(receiptId);
      if (response.success) {
        const backendAssignments = response.assignments || {};
        const expandedAssignments = {};
        
        // Map backend assignments (by original itemId) to expanded itemIds
        // Backend returns: { "1": 2 } means itemId 1 has qty 2 claimed
        // Expanded items: "1_0", "1_1" (if original qty was 2)
        // So we need to mark "1_0" and "1_1" as claimed if backend says "1" has qty 2 claimed
        billData.items.forEach((item) => {
          const expandedItemId = item.itemId || item.id;
          const originalItemId = item.originalItemId || expandedItemId;
          
          // If this is an expanded item, check the original itemId's assignment
          if (item.originalItemId) {
            const claimedQty = backendAssignments[originalItemId] || 0;
            // Find which instance this is (e.g., "1_0" is instance 0, "1_1" is instance 1)
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
        setOwedAmount(newOwedAmount);
        
        // Store payment info for all items - this shows which items are paid and by whom
        // Map payment info from original itemIds to expanded itemIds
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
        console.log('Loaded payment info for', Object.keys(expandedPaymentInfo).length, 'items:', expandedPaymentInfo);
        
        // Check if user has actually paid
        // Only set userHasPaid to true if:
        // 1. User has items claimed (assignments exist) AND
        // 2. Owed amount is 0 or very close to 0 AND
        // 3. We have payment info for items (meaning payment was actually made)
        // This prevents false positives when user hasn't claimed anything yet
        const hasItemsClaimed = Object.keys(response.assignments || {}).length > 0;
        const hasPaymentInfo = Object.keys(paymentInfo).length > 0;
        const hasNoOwedAmount = newOwedAmount <= 0.01;
        
        // Only mark as paid if user has claimed items AND paid for them
        const hasPaid = hasItemsClaimed && hasNoOwedAmount && hasPaymentInfo;
        
        // Always set based on actual payment status
        setUserHasPaid(hasPaid);
        
        console.log('Payment status check - hasPaid:', hasPaid, 'hasItemsClaimed:', hasItemsClaimed, 'owedAmount:', newOwedAmount, 'paymentInfo items:', Object.keys(paymentInfo).length);
      } else {
        console.error('Failed to load item assignments:', response.message);
      }
    } catch (error) {
      console.error('Error loading item assignments:', error);
      // Don't show alert - network errors are usually temporary
    } finally {
      setIsLoadingAssignments(false);
    }
  };

  const handleToggleItemClaim = async (itemId) => {
    if (!receiptId) return;
    
    // Prevent double-clicks and concurrent claims on the same item
    if (claimingItems.has(itemId)) {
      return; // Already processing this item
    }
    
    // Find the item to get its originalItemId (for backend) or use itemId if not expanded
    const item = billData.items.find(i => (i.itemId || i.id) === itemId);
    const backendItemId = item?.originalItemId || itemId; // Use originalItemId if available, otherwise use itemId
    
    const isClaimed = itemAssignments[itemId] && itemAssignments[itemId] > 0;
    
    // Mark item as being processed
    setClaimingItems(prev => new Set(prev).add(itemId));
    
    // Optimistic update: Update UI immediately before API call completes
    const previousAssignments = { ...itemAssignments };
    const newAssignments = { ...itemAssignments };
    if (isClaimed) {
      delete newAssignments[itemId];
    } else {
      newAssignments[itemId] = 1;
    }
    setItemAssignments(newAssignments); // Update UI immediately - instant feedback!
    
    try {
      let response;
      if (isClaimed) {
        response = await unclaimItem(receiptId, backendItemId);
      } else {
        response = await claimItem(receiptId, backendItemId, 1);
      }
      
      if (response.success) {
        // Update with actual values from server
        setItemAssignments(newAssignments);
        setOwedAmount(response.owedAmount || 0);
        // No reload needed - optimistic update already done
      } else {
        // Rollback optimistic update on error
        setItemAssignments(previousAssignments);
        Alert.alert('Error', response.message || 'Failed to update item claim');
        // Don't reload - just rollback is enough, avoids flickering
      }
    } catch (error) {
      // Rollback optimistic update on error
      setItemAssignments(previousAssignments);
      console.error('Error toggling item claim:', error);
      Alert.alert('Error', 'Failed to update item claim');
      // Don't reload - just rollback is enough, avoids flickering
    } finally {
      // Remove from claiming set so item can be clicked again
      setClaimingItems(prev => {
        const next = new Set(prev);
        next.delete(itemId);
        return next;
      });
    }
  };

  const handlePay = async () => {
    if (!receiptId) return;
    
    Alert.alert(
      'Confirm Payment',
      `Pay $${owedAmount.toFixed(2)} for your portion of this receipt?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Pay',
          onPress: async () => {
            try {
              const response = await payReceipt(receiptId);
              
              if (response.success) {
                // Payment was successful - set userHasPaid to true
                setUserHasPaid(true);
                
                // Update owed amount
                const newOwedAmount = response.owedAmount || 0;
                setOwedAmount(newOwedAmount);
                
                // Update item payment info from response
                if (response.itemPaymentInfo) {
                  setItemPaymentInfo(prev => ({
                    ...prev,
                    ...response.itemPaymentInfo
                  }));
                }
                
                // Reload item assignments to get fresh payment info from database
                // This ensures "Paid by [name]" shows correctly
                await loadItemAssignments();
                
                // After reload, ensure userHasPaid stays true (payment was successful)
                setUserHasPaid(true);
                
                // Show success message
                const message = response.receiptCompleted
                  ? `Payment successful! Receipt is now completed.`
                  : `Payment successful! $${response.amountPaid?.toFixed(2)} paid.`;
                
                Alert.alert('Success', message);
              } else {
                // Payment failed - keep userHasPaid as false
                setUserHasPaid(false);
                Alert.alert('Payment Failed', response.message || 'Failed to process payment');
              }
            } catch (error) {
              console.error('Error processing payment:', error);
              Alert.alert('Error', 'Failed to process payment. Please try again.');
            }
          },
        },
      ]
    );
  };

  // Transform backend receipt data to match our expected format
  const transformReceiptData = (data) => {
    // Backend parser returns: items (with name, qty, price), subtotal, tax, total, merchant
    // Note: price in items might be per-item or line total - we'll treat it as per-item
    // Expand items with qty > 1 into multiple items with qty=1 so users can claim individually
    const expandedItems = [];
    let itemIndex = 0;
    
    (data.items || []).forEach((item, originalIndex) => {
      const baseItemId = item.itemId || item.id || (originalIndex + 1);
      const qty = item.qty || item.quantity || 1;
      const price = parseFloat(item.price) || 0;
      const name = item.name || 'Unknown Item';
      
      // Create one item per quantity, each with qty=1
      for (let i = 0; i < qty; i++) {
        expandedItems.push({
          itemId: `${baseItemId}_${i}`, // Unique ID for each instance (e.g., "1_0", "1_1")
          originalItemId: baseItemId, // Keep reference to original itemId for backend operations
          id: `${baseItemId}_${i}`,
          name: name,
          price: price, // Price per item
          qty: 1, // Always 1 for expanded items
        });
        itemIndex++;
      }
    });

    const total = parseFloat(data.total) || 0;
    const tax = parseFloat(data.tax) || 0;
    const tip = parseFloat(data.tip) || 0;
    const subtotal = parseFloat(data.subtotal) || 0;
    
    // Calculate subtotal if not provided (sum of price * qty for all items)
    // Since all items now have qty=1, this is just sum of prices
    const calculatedSubtotal = subtotal || (expandedItems.reduce((sum, item) => sum + item.price, 0));
    
    // Calculate tip if not provided (as difference between total and subtotal + tax)
    const calculatedTip = tip || Math.max(0, total - calculatedSubtotal - tax);

    return {
      restaurant_name: data.merchant || data.restaurant_name || 'Unknown Merchant',
      date: data.date || new Date().toLocaleDateString(),
      items: expandedItems,
      tax: tax || 0,
      tip: calculatedTip,
      total: total,
      subtotal: calculatedSubtotal,
    };
  };

  const subtotal = billData.subtotal || (billData.total - billData.tax - billData.tip);

  // Helper function to calculate item total with tax and tip
  const calculateItemTotal = (itemPrice) => {
    if (!subtotal || subtotal === 0) return itemPrice;
    const proportion = itemPrice / subtotal;
    const itemTax = billData.tax * proportion;
    const itemTip = billData.tip * proportion;
    return itemPrice + itemTax + itemTip;
  };

  // Get user's claimed items
  const getMyItems = () => {
    return billData.items.filter((item) => {
      const itemId = item.itemId || item.id;
      return itemAssignments[itemId] && itemAssignments[itemId] > 0;
    }).map((item) => {
      const itemId = item.itemId || item.id;
      const qty = itemAssignments[itemId] || 1;
      return {
        ...item,
        qty: qty,
        totalPrice: item.price * qty,
      };
    });
  };

  const [otherParticipants, setOtherParticipants] = useState([]);
  const [currentUserId, setCurrentUserId] = useState(null);

  // Get current user ID on mount
  useEffect(() => {
    const loadUserId = async () => {
      const userId = await AsyncStorage.getItem('userId');
      setCurrentUserId(userId);
    };
    loadUserId();
  }, []);

  // Get other participants and their paid items
  useEffect(() => {
    if (!receiptId || !isFromActivity || !currentUserId || Object.keys(itemPaymentInfo).length === 0) {
      setOtherParticipants([]);
      return;
    }
    
    // Group items by payer from itemPaymentInfo
    const participantsMap = new Map();
    
    billData.items.forEach((item) => {
      const itemId = item.itemId || item.id;
      const paymentInfo = itemPaymentInfo[String(itemId)] || itemPaymentInfo[itemId];
      
      if (paymentInfo && paymentInfo.paidBy && paymentInfo.paidBy !== currentUserId) {
        const payerId = paymentInfo.paidBy;
        const payerName = paymentInfo.payerName || 'Unknown';
        
        if (!participantsMap.has(payerId)) {
          participantsMap.set(payerId, {
            userId: payerId,
            name: payerName,
            initials: getInitials(payerName),
            items: [],
            totalAmount: 0,
          });
        }
        
        const participant = participantsMap.get(payerId);
        const itemTotal = calculateItemTotal(item.price); // qty is always 1 for expanded items
        participant.items.push(item.name);
        participant.totalAmount += itemTotal;
      }
    });
    
    setOtherParticipants(Array.from(participantsMap.values()));
  }, [itemPaymentInfo, isFromActivity, receiptId, currentUserId, billData.items]);

  // Get initials from name
  const getInitials = (name) => {
    if (!name) return '?';
    const parts = name.trim().split(' ');
    if (parts.length >= 2) {
      return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  };

  // Format date for display
  const formatDisplayDate = (dateStr) => {
    if (!dateStr) return 'Unknown date';
    try {
      const date = new Date(dateStr);
      if (isNaN(date.getTime())) return dateStr;
      return date.toLocaleDateString('en-US', { 
        month: 'long', 
        day: 'numeric', 
        year: 'numeric' 
      });
    } catch {
      return dateStr;
    }
  };

  const handleCreateAndShare = async () => {
    if (!friendsEmails.trim()) {
      Alert.alert('Error', 'Please enter at least one friend\'s email address');
      return;
    }

    setIsCreating(true);
    
    try {
      // If viewing from Activity and user is uploader, add participants to existing receipt
      if (isFromActivity && receiptId && isUploader) {
        const participantEmails = friendsEmails.split(',').map(email => email.trim()).filter(email => email);
        const response = await addParticipantsToReceipt(receiptId, participantEmails);
        
        if (response.success) {
          const participantsCount = response.participantsAdded || 0;
          const message = participantsCount > 0
            ? `Added ${participantsCount} friend${participantsCount > 1 ? 's' : ''} to this receipt!`
            : 'No new friends were added. (They may not have accounts yet)';
          
          Alert.alert('Success', message);
          setFriendsEmails(''); // Clear input
        } else {
          Alert.alert('Error', response.message || 'Failed to add friends. Please try again.');
        }
      } else {
        // Create new receipt
        // Items are already expanded (qty=1 each), so send them as-is
        const receiptData = {
          restaurant_name: billData.restaurant_name,
          total_amount: billData.total,
          tax: billData.tax,
          tip: billData.tip,
          items: billData.items.map((item) => ({
            name: item.name,
            price: item.price,
            qty: 1, // All items are already expanded to qty=1
          })),
          participants: friendsEmails.split(',').map(email => email.trim()).filter(email => email),
        };
        
        // Call API to create receipt
        console.log('Creating receipt with data:', JSON.stringify(receiptData, null, 2));
        const response = await createReceipt(receiptData);
        console.log('Receipt creation response:', response);
        
        if (response.success) {
          const participantsCount = response.participantsAdded || 0;
          const message = participantsCount > 0
            ? `Bill created successfully! Shared with ${participantsCount} friend${participantsCount > 1 ? 's' : ''}.`
            : 'Bill created successfully! (Note: Some friends may not have accounts yet)';
          
          Alert.alert('Success', message, [
            {
              text: 'OK',
              onPress: () => navigation.navigate('Home'),
            },
          ]);
        } else {
          Alert.alert('Error', response.message || 'Failed to create bill. Please try again.');
        }
      }
    } catch (error) {
      console.error("Error creating/updating bill:", error);
      Alert.alert('Error', 'Failed to process request. Please try again.');
    }
    
    setIsCreating(false);
  };

  const myItems = getMyItems();
  const myItemsTotal = myItems.reduce((sum, item) => sum + calculateItemTotal(item.totalPrice), 0);

  return (
    <View style={styles.wrapper}>
      {/* Header with Back Button */}
      <View style={styles.headerContainer}>
        <TouchableOpacity 
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <Ionicons name="arrow-back" size={24} color="#111827" />
        </TouchableOpacity>
        <View style={styles.headerContent}>
          <Text style={styles.headerTitle}>{billData.restaurant_name}</Text>
          <View style={styles.headerDateRow}>
            <Ionicons name="time-outline" size={14} color="#6B7280" />
            <Text style={styles.headerDate}>
              {formatDisplayDate(billData.date)} • {billData.date.includes('PM') || billData.date.includes('AM') ? billData.date.split(' ').slice(-2).join(' ') : ''}
            </Text>
          </View>
        </View>
      </View>

      <KeyboardAvoidingView 
        style={styles.keyboardAvoid}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 20}
      >
        <ScrollView 
          ref={scrollViewRef}
          style={styles.container}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
        >
        {/* Payment Summary Card */}
        {isFromActivity && userHasPaid && (
          <View style={styles.paymentSummaryCard}>
            <View style={styles.paymentSummaryContent}>
              <View>
                <Text style={styles.paymentAmount}>${myItemsTotal.toFixed(2)}</Text>
                <Text style={styles.paymentLabel}>You paid</Text>
              </View>
              <View style={styles.paymentStatus}>
                <Ionicons name="checkmark-circle" size={24} color="#0d9488" />
                <View style={styles.paidBadgeLarge}>
                  <Text style={styles.paidBadgeLargeText}>Paid</Text>
                </View>
              </View>
            </View>
          </View>
        )}

        {/* What You Paid For - Only show if user has claimed items */}
        {isFromActivity && myItems.length > 0 && (
          <View style={styles.card}>
            <View style={styles.cardHeader}>
              <Text style={styles.cardTitle}>What You Paid For</Text>
            </View>
            <View style={styles.cardContent}>
              {myItems.map((item, index) => (
                <View key={item.itemId || item.id} style={styles.myItemRow}>
                  <View style={styles.myItemInfo}>
                    <Text style={styles.myItemName}>{item.name}</Text>
                    <Text style={styles.myItemSubtext}>Item: ${item.price.toFixed(2)}</Text>
                  </View>
                  <Text style={styles.myItemTotal}>${calculateItemTotal(item.totalPrice).toFixed(2)}</Text>
                </View>
              ))}
            </View>
          </View>
        )}

        {/* Other People - Show participants who have paid */}
        {isFromActivity && otherParticipants.length > 0 && (
          <View style={styles.card}>
            <View style={styles.cardHeader}>
              <View style={styles.cardHeaderWithIcon}>
                <Ionicons name="people-outline" size={20} color="#111827" />
                <Text style={styles.cardTitle}>
                  Other People ({otherParticipants.length})
                </Text>
              </View>
            </View>
            <View style={styles.cardContent}>
              {otherParticipants.map((participant, index) => (
                <View key={participant.userId}>
                  <View style={styles.participantRow}>
                    <View style={styles.participantLeft}>
                      <View style={styles.avatar}>
                        <Text style={styles.avatarText}>{participant.initials}</Text>
                      </View>
                      <View style={styles.participantInfo}>
                        <Text style={styles.participantName}>{participant.name}</Text>
                        <Text style={styles.participantAmount}>${participant.totalAmount.toFixed(2)}</Text>
                      </View>
                    </View>
                  </View>
                  <View style={styles.participantItems}>
                    {participant.items.map((itemName, itemIndex) => (
                      <Text key={itemIndex} style={styles.participantItemText}>
                        • {itemName}
                      </Text>
                    ))}
                  </View>
                  {index < otherParticipants.length - 1 && <View style={styles.separator} />}
                </View>
              ))}
            </View>
          </View>
        )}

        {/* All Items - For claiming (when not paid) or viewing all items */}
        <View style={styles.card}>
          <View style={styles.cardHeader}>
            <Text style={styles.cardTitle}>
              {isFromActivity ? 'Items' : `Items (${billData.items.length})`}
            </Text>
          </View>
          <View style={styles.cardContent}>
            {billData.items.length === 0 ? (
              <View style={styles.emptyItemsContainer}>
                <Text style={styles.emptyItemsText}>No items found</Text>
              </View>
            ) : (
              billData.items.map((item, index) => {
                const itemId = item.itemId || item.id;
                const isClaimed = itemId && itemAssignments[itemId] && itemAssignments[itemId] > 0;
                const paymentInfo = itemPaymentInfo[String(itemId)] || itemPaymentInfo[itemId] || null;
                const isPaid = paymentInfo != null;
                const payerName = paymentInfo?.payerName || null;
                const isClaiming = claimingItems.has(itemId);
                
                return (
                  <View key={itemId || index}>
                    <TouchableOpacity
                      style={[
                        styles.itemRow, 
                        isFromActivity && !isPaid && styles.itemRowClickable,
                        isPaid && styles.itemRowPaid,
                        isClaiming && styles.itemRowProcessing
                      ]}
                      onPress={isFromActivity && !isPaid && !isClaiming ? () => handleToggleItemClaim(itemId) : undefined}
                      disabled={!isFromActivity || isPaid || isClaiming}
                      activeOpacity={isFromActivity && !isPaid && !isClaiming ? 0.7 : 1}
                    >
                      <View style={styles.itemInfo}>
                        <View style={styles.itemNameRow}>
                          <Text style={[styles.itemName, isPaid && styles.itemNamePaid]}>{item.name}</Text>
                          {isPaid ? (
                            <View style={styles.paidBadge}>
                              <Ionicons name="checkmark-circle" size={16} color="#0d9488" />
                              <Text style={styles.paidBadgeText}>Paid by {payerName || 'Someone'}</Text>
                            </View>
                          ) : (
                            isFromActivity && (
                              <View style={[styles.claimBadge, isClaimed && styles.claimBadgeActive]}>
                                <Ionicons 
                                  name={isClaimed ? "checkmark-circle" : "ellipse-outline"} 
                                  size={16} 
                                  color={isClaimed ? "#0d9488" : "#9CA3AF"} 
                                />
                                <Text style={[styles.claimBadgeText, isClaimed && styles.claimBadgeTextActive]}>
                                  {isClaimed ? "Claimed" : "Tap to claim"}
                                </Text>
                              </View>
                            )
                          )}
                        </View>
                        {/* Qty is always 1 for expanded items, so no need to show qty */}
                      </View>
                      <Text style={[styles.itemPrice, isPaid && styles.itemPricePaid]}>
                        ${item.price.toFixed(2)}
                      </Text>
                    </TouchableOpacity>
                    {index < billData.items.length - 1 && <View style={styles.separator} />}
                  </View>
                );
              })
            )}
          </View>
        </View>

        {/* Your Portion - Payment Section (only shown when viewing from Activity and not paid) */}
        {isFromActivity && !userHasPaid && (
          <View style={styles.card}>
            <View style={styles.cardHeader}>
              <Text style={styles.cardTitle}>Your Portion</Text>
            </View>
            <View style={styles.cardContent}>
              <View style={styles.breakdownRow}>
                <Text style={styles.totalLabelBold}>Amount Owed</Text>
                <Text style={styles.totalValueBold}>${owedAmount.toFixed(2)}</Text>
              </View>
              {Object.keys(itemAssignments).length === 0 && (
                <Text style={styles.hintText}>Tap items above to claim them and calculate your portion</Text>
              )}
              {owedAmount > 0.01 && (
                <TouchableOpacity style={styles.payButton} onPress={handlePay}>
                  <Ionicons name="card-outline" size={20} color="#fff" />
                  <Text style={styles.payButtonText}>Pay ${owedAmount.toFixed(2)}</Text>
                </TouchableOpacity>
              )}
            </View>
          </View>
        )}

        {/* Bill Summary */}
        <View style={styles.card}>
          <View style={styles.cardHeader}>
            <Text style={styles.cardTitle}>Bill Summary</Text>
          </View>
          <View style={styles.cardContent}>
            <View style={styles.breakdownRow}>
              <Text style={styles.breakdownLabel}>Subtotal</Text>
              <Text style={styles.breakdownValue}>${subtotal.toFixed(2)}</Text>
            </View>
            <View style={styles.breakdownRow}>
              <Text style={styles.breakdownLabel}>Tax & Tip</Text>
              <Text style={styles.breakdownValue}>${(billData.tax + billData.tip).toFixed(2)}</Text>
            </View>
            <View style={styles.separator} />
            <View style={styles.breakdownRow}>
              <Text style={styles.totalLabelBold}>Total Bill</Text>
              <Text style={styles.totalValueBold}>${billData.total.toFixed(2)}</Text>
            </View>
            {isFromActivity && myItems.length > 0 && (
              <View style={styles.breakdownRow}>
                <Text style={styles.yourShareLabel}>Your Share</Text>
                <Text style={styles.yourShareValue}>${myItemsTotal.toFixed(2)}</Text>
              </View>
            )}
          </View>
        </View>

        {/* Invite Friends (only shown when creating new receipt, not from Activity) */}
        {!isFromActivity && (
          <View style={styles.card}>
            <View style={styles.cardHeader}>
              <Text style={styles.cardTitle}>Invite Friends</Text>
            </View>
            <View style={styles.inviteContent}>
              <Text style={styles.inputLabel}>
                Enter email addresses (separated by commas)
              </Text>
              <TextInput
                ref={emailInputRef}
                style={styles.input}
                placeholder="sarah@email.com, mike@email.com"
                placeholderTextColor={colors.textLight}
                value={friendsEmails}
                onChangeText={setFriendsEmails}
                multiline={false}
                autoCapitalize="none"
                keyboardType="email-address"
                returnKeyType="done"
                blurOnSubmit={true}
                onFocus={() => {
                  // Scroll to end to show input above keyboard
                  setTimeout(() => {
                    scrollViewRef.current?.scrollToEnd({ animated: true });
                  }, 300);
                }}
              />
              
              <View style={styles.buttonContainer}>
                <TouchableOpacity
                  style={[styles.primaryButton, (isCreating || !friendsEmails.trim()) && styles.buttonDisabled]}
                  onPress={handleCreateAndShare}
                  disabled={isCreating || !friendsEmails.trim()}
                >
                  {isCreating ? (
                    <ActivityIndicator color="#fff" />
                  ) : (
                    <>
                      <Ionicons name="share-outline" size={20} color="#fff" style={styles.buttonIcon} />
                      <Text style={styles.primaryButtonText}>Create & Share Bill</Text>
                    </>
                  )}
                </TouchableOpacity>
                
                {isFromCamera && (
                  <TouchableOpacity
                    style={styles.secondaryButton}
                    onPress={() => navigation.navigate('Home')}
                  >
                    <Ionicons name="camera-outline" size={20} color={colors.primary} style={styles.buttonIcon} />
                    <Text style={styles.secondaryButtonText}>Scan Another Receipt</Text>
                  </TouchableOpacity>
                )}
              </View>
            </View>
          </View>
        )}

        {/* View Receipt Button */}
        {billData.imageUrl && (
          <TouchableOpacity 
            style={styles.viewReceiptButton}
            onPress={() => {
              // Open receipt image - you can implement image viewer here
              Alert.alert('View Receipt', 'Receipt image viewer coming soon!');
            }}
          >
            <Ionicons name="receipt-outline" size={20} color="#0d9488" />
            <Text style={styles.viewReceiptButtonText}>View Original Receipt</Text>
          </TouchableOpacity>
        )}
        </ScrollView>
      </KeyboardAvoidingView>
      <BottomNavBar />
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    flex: 1,
    backgroundColor: colors.background,
  },
  keyboardAvoid: {
    flex: 1,
  },
  container: {
    flex: 1,
  },
  scrollContent: {
    padding: spacing.md,
    paddingBottom: 120, // Space for bottom nav bar
  },
  headerContainer: {
    backgroundColor: '#fff',
    paddingHorizontal: 24,
    paddingTop: 48,
    paddingBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  backButton: {
    marginBottom: 12,
  },
  headerContent: {
    marginLeft: 0,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#111827',
    marginBottom: 4,
  },
  headerDateRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  headerDate: {
    fontSize: 14,
    color: '#6B7280',
  },
  paymentSummaryCard: {
    backgroundColor: '#ccfbf1',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#99f6e4',
    marginBottom: spacing.md,
    marginTop: spacing.md,
  },
  paymentSummaryContent: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
  },
  paymentAmount: {
    fontSize: 28,
    fontWeight: '700',
    color: '#0f766e',
    marginBottom: 4,
  },
  paymentLabel: {
    fontSize: 14,
    color: '#14b8a6',
    fontWeight: '600',
  },
  paymentStatus: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  paidBadgeLarge: {
    backgroundColor: '#99f6e4',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
  },
  paidBadgeLargeText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#0f766e',
  },
  header: {
    alignItems: 'center',
    marginBottom: spacing.lg,
  },
  restaurantName: {
    fontSize: typography.sizes.xxl,
    fontWeight: 'bold',
    color: colors.text,
    marginBottom: spacing.xs,
  },
  date: {
    fontSize: typography.sizes.md,
    color: colors.textLight,
  },
  badgeContainer: {
    marginTop: spacing.sm,
  },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ccfbf1',
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    borderRadius: 12,
    gap: spacing.xs,
  },
  badgeText: {
    fontSize: typography.sizes.sm,
    color: '#0d9488',
    fontWeight: '600',
  },
  totalCard: {
    backgroundColor: '#dbeafe',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#93c5fd',
    marginBottom: spacing.md,
  },
  totalCardContent: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.md,
  },
  totalAmount: {
    fontSize: typography.sizes.xxl,
    fontWeight: 'bold',
    color: '#2563eb',
  },
  totalLabel: {
    fontSize: typography.sizes.sm,
    color: '#1e40af',
  },
  totalRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
  },
  readyText: {
    fontSize: typography.sizes.md,
    fontWeight: '600',
    color: '#2563eb',
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: 12,
    marginBottom: spacing.md,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.md,
    paddingBottom: spacing.sm,
  },
  cardHeaderWithIcon: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#111827',
  },
  cardContent: {
    padding: spacing.md,
    paddingTop: 0,
  },
  itemsList: {
    paddingTop: 0,
  },
  itemRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.md,
  },
  itemInfo: {
    flex: 1,
  },
  itemName: {
    fontSize: typography.sizes.md,
    fontWeight: '600',
    color: colors.text,
  },
  itemNamePaid: {
    color: '#0d9488',
  },
  itemQty: {
    fontSize: typography.sizes.sm,
    color: colors.textLight,
    marginTop: spacing.xs,
  },
  itemQtyPaid: {
    color: '#0d9488',
  },
  itemPrice: {
    fontSize: typography.sizes.md,
    fontWeight: '600',
    color: colors.text,
  },
  itemPricePaid: {
    color: '#0d9488',
  },
  separator: {
    height: 1,
    backgroundColor: colors.border,
    marginLeft: spacing.md,
  },
  breakdownContent: {
    padding: spacing.md,
  },
  breakdownRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  breakdownLabel: {
    fontSize: typography.sizes.md,
    color: colors.text,
  },
  breakdownValue: {
    fontSize: typography.sizes.md,
    color: colors.text,
  },
  totalLabelBold: {
    fontSize: typography.sizes.lg,
    fontWeight: 'bold',
    color: colors.text,
  },
  totalValueBold: {
    fontSize: typography.sizes.lg,
    fontWeight: 'bold',
    color: colors.text,
  },
  inviteContent: {
    padding: spacing.md,
    paddingTop: 0,
  },
  inputLabel: {
    fontSize: typography.sizes.sm,
    fontWeight: '600',
    color: colors.text,
    marginBottom: spacing.xs,
  },
  input: {
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 8,
    padding: spacing.md,
    fontSize: typography.sizes.md,
    color: colors.text,
    marginBottom: spacing.md,
  },
  buttonContainer: {
    gap: spacing.sm,
  },
  primaryButton: {
    backgroundColor: '#0d9488',
    borderRadius: 12,
    padding: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.xs,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  primaryButtonText: {
    color: colors.white,
    fontSize: typography.sizes.md,
    fontWeight: 'bold',
  },
  secondaryButton: {
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    padding: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.xs,
  },
  secondaryButtonText: {
    color: colors.primary,
    fontSize: typography.sizes.md,
    fontWeight: '600',
  },
  buttonIcon: {
    marginRight: spacing.xs,
  },
  itemRowClickable: {
    backgroundColor: '#f9fafb',
    borderRadius: 8,
    marginVertical: 2,
  },
  itemRowPaid: {
    backgroundColor: '#ccfbf1',
    borderRadius: 8,
    marginVertical: 2,
    opacity: 0.8,
  },
  itemRowProcessing: {
    opacity: 0.6,
  },
  itemNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.xs,
  },
  paidBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ccfbf1',
    paddingHorizontal: spacing.xs,
    paddingVertical: 2,
    borderRadius: 12,
    marginLeft: spacing.xs,
  },
  paidBadgeText: {
    fontSize: typography.sizes.xs,
    color: '#0d9488',
    fontWeight: '600',
    marginLeft: 4,
  },
  claimBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#f3f4f6',
    paddingHorizontal: spacing.xs,
    paddingVertical: 4,
    borderRadius: 12,
    marginLeft: spacing.xs,
    gap: 4,
  },
  claimBadgeActive: {
    backgroundColor: '#ccfbf1',
  },
  claimBadgeText: {
    fontSize: typography.sizes.xs,
    color: '#9CA3AF',
    fontWeight: '500',
  },
  claimBadgeTextActive: {
    color: '#0d9488',
  },
  payButton: {
    backgroundColor: '#0d9488',
    borderRadius: 12,
    padding: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: spacing.md,
    gap: spacing.xs,
  },
  payButtonText: {
    color: '#fff',
    fontSize: typography.sizes.md,
    fontWeight: 'bold',
  },
  hintText: {
    fontSize: typography.sizes.sm,
    color: colors.textLight,
    fontStyle: 'italic',
    marginTop: spacing.sm,
    textAlign: 'center',
  },
  emptyItemsContainer: {
    padding: spacing.lg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyItemsText: {
    fontSize: typography.sizes.md,
    color: colors.textLight,
    fontWeight: '600',
    marginBottom: spacing.xs,
  },
  emptyItemsSubtext: {
    fontSize: typography.sizes.sm,
    color: colors.textLight,
    textAlign: 'center',
  },
  myItemRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
  },
  myItemInfo: {
    flex: 1,
  },
  myItemName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#111827',
    marginBottom: 4,
  },
  myItemSubtext: {
    fontSize: 12,
    color: '#6B7280',
  },
  myItemTotal: {
    fontSize: 16,
    fontWeight: '600',
    color: '#111827',
  },
  participantRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  participantLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    flex: 1,
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#F3F4F6',
    justifyContent: 'center',
    alignItems: 'center',
  },
  avatarText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#374151',
  },
  participantInfo: {
    flex: 1,
  },
  participantName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#111827',
    marginBottom: 4,
  },
  participantAmount: {
    fontSize: 14,
    color: '#6B7280',
  },
  participantItems: {
    marginLeft: 52,
    marginTop: 4,
    marginBottom: 12,
  },
  participantItemText: {
    fontSize: 14,
    color: '#6B7280',
    marginBottom: 4,
  },
  yourShareLabel: {
    fontSize: 16,
    fontWeight: '700',
    color: '#0d9488',
  },
  yourShareValue: {
    fontSize: 16,
    fontWeight: '700',
    color: '#0d9488',
  },
  viewReceiptButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 12,
    padding: 16,
    marginTop: spacing.sm,
    gap: 8,
  },
  viewReceiptButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#0d9488',
  },
});
