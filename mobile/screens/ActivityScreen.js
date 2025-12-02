import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useFocusEffect } from '@react-navigation/native';
import BottomNavBar from '../components/BottomNavBar';
import { getActivityReceipts } from '../services/receiptsService';

export default function ActivityScreen() {
  const navigation = useNavigation();
  const [receipts, setReceipts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const loadReceipts = async () => {
    try {
      setLoading(true);
      const response = await getActivityReceipts();
      
      if (response.success && response.receipts) {
        setReceipts(response.receipts);
      } else {
        console.error('Failed to load receipts:', response.message);
        setReceipts([]);
      }
    } catch (error) {
      console.error('Error loading receipts:', error);
      setReceipts([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

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

  const handleReceiptClick = (receipt) => {
    console.log('ActivityScreen: Receipt clicked:', {
      receiptId: receipt.receiptId,
      merchantName: receipt.merchantName,
      itemsCount: receipt.items ? receipt.items.length : 0,
      items: receipt.items,
    });
    
    // Transform receipt data to match BillReview format
    const billData = {
      merchant: receipt.merchantName || 'Unknown Merchant',
      total: receipt.totalAmount || 0,
      subtotal: (receipt.totalAmount || 0) - (receipt.taxAmount || 0) - (receipt.tipAmount || 0),
      tax: receipt.taxAmount || 0,
      tip: receipt.tipAmount || 0,
      items: (receipt.items || []).map(item => ({
        itemId: item.itemId, // Use actual itemId from backend
        id: item.itemId, // Also set id for compatibility
        name: item.name,
        qty: item.quantity || item.qty || 1, // Support both quantity and qty
        price: item.price || 0,
      })),
      date: receipt.date ? new Date(receipt.date).toLocaleString() : 'Unknown date',
    };
    
    console.log('ActivityScreen: Transformed billData:', {
      itemsCount: billData.items.length,
      items: billData.items,
    });
    
    // Navigate to BillReview screen with receipt data
    navigation.navigate('BillReview', { 
      data: billData,
      receiptId: receipt.receiptId,
      uploadedBy: receipt.uploadedBy, // Pass uploadedBy to check if user is uploader
      isFromActivity: true, // Flag to indicate this is from activity (enables item claiming)
      userHasPaid: receipt.userHasPaid || false, // Pass payment status
    });
  };

  const ReceiptCard = ({ receipt }) => {
    const status = receipt.status || 'pending';
    const isCompleted = status === 'completed';
    const isAccepted = status === 'accepted' || isCompleted;
    const userHasPaid = receipt.userHasPaid || false; // Check if current user has paid
    const itemCount = receipt.items ? receipt.items.length : 0;
    
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
                color={isAccepted ? "#059669" : "#6B7280"} 
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
            <Text style={styles.amountText}>
              ${(receipt.totalAmount || 0).toFixed(2)}
            </Text>
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
      </TouchableOpacity>
    );
  };

  return (
    <View style={styles.wrapper}>
      <View style={styles.container}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Activity</Text>
          <Text style={styles.headerSubtitle}>Past transactions</Text>
        </View>

        {/* Content */}
        {loading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#059669" />
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
            {receipts.map((receipt) => (
              <ReceiptCard key={receipt.receiptId} receipt={receipt} />
            ))}
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
    backgroundColor: '#D1FAE5',
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
  participantsText: {
    fontSize: 12,
    color: '#9CA3AF',
    marginTop: 2,
  },
  cardRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  amountText: {
    fontSize: 18,
    fontWeight: '700',
    color: '#111827',
    marginRight: 8,
  },
  paidBadge: {
    backgroundColor: '#D1FAE5',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
    marginRight: 8,
  },
  paidBadgeText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#059669',
  },
  pendingBadge: {
    backgroundColor: '#FEF3C7',
  },
  pendingBadgeText: {
    color: '#D97706',
  },
  completedBadge: {
    backgroundColor: '#D1FAE5',
  },
  completedBadgeText: {
    color: '#059669',
    fontWeight: '600',
  },
  paidStatusBadge: {
    backgroundColor: '#D1FAE5',
  },
  paidStatusBadgeText: {
    color: '#059669',
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

