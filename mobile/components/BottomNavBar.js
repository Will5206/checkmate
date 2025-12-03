import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';

export default function BottomNavBar() {
  const navigation = useNavigation();
  const route = useRoute();

  const navigationItems = [
    {
      name: 'Pending',
      screen: 'Pending',
      icon: 'timer-outline',
      activeIcon: 'timer',
    },
    {
      name: 'History',
      screen: 'Activity', 
      icon: 'archive-outline',
      activeIcon: 'archive',
    },
    {
      name: 'Scan',
      screen: 'ScanReceipt', 
      icon: 'receipt-outline',
      activeIcon: 'receipt',
      isCenter: true,
    },
    {
      name: 'Friends',
      screen: 'Friends',  
      icon: 'people-outline',
      activeIcon: 'people',
    },
    {
      name: 'Home',
      screen: 'Home',
      icon: 'home-outline',
      activeIcon: 'home',
    },
  ];

  return (
    <View style={styles.container}>
      <View style={styles.navItems}>
        {navigationItems.map((item) => {
          const isActive = route.name === item.screen;
          const iconName = isActive ? item.activeIcon : item.icon;

          if (item.isCenter) {
            return (
              <TouchableOpacity
                key={item.name}
                onPress={() => navigation.navigate(item.screen)}
                style={styles.centerButton}
              >
                <View style={styles.centerIconContainer}>
                  <Ionicons name={iconName} size={28} color="#fff" />
                </View>
                <Text style={styles.centerLabel}>{item.name}</Text>
              </TouchableOpacity>
            );
          }

          return (
            <TouchableOpacity
              key={item.name}
              onPress={() => navigation.navigate(item.screen)}
              style={styles.navItem}
            >
              <Ionicons
                name={iconName}
                size={24}
                color={isActive ? '#0d9488' : '#6B7280'}
              />
              <Text
                style={[
                  styles.navLabel,
                  isActive && styles.navLabelActive,
                ]}
              >
                {item.name}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: '#fff',
    borderTopWidth: 1,
    borderTopColor: '#E5E7EB',
    paddingVertical: 8,
    paddingHorizontal: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 5,
  },
  navItems: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
  },
  navItem: {
    alignItems: 'center',
    paddingVertical: 8,
    paddingHorizontal: 12,
    minWidth: 0,
  },
  navLabel: {
    fontSize: 12,
    marginTop: 4,
    color: '#6B7280',
    fontWeight: '500',
  },
  navLabelActive: {
    color: '#0d9488',
  },
  centerButton: {
    alignItems: 'center',
  },
  centerIconContainer: {
    width: 56,
    height: 56,
    backgroundColor: '#0d9488',
    borderRadius: 28,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: -20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 5,
  },
  centerLabel: {
    fontSize: 12,
    marginTop: 4,
    color: '#0d9488',
    fontWeight: '600',
  },
});

