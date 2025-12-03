import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useFocusEffect } from '@react-navigation/native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import BottomNavBar from '../components/BottomNavBar';
import { getActivityReceipts, getReceiptDetails } from '../services/receiptsService';

export default function ActivityScreen() {
  const navigation = useNavigation();
  const [receipts, setReceipts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [currentUserId, setCurrentUserId] = useState(null);

  const loadReceipts = async () => {
    try {
      console.log('[ActivityScreen] STEP 1: Starting loadReceipts');
      setLoading(true);
      
      console.log('[ActivityScreen] STEP 2: Calling getActivityReceipts()');
      const response = await getActivityReceipts();
      
      console.log('[ActivityScreen] STEP 3: Received response from API:', {
        success: response.success,
        receiptsCount: response.receipts ? response.receipts.length : 0,
        hasReceipts: !!response.receipts,
        receipts: response.receipts ? response.receipts.map(r => ({ id: r.receiptId, merchant: r.merchantName })) : null
      });
      
      if (response.success && response.receipts) {
        console.log('[ActivityScreen] STEP 4: Response is successful, receipts array length:', response.receipts.length);
        console.log('[ActivityScreen] STEP 5: About to setReceipts with', response.receipts.length, 'receipts');
        console.log('[ActivityScreen] STEP 6: Receipt IDs:', response.receipts.map(r => r.receiptId));
        setReceipts(response.receipts);
        console.log('[ActivityScreen] STEP 7: setReceipts called, state should update');
      } else {
        console.error('[ActivityScreen] ERROR: Response failed or no receipts:', response.message);
        setReceipts([]);
      }
    } catch (error) {
      console.error('[ActivityScreen] EXCEPTION: Error loading receipts:', error);
      setReceipts([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
      console.log('[ActivityScreen] STEP 8: loadReceipts completed, loading set to false');
    }
  };

  // Load current user ID
  useEffect(() => {
    const loadCurrentUserId = async () => {
      const userId = await AsyncStorage.getItem('userId');
      setCurrentUserId(userId);
    };
    loadCurrentUserId();
  }, []);

  // Log whenever receipts state changes
  useEffect(() => {
    console.log('[ActivityScreen] STEP 12: Receipts state changed, new count:', receipts.length);
    console.log('[ActivityScreen] STEP 13: Receipt IDs in state:', receipts.map(r => r.receiptId));
  }, [receipts]);

  // Load receipts when screen comes into focus
  useFocusEffect(
    React.useCallback(() => {
      loadReceipts();
    }, [])
  );

  const onRefresh = () => {
    setRefreshing(true);
    loadReceipts();
  };

  const formatDate = (timestamp) => {
    if (!timestamp) return 'Unknown date';
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now - date;
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    
    if (diffDays === 0) return 'Today';
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays} days ago`;
    if (diffDays < 30) return `${Math.floor(diffDays / 7)} week${Math.floor(diffDays / 7) > 1 ? 's' : ''} ago`;
    return date.toLocaleDateString();
  };

  const handleReceiptClick = async (receipt) => {
    console.log('[ActivityScreen] Receipt clicked, fetching full details for receipt:', receipt.receiptId);
    
    try {
      // Fetch full receipt details with items
      const response = await getReceiptDetails(receipt.receiptId);
      
      if (!response.success || !response.receipt) {
        console.error('[ActivityScreen] Failed to fetch receipt details:', response.message);
        Alert.alert('Error', 'Failed to load receipt details');
        return;
      }
      
      const fullReceipt = response.receipt;
      console.log('[ActivityScreen] Received full receipt with', fullReceipt.items?.length || 0, 'items');
      
      // Transform receipt data to match BillReview format
      const billData = {
        merchant: fullReceipt.merchantName || 'Unknown Merchant',
        total: fullReceipt.totalAmount || 0,
        subtotal: (fullReceipt.totalAmount || 0) - (fullReceipt.taxAmount || 0) - (fullReceipt.tipAmount || 0),
        tax: fullReceipt.taxAmount || 0,
        tip: fullReceipt.tipAmount || 0,
        items: (fullReceipt.items || []).map(item => ({
          itemId: item.itemId, // Use actual itemId from backend
          id: item.itemId, // Also set id for compatibility
          name: item.name,
          qty: item.quantity || item.qty || 1, // Support both quantity and qty
          price: item.price || 0,
        })),
        date: fullReceipt.date ? new Date(fullReceipt.date).toLocaleString() : 'Unknown date',
      };
      
      console.log('[ActivityScreen] Transformed billData with', billData.items.length, 'items');
      
      // Navigate to BillReview screen with full receipt data
      navigation.navigate('BillReview', { 
        data: billData,
        receiptId: fullReceipt.receiptId,
        uploadedBy: fullReceipt.uploadedBy, // Pass uploadedBy to check if user is uploader
        isFromActivity: true, // Flag to indicate this is from activity (enables item claiming)
        userHasPaid: receipt.userHasPaid || false, // Pass payment status from list view
      });
    } catch (error) {
      console.error('[ActivityScreen] Error fetching receipt details:', error);
      Alert.alert('Error', 'Failed to load receipt details');
    }
  };

  const ReceiptCard = ({ receipt }) => {
    const status = receipt.status || 'pending';
    const isCompleted = status === 'completed';
    const isAccepted = status === 'accepted' || isCompleted;
    const userHasPaid = receipt.userHasPaid || false; // Check if current user has paid
    const itemCount = receipt.items ? receipt.items.length : 0;
    
    // Determine sender display text
    const isUploader = currentUserId && receipt.uploadedBy && currentUserId === receipt.uploadedBy;
    const senderName = isUploader ? 'You' : (receipt.uploadedByName || 'Unknown');
    
    return (
      <TouchableOpacity
        style={styles.pastCard}
        onPress={() => handleReceiptClick(receipt)}
        activeOpacity={0.7}
      >
        <View style={styles.cardContent}>
          <View style={styles.cardLeft}>
            <View style={styles.iconContainer}>
              <Ionicons 
                name={isAccepted ? "checkmark-circle" : "time-outline"} 
                size={20} 
                color={isAccepted ? "#0d9488" : "#6B7280"} 
              />
            </View>
            <View style={styles.cardInfo}>
              <Text style={styles.restaurantName}>
                {receipt.merchantName || 'Unknown Merchant'}
              </Text>
              <View style={styles.timeRow}>
                <Ionicons name="time-outline" size={12} color="#6B7280" />
                <Text style={styles.timeText}>
                  {formatDate(receipt.date || receipt.createdAt)}
                </Text>
              </View>
              <Text style={styles.participantsText}>
                {itemCount} item{itemCount !== 1 ? 's' : ''}
              </Text>
            </View>
          </View>
          <View style={styles.cardRight}>
            <View style={styles.cardRightContent}>
              <View style={styles.amountSection}>
                <Text style={styles.amountText}>
                  ${(receipt.totalAmount || 0).toFixed(2)}
                </Text>
                <View style={styles.senderRow}>
                  <Ionicons name="person-outline" size={10} color="#9CA3AF" />
                  <Text style={styles.senderText}>
                    {isUploader ? 'You' : senderName}
                  </Text>
                </View>
              </View>
              <View style={styles.statusSection}>
                <View style={[
                  styles.paidBadge, 
                  !isAccepted && !userHasPaid && styles.pendingBadge,
                  isCompleted && styles.completedBadge,
                  userHasPaid && !isCompleted && styles.paidStatusBadge
                ]}>
                  <Text style={[
                    styles.paidBadgeText, 
                    !isAccepted && !userHasPaid && styles.pendingBadgeText,
                    isCompleted && styles.completedBadgeText,
                    userHasPaid && !isCompleted && styles.paidStatusBadgeText
                  ]}>
                    {isCompleted ? 'Completed' : userHasPaid ? 'Paid' : isAccepted ? 'Accepted' : 'Pending'}
                  </Text>
                </View>
                <Ionicons name="chevron-forward" size={20} color="#9CA3AF" />
              </View>
            </View>
          </View>
        </View>
      </TouchableOpacity>
    );
  };

  return (
    <View style={styles.wrapper}>
      <View style={styles.container}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.headerTitle}>History</Text>
          <Text style={styles.headerSubtitle}>Past transactions</Text>
        </View>

        {/* Content */}
        {loading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#0d9488" />
            <Text style={styles.loadingText}>Loading receipts...</Text>
          </View>
        ) : receipts.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Ionicons name="receipt-outline" size={64} color="#9CA3AF" />
            <Text style={styles.emptyText}>No receipts yet</Text>
            <Text style={styles.emptySubtext}>
              Accepted receipts will appear here
            </Text>
          </View>
        ) : (
          <ScrollView 
            style={styles.scrollView} 
            contentContainerStyle={styles.scrollContent}
            refreshControl={
              <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
            }
          >
            {(() => {
              console.log('[ActivityScreen] STEP 9: Rendering receipts, count:', receipts.length);
              console.log('[ActivityScreen] STEP 10: Receipt IDs to render:', receipts.map(r => r.receiptId));
              return receipts.map((receipt, index) => {
                console.log(`[ActivityScreen] STEP 11: Rendering receipt ${index + 1}/${receipts.length}, ID: ${receipt.receiptId}, merchant: ${receipt.merchantName}`);
                return <ReceiptCard key={receipt.receiptId} receipt={receipt} />;
              });
            })()}
          </ScrollView>
        )}
      </View>
      <BottomNavBar />
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    flex: 1,
    backgroundColor: '#F9FAFB',
  },
  container: {
    flex: 1,
    paddingBottom: 100, // Space for bottom nav bar
  },
  header: {
    backgroundColor: '#fff',
    paddingHorizontal: 24,
    paddingTop: 48,
    paddingBottom: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: '700',
    color: '#111827',
    marginBottom: 4,
  },
  headerSubtitle: {
    fontSize: 14,
    color: '#6B7280',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 24,
  },
  pastCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 3,
    elevation: 2,
  },
  cardContent: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
  },
  cardLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  iconContainer: {
    width: 40,
    height: 40,
    backgroundColor: '#ccfbf1',
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  cardInfo: {
    flex: 1,
  },
  restaurantName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#111827',
    marginBottom: 4,
  },
  timeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginBottom: 4,
  },
  timeText: {
    fontSize: 14,
    color: '#6B7280',
  },
  senderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    marginTop: 1,
  },
  senderText: {
    fontSize: 11,
    color: '#9CA3AF',
    fontWeight: '400',
    lineHeight: 14,
  },
  participantsText: {
    fontSize: 12,
    color: '#9CA3AF',
    marginTop: 2,
  },
  cardRight: {
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  cardRightContent: {
    alignItems: 'flex-end',
    gap: 8,
  },
  amountSection: {
    alignItems: 'flex-end',
  },
  amountText: {
    fontSize: 20,
    fontWeight: '700',
    color: '#111827',
    lineHeight: 24,
    marginBottom: 2,
  },
  statusSection: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  paidBadge: {
    backgroundColor: '#ccfbf1',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  paidBadgeText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#0d9488',
  },
  pendingBadge: {
    backgroundColor: '#FEF3C7',
  },
  pendingBadgeText: {
    color: '#D97706',
  },
  completedBadge: {
    backgroundColor: '#ccfbf1',
  },
  completedBadgeText: {
    color: '#0d9488',
    fontWeight: '600',
  },
  paidStatusBadge: {
    backgroundColor: '#ccfbf1',
  },
  paidStatusBadgeText: {
    color: '#0d9488',
    fontWeight: '600',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 100,
  },
  loadingText: {
    marginTop: 12,
    fontSize: 14,
    color: '#6B7280',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 100,
  },
  emptyText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#111827',
    marginTop: 16,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 8,
  },
});

