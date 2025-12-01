import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import BottomNavBar from '../components/BottomNavBar';
import { colors, spacing, typography } from '../styles/theme';
import { logoutUser } from '../services/authService';

export default function ProfileScreen() {
  const navigation = useNavigation();
  const [balance] = useState(247.83);
  const [userName, setUserName] = useState('Mike Baron');
  const [userEmail, setUserEmail] = useState('mike.baron@email.com');

  useEffect(() => {
    // Load user info from AsyncStorage
    const loadUserInfo = async () => {
      const storedName = await AsyncStorage.getItem('userName');
      const storedEmail = await AsyncStorage.getItem('userEmail');
      if (storedName) setUserName(storedName);
      if (storedEmail) setUserEmail(storedEmail);
    };
    loadUserInfo();
  }, []);

  const getInitials = (name) => {
    return name
      .split(' ')
      .map((n) => n[0])
      .join('')
      .toUpperCase();
  };

  const handleLogout = async () => {
    Alert.alert('Log Out', 'Are you sure you want to log out?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Log Out',
        style: 'destructive',
        onPress: async () => {
          await logoutUser();
          navigation.replace('Login');
        },
      },
    ]);
  };

  const menuItems = [
    {
      icon: 'card-outline',
      title: 'Attached Cards',
      description: 'Manage your payment methods',
      action: () => console.log('Cards'),
    },
    {
      icon: 'cash-outline',
      title: 'Deposit or Withdraw Money',
      description: 'Add funds or cash out',
      action: () => console.log('Deposit/Withdraw'),
    },
    {
      icon: 'settings-outline',
      title: 'Settings',
      description: 'App preferences and account',
      action: () => console.log('Settings'),
    },
    {
      icon: 'help-circle-outline',
      title: 'FAQs',
      description: 'Get help and support',
      action: () => console.log('FAQs'),
    },
    {
      icon: 'log-out-outline',
      title: 'Log Out',
      description: 'Sign out of your account',
      action: handleLogout,
      isDestructive: true,
    },
  ];

  return (
    <View style={styles.wrapper}>
      {/* Status Bar Buffer */}
      <View style={styles.statusBarBuffer} />
      <ScrollView style={styles.container} contentContainerStyle={styles.scrollContent}>
        {/* Header */}
        <View style={styles.header}>
          <View style={styles.profileContainer}>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>{getInitials(userName)}</Text>
            </View>
            <View style={styles.profileInfo}>
              <Text style={styles.userName}>{userName}</Text>
              <Text style={styles.userEmail}>{userEmail}</Text>
            </View>
          </View>
        </View>

        {/* Balance Card */}
        <View style={styles.balanceCardContainer}>
          <View style={styles.balanceCard}>
            <Text style={styles.balanceLabel}>CheckMate Balance</Text>
            <Text style={styles.balanceAmount}>${balance.toFixed(2)}</Text>
            <View style={styles.balanceActions}>
              <TouchableOpacity style={styles.balanceButton}>
                <Ionicons name="add-circle-outline" size={16} color="#fff" />
                <Text style={styles.balanceButtonText}>Add Money</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.balanceButton}>
                <Ionicons name="remove-circle-outline" size={16} color="#fff" />
                <Text style={styles.balanceButtonText}>Cash Out</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>

        {/* Menu Items */}
        <View style={styles.menuContainer}>
          {menuItems.map((item, index) => (
            <TouchableOpacity
              key={index}
              style={styles.menuItem}
              onPress={item.action}
            >
              <View style={styles.menuItemContent}>
                <View style={styles.menuItemLeft}>
                  <View
                    style={[
                      styles.menuIconContainer,
                      item.isDestructive && styles.menuIconContainerDestructive,
                    ]}
                  >
                    <Ionicons
                      name={item.icon}
                      size={20}
                      color={item.isDestructive ? colors.error : colors.textLight}
                    />
                  </View>
                  <View style={styles.menuItemText}>
                    <Text
                      style={[
                        styles.menuItemTitle,
                        item.isDestructive && styles.menuItemTitleDestructive,
                      ]}
                    >
                      {item.title}
                    </Text>
                    <Text style={styles.menuItemDescription}>{item.description}</Text>
                  </View>
                </View>
                <Ionicons name="chevron-forward-outline" size={20} color={colors.textLight} />
              </View>
            </TouchableOpacity>
          ))}
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
  statusBarBuffer: {
    height: 44, // Standard iOS status bar height
    backgroundColor: colors.white,
  },
  container: {
    flex: 1,
  },
  scrollContent: {
    paddingBottom: 120, // Space for bottom nav bar
  },
  header: {
    backgroundColor: colors.white,
    paddingTop: spacing.lg,
    paddingBottom: spacing.lg,
    paddingHorizontal: spacing.md,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  profileContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
  },
  avatar: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: '#b2f5ea',
    justifyContent: 'center',
    alignItems: 'center',
  },
  avatarText: {
    fontSize: typography.sizes.xl,
    fontWeight: 'bold',
    color: '#0d9488',
  },
  profileInfo: {
    flex: 1,
  },
  userName: {
    fontSize: typography.sizes.xxl,
    fontWeight: 'bold',
    color: colors.text,
    marginBottom: spacing.xs / 2,
  },
  userEmail: {
    fontSize: typography.sizes.md,
    color: colors.textLight,
  },
  balanceCardContainer: {
    paddingHorizontal: spacing.md,
    marginTop: -spacing.md,
    marginBottom: spacing.md,
  },
  balanceCard: {
    backgroundColor: '#0d9488',
    borderRadius: 12,
    padding: spacing.lg,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 5,
  },
  balanceLabel: {
    fontSize: typography.sizes.sm,
    color: 'rgba(255, 255, 255, 0.9)',
    marginBottom: spacing.xs,
    textAlign: 'center',
  },
  balanceAmount: {
    fontSize: typography.sizes.xxl * 1.5,
    fontWeight: 'bold',
    color: colors.white,
    marginBottom: spacing.md,
    textAlign: 'center',
  },
  balanceActions: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: spacing.sm,
  },
  balanceButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: 8,
    gap: spacing.xs,
  },
  balanceButtonText: {
    color: colors.white,
    fontSize: typography.sizes.sm,
    fontWeight: '600',
  },
  menuContainer: {
    paddingHorizontal: spacing.md,
    gap: spacing.xs,
  },
  menuItem: {
    backgroundColor: colors.white,
    borderRadius: 12,
    marginBottom: spacing.xs,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  menuItemContent: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.md,
  },
  menuItemLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    gap: spacing.md,
  },
  menuIconContainer: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.background,
    justifyContent: 'center',
    alignItems: 'center',
  },
  menuIconContainerDestructive: {
    backgroundColor: '#fee2e2',
  },
  menuItemText: {
    flex: 1,
  },
  menuItemTitle: {
    fontSize: typography.sizes.md,
    fontWeight: '600',
    color: colors.text,
    marginBottom: spacing.xs / 2,
  },
  menuItemTitleDestructive: {
    color: colors.error,
  },
  menuItemDescription: {
    fontSize: typography.sizes.sm,
    color: colors.textLight,
  },
});
