/**
 * Custom hook for managing item claims
 * Handles claiming/unclaiming items with optimistic updates
 */

import { useState, useCallback } from 'react';
import { Alert } from 'react-native';
import { claimItem, unclaimItem } from '../services/receiptsService';

export function useItemClaims(receiptId, billData) {
  const [itemAssignments, setItemAssignments] = useState({}); // itemId -> quantity
  const [claimingItems, setClaimingItems] = useState(new Set()); // Track items being claimed

  const handleToggleItemClaim = useCallback(async (itemId) => {
    if (!receiptId) return;
    
    // Prevent double-clicks and concurrent claims on the same item
    if (claimingItems.has(itemId)) {
      return; // Already processing this item
    }
    
    // Find the item to get its originalItemId (for backend) or use itemId if not expanded
    const item = billData.items.find(i => (i.itemId || i.id) === itemId);
    const backendItemId = item?.originalItemId || itemId; // Use originalItemId if available, otherwise use itemId
    
    const isClaimed = itemAssignments[itemId] && itemAssignments[itemId] > 0;
    
    // Mark item as being processed
    setClaimingItems(prev => new Set(prev).add(itemId));
    
    // Optimistic update: Update UI immediately before API call completes
    const previousAssignments = { ...itemAssignments };
    const newAssignments = { ...itemAssignments };
    if (isClaimed) {
      delete newAssignments[itemId];
    } else {
      newAssignments[itemId] = 1;
    }
    setItemAssignments(newAssignments); // Update UI immediately - instant feedback!
    
    try {
      let response;
      if (isClaimed) {
        response = await unclaimItem(receiptId, backendItemId);
      } else {
        response = await claimItem(receiptId, backendItemId, 1);
      }
      
      if (response.success) {
        // Success - optimistic update was correct
        // Return the response so parent can update owed amounts
        return { success: true, ...response };
      } else {
        // Rollback optimistic update on error
        setItemAssignments(previousAssignments);
        Alert.alert('Error', response.message || 'Failed to update item claim');
        return null;
      }
    } catch (error) {
      // Rollback optimistic update on error
      setItemAssignments(previousAssignments);
      console.error('Error toggling item claim:', error);
      Alert.alert('Error', 'Failed to update item claim');
      return null;
    } finally {
      // Remove from claiming set so item can be clicked again
      setClaimingItems(prev => {
        const next = new Set(prev);
        next.delete(itemId);
        return next;
      });
    }
  }, [receiptId, billData.items, itemAssignments, claimingItems]);

  return {
    itemAssignments,
    setItemAssignments,
    claimingItems,
    handleToggleItemClaim,
  };
}

