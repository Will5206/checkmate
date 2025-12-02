import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  RefreshControl,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import BottomNavBar from '../components/BottomNavBar';
import { getPendingReceipts, acceptReceipt, declineReceipt } from '../services/receiptsService';

export default function PendingScreen() {
  const navigation = useNavigation();
  const [receipts, setReceipts] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [processingIds, setProcessingIds] = useState(new Set());

  useEffect(() => {
    loadPendingReceipts();
  }, []);

  const loadPendingReceipts = async () => {
    setIsLoading(true);
    try {
      const response = await getPendingReceipts();
      
      if (response.success) {
        setReceipts(response.receipts || []);
      } else {
        Alert.alert('Error', response.message || 'Failed to load pending receipts');
      }
    } catch (error) {
      console.error('Error loading pending receipts:', error);
      Alert.alert('Error', 'Failed to load pending receipts');
    } finally {
      setIsLoading(false);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadPendingReceipts();
    setRefreshing(false);
  };

  const handleReview = (receipt) => {
    // Transform receipt data to match BillReview format
    const billData = {
      merchant: receipt.merchantName || 'Unknown Merchant',
      total: receipt.totalAmount || 0,
      subtotal: (receipt.totalAmount || 0) - (receipt.taxAmount || 0) - (receipt.tipAmount || 0),
      tax: receipt.taxAmount || 0,
      tip: receipt.tipAmount || 0,
      items: (receipt.items || []).map(item => ({
        itemId: item.itemId,
        id: item.itemId,
        name: item.name,
        qty: item.quantity || item.qty || 1,
        price: item.price || 0,
      })),
      date: receipt.date ? new Date(receipt.date).toLocaleString() : 'Unknown date',
    };
    
    // Navigate to BillReview screen with receipt data
    navigation.navigate('BillReview', { 
      data: billData,
      receiptId: receipt.receiptId,
      isFromActivity: true, // Enable item claiming
    });
  };

  const handleAccept = async (receiptId) => {
    setProcessingIds(prev => new Set(prev).add(receiptId));
    
    try {
      const response = await acceptReceipt(receiptId);
      
      if (response.success) {
        Alert.alert('Success', 'Receipt accepted!');
        // Remove from list
        setReceipts(prev => prev.filter(r => r.receiptId !== receiptId));
      } else {
        Alert.alert('Error', response.message || 'Failed to accept receipt');
      }
    } catch (error) {
      console.error('Error accepting receipt:', error);
      Alert.alert('Error', 'Failed to accept receipt');
    } finally {
      setProcessingIds(prev => {
        const next = new Set(prev);
        next.delete(receiptId);
        return next;
      });
    }
  };

  const handleDecline = async (receiptId) => {
    Alert.alert(
      'Decline Receipt',
      'Are you sure you want to decline this receipt?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Decline',
          style: 'destructive',
          onPress: async () => {
            setProcessingIds(prev => new Set(prev).add(receiptId));
            
            try {
              const response = await declineReceipt(receiptId);
              
              if (response.success) {
                Alert.alert('Success', 'Receipt declined');
                // Remove from list
                setReceipts(prev => prev.filter(r => r.receiptId !== receiptId));
              } else {
                Alert.alert('Error', response.message || 'Failed to decline receipt');
              }
            } catch (error) {
              console.error('Error declining receipt:', error);
              Alert.alert('Error', 'Failed to decline receipt');
            } finally {
              setProcessingIds(prev => {
                const next = new Set(prev);
                next.delete(receiptId);
                return next;
              });
            }
          },
        },
      ]
    );
  };

  const formatDate = (timestamp) => {
    if (!timestamp) return 'Unknown date';
    
    const date = new Date(timestamp);
    const now = new Date();
    const diffTime = Math.abs(now - date);
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    
    if (diffDays === 1) {
      return 'Today';
    } else if (diffDays === 2) {
      return 'Yesterday';
    } else if (diffDays <= 7) {
      return `${diffDays - 1} days ago`;
    } else {
      return date.toLocaleDateString();
    }
  };

  const PendingReceiptCard = ({ receipt }) => {
    const isProcessing = processingIds.has(receipt.receiptId);
    const itemCount = receipt.items?.length || 0;

    return (
      <View style={styles.receiptCard}>
        <View style={styles.cardHeader}>
          <View style={styles.cardHeaderLeft}>
            <View style={styles.iconContainer}>
              <Ionicons name="receipt-outline" size={20} color="#F59E0B" />
            </View>
            <View style={styles.cardInfo}>
              <Text style={styles.merchantName}>{receipt.merchantName || 'Unknown Merchant'}</Text>
              <View style={styles.timeRow}>
                <Ionicons name="time-outline" size={12} color="#6B7280" />
                <Text style={styles.timeText}>{formatDate(receipt.date)}</Text>
              </View>
              <Text style={styles.itemCountText}>
                {itemCount} item{itemCount !== 1 ? 's' : ''}
              </Text>
            </View>
          </View>
          <View style={styles.amountContainer}>
            <Text style={styles.amountText}>
              ${receipt.totalAmount?.toFixed(2) || '0.00'}
            </Text>
          </View>
        </View>

        {receipt.items && receipt.items.length > 0 && (
          <View style={styles.itemsPreview}>
            {receipt.items.slice(0, 3).map((item, index) => (
              <Text key={index} style={styles.itemPreviewText}>
                • {item.name} ${item.price?.toFixed(2)}
              </Text>
            ))}
            {receipt.items.length > 3 && (
              <Text style={styles.itemPreviewText}>
                +{receipt.items.length - 3} more item{receipt.items.length - 3 !== 1 ? 's' : ''}
              </Text>
            )}
          </View>
        )}

        <View style={styles.actionButtons}>
          <TouchableOpacity
            style={[styles.reviewButton, isProcessing && styles.buttonDisabled]}
            onPress={() => handleReview(receipt)}
            disabled={isProcessing}
          >
            <Ionicons name="eye-outline" size={18} color="#059669" />
            <Text style={styles.reviewButtonText}>Review</Text>
          </TouchableOpacity>
          
          <TouchableOpacity
            style={[styles.declineButton, isProcessing && styles.buttonDisabled]}
            onPress={() => handleDecline(receipt.receiptId)}
            disabled={isProcessing}
          >
            {isProcessing ? (
              <ActivityIndicator size="small" color="#DC2626" />
            ) : (
              <>
                <Ionicons name="close-circle-outline" size={18} color="#DC2626" />
                <Text style={styles.declineButtonText}>Decline</Text>
              </>
            )}
          </TouchableOpacity>
          
          <TouchableOpacity
            style={[styles.acceptButton, isProcessing && styles.buttonDisabled]}
            onPress={() => handleAccept(receipt.receiptId)}
            disabled={isProcessing}
          >
            {isProcessing ? (
              <ActivityIndicator size="small" color="#fff" />
            ) : (
              <>
                <Ionicons name="checkmark-circle-outline" size={18} color="#fff" />
                <Text style={styles.acceptButtonText}>Accept</Text>
              </>
            )}
          </TouchableOpacity>
        </View>
      </View>
    );
  };

  return (
    <View style={styles.wrapper}>
      <View style={styles.container}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Pending</Text>
          <Text style={styles.headerSubtitle}>
            {receipts.length} receipt{receipts.length !== 1 ? 's' : ''} waiting for your response
          </Text>
        </View>

        {/* Content */}
        {isLoading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#059669" />
            <Text style={styles.loadingText}>Loading receipts...</Text>
          </View>
        ) : receipts.length === 0 ? (
          <ScrollView
            contentContainerStyle={styles.emptyContainer}
            refreshControl={
              <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
            }
          >
            <Ionicons name="receipt-outline" size={64} color="#D1D5DB" />
            <Text style={styles.emptyText}>No pending receipts</Text>
            <Text style={styles.emptySubtext}>
              Receipts shared with you will appear here
            </Text>
            <TouchableOpacity
              style={styles.refreshButton}
              onPress={onRefresh}
            >
              <Text style={styles.refreshButtonText}>Refresh</Text>
            </TouchableOpacity>
          </ScrollView>
        ) : (
          <ScrollView
            style={styles.scrollView}
            contentContainerStyle={styles.scrollContent}
            refreshControl={
              <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
            }
          >
            {receipts.map((receipt) => (
              <PendingReceiptCard key={receipt.receiptId} receipt={receipt} />
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
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 14,
    color: '#6B7280',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 24,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  emptyText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#374151',
    marginTop: 16,
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
    marginBottom: 24,
  },
  refreshButton: {
    backgroundColor: '#059669',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  refreshButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  receiptCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    marginBottom: 16,
    padding: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 3,
    elevation: 2,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  cardHeaderLeft: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    flex: 1,
  },
  iconContainer: {
    width: 40,
    height: 40,
    backgroundColor: '#FEF3C7',
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  cardInfo: {
    flex: 1,
  },
  merchantName: {
    fontSize: 18,
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
  itemCountText: {
    fontSize: 12,
    color: '#9CA3AF',
    marginTop: 2,
  },
  amountContainer: {
    alignItems: 'flex-end',
  },
  amountText: {
    fontSize: 20,
    fontWeight: '700',
    color: '#111827',
  },
  itemsPreview: {
    backgroundColor: '#F9FAFB',
    borderRadius: 8,
    padding: 12,
    marginBottom: 12,
  },
  itemPreviewText: {
    fontSize: 13,
    color: '#6B7280',
    marginBottom: 4,
  },
  actionButtons: {
    flexDirection: 'row',
    gap: 12,
  },
  reviewButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#D1FAE5',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    gap: 6,
  },
  reviewButtonText: {
    color: '#059669',
    fontSize: 14,
    fontWeight: '600',
  },
  declineButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FEE2E2',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    gap: 6,
  },
  declineButtonText: {
    color: '#DC2626',
    fontSize: 14,
    fontWeight: '600',
  },
  acceptButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#059669',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    gap: 6,
  },
  acceptButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  buttonDisabled: {
    opacity: 0.6,
  },
});
