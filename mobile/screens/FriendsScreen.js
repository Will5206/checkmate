import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
  Alert,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  RefreshControl,
} from 'react-native';
import { addFriendByEmail, getFriendsList, removeFriend } from '../services/friendsService';
import BottomNavBar from '../components/BottomNavBar';

export default function FriendsScreen() {
  const [email, setEmail] = useState('');
  const [friends, setFriends] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingFriends, setIsLoadingFriends] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [removingFriendId, setRemovingFriendId] = useState(null);

  // Load friends list on component mount
  useEffect(() => {
    loadFriends();
  }, []);

  const loadFriends = async () => {
    setIsLoadingFriends(true);
    const response = await getFriendsList();

    if (response.success) {
      // Transform friendships data into displayable format
      const friendsList = response.friends || [];
      setFriends(friendsList);
    } else {
      Alert.alert('Error', response.message || 'Failed to load friends');
    }

    setIsLoadingFriends(false);
  };

  const handleAddFriend = async () => {
    console.log('handleAddFriend called with email:', email);
    setError(null); // Clear previous errors
    setSuccess(null); // Clear previous success

    // Validate email input
    if (!email.trim()) {
      setError('Please enter an email address');
      return;
    }

    // Basic email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email.trim())) {
      setError('Please enter a valid email address');
      return;
    }

    setIsLoading(true);
    console.log('Calling addFriendByEmail...');

    const response = await addFriendByEmail(email.trim().toLowerCase());

    console.log('Response from addFriendByEmail:', response);
    setIsLoading(false);

    if (response.success) {
      setError(null);
      if (response.added) {
        setSuccess(`✅ Friend added! ${response.friendName} is now your friend`);
        loadFriends(); // Refresh friends list

        // Clear success message and email after 3 seconds
        setTimeout(() => {
          setEmail('');
          setSuccess(null);
        }, 3000);
      } else {
        setError('You are already friends with this user');
        setTimeout(() => {
          setEmail('');
          setError(null);
        }, 2000);
      }
    } else {
      // Handle different error cases with visual feedback
      const errorMsg = (response.message || '').toLowerCase();

      if (errorMsg.includes('not found') || errorMsg.includes('no user')) {
        setError('❌ User not found - No account with this email');
      } else if (errorMsg.includes('yourself')) {
        setError('❌ Cannot add yourself as a friend');
      } else if (errorMsg.includes('network') || errorMsg.includes('connection')) {
        setError('❌ Network error - Check your connection');
      } else {
        setError(`❌ ${response.message || 'Failed to add friend'}`);
      }

      // Clear email after 3 seconds
      setTimeout(() => {
        setEmail('');
        setError(null);
      }, 3000);
    }
  };

  const handleRemoveFriend = async (friendId, friendName) => {
    console.log('🔴 [1/8] handleRemoveFriend called: friendId=', friendId, 'friendName=', friendName);
    setRemovingFriendId(friendId);

    // Bypass Alert.alert for web testing - it doesn't work properly on web
    console.log('🔴 [1.5/8] User confirmed removal (auto-confirm for web), calling removeFriend');
    const response = await removeFriend(friendId);
    console.log('🔴 [8.5/8] Got response from removeFriend:', response);
    if (response.success) {
      console.log('✅ Friend removed successfully!');
      loadFriends();
      setRemovingFriendId(null);
    } else {
      console.log('❌ Failed to remove friend:', response.message);
      setRemovingFriendId(null);
    }
  };

  const getInitials = (name) => {
    if (!name) return '?';
    return name
      .split(' ')
      .map(word => word[0])
      .join('')
      .toUpperCase()
      .slice(0, 2);
  };

  const getAvatarColor = (name) => {
    const colors = ['#EF4444', '#F59E0B', '#10B981', '#3B82F6', '#8B5CF6', '#EC4899'];
    const index = (name || '').length % colors.length;
    return colors[index];
  };

  const renderFriendItem = ({ item }) => {
    const isRemoving = removingFriendId === (item.userId || item);
    const initials = getInitials(item.name);
    const avatarColor = getAvatarColor(item.name);

    return (
      <View style={[
        styles.friendItem,
        isRemoving && styles.friendItemRemoving
      ]}>
        <View style={styles.friendLeft}>
          <View style={[styles.avatar, { backgroundColor: avatarColor }]}>
            <Text style={styles.avatarText}>{initials}</Text>
          </View>
          <View style={styles.friendInfo}>
            <Text style={styles.friendName}>{item.name || 'Unknown'}</Text>
            <Text style={styles.friendEmail}>{item.email || item}</Text>
          </View>
        </View>
        <TouchableOpacity
          style={styles.removeButton}
          onPress={() => handleRemoveFriend(item.userId || item, item.name || 'this friend')}
          disabled={isRemoving}
        >
          {isRemoving ? (
            <ActivityIndicator size="small" color="#DC2626" />
          ) : (
            <Text style={styles.removeButtonText}>✕</Text>
          )}
        </TouchableOpacity>
      </View>
    );
  };

  return (
    <KeyboardAvoidingView
      style={styles.wrapper}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <View style={styles.container}>
        {/* Header */}
        <Text style={styles.title}>Friends</Text>

        {/* Add Friend Section */}
        <View style={styles.addFriendSection}>
          <Text style={styles.sectionTitle}>Add Friend by Email</Text>
          <TextInput
            style={[
              styles.input,
              error && styles.inputError,
              success && styles.inputSuccess
            ]}
            placeholder="friend@example.com"
            placeholderTextColor="#9CA3AF"
            value={email}
            onChangeText={(text) => {
              setEmail(text);
              setError(null); // Clear error when user types
              setSuccess(null); // Clear success when user types
            }}
            keyboardType="email-address"
            autoCapitalize="none"
            autoCorrect={false}
            editable={!isLoading}
          />
          {error && (
            <Text style={styles.errorText}>{error}</Text>
          )}
          {success && (
            <Text style={styles.successText}>{success}</Text>
          )}
          <TouchableOpacity
            style={[
              styles.addButton,
              isLoading && styles.addButtonDisabled,
              error && styles.addButtonError,
              success && styles.addButtonSuccess
            ]}
            onPress={handleAddFriend}
            disabled={isLoading}
            activeOpacity={0.7}
          >
            {isLoading ? (
              <View style={styles.buttonLoadingContainer}>
                <ActivityIndicator color="white" size="small" />
                <Text style={styles.buttonLoadingText}>Searching...</Text>
              </View>
            ) : (
              <Text style={styles.addButtonText}>Add Friend</Text>
            )}
          </TouchableOpacity>
        </View>

        {/* Friends List Section */}
        <View style={styles.friendsListSection}>
          <Text style={styles.sectionTitle}>
            Your Friends ({friends.length})
          </Text>

          {isLoadingFriends ? (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="large" color="#059669" />
            </View>
          ) : friends.length === 0 ? (
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyText}>No friends yet</Text>
              <Text style={styles.emptySubtext}>
                Add friends by entering their email above
              </Text>
            </View>
          ) : (
            <FlatList
              data={friends}
              renderItem={renderFriendItem}
              keyExtractor={(item, index) => item.userId || item || index.toString()}
              style={styles.friendsList}
              contentContainerStyle={styles.friendsListContent}
              refreshControl={
                <RefreshControl
                  refreshing={isLoadingFriends}
                  onRefresh={loadFriends}
                  colors={['#059669']}
                  tintColor="#059669"
                />
              }
              showsVerticalScrollIndicator={false}
            />
          )}
        </View>
      </View>
      <BottomNavBar />
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    flex: 1,
    backgroundColor: '#f9fafb',
  },
  container: {
    flex: 1,
    padding: 20,
    paddingBottom: 100,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#111827',
    marginBottom: 20,
    marginTop: 20,
  },
  addFriendSection: {
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 16,
    marginBottom: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 3,
    elevation: 3,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#374151',
    marginBottom: 12,
  },
  input: {
    backgroundColor: '#f9fafb',
    borderWidth: 1,
    borderColor: '#d1d5db',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    color: '#111827',
    marginBottom: 12,
  },
  inputError: {
    borderColor: '#ef4444',
    borderWidth: 2,
    backgroundColor: '#fef2f2',
  },
  inputSuccess: {
    borderColor: '#10b981',
    borderWidth: 2,
    backgroundColor: '#f0fdf4',
  },
  errorText: {
    color: '#dc2626',
    fontSize: 14,
    fontWeight: '600',
    marginTop: -8,
    marginBottom: 12,
    paddingHorizontal: 4,
  },
  successText: {
    color: '#059669',
    fontSize: 14,
    fontWeight: '600',
    marginTop: -8,
    marginBottom: 12,
    paddingHorizontal: 4,
  },
  addButton: {
    backgroundColor: '#059669',
    borderRadius: 8,
    padding: 14,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
  },
  addButtonDisabled: {
    backgroundColor: '#9CA3AF',
  },
  addButtonError: {
    backgroundColor: '#dc2626',
  },
  addButtonSuccess: {
    backgroundColor: '#10b981',
  },
  addButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
  buttonLoadingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  buttonLoadingText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
    marginLeft: 8,
  },
  friendsListSection: {
    flex: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 40,
  },
  emptyContainer: {
    alignItems: 'center',
    paddingTop: 40,
  },
  emptyText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#6B7280',
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#9CA3AF',
    textAlign: 'center',
  },
  friendsList: {
    flex: 1,
  },
  friendsListContent: {
    paddingBottom: 20,
  },
  friendItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: 'white',
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 3,
    borderWidth: 1,
    borderColor: '#f3f4f6',
  },
  friendItemRemoving: {
    backgroundColor: '#fef2f2',
    borderWidth: 2,
    borderColor: '#fecaca',
    opacity: 0.7,
  },
  friendLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14,
  },
  avatarText: {
    color: 'white',
    fontSize: 18,
    fontWeight: '700',
  },
  friendInfo: {
    flex: 1,
  },
  friendName: {
    fontSize: 17,
    fontWeight: '600',
    color: '#111827',
    marginBottom: 4,
  },
  friendEmail: {
    fontSize: 14,
    color: '#6B7280',
  },
  removeButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: '#FEE2E2',
    justifyContent: 'center',
    alignItems: 'center',
    marginLeft: 8,
  },
  removeButtonText: {
    color: '#DC2626',
    fontSize: 20,
    fontWeight: '600',
  },
});
