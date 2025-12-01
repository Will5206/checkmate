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

  // Check for parsed receipt data from route params or AsyncStorage
  useEffect(() => {
    // First check route params (from HomeScreen navigation)
    const routeData = route.params?.data;
    if (routeData) {
      try {
        // Transform backend data to match our format
        const transformedData = transformReceiptData(routeData);
        setBillData(transformedData);
        setIsFromCamera(true);
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

  // Transform backend receipt data to match our expected format
  const transformReceiptData = (data) => {
    // Backend parser returns: items (with name, qty, price), subtotal, tax, total, merchant
    const items = (data.items || []).map((item, index) => ({
      id: index + 1,
      name: item.name || 'Unknown Item',
      price: parseFloat(item.price) || 0,
      qty: item.qty || 1,
    }));

    const total = parseFloat(data.total) || 0;
    const tax = parseFloat(data.tax) || 0;
    const tip = parseFloat(data.tip) || 0;
    const subtotal = parseFloat(data.subtotal) || 0;
    
    // Calculate subtotal if not provided
    const calculatedSubtotal = subtotal || (items.reduce((sum, item) => sum + item.price, 0));
    
    // Calculate tip if not provided (as difference between total and subtotal + tax)
    const calculatedTip = tip || Math.max(0, total - calculatedSubtotal - tax);

    return {
      restaurant_name: data.merchant || data.restaurant_name || 'Unknown Merchant',
      date: data.date || new Date().toLocaleDateString(),
      items: items,
      tax: tax,
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
      // Generate a simple share code
      const shareCode = Math.random().toString(36).substr(2, 8).toUpperCase();
      
      const billToCreate = {
        restaurant_name: billData.restaurant_name,
        total_amount: billData.total,
        tax: billData.tax,
        tip: billData.tip,
        subtotal: subtotal,
        items: billData.items.map((item) => ({
          name: item.name,
          price: item.price,
          qty: item.qty || 1,
          claimed_by: null
        })),
        participants: friendsEmails.split(',').map(email => email.trim()).filter(email => email),
        status: "pending",
        share_code: shareCode
      };
      
      // TODO: Replace with actual API call to create bill
      // const createdBill = await createBill(billToCreate);
      console.log('Bill to create:', billToCreate);
      
      Alert.alert('Success', 'Bill created successfully!', [
        {
          text: 'OK',
          onPress: () => navigation.navigate('Home'),
        },
      ]);
    } catch (error) {
      console.error("Error creating bill:", error);
      Alert.alert('Error', 'Failed to create bill. Please try again.');
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
            <TouchableOpacity>
              <Ionicons name="create-outline" size={20} color={colors.primary} />
            </TouchableOpacity>
          </View>
          <View style={styles.itemsList}>
            {billData.items.map((item, index) => (
              <View key={item.id || index}>
                <View style={styles.itemRow}>
                  <View style={styles.itemInfo}>
                    <Text style={styles.itemName}>{item.name}</Text>
                    {item.qty > 1 && (
                      <Text style={styles.itemQty}>Qty: {item.qty}</Text>
                    )}
                  </View>
                  <Text style={styles.itemPrice}>${item.price.toFixed(2)}</Text>
                </View>
                {index < billData.items.length - 1 && <View style={styles.separator} />}
              </View>
            ))}
          </View>
        </View>

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

        {/* Invite Friends */}
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
  itemQty: {
    fontSize: typography.sizes.sm,
    color: colors.textLight,
    marginTop: spacing.xs,
  },
  itemPrice: {
    fontSize: typography.sizes.md,
    fontWeight: '600',
    color: colors.text,
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
});
