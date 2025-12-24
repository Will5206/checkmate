/**
 * Completed receipt indicator component
 * Shows when all items have been paid for
 */

import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, spacing, typography } from '../../styles/theme';

export default function CompletedIndicator() {
  return (
    <View style={styles.completedCard}>
      <View style={styles.completedHeader}>
        <View style={styles.completedBadge}>
          <Ionicons name="checkmark-circle" size={24} color="#10B981" />
          <Text style={styles.completedTitle}>Receipt Completed</Text>
        </View>
        <Text style={styles.completedSubtext}>
          All items have been paid for. This receipt is now in your history.
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
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
});






