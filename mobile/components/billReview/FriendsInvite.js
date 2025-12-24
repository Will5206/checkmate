/**
 * Friends invite component for BillReview screen
 * Handles friends selection, email input, and create/share functionality
 */

import React, { useRef } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { getInitials, getAvatarColor } from '../../utils/formatting';
import { colors, spacing, typography } from '../../styles/theme';

export default function FriendsInvite({
  friends,
  friendsEmails,
  setFriendsEmails,
  selectedFriendEmails,
  showFriendsDropdown,
  setShowFriendsDropdown,
  isLoadingFriends,
  isCreating,
  isFromCamera,
  onFriendSelect,
  onRemoveFriend,
  onCreateAndShare,
  onReScanReceipt,
  scrollViewRef,
}) {
  const emailInputRef = useRef(null);

  return (
    <View style={styles.card}>
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle}>Invite Friends</Text>
      </View>
      <View style={styles.inviteContent}>
        {/* Friends Dropdown */}
        <View style={styles.friendsDropdownContainer}>
          <TouchableOpacity
            style={styles.friendsDropdownButton}
            onPress={() => setShowFriendsDropdown(!showFriendsDropdown)}
            activeOpacity={0.7}
          >
            <View style={styles.friendsDropdownButtonContent}>
              <Ionicons name="people-outline" size={20} color="#0d9488" />
              <Text style={styles.friendsDropdownButtonText}>Friends</Text>
              <Ionicons 
                name={showFriendsDropdown ? "chevron-up" : "chevron-down"} 
                size={20} 
                color="#6B7280" 
              />
            </View>
          </TouchableOpacity>
          
          {showFriendsDropdown && (
            <View style={styles.friendsDropdownList}>
              {isLoadingFriends ? (
                <View style={styles.friendsDropdownLoading}>
                  <ActivityIndicator size="small" color="#0d9488" />
                  <Text style={styles.friendsDropdownLoadingText}>Loading friends...</Text>
                </View>
              ) : friends.length === 0 ? (
                <View style={styles.friendsDropdownEmpty}>
                  <Text style={styles.friendsDropdownEmptyText}>No friends yet</Text>
                  <Text style={styles.friendsDropdownEmptySubtext}>Add friends from the Friends tab</Text>
                </View>
              ) : (
                <ScrollView 
                  style={styles.friendsDropdownScroll}
                  nestedScrollEnabled={true}
                  showsVerticalScrollIndicator={true}
                >
                  {friends.map((friend, index) => {
                    const isSelected = selectedFriendEmails.includes(friend.email);
                    return (
                      <TouchableOpacity
                        key={friend.userId || index}
                        style={[
                          styles.friendDropdownItem,
                          isSelected && styles.friendDropdownItemSelected
                        ]}
                        onPress={() => onFriendSelect(friend.email)}
                        activeOpacity={0.7}
                      >
                        <View style={styles.friendDropdownItemContent}>
                          <View style={[styles.friendDropdownAvatar, { backgroundColor: getAvatarColor(friend.name) }]}>
                            <Text style={styles.friendDropdownAvatarText}>
                              {getInitials(friend.name)}
                            </Text>
                          </View>
                          <View style={styles.friendDropdownInfo}>
                            <Text style={styles.friendDropdownName}>{friend.name || 'Unknown'}</Text>
                            <Text style={styles.friendDropdownEmail}>{friend.email}</Text>
                          </View>
                          {isSelected && (
                            <Ionicons name="checkmark-circle" size={24} color="#0d9488" />
                          )}
                        </View>
                      </TouchableOpacity>
                    );
                  })}
                </ScrollView>
              )}
            </View>
          )}
        </View>

        {/* Selected Friends Chips */}
        {selectedFriendEmails.length > 0 && (
          <View style={styles.selectedFriendsContainer}>
            <Text style={styles.selectedFriendsLabel}>Selected:</Text>
            <View style={styles.selectedFriendsChips}>
              {selectedFriendEmails.map((email, index) => {
                const friend = friends.find(f => f.email === email);
                return (
                  <View key={email || index} style={styles.friendChip}>
                    <Text style={styles.friendChipText}>
                      {friend?.name || email}
                    </Text>
                    <TouchableOpacity
                      onPress={() => onRemoveFriend(email)}
                      style={styles.friendChipRemove}
                    >
                      <Ionicons name="close-circle" size={18} color="#6B7280" />
                    </TouchableOpacity>
                  </View>
                );
              })}
            </View>
          </View>
        )}

        <Text style={styles.inputLabel}>
          Or enter email addresses (separated by commas)
        </Text>
        <TextInput
          ref={emailInputRef}
          style={styles.input}
          placeholder="sarah@email.com, mike@email.com"
          placeholderTextColor={colors.textLight}
          value={friendsEmails}
          onChangeText={setFriendsEmails}
          multiline={false}
          autoCapitalize="none"
          keyboardType="email-address"
          returnKeyType="done"
          blurOnSubmit={true}
          onFocus={() => {
            setTimeout(() => {
              scrollViewRef.current?.scrollToEnd({ animated: true });
            }, 300);
          }}
        />
        
        <View style={styles.buttonContainer}>
          <TouchableOpacity
            style={[
              styles.primaryButton,
              (isCreating || (friendsEmails.trim().length === 0 && selectedFriendEmails.length === 0)) && styles.buttonDisabled
            ]}
            onPress={onCreateAndShare}
            disabled={isCreating || (friendsEmails.trim().length === 0 && selectedFriendEmails.length === 0)}
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
              onPress={onReScanReceipt}
            >
              <Ionicons name="camera-outline" size={20} color={colors.primary} style={styles.buttonIcon} />
              <Text style={styles.secondaryButtonText}>Re-Scan Receipt</Text>
            </TouchableOpacity>
          )}
        </View>
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
  inviteContent: {
    padding: spacing.md,
    paddingTop: 0,
  },
  friendsDropdownContainer: {
    marginBottom: spacing.md,
  },
  friendsDropdownButton: {
    backgroundColor: '#f9fafb',
    borderWidth: 1,
    borderColor: '#d1d5db',
    borderRadius: 8,
    padding: spacing.md,
  },
  friendsDropdownButtonContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.xs,
  },
  friendsDropdownButtonText: {
    flex: 1,
    fontSize: 16,
    fontWeight: '600',
    color: '#111827',
    marginLeft: spacing.xs,
  },
  friendsDropdownList: {
    marginTop: 8,
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#d1d5db',
    borderRadius: 8,
    maxHeight: 200,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  friendsDropdownLoading: {
    padding: spacing.md,
    alignItems: 'center',
    gap: spacing.xs,
  },
  friendsDropdownLoadingText: {
    fontSize: typography.sizes.sm,
    color: colors.textLight,
  },
  friendsDropdownEmpty: {
    padding: spacing.md,
    alignItems: 'center',
  },
  friendsDropdownEmptyText: {
    fontSize: typography.sizes.sm,
    fontWeight: '600',
    color: colors.textLight,
    marginBottom: 4,
  },
  friendsDropdownEmptySubtext: {
    fontSize: typography.sizes.xs,
    color: colors.textLight,
  },
  friendsDropdownScroll: {
    maxHeight: 200,
  },
  friendDropdownItem: {
    padding: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: '#f3f4f6',
  },
  friendDropdownItemSelected: {
    backgroundColor: '#ccfbf1',
  },
  friendDropdownItemContent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  friendDropdownAvatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  friendDropdownAvatarText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
  },
  friendDropdownInfo: {
    flex: 1,
  },
  friendDropdownName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#111827',
    marginBottom: 2,
  },
  friendDropdownEmail: {
    fontSize: 14,
    color: '#6B7280',
  },
  selectedFriendsContainer: {
    marginBottom: spacing.md,
  },
  selectedFriendsLabel: {
    fontSize: typography.sizes.sm,
    fontWeight: '600',
    color: '#111827',
    marginBottom: spacing.xs,
  },
  selectedFriendsChips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.xs,
  },
  friendChip: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ccfbf1',
    paddingHorizontal: spacing.sm,
    paddingVertical: 6,
    borderRadius: 16,
    gap: 4,
  },
  friendChipText: {
    fontSize: 14,
    fontWeight: '500',
    color: '#0d9488',
  },
  friendChipRemove: {
    marginLeft: 2,
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
    backgroundColor: '#0d9488',
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






