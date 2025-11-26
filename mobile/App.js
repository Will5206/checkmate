import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { View, Text, StyleSheet } from 'react-native';
import LoginScreen from './screens/LoginScreen';
import SignupScreen from './screens/SignupScreen';
import HomeScreen from './screens/HomeScreen';
import BillReview from './screens/BillReview';
import ActivityScreen from './screens/ActivityScreen';
import FriendsScreen from './screens/FriendsScreen';
import BottomNavBar from './components/BottomNavBar';

const Stack = createNativeStackNavigator();

// Placeholder screens for navigation tabs (you'll replace these with actual screens later)
function PlaceholderScreen({ screenName }) {
  return (
    <View style={styles.placeholderWrapper}>
      <View style={styles.placeholder}>
        <Text style={styles.placeholderText}>{screenName} Screen</Text>
        <Text style={styles.placeholderSubtext}>Coming soon...</Text>
      </View>
      <BottomNavBar />
    </View>
  );
}

function GroupPotsScreen() {
  return <PlaceholderScreen screenName="Group Pots" />;
}

function ScanReceiptScreen() {
  return <HomeScreen />; // Using HomeScreen for now since it's the scan receipt functionality
}

export default function App() {
  return (
    <NavigationContainer>
      <Stack.Navigator
        initialRouteName="Login"
        screenOptions={{
          headerShown: false,
          animation: 'none',
          gestureEnabled: false,
        }}
      >
        <Stack.Screen name="Login" component={LoginScreen} />
        <Stack.Screen name="Signup" component={SignupScreen} />
        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen name="BillReview" component={BillReview} />
        <Stack.Screen name="GroupPots" component={GroupPotsScreen} />
        <Stack.Screen name="Friends" component={FriendsScreen} />
        <Stack.Screen name="ScanReceipt" component={ScanReceiptScreen} />
        <Stack.Screen name="Activity" component={ActivityScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  placeholderWrapper: {
    flex: 1,
    backgroundColor: '#f9fafb',
  },
  placeholder: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f9fafb',
    paddingBottom: 100, // Space for bottom nav bar
  },
  placeholderText: {
    fontSize: 24,
    fontWeight: '700',
    color: '#374151',
    marginBottom: 8,
  },
  placeholderSubtext: {
    fontSize: 16,
    color: '#6B7280',
  },
});
