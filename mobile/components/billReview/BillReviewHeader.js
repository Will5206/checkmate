/**
 * Header component for BillReview screen
 * Displays restaurant name, date, and back button
 */

import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { formatDisplayDate } from '../../utils/formatting';
import { colors, spacing } from '../../styles/theme';

export default function BillReviewHeader({ restaurantName, date, onBackPress }) {
  return (
    <View style={styles.headerContainer}>
      <TouchableOpacity 
        style={styles.backButton}
        onPress={onBackPress}
      >
        <Ionicons name="arrow-back" size={24} color="#111827" />
      </TouchableOpacity>
      <View style={styles.headerContent}>
        <Text style={styles.headerTitle}>{restaurantName}</Text>
        <View style={styles.headerDateRow}>
          <Ionicons name="time-outline" size={14} color="#6B7280" />
          <Text style={styles.headerDate}>
            {formatDisplayDate(date)} • {date.includes('PM') || date.includes('AM') ? date.split(' ').slice(-2).join(' ') : ''}
          </Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  headerContainer: {
    backgroundColor: '#fff',
    paddingHorizontal: 24,
    paddingTop: 48,
    paddingBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  backButton: {
    marginBottom: 12,
  },
  headerContent: {
    marginLeft: 0,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#111827',
    marginBottom: 4,
  },
  headerDateRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  headerDate: {
    fontSize: 14,
    color: '#6B7280',
  },
});






