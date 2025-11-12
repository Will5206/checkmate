import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import BottomNavBar from '../components/BottomNavBar';

export default function ActivityScreen() {
  const navigation = useNavigation();

  const pastTransactions = [
    {
      id: 4,
      restaurant: "Olive Garden",
      amount: 24.30,
      type: "payment",
      time: "2 days ago",
      status: "paid",
      participants: ["Sarah Johnson", "Mike Chen"],
      myItems: ["Caesar Salad", "Breadsticks"],
    },
    {
      id: 5,
      restaurant: "Pizza Hut",
      amount: 18.95,
      type: "payment",
      time: "3 days ago",
      status: "paid",
      participants: ["Emma Wilson", "Alex Rodriguez", "Jessica Kim"],
      myItems: ["Large Pizza (1/3)", "Garlic Bread"],
    },
    {
      id: 6,
      restaurant: "Subway",
      amount: 9.50,
      type: "payment",
      time: "1 week ago",
      status: "paid",
      participants: ["Sarah Johnson"],
      myItems: ["Footlong Turkey Sub", "Chips"],
    },
    {
      id: 7,
      restaurant: "Taco Bell",
      amount: 15.75,
      type: "payment",
      time: "1 week ago",
      status: "paid",
      participants: ["Mike Chen", "Emma Wilson"],
      myItems: ["Crunchwrap Supreme", "Nacho Fries", "Drink"],
    },
  ];

  const handlePastTransactionClick = (transaction) => {
    // Navigate to transaction detail screen
    // For now, we'll just log it - you can add navigation later
    console.log('Navigate to transaction detail:', transaction.id);
    // navigation.navigate('TransactionDetail', { transactionId: transaction.id });
  };

  const PastCard = ({ transaction }) => (
    <TouchableOpacity
      style={styles.pastCard}
      onPress={() => handlePastTransactionClick(transaction)}
      activeOpacity={0.7}
    >
      <View style={styles.cardContent}>
        <View style={styles.cardLeft}>
          <View style={styles.iconContainer}>
            <Ionicons name="checkmark-circle" size={20} color="#059669" />
          </View>
          <View style={styles.cardInfo}>
            <Text style={styles.restaurantName}>{transaction.restaurant}</Text>
            <View style={styles.timeRow}>
              <Ionicons name="time-outline" size={12} color="#6B7280" />
              <Text style={styles.timeText}>{transaction.time}</Text>
            </View>
            <Text style={styles.participantsText}>
              With {transaction.participants.length} other{transaction.participants.length === 1 ? '' : 's'}
            </Text>
          </View>
        </View>
        <View style={styles.cardRight}>
          <Text style={styles.amountText}>
            ${transaction.amount.toFixed(2)}
          </Text>
          <View style={styles.paidBadge}>
            <Text style={styles.paidBadgeText}>Paid</Text>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#9CA3AF" />
        </View>
      </View>
    </TouchableOpacity>
  );

  return (
    <View style={styles.wrapper}>
      <View style={styles.container}>
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Activity</Text>
          <Text style={styles.headerSubtitle}>Past transactions</Text>
        </View>

        {/* Content */}
        <ScrollView style={styles.scrollView} contentContainerStyle={styles.scrollContent}>
          {pastTransactions.map((transaction) => (
            <PastCard key={transaction.id} transaction={transaction} />
          ))}
        </ScrollView>
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
});

