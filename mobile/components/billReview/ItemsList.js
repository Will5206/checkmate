/**
 * Items list component for BillReview screen
 * Displays items with claiming functionality and payment status
 */

import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, spacing, typography } from '../../styles/theme';

export default function ItemsList({
  items,
  itemAssignments,
  itemPaymentInfo,
  claimingItems,
  isFromActivity,
  isReceiptComplete,
  onItemPress,
}) {
  if (items.length === 0) {
    return (
      <View style={styles.emptyItemsContainer}>
        <Text style={styles.emptyItemsText}>No items found</Text>
      </View>
    );
  }

  return (
    <>
      {items.map((item, index) => {
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
              onPress={isFromActivity && !isPaid && !isClaiming && !isReceiptComplete ? () => onItemPress(itemId) : undefined}
              disabled={!isFromActivity || isPaid || isClaiming || isReceiptComplete}
              activeOpacity={isFromActivity && !isPaid && !isClaiming ? 0.7 : 1}
            >
              <View style={styles.itemInfo}>
                <View style={styles.itemNameRow}>
                  <View style={styles.itemNameContainer}>
                    <View style={styles.itemNameWithIcon}>
                      {isPaid && (
                        <Ionicons name="checkmark-circle" size={14} color="#0d9488" style={styles.paidIcon} />
                      )}
                      <Text style={[styles.itemName, isPaid && styles.itemNamePaid]}>{item.name}</Text>
                    </View>
                    {isPaid && (
                      <Text style={styles.paidByText}>Paid by {payerName || 'Someone'}</Text>
                    )}
                    {!isPaid && isFromActivity && !isReceiptComplete && (
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
                    )}
                  </View>
                </View>
              </View>
              <Text style={[styles.itemPrice, isPaid && styles.itemPricePaid]}>
                ${item.price.toFixed(2)}
              </Text>
            </TouchableOpacity>
            {index < items.length - 1 && <View style={styles.separator} />}
          </View>
        );
      })}
    </>
  );
}

const styles = StyleSheet.create({
  itemRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.md,
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
  itemInfo: {
    flex: 1,
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
  itemName: {
    fontSize: typography.sizes.md,
    fontWeight: '600',
    color: colors.text,
  },
  itemNamePaid: {
    color: '#0d9488',
  },
  paidByText: {
    fontSize: typography.sizes.xs,
    color: '#14b8a6',
    fontWeight: '500',
    marginTop: 2,
    marginLeft: 20,
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
});






