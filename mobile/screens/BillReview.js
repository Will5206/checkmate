import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  Alert,
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
        setItemAssignments(response.assignments || {});
        const newOwedAmount = response.owedAmount || 0;
        setOwedAmount(newOwedAmount);
        
        // Store payment info for all items - this shows which items are paid and by whom
        const paymentInfo = response.itemPaymentInfo || {};
        setItemPaymentInfo(paymentInfo);
        console.log('Loaded payment info for', Object.keys(paymentInfo).length, 'items:', paymentInfo);
        
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
        response = await unclaimItem(receiptId, itemId);
      } else {
        response = await claimItem(receiptId, itemId, 1);
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
    const items = (data.items || []).map((item, index) => ({
      itemId: item.itemId || item.id || (index + 1), // Preserve itemId if present (needed for claiming)
      id: item.itemId || item.id || (index + 1), // Use itemId if available, otherwise use index
      name: item.name || 'Unknown Item',
      price: parseFloat(item.price) || 0,  // Price per item
      qty: item.qty || item.quantity || 1, // Support both qty and quantity
    }));

    const total = parseFloat(data.total) || 0;
    const tax = parseFloat(data.tax) || 0;
    const tip = parseFloat(data.tip) || 0;
    const subtotal = parseFloat(data.subtotal) || 0;
    
    // Calculate subtotal if not provided (sum of price * qty for all items)
    const calculatedSubtotal = subtotal || (items.reduce((sum, item) => sum + (item.price * item.qty), 0));
    
    // Calculate tip if not provided (as difference between total and subtotal + tax)
    const calculatedTip = tip || Math.max(0, total - calculatedSubtotal - tax);

    return {
      restaurant_name: data.merchant || data.restaurant_name || 'Unknown Merchant',
      date: data.date || new Date().toLocaleDateString(),
      items: items,
      tax: tax || 0,
      tip: calculatedTip,
      total: total,
      subtotal: calculatedSubtotal,
    };
  };

  const subtotal = billData.subtotal || (billData.total - billData.tax - billData.tip);

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
        const receiptData = {
          restaurant_name: billData.restaurant_name,
          total_amount: billData.total,
          tax: billData.tax,
          tip: billData.tip,
          items: billData.items.map((item) => ({
            name: item.name,
            price: item.price,
            qty: item.qty || 1,
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

  return (
    <View style={styles.wrapper}>
      <ScrollView 
        style={styles.container}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.restaurantName}>{billData.restaurant_name}</Text>
          <Text style={styles.date}>{billData.date}</Text>
          {isFromCamera && (
            <View style={styles.badgeContainer}>
              <View style={styles.badge}>
                <Ionicons name="camera" size={14} color="#059669" />
                <Text style={styles.badgeText}>Scanned from receipt</Text>
              </View>
            </View>
          )}
        </View>

        {/* Total Summary */}
        <View style={styles.totalCard}>
          <View style={styles.totalCardContent}>
            <View>
              <Text style={styles.totalAmount}>${billData.total.toFixed(2)}</Text>
              <Text style={styles.totalLabel}>Total Bill</Text>
            </View>
            <View style={styles.totalRight}>
              <Ionicons name="people" size={24} color="#2563eb" />
              <Text style={styles.readyText}>Ready to split</Text>
            </View>
          </View>
        </View>

        {/* Items List */}
        <View style={styles.card}>
          <View style={styles.cardHeader}>
            <Text style={styles.cardTitle}>Items ({billData.items.length})</Text>
            {!isFromActivity && (
              <TouchableOpacity>
                <Ionicons name="create-outline" size={20} color={colors.primary} />
              </TouchableOpacity>
            )}
          </View>
          <View style={styles.itemsList}>
            {billData.items.length === 0 ? (
              <View style={styles.emptyItemsContainer}>
                <Text style={styles.emptyItemsText}>No items found</Text>
                {isFromActivity && (
                  <Text style={styles.emptyItemsSubtext}>
                    This receipt may not have items loaded. Please check the backend logs.
                  </Text>
                )}
              </View>
            ) : (
              billData.items.map((item, index) => {
                const itemId = item.itemId || item.id;
                const isClaimed = itemId && itemAssignments[itemId] && itemAssignments[itemId] > 0;
                
                // Check payment info - backend returns string keys, so try both string and number
                const paymentInfo = itemPaymentInfo[String(itemId)] || itemPaymentInfo[itemId] || null;
                const isPaid = paymentInfo != null;
                const payerName = paymentInfo?.payerName || null;
                
                // Debug logging
                if (isFromActivity && index === 0) {
                  console.log('BillReview: First item - itemId:', itemId, 'isFromActivity:', isFromActivity, 'itemAssignments:', itemAssignments, 'isPaid:', isPaid, 'paymentInfo:', paymentInfo, 'itemPaymentInfo keys:', Object.keys(itemPaymentInfo));
                }
                
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
                          {/* Show "Paid by [name]" if item is paid - this takes priority over claim status */}
                          {isPaid ? (
                            <View style={styles.paidBadge}>
                              <Ionicons name="checkmark-circle" size={16} color="#059669" />
                              <Text style={styles.paidBadgeText}>Paid by {payerName || 'Someone'}</Text>
                            </View>
                          ) : (
                            /* Only show claim badge if item is not paid and we're viewing from Activity */
                            isFromActivity && (
                              <View style={[styles.claimBadge, isClaimed && styles.claimBadgeActive]}>
                                <Ionicons 
                                  name={isClaimed ? "checkmark-circle" : "ellipse-outline"} 
                                  size={16} 
                                  color={isClaimed ? "#059669" : "#9CA3AF"} 
                                />
                                <Text style={[styles.claimBadgeText, isClaimed && styles.claimBadgeTextActive]}>
                                  {isClaimed ? "Claimed" : "Tap to claim"}
                                </Text>
                              </View>
                            )
                          )}
                        </View>
                        {item.qty > 1 && (
                          <Text style={[styles.itemQty, isPaid && styles.itemQtyPaid]}>Qty: {item.qty}</Text>
                        )}
                      </View>
                      <Text style={[styles.itemPrice, isPaid && styles.itemPricePaid]}>
                        ${(item.price * (item.qty || 1)).toFixed(2)}
                      </Text>
                    </TouchableOpacity>
                    {index < billData.items.length - 1 && <View style={styles.separator} />}
                  </View>
                );
              })
            )}
          </View>
        </View>

        {/* Your Portion (only shown when viewing from Activity) */}
        {isFromActivity && (
          <View style={styles.card}>
            <View style={styles.cardHeader}>
              <Text style={styles.cardTitle}>Your Portion</Text>
            </View>
            <View style={styles.breakdownContent}>
              <View style={styles.breakdownRow}>
                <Text style={styles.totalLabelBold}>Amount Owed</Text>
                <Text style={styles.totalValueBold}>${owedAmount.toFixed(2)}</Text>
              </View>
              {Object.keys(itemAssignments).length === 0 && (
                <Text style={styles.hintText}>Tap items above to claim them and calculate your portion</Text>
              )}
              {owedAmount > 0.01 && !userHasPaid && (
                <TouchableOpacity style={styles.payButton} onPress={handlePay}>
                  <Ionicons name="card-outline" size={20} color="#fff" style={styles.buttonIcon} />
                  <Text style={styles.payButtonText}>Pay ${owedAmount.toFixed(2)}</Text>
                </TouchableOpacity>
              )}
              {userHasPaid && (
                <View style={[styles.paidBadge, { marginTop: 10, padding: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'center' }]}>
                  <Ionicons name="checkmark-circle" size={20} color="#059669" />
                  <Text style={[styles.paidBadgeText, { marginLeft: 8 }]}>You have paid for your items</Text>
                </View>
              )}
            </View>
          </View>
        )}

        {/* Bill Breakdown */}
        <View style={styles.card}>
          <View style={styles.breakdownContent}>
            <View style={styles.breakdownRow}>
              <Text style={styles.breakdownLabel}>Subtotal</Text>
              <Text style={styles.breakdownValue}>${subtotal.toFixed(2)}</Text>
            </View>
            <View style={styles.breakdownRow}>
              <Text style={styles.breakdownLabel}>Tax</Text>
              <Text style={styles.breakdownValue}>${billData.tax.toFixed(2)}</Text>
            </View>
            <View style={styles.breakdownRow}>
              <Text style={styles.breakdownLabel}>Tip</Text>
              <Text style={styles.breakdownValue}>${billData.tip.toFixed(2)}</Text>
            </View>
            <View style={styles.separator} />
            <View style={styles.breakdownRow}>
              <Text style={styles.totalLabelBold}>Total</Text>
              <Text style={styles.totalValueBold}>${billData.total.toFixed(2)}</Text>
            </View>
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
                style={styles.input}
                placeholder="sarah@email.com, mike@email.com"
                placeholderTextColor={colors.textLight}
                value={friendsEmails}
                onChangeText={setFriendsEmails}
                multiline={false}
                autoCapitalize="none"
                keyboardType="email-address"
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
      </ScrollView>
      <BottomNavBar />
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    flex: 1,
    backgroundColor: colors.background,
  },
  container: {
    flex: 1,
  },
  scrollContent: {
    padding: spacing.md,
    paddingBottom: 120, // Space for bottom nav bar
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
    backgroundColor: '#d1fae5',
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    borderRadius: 12,
    gap: spacing.xs,
  },
  badgeText: {
    fontSize: typography.sizes.sm,
    color: '#059669',
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
  cardTitle: {
    fontSize: typography.sizes.lg,
    fontWeight: 'bold',
    color: colors.text,
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
    color: '#059669',
  },
  itemQty: {
    fontSize: typography.sizes.sm,
    color: colors.textLight,
    marginTop: spacing.xs,
  },
  itemQtyPaid: {
    color: '#059669',
  },
  itemPrice: {
    fontSize: typography.sizes.md,
    fontWeight: '600',
    color: colors.text,
  },
  itemPricePaid: {
    color: '#059669',
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
    backgroundColor: '#059669',
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
    backgroundColor: '#D1FAE5',
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
    backgroundColor: '#D1FAE5',
    paddingHorizontal: spacing.xs,
    paddingVertical: 2,
    borderRadius: 12,
    marginLeft: spacing.xs,
  },
  paidBadgeText: {
    fontSize: typography.sizes.xs,
    color: '#059669',
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
    backgroundColor: '#d1fae5',
  },
  claimBadgeText: {
    fontSize: typography.sizes.xs,
    color: '#9CA3AF',
    fontWeight: '500',
  },
  claimBadgeTextActive: {
    color: '#059669',
  },
  payButton: {
    backgroundColor: '#059669',
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
});
