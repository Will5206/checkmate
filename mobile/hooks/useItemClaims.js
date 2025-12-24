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
    console.log('[useItemClaims] 🔵 STEP 1: handleToggleItemClaim called - itemId=' + itemId + ', receiptId=' + receiptId);
    
    if (!receiptId) {
      console.error('[useItemClaims] 🔴 ERROR: No receiptId provided');
      return;
    }
    
    // Prevent double-clicks and concurrent claims on the same item
    if (claimingItems.has(itemId)) {
      console.log('[useItemClaims] ⚠️ WARNING: Item already being processed, ignoring click');
      return; // Already processing this item
    }
    
    // Find the item to get its originalItemId (for backend) or use itemId if not expanded
    const item = billData.items.find(i => (i.itemId || i.id) === itemId);
    const backendItemId = item?.originalItemId || itemId; // Use originalItemId if available, otherwise use itemId
    
    console.log('[useItemClaims] 🔵 STEP 2: Item found - frontendItemId=' + itemId + ', backendItemId=' + backendItemId);
    
    const isClaimed = itemAssignments[itemId] && itemAssignments[itemId] > 0;
    console.log('[useItemClaims] 🔵 STEP 3: Current state - isClaimed=' + isClaimed);
    
    // Mark item as being processed
    setClaimingItems(prev => new Set(prev).add(itemId));
    console.log('[useItemClaims] 🔵 STEP 4: Marked item as processing');
    
    // Optimistic update: Update UI immediately before API call completes
    const previousAssignments = { ...itemAssignments };
    const newAssignments = { ...itemAssignments };
    if (isClaimed) {
      delete newAssignments[itemId];
      console.log('[useItemClaims] 🔵 STEP 5: Optimistic update - UNCLAIMING item (removing from assignments)');
    } else {
      newAssignments[itemId] = 1;
      console.log('[useItemClaims] 🔵 STEP 5: Optimistic update - CLAIMING item (adding to assignments)');
    }
    setItemAssignments(newAssignments); // Update UI immediately - instant feedback!
    console.log('[useItemClaims] 🔵 STEP 6: UI updated optimistically, making API call...');
    
    try {
      let response;
      const startTime = Date.now();
      
      if (isClaimed) {
        console.log('[useItemClaims] 🔵 STEP 7: Calling unclaimItem API...');
        response = await unclaimItem(receiptId, backendItemId);
      } else {
        console.log('[useItemClaims] 🔵 STEP 7: Calling claimItem API...');
        response = await claimItem(receiptId, backendItemId, 1);
      }
      
      const duration = Date.now() - startTime;
      console.log('[useItemClaims] 🔵 STEP 8: API call completed in ' + duration + 'ms');
      console.log('[useItemClaims] 🔵 STEP 8: Response - success=' + response?.success + ', message=' + (response?.message || 'none'));
      
      if (response && response.success) {
        console.log('[useItemClaims] ✅ STEP 9: API call successful - optimistic update was correct');
        // Success - optimistic update was correct
        // Return the response so parent can update owed amounts
        return { success: true, ...response };
      } else {
        console.error('[useItemClaims] 🔴 STEP 9: API call failed - rolling back optimistic update');
        console.error('[useItemClaims] 🔴 ERROR: Response details:', JSON.stringify(response));
        // Rollback optimistic update on error
        setItemAssignments(previousAssignments);
        Alert.alert('Error', response?.message || 'Failed to update item claim');
        return null;
      }
    } catch (error) {
      console.error('[useItemClaims] 🔴 STEP 9: Exception caught - rolling back optimistic update');
      console.error('[useItemClaims] 🔴 ERROR: Exception type:', error?.constructor?.name);
      console.error('[useItemClaims] 🔴 ERROR: Exception message:', error?.message);
      console.error('[useItemClaims] 🔴 ERROR: Full error:', error);
      // Rollback optimistic update on error
      setItemAssignments(previousAssignments);
      Alert.alert('Error', 'Failed to update item claim: ' + (error?.message || 'Network error'));
      return null;
    } finally {
      // Remove from claiming set so item can be clicked again
      setClaimingItems(prev => {
        const next = new Set(prev);
        next.delete(itemId);
        return next;
      });
      console.log('[useItemClaims] 🔵 STEP 10: Cleanup complete - item removed from processing set');
    }
  }, [receiptId, billData.items, itemAssignments, claimingItems]);

  return {
    itemAssignments,
    setItemAssignments,
    claimingItems,
    handleToggleItemClaim,
  };
}

