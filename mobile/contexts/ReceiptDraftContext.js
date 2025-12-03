import React, { createContext, useContext, useState } from 'react';

/**
 * ReceiptDraftContext
 * Manages persistent draft state for parsed receipts in the ScanReceipt flow
 * - Stores parsed receipt data so BillReview persists across tab navigation
 * - Cleared when user sends receipt or retakes photo
 * - Persists across tab switches (survives component unmount/remount)
 */

const ReceiptDraftContext = createContext(null);

export function ReceiptDraftProvider({ children }) {
  const [draftReceipt, setDraftReceipt] = useState(null);

  /**
   * Set draft receipt data (parsed receipt from scan)
   * @param {Object} receiptData - Parsed receipt data
   */
  const setDraft = (receiptData) => {
    setDraftReceipt(receiptData);
  };

  /**
   * Clear draft receipt data
   */
  const clearDraft = () => {
    setDraftReceipt(null);
  };

  const value = {
    draftReceipt,
    setDraft,
    clearDraft,
  };

  return (
    <ReceiptDraftContext.Provider value={value}>
      {children}
    </ReceiptDraftContext.Provider>
  );
}

/**
 * Hook to use ReceiptDraftContext
 * @returns {Object} { draftReceipt, setDraft, clearDraft }
 */
export function useReceiptDraft() {
  const context = useContext(ReceiptDraftContext);
  if (!context) {
    throw new Error('useReceiptDraft must be used within ReceiptDraftProvider');
  }
  return context;
}

