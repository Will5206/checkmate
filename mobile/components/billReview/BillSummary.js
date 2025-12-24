/**
 * Bill summary component for BillReview screen
 * Displays subtotal, tax, tip, total, and user's share
 */

import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors, spacing, typography } from '../../styles/theme';

export default function BillSummary({
  subtotal,
  tax,
  tip,
  total,
  myItemsTotal,
  isFromActivity,
  myItemsCount,
}) {
  return (
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
          <Text style={styles.breakdownValue}>${(tax + tip).toFixed(2)}</Text>
        </View>
        <View style={styles.separator} />
        <View style={styles.breakdownRow}>
          <Text style={styles.totalLabelBold}>Total Bill</Text>
          <Text style={styles.totalValueBold}>${total.toFixed(2)}</Text>
        </View>
        {isFromActivity && myItemsCount > 0 && (
          <View style={styles.breakdownRow}>
            <Text style={styles.yourShareLabel}>Your Share</Text>
            <Text style={styles.yourShareValue}>${myItemsTotal.toFixed(2)}</Text>
          </View>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
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
  separator: {
    height: 1,
    backgroundColor: colors.border,
    marginLeft: spacing.md,
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
});






