/**
 * Utility functions for transforming receipt data
 */

/**
 * Transform backend receipt data to match frontend format
 * Expands items with qty > 1 into multiple items with qty=1
 * 
 * @param {Object} data - Backend receipt data
 * @returns {Object} Transformed receipt data
 */
export function transformReceiptData(data) {
  // Backend parser returns: items (with name, qty, price), subtotal, tax, total, merchant
  // Note: price in items might be per-item or line total - we'll treat it as per-item
  // Expand items with qty > 1 into multiple items with qty=1 so users can claim individually
  const expandedItems = [];
  let itemIndex = 0;
  
  (data.items || []).forEach((item, originalIndex) => {
    const baseItemId = item.itemId || item.id || (originalIndex + 1);
    const qty = item.qty || item.quantity || 1;
    const price = parseFloat(item.price) || 0;
    const name = item.name || 'Unknown Item';
    
    // Create one item per quantity, each with qty=1
    for (let i = 0; i < qty; i++) {
      expandedItems.push({
        itemId: `${baseItemId}_${i}`, // Unique ID for each instance (e.g., "1_0", "1_1")
        originalItemId: baseItemId, // Keep reference to original itemId for backend operations
        id: `${baseItemId}_${i}`,
        name: name,
        price: price, // Price per item
        qty: 1, // Always 1 for expanded items
      });
      itemIndex++;
    }
  });

  const total = parseFloat(data.total) || 0;
  const tax = parseFloat(data.tax) || 0;
  const tip = parseFloat(data.tip) || 0;
  const subtotal = parseFloat(data.subtotal) || 0;
  
  // Calculate subtotal if not provided (sum of price * qty for all items)
  // Since all items now have qty=1, this is just sum of prices
  const calculatedSubtotal = subtotal || (expandedItems.reduce((sum, item) => sum + item.price, 0));
  
  // Calculate tip if not provided (as difference between total and subtotal + tax)
  const calculatedTip = tip || Math.max(0, total - calculatedSubtotal - tax);

  return {
    restaurant_name: data.merchant || data.restaurant_name || 'Unknown Merchant',
    date: data.date || new Date().toLocaleDateString(),
    items: expandedItems,
    tax: tax || 0,
    tip: calculatedTip,
    total: total,
    subtotal: calculatedSubtotal,
  };
}






