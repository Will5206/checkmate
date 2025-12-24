/**
 * Custom hook for managing friends selection in bill review
 * Handles friends list loading and selection state
 */

import { useState, useEffect } from 'react';
import { getFriendsList } from '../services/friendsService';

export function useFriendsSelection(isFromActivity) {
  const [friends, setFriends] = useState([]);
  const [friendsEmails, setFriendsEmails] = useState('');
  const [selectedFriendEmails, setSelectedFriendEmails] = useState([]);
  const [showFriendsDropdown, setShowFriendsDropdown] = useState(false);
  const [isLoadingFriends, setIsLoadingFriends] = useState(false);

  // Load friends list when creating new receipt (not from activity)
  useEffect(() => {
    if (!isFromActivity) {
      loadFriends();
    }
  }, [isFromActivity]);

  // Sync selectedFriendEmails with friendsEmails when manually typed
  useEffect(() => {
    if (!isFromActivity) {
      const typedEmails = friendsEmails.split(',').map(e => e.trim()).filter(e => e);
      const remainingSelected = selectedFriendEmails.filter(email => 
        typedEmails.includes(email)
      );
      if (remainingSelected.length !== selectedFriendEmails.length) {
        setSelectedFriendEmails(remainingSelected);
      }
    }
  }, [friendsEmails, isFromActivity, selectedFriendEmails]);

  const loadFriends = async () => {
    setIsLoadingFriends(true);
    try {
      const response = await getFriendsList();
      if (response.success) {
        setFriends(response.friends || []);
      }
    } catch (error) {
      console.error('Error loading friends:', error);
    } finally {
      setIsLoadingFriends(false);
    }
  };

  const handleFriendSelect = (friendEmail) => {
    if (!selectedFriendEmails.includes(friendEmail)) {
      const newSelected = [...selectedFriendEmails, friendEmail];
      setSelectedFriendEmails(newSelected);
      
      const currentEmails = friendsEmails.split(',').map(e => e.trim()).filter(e => e);
      if (!currentEmails.includes(friendEmail)) {
        const updatedEmails = currentEmails.length > 0 
          ? `${friendsEmails}, ${friendEmail}`
          : friendEmail;
        setFriendsEmails(updatedEmails);
      }
    }
    setShowFriendsDropdown(false);
  };

  const handleRemoveSelectedFriend = (emailToRemove) => {
    const newSelected = selectedFriendEmails.filter(email => email !== emailToRemove);
    setSelectedFriendEmails(newSelected);
    
    const currentEmails = friendsEmails.split(',').map(e => e.trim()).filter(e => e && e !== emailToRemove);
    setFriendsEmails(currentEmails.join(', '));
  };

  return {
    friends,
    friendsEmails,
    setFriendsEmails,
    selectedFriendEmails,
    showFriendsDropdown,
    setShowFriendsDropdown,
    isLoadingFriends,
    handleFriendSelect,
    handleRemoveSelectedFriend,
  };
}






