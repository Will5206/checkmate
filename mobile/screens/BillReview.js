import React, { useState, useEffect, useRef, useCallback } from 'react';
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
import { createReceipt, addParticipantsToReceipt } from '../services/receiptsService';
import { calculateItemTotal, getMyItems, calculateMyItemsTotal } from '../utils/billCalculations';
import { getInitials } from '../utils/formatting';
import { useBillReviewData } from '../hooks/useBillReviewData';
import { useItemClaims } from '../hooks/useItemClaims';
import { useReceiptPayment } from '../hooks/useReceiptPayment';
import { useFriendsSelection } from '../hooks/useFriendsSelection';
import BillReviewHeader from '../components/billReview/BillReviewHeader';
import ItemsList from '../components/billReview/ItemsList';
import PaymentSection from '../components/billReview/PaymentSection';
import BillSummary from '../components/billReview/BillSummary';
import ParticipantsList from '../components/billReview/ParticipantsList';
import CompletedIndicator from '../components/billReview/CompletedIndicator';
import FriendsInvite from '../components/billReview/FriendsInvite';

export default function BillReview() {
  const navigation = useNavigation();
  const route = useRoute();
  const scrollViewRef = useRef(null);
  const emailInputRef = useRef(null);
  
  // Use custom hooks for data management
  const { billData, setBillData, isFromCamera, isFromActivity, receiptId, uploadedBy } = useBillReviewData(route);
  const { itemAssignments, setItemAssignments, claimingItems, handleToggleItemClaim } = useItemClaims(receiptId, billData);
  const {
    itemPaymentInfo,
    owedAmount,
    owedAmountExcludingPaid,
    userHasPaid,
    setUserHasPaid,
    isReceiptComplete,
    isLoadingAssignments,
    isUploader,
    handlePay,
    updateOwedAmounts,
  } = useReceiptPayment(receiptId, isFromActivity, uploadedBy, billData, itemAssignments, setItemAssignments);
  const {
    friends,
    friendsEmails,
    setFriendsEmails,
    selectedFriendEmails,
    showFriendsDropdown,
    setShowFriendsDropdown,
    isLoadingFriends,
    handleFriendSelect,
    handleRemoveSelectedFriend,
  } = useFriendsSelection(isFromActivity);
  
  const [isCreating, setIsCreating] = useState(false);

  const subtotal = billData.subtotal || (billData.total - billData.tax - billData.tip);

  // Wrapper functions to use billData context
  const calculateItemTotalWithContext = (itemPrice) => {
    return calculateItemTotal(itemPrice, subtotal, billData.tax, billData.tip);
  };

  const getMyItemsWithContext = () => {
    return getMyItems(billData.items, itemAssignments);
  };

  // Handle item claim with owed amount updates
  const handleItemClaimWithUpdate = async (itemId) => {
    const response = await handleToggleItemClaim(itemId);
    if (response && response.success) {
      // Update owed amounts from response immediately for instant feedback
      if (response.owedAmount !== undefined) {
        updateOwedAmounts(response.owedAmount, response.owedAmountExcludingPaid || response.owedAmount);
      }
    }
  };

  // Handle payment with proper success handling
  const handlePayWithAlert = async () => {
    const response = await handlePay();
    if (response && response.success) {
      const amountPaid = response.amountPaid || response.owedAmountExcludingPaid || 0;
      const message = `Payment successful! $${amountPaid.toFixed(2)} paid.`;
      Alert.alert('Success', message);
    } else if (response === null) {
      // User cancelled
      return;
    } else {
      // Payment failed
      const errorMessage = response?.message || 'Failed to process payment. Please try again.';
      Alert.alert('Payment Failed', errorMessage);
    }
  };

  // Get other participants and their paid items
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
                      const itemTotal = calculateItemTotalWithContext(item.price); // qty is always 1 for expanded items
                      participant.items.push(item.name);
                      participant.totalAmount += itemTotal;
                    }
    });
    
    setOtherParticipants(Array.from(participantsMap.values()));
  }, [itemPaymentInfo, isFromActivity, receiptId, currentUserId, billData.items]);

  // getInitials, getAvatarColor, and formatDisplayDate are now imported from utils/formatting.js

  const handleCreateAndShare = async () => {
    // Combine emails from both text input and selected friends, then deduplicate
    const textEmails = friendsEmails.split(',').map(e => e.trim()).filter(e => e);
    const allEmails = [...new Set([...selectedFriendEmails, ...textEmails])];
    
    if (allEmails.length === 0) {
      Alert.alert('Error', 'Please enter at least one friend\'s email address or select a friend');
      return;
    }

    setIsCreating(true);
    
    try {
      // If viewing from Activity and user is uploader, add participants to existing receipt
      if (isFromActivity && receiptId && isUploader) {
        const participantEmails = allEmails;
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
          participants: allEmails,
        };
        
        // Call API to create receipt
        console.log('Creating receipt with data:', JSON.stringify(receiptData, null, 2));
        const response = await createReceipt(receiptData);
        console.log('Receipt creation response:', response);
        
        if (response.success) {
          const participantsCount = (response.participantsAdded || 0) - 1; // Subtract 1 to exclude uploader
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

  const myItems = getMyItemsWithContext();
  const myItemsTotal = calculateMyItemsTotal(myItems, subtotal, billData.tax, billData.tip);

  return (
    <View style={styles.wrapper}>
      <BillReviewHeader
        restaurantName={billData.restaurant_name}
        date={billData.date}
        onBackPress={() => navigation.goBack()}
      />

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
        <ParticipantsList participants={otherParticipants} />

        <View style={styles.card}>
          <View style={styles.cardHeader}>
            <Text style={styles.cardTitle}>
              {isFromActivity ? 'Items' : `Items (${billData.items.length})`}
            </Text>
          </View>
          <View style={styles.cardContent}>
            <ItemsList
              items={billData.items}
              itemAssignments={itemAssignments}
              itemPaymentInfo={itemPaymentInfo}
              claimingItems={claimingItems}
              isFromActivity={isFromActivity}
              isReceiptComplete={isReceiptComplete}
              onItemPress={handleItemClaimWithUpdate}
            />
          </View>
        </View>

        <PaymentSection
          isFromActivity={isFromActivity}
          userHasPaid={userHasPaid}
          isReceiptComplete={isReceiptComplete}
          myItems={myItems}
          myItemsTotal={myItemsTotal}
          owedAmountExcludingPaid={owedAmountExcludingPaid}
          itemAssignments={itemAssignments}
          onPayPress={handlePayWithAlert}
          calculateItemTotal={calculateItemTotalWithContext}
        />

        {isFromActivity && isReceiptComplete && <CompletedIndicator />}

        <BillSummary
          subtotal={subtotal}
          tax={billData.tax}
          tip={billData.tip}
          total={billData.total}
          myItemsTotal={myItemsTotal}
          isFromActivity={isFromActivity}
          myItemsCount={myItems.length}
        />

        {!isFromActivity && (
          <FriendsInvite
            friends={friends}
            friendsEmails={friendsEmails}
            setFriendsEmails={setFriendsEmails}
            selectedFriendEmails={selectedFriendEmails}
            showFriendsDropdown={showFriendsDropdown}
            setShowFriendsDropdown={setShowFriendsDropdown}
            isLoadingFriends={isLoadingFriends}
            isCreating={isCreating}
            isFromCamera={isFromCamera}
            onFriendSelect={handleFriendSelect}
            onRemoveFriend={handleRemoveSelectedFriend}
            onCreateAndShare={handleCreateAndShare}
            onReScanReceipt={() => {
              console.log('Re-Scan Receipt pressed - navigating to ScanReceipt');
              navigation.replace('ScanReceipt');
            }}
            scrollViewRef={scrollViewRef}
          />
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
    backgroundColor: '#f0fdfa',
    borderRadius: 8,
    marginVertical: 2,
    borderLeftWidth: 3,
    borderLeftColor: '#0d9488',
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
  itemNameContainer: {
    flex: 1,
  },
  itemNameWithIcon: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  paidIcon: {
    marginRight: 2,
  },
  paidByText: {
    fontSize: typography.sizes.xs,
    color: '#14b8a6',
    fontWeight: '500',
    marginTop: 2,
    marginLeft: 20, // Align with item name (icon width + gap)
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
  completedCard: {
    backgroundColor: '#F0FDF4',
    borderRadius: 12,
    padding: spacing.lg,
    marginBottom: spacing.md,
    borderWidth: 1,
    borderColor: '#BBF7D0',
  },
  completedHeader: {
    alignItems: 'center',
  },
  completedBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    marginBottom: spacing.sm,
  },
  completedTitle: {
    fontSize: typography.sizes.lg,
    fontWeight: '700',
    color: '#059669',
  },
  completedSubtext: {
    fontSize: typography.sizes.sm,
    color: '#047857',
    textAlign: 'center',
    lineHeight: 20,
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
  friendsDropdownContainer: {
    marginBottom: spacing.md,
  },
  friendsDropdownButton: {
    backgroundColor: '#f9fafb',
    borderWidth: 1,
    borderColor: '#d1d5db',
    borderRadius: 8,
    padding: spacing.md,
  },
  friendsDropdownButtonContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.xs,
  },
  friendsDropdownButtonText: {
    flex: 1,
    fontSize: 16,
    fontWeight: '600',
    color: '#111827',
    marginLeft: spacing.xs,
  },
  friendsDropdownList: {
    marginTop: 8,
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#d1d5db',
    borderRadius: 8,
    maxHeight: 200,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  friendsDropdownLoading: {
    padding: spacing.md,
    alignItems: 'center',
    gap: spacing.xs,
  },
  friendsDropdownLoadingText: {
    fontSize: typography.sizes.sm,
    color: colors.textLight,
  },
  friendsDropdownEmpty: {
    padding: spacing.md,
    alignItems: 'center',
  },
  friendsDropdownEmptyText: {
    fontSize: typography.sizes.sm,
    fontWeight: '600',
    color: colors.textLight,
    marginBottom: 4,
  },
  friendsDropdownEmptySubtext: {
    fontSize: typography.sizes.xs,
    color: colors.textLight,
  },
  friendsDropdownScroll: {
    maxHeight: 200,
  },
  friendDropdownItem: {
    padding: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: '#f3f4f6',
  },
  friendDropdownItemSelected: {
    backgroundColor: '#ccfbf1',
  },
  friendDropdownItemContent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  friendDropdownAvatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  friendDropdownAvatarText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
  },
  friendDropdownInfo: {
    flex: 1,
  },
  friendDropdownName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#111827',
    marginBottom: 2,
  },
  friendDropdownEmail: {
    fontSize: 14,
    color: '#6B7280',
  },
  selectedFriendsContainer: {
    marginBottom: spacing.md,
  },
  selectedFriendsLabel: {
    fontSize: typography.sizes.sm,
    fontWeight: '600',
    color: '#111827',
    marginBottom: spacing.xs,
  },
  selectedFriendsChips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.xs,
  },
  friendChip: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ccfbf1',
    paddingHorizontal: spacing.sm,
    paddingVertical: 6,
    borderRadius: 16,
    gap: 4,
  },
  friendChipText: {
    fontSize: 14,
    fontWeight: '500',
    color: '#0d9488',
  },
  friendChipRemove: {
    marginLeft: 2,
  },
});
