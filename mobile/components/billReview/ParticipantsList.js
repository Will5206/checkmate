/**
 * Participants list component for BillReview screen
 * Displays other people who have paid for items
 */

import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { getInitials, getAvatarColor } from '../../utils/formatting';
import { colors, spacing, typography } from '../../styles/theme';

export default function ParticipantsList({ participants }) {
  if (!participants || participants.length === 0) {
    return null;
  }

  return (
    <View style={styles.card}>
      <View style={styles.cardHeader}>
        <View style={styles.cardHeaderWithIcon}>
          <Ionicons name="people-outline" size={20} color="#111827" />
          <Text style={styles.cardTitle}>
            Other People ({participants.length})
          </Text>
        </View>
      </View>
      <View style={styles.cardContent}>
        {participants.map((participant, index) => (
          <View key={participant.userId || index}>
            <View style={styles.participantRow}>
              <View style={styles.participantLeft}>
                <View style={[styles.avatar, { backgroundColor: getAvatarColor(participant.name) }]}>
                  <Text style={styles.avatarText}>{participant.initials}</Text>
                </View>
                <View style={styles.participantInfo}>
                  <Text style={styles.participantName}>{participant.name}</Text>
                  <Text style={styles.participantAmount}>${participant.totalAmount.toFixed(2)}</Text>
                </View>
              </View>
            </View>
            <View style={styles.participantItems}>
              {participant.items.map((itemName, itemIndex) => (
                <Text key={itemIndex} style={styles.participantItemText}>
                  • {itemName}
                </Text>
              ))}
            </View>
            {index < participants.length - 1 && <View style={styles.separator} />}
          </View>
        ))}
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
    justifyContent: 'center',
    alignItems: 'center',
  },
  avatarText: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.white,
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
  separator: {
    height: 1,
    backgroundColor: colors.border,
    marginLeft: spacing.md,
  },
});

