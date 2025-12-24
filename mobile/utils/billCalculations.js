/**
 * Utility functions for bill calculations
 */

/**
 * Calculate item total with proportional tax and tip
 * 
 * @param {number} itemPrice - Price of the item
 * @param {number} subtotal - Total subtotal of the bill
 * @param {number} tax - Tax amount
 * @param {number} tip - Tip amount
 * @returns {number} Total price including proportional tax and tip
 */
export function calculateItemTotal(itemPrice, subtotal, tax, tip) {
  if (!subtotal || subtotal === 0) return itemPrice;
  const proportion = itemPrice / subtotal;
  const itemTax = tax * proportion;
  const itemTip = tip * proportion;
  return itemPrice + itemTax + itemTip;
}

/**
 * Get user's claimed items from bill data and assignments
 * 
 * @param {Array} items - Array of bill items
 * @param {Object} itemAssignments - Map of itemId -> quantity claimed
 * @returns {Array} Array of claimed items with quantities
 */
export function getMyItems(items, itemAssignments) {
  return items.filter((item) => {
    const itemId = item.itemId || item.id;
    return itemAssignments[itemId] && itemAssignments[itemId] > 0;
  }).map((item) => {
    const itemId = item.itemId || item.id;
    const qty = itemAssignments[itemId] || 1;
    return {
      ...item,
      qty: qty,
      totalPrice: item.price * qty,
    };
  });
}

/**
 * Calculate total for my items including tax and tip
 * 
 * @param {Array} myItems - Array of claimed items
 * @param {number} subtotal - Bill subtotal
 * @param {number} tax - Tax amount
 * @param {number} tip - Tip amount
 * @returns {number} Total amount for my items
 */
export function calculateMyItemsTotal(myItems, subtotal, tax, tip) {
  return myItems.reduce((sum, item) => {
    return sum + calculateItemTotal(item.totalPrice, subtotal, tax, tip);
  }, 0);
}






