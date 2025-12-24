/**
 * Payment section component for BillReview screen
 * Displays payment summary, "Your Portion" section, and pay button
 */

import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, spacing, typography } from '../../styles/theme';

export default function PaymentSection({
  isFromActivity,
  userHasPaid,
  isReceiptComplete,
  myItems,
  myItemsTotal,
  owedAmountExcludingPaid,
  itemAssignments,
  onPayPress,
  calculateItemTotal,
}) {
  // Payment Summary Card (shown when user has paid)
  const PaymentSummaryCard = () => (
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
  );

  // What You Paid For section
  const WhatYouPaidFor = () => (
    <View style={styles.card}>
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle}>What You Paid For</Text>
      </View>
      <View style={styles.cardContent}>
        {myItems.map((item) => (
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
  );

  // Your Portion section (payment section)
  const YourPortion = () => (
    <View style={styles.card}>
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle}>Your Portion</Text>
      </View>
      <View style={styles.cardContent}>
        <View style={styles.breakdownRow}>
          <Text style={styles.totalLabelBold}>Amount Owed</Text>
          <Text style={styles.totalValueBold}>${owedAmountExcludingPaid.toFixed(2)}</Text>
        </View>
        {Object.keys(itemAssignments).length === 0 && (
          <Text style={styles.hintText}>Tap items above to claim them and calculate your portion</Text>
        )}
        {owedAmountExcludingPaid > 0.01 && (
          <TouchableOpacity style={styles.payButton} onPress={onPayPress}>
            <Ionicons name="card-outline" size={20} color="#fff" />
            <Text style={styles.payButtonText}>Pay ${owedAmountExcludingPaid.toFixed(2)}</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );

  return (
    <>
      {isFromActivity && userHasPaid && <PaymentSummaryCard />}
      {isFromActivity && myItems.length > 0 && <WhatYouPaidFor />}
      {isFromActivity && !userHasPaid && !isReceiptComplete && <YourPortion />}
    </>
  );
}

const styles = StyleSheet.create({
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
    fontSize: 18,
    fontWeight: '600',
    color: '#111827',
  },
  cardContent: {
    padding: spacing.md,
    paddingTop: 0,
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
  breakdownRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
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
  hintText: {
    fontSize: typography.sizes.sm,
    color: colors.textLight,
    fontStyle: 'italic',
    marginTop: spacing.sm,
    textAlign: 'center',
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
});






